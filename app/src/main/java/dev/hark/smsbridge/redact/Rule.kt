package dev.hark.smsbridge.redact

enum class RuleAction {
    MASK,
    SKIP
}

enum class SensitivityMode {
    MASK,
    GATE
}

data class RedactionRule(
    val id: String,
    val name: String,
    val pattern: String,
    val action: RuleAction,
    val group: Int = 0,
    val replacement: String = "[REDACTED]",
    val enabled: Boolean = true,
    val priority: Int = 100,
    val builtIn: Boolean = false
)

sealed interface RedactionDecision {
    data class Allow(val body: String) : RedactionDecision

    data class Mask(
        val body: String,
        val redactions: Int,
        val rules: List<String>
    ) : RedactionDecision

    data class Skip(
        val reason: String,
        val rules: List<String> = emptyList()
    ) : RedactionDecision
}
