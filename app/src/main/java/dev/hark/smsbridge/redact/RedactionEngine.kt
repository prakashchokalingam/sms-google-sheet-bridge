package dev.hark.smsbridge.redact

import java.util.concurrent.ConcurrentHashMap

class RedactionEngine(
    private val timeoutMs: Long = 60L,
    private val maxBodyLength: Int = 16_384,
    private val maxMatches: Int = 128
) {

    private data class CompiledRule(val rule: RedactionRule, val regex: Regex)

    private data class Span(
        val start: Int,
        val end: Int,
        val ruleId: String,
        val replacement: String
    )

    private val regexCache = ConcurrentHashMap<String, Regex?>()

    private val residualPatterns = listOf(
        "(?i)\\b${BuiltInRules.strongOtpWords}\\b[^\\d]{0,40}?(?<!\\d)\\d(?:[\\s\\-.]?\\d){3,9}(?!\\d)",
        "(?i)(?<!\\d)\\d(?:[\\s\\-.]?\\d){3,9}(?!\\d)[^\\d]{0,40}?\\b${BuiltInRules.strongOtpWords}\\b"
    ).mapNotNull { pattern ->
        runCatching { Regex(pattern) }.getOrNull()
    }.map { pattern ->
        CompiledRule(
            RedactionRule(
                id = "residual",
                name = "residual",
                pattern = pattern.pattern,
                action = RuleAction.SKIP
            ),
            pattern
        )
    }

    fun validate(rule: RedactionRule): String? {
        if (rule.id.isBlank()) return "Rule id is empty"
        if (rule.name.isBlank()) return "Rule name is empty"
        if (rule.pattern.isBlank()) return "Regex is empty"
        if (rule.pattern.length > 512) return "Regex is longer than 512 characters"
        if (rule.action == RuleAction.MASK && rule.replacement.isEmpty()) return "Replacement is empty"

        val regex = regexFor(rule.pattern) ?: return "Regex does not compile"

        val groupCount = regex.toPattern().matcher("").groupCount()
        if (rule.group < 0 || rule.group > groupCount) {
            return if (groupCount == 0) {
                "Regex has no capture group. Use group 0 to mask the whole match."
            } else {
                "Capture group ${rule.group} does not exist. This regex has $groupCount."
            }
        }

        if (regex.matches("")) return "Regex matches empty text"

        val samples = listOf(
            "a".repeat(96) + "!",
            "1 ".repeat(96) + "x",
            "0".repeat(512),
            "OTP " + "9".repeat(64)
        )
        for (sample in samples) {
            val deadline = System.nanoTime() + 80_000_000L
            try {
                regex.containsMatchIn(DeadlineCharSequence(sample, deadline))
            } catch (e: RegexTimeoutException) {
                return "Regex is too slow"
            } catch (e: StackOverflowError) {
                return "Regex is too complex"
            }
        }
        return null
    }

    fun validateAll(rules: List<RedactionRule>): Map<String, String> {
        val errors = LinkedHashMap<String, String>()
        for (rule in rules.filter { it.enabled }) {
            validate(rule)?.let { errors[rule.id] = it }
        }
        return errors
    }

    fun evaluate(
        body: String,
        rules: List<RedactionRule>,
        mode: SensitivityMode = SensitivityMode.MASK,
        sweep: Boolean = true
    ): RedactionDecision {
        if (body.isBlank()) return RedactionDecision.Allow(body)
        if (body.length > maxBodyLength) {
            return RedactionDecision.Skip("body-too-long")
        }

        val normalized = TextNormalizer.normalizePreservingLength(body)
        val enabled = rules.filter { it.enabled }

        val errors = LinkedHashMap<String, String>()
        for (rule in enabled) {
            quickValidate(rule)?.let { errors[rule.id] = it }
        }
        if (errors.isNotEmpty()) {
            return RedactionDecision.Skip("config-error", errors.keys.toList())
        }

        val compiled = enabled.mapNotNull { rule ->
            regexFor(rule.pattern)?.let { CompiledRule(rule, it) }
        }
        val skipRules = compiled.filter { it.rule.action == RuleAction.SKIP }.sortedBy { it.rule.priority }
        val maskRules = compiled.filter { it.rule.action == RuleAction.MASK }.sortedBy { it.rule.priority }

        for (rule in skipRules) {
            val matches = findMatches(rule, normalized)
                ?: return RedactionDecision.Skip("rule-timeout:${rule.rule.id}", listOf(rule.rule.id))
            if (matches.isNotEmpty()) {
                return RedactionDecision.Skip("rule:${rule.rule.id}", listOf(rule.rule.id))
            }
        }

        val spans = ArrayList<Span>()
        val used = LinkedHashSet<String>()

        for (rule in maskRules) {
            val matches = findMatches(rule, normalized)
                ?: return RedactionDecision.Skip("rule-timeout:${rule.rule.id}", used.toList())
            for (match in matches) {
                val target = match.groups[rule.rule.group]
                    ?: return RedactionDecision.Skip("rule-group:${rule.rule.id}", listOf(rule.rule.id))
                if (target.value.isEmpty()) {
                    return RedactionDecision.Skip("rule-empty:${rule.rule.id}", listOf(rule.rule.id))
                }
                val range = target.range
                spans.add(
                    Span(
                        start = range.first,
                        end = range.last + 1,
                        ruleId = rule.rule.id,
                        replacement = rule.rule.replacement
                    )
                )
                used.add(rule.rule.id)
            }
        }

        if (sweep) {
            val swept = sweepSpans(normalized)
            if (swept.isNotEmpty()) {
                spans.addAll(swept)
                used.add("sweep.otp")
            }
        }

        if (spans.isEmpty()) return RedactionDecision.Allow(body)
        if (mode == SensitivityMode.GATE) {
            return RedactionDecision.Skip("gate", used.toList())
        }

        val merged = merge(spans)
        val masked = applySpans(body, merged)
        val usedList = used.toList()

        for (rule in skipRules) {
            val matches = findMatches(rule, masked)
                ?: return RedactionDecision.Skip("rescan-timeout:${rule.rule.id}", usedList + rule.rule.id)
            if (matches.isNotEmpty()) {
                return RedactionDecision.Skip("rescan:${rule.rule.id}", usedList + rule.rule.id)
            }
        }

        if (hasResidualRisk(masked)) {
            return RedactionDecision.Skip("residual", usedList)
        }

        return RedactionDecision.Mask(masked, merged.size, usedList)
    }

    private fun quickValidate(rule: RedactionRule): String? {
        if (rule.pattern.isBlank()) return "Regex is empty"
        if (rule.pattern.length > 512) return "Regex is longer than 512 characters"
        if (rule.action == RuleAction.MASK && rule.replacement.isEmpty()) return "Replacement is empty"
        val regex = regexFor(rule.pattern) ?: return "Regex does not compile"
        val groupCount = regex.toPattern().matcher("").groupCount()
        if (rule.group < 0 || rule.group > groupCount) return "Capture group ${rule.group} does not exist"
        if (regex.matches("")) return "Regex matches empty text"
        return null
    }

    private fun regexFor(pattern: String): Regex? {
        return regexCache.computeIfAbsent(pattern) {
            runCatching { Regex(pattern) }.getOrNull()
        }
    }

    private fun findMatches(rule: CompiledRule, text: String): List<MatchResult>? {
        return try {
            val deadline = System.nanoTime() + timeoutMs * 1_000_000L
            val input = DeadlineCharSequence(text, deadline)
            val results = ArrayList<MatchResult>()
            var index = 0
            while (index <= text.length) {
                val match = rule.regex.find(input, index) ?: break
                results.add(match)
                if (results.size > maxMatches) return null
                val next = match.range.last + 1
                index = if (next > index) next else index + 1
            }
            results
        } catch (e: RegexTimeoutException) {
            null
        } catch (e: StackOverflowError) {
            null
        }
    }

    private fun sweepSpans(text: String): List<Span> {
        val keywordRegex = runCatching {
            Regex("(?i)\\b${BuiltInRules.strongOtpWords}\\b")
        }.getOrNull() ?: return emptyList()

        val keywords = ArrayList<IntRange>()
        var scan = 0
        while (scan <= text.length) {
            val match = keywordRegex.find(text, scan) ?: break
            keywords.add(match.range)
            if (keywords.size > 64) break
            scan = match.range.last + 1
        }
        if (keywords.isEmpty()) return emptyList()

        val codeRegex = runCatching {
            Regex("(?<!\\d)\\d(?:[\\s\\-.]?\\d){3,9}(?!\\d)")
        }.getOrNull() ?: return emptyList()

        val spans = ArrayList<Span>()
        var index = 0
        while (index <= text.length) {
            val match = codeRegex.find(text, index) ?: break
            if (spans.size > 256) break
            val start = match.range.first
            val end = match.range.last + 1
            val near = keywords.any { keyword ->
                val keywordEnd = keyword.last + 1
                val gap = when {
                    keywordEnd <= start -> start - keywordEnd
                    keyword.first >= end -> keyword.first - end
                    else -> 0
                }
                gap <= 40
            }
            if (near) {
                spans.add(Span(start, end, "sweep.otp", "[REDACTED]"))
            }
            index = if (end > index) end else index + 1
        }
        return spans
    }

    private fun hasResidualRisk(masked: String): Boolean {
        val normalized = TextNormalizer.normalizePreservingLength(masked)
        for (rule in residualPatterns) {
            val matches = findMatches(rule, normalized) ?: return true
            if (matches.isNotEmpty()) return true
        }
        return false
    }

    private fun merge(spans: List<Span>): List<Span> {
        val sorted = spans.sortedWith(compareBy({ it.start }, { it.end }))
        val merged = ArrayList<Span>(sorted.size)
        for (span in sorted) {
            val last = merged.lastOrNull()
            if (last != null && span.start <= last.end) {
                if (span.end > last.end) {
                    merged[merged.size - 1] = last.copy(end = span.end)
                }
            } else {
                merged.add(span)
            }
        }
        return merged
    }

    private fun applySpans(body: String, spans: List<Span>): String {
        val builder = StringBuilder(body)
        for (span in spans.asReversed()) {
            builder.replace(span.start, span.end, span.replacement)
        }
        return builder.toString()
    }
}
