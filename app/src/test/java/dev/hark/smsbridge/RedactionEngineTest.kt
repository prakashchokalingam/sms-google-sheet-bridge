package dev.hark.smsbridge

import dev.hark.smsbridge.redact.BuiltInRules
import dev.hark.smsbridge.redact.RedactionDecision
import dev.hark.smsbridge.redact.RedactionEngine
import dev.hark.smsbridge.redact.RedactionRule
import dev.hark.smsbridge.redact.RuleAction
import dev.hark.smsbridge.redact.SensitivityMode
import dev.hark.smsbridge.redact.TextNormalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RedactionEngineTest {

    private val engine = RedactionEngine()

    private fun decide(
        body: String,
        rules: List<RedactionRule> = BuiltInRules.all,
        mode: SensitivityMode = SensitivityMode.MASK,
        sweep: Boolean = true
    ): RedactionDecision = engine.evaluate(body, rules, mode, sweep)

    private fun masked(body: String, rules: List<RedactionRule> = BuiltInRules.all): String {
        val decision = decide(body, rules)
        assertTrue("Expected MASK for: $body, got $decision", decision is RedactionDecision.Mask)
        return (decision as RedactionDecision.Mask).body
    }

    private fun assertSkip(body: String, rules: List<RedactionRule> = BuiltInRules.all) {
        val decision = decide(body, rules)
        assertTrue("Expected SKIP for: $body, got $decision", decision is RedactionDecision.Skip)
    }

    private fun assertAllow(body: String, rules: List<RedactionRule> = BuiltInRules.all, sweep: Boolean = true) {
        val decision = decide(body, rules, SensitivityMode.MASK, sweep)
        assertTrue("Expected ALLOW for: $body, got $decision", decision is RedactionDecision.Allow)
    }

    @Test
    fun masksCodeAfterOtpKeyword() {
        assertEquals("Your OTP is [REDACTED]", masked("Your OTP is 123456"))
    }

    @Test
    fun masksCodeBeforeOtpKeyword() {
        assertEquals("[REDACTED] is your OTP for HDFC", masked("123456 is your OTP for HDFC"))
    }

    @Test
    fun masksShortOtp() {
        assertEquals("OTP: [REDACTED]", masked("OTP: 4821"))
    }

    @Test
    fun masksCodeUsedToVerify() {
        assertEquals("Use [REDACTED] to verify your number", masked("Use 918273 to verify your number"))
    }

    @Test
    fun masksOneTimePassword() {
        assertEquals("Your one-time password is [REDACTED]", masked("Your one-time password is 556677"))
    }

    @Test
    fun masksVerificationCode() {
        assertEquals("Your verification code is [REDACTED]", masked("Your verification code is 778899"))
    }

    @Test
    fun masksSecurityCode() {
        assertEquals("Security code: [REDACTED]", masked("Security code: 445566"))
    }

    @Test
    fun masksLoginCode() {
        assertEquals("Your login code is [REDACTED]", masked("Your login code is 314159"))
    }

    @Test
    fun masksSplitDigitsWithSpaces() {
        assertEquals("OTP [REDACTED]", masked("OTP 123 456"))
    }

    @Test
    fun masksSplitDigitsWithHyphens() {
        assertEquals("OTP [REDACTED]", masked("OTP 12-34-56"))
    }

    @Test
    fun masksDigitsWithDots() {
        assertEquals("OTP [REDACTED]", masked("OTP 12.34.56"))
    }

    @Test
    fun masksEightDigitCode() {
        assertEquals("OTP is [REDACTED]", masked("OTP is 12345678"))
    }

    @Test
    fun masksCodeWithTrailingPunctuation() {
        assertEquals("Your OTP is [REDACTED].", masked("Your OTP is 123456."))
    }

    @Test
    fun masksFullWidthDigits() {
        assertEquals("OTP [REDACTED]", masked("OTP １２３４５６"))
    }

    @Test
    fun masksFullWidthKeyword() {
        assertEquals("ＯＴＰ [REDACTED]", masked("ＯＴＰ １２３４５６"))
    }

    @Test
    fun masksDevanagariDigits() {
        assertEquals("OTP [REDACTED]", masked("OTP १२३४५६"))
    }

    @Test
    fun masksZeroWidthSplit() {
        assertEquals("OTP [REDACTED]", masked("OTP 123\u200B456"))
    }

    @Test
    fun masksAlphanumericCode() {
        assertEquals("OTP: [REDACTED]", masked("OTP: AB12CD"))
    }

    @Test
    fun masksHyphenatedAlphanumericCode() {
        assertEquals("OTP: [REDACTED]", masked("OTP: G-123456"))
    }

    @Test
    fun masksCodeWithSpacedKeyword() {
        assertEquals("Your o.t.p. is [REDACTED]", masked("Your o.t.p. is 123456"))
    }

    @Test
    fun masksMultipleCodesNearOtp() {
        assertEquals(
            "Your OTP is [REDACTED] for Rs [REDACTED]",
            masked("Your OTP is 123456 for Rs 5000")
        )
    }

    @Test
    fun masksAmountAfterOtpBySweep() {
        assertEquals(
            "Your OTP is [REDACTED]. Order [REDACTED] shipped",
            masked("Your OTP is 123456. Order 987654 shipped")
        )
    }

    @Test
    fun masksLongNumberNearOtp() {
        assertEquals(
            "Your OTP is [REDACTED]. Call [REDACTED]",
            masked("Your OTP is 123456. Call 1800123456")
        )
    }

    @Test
    fun masksCodeWhenOnlyGenericCodeKeyword() {
        assertEquals("Order code [REDACTED] has shipped", masked("Order code 123456 has shipped"))
    }

    @Test
    fun skipsPassword() {
        assertSkip("Your password is 1234")
    }

    @Test
    fun skipsPin() {
        assertSkip("Your PIN is 4321")
    }

    @Test
    fun skipsCvv() {
        assertSkip("CVV 123 for your card")
    }

    @Test
    fun skipsDoNotShare() {
        assertSkip("Do not share this OTP 123456")
        assertSkip("Your OTP is 123456. Do not share it with anyone.")
    }

    @Test
    fun skipsConfidential() {
        assertSkip("Confidential: your code is 123456")
    }

    @Test
    fun allowsDebitWithoutCodeKeyword() {
        assertAllow("Rs 5000 debited from A/c XX1234 on 11-11-26")
    }

    @Test
    fun allowsOrderWithoutCodeKeyword() {
        assertAllow("Your order 123456 has shipped")
    }

    @Test
    fun allowsOtpWithoutDigits() {
        assertAllow("Your OTP is pending")
    }

    @Test
    fun allowsThreeDigitNumber() {
        assertAllow("Your OTP is 123")
    }

    @Test
    fun allowsEmptyBody() {
        assertAllow("")
    }

    @Test
    fun gateModeSkipsMaskedMessages() {
        val decision = decide("Your OTP is 123456", mode = SensitivityMode.GATE)
        assertTrue(decision is RedactionDecision.Skip)
        assertEquals("gate", (decision as RedactionDecision.Skip).reason)
    }

    @Test
    fun disabledRulesAndSweepAllowEverything() {
        val rules = BuiltInRules.all.map { it.copy(enabled = false) }
        assertAllow("Your OTP is 123456", rules, sweep = false)
    }

    @Test
    fun sweepCanBeDisabled() {
        val rules = BuiltInRules.all.filter { it.action != RuleAction.MASK }
        assertAllow("Your OTP is 123456", rules, sweep = false)
    }

    @Test
    fun invalidEnabledRuleFailsClosed() {
        val rules = BuiltInRules.all + RedactionRule(
            id = "custom.bad",
            name = "Bad regex",
            pattern = "(",
            action = RuleAction.MASK
        )
        val decision = decide("Your OTP is 123456", rules)
        assertTrue(decision is RedactionDecision.Skip)
        assertEquals("config-error", (decision as RedactionDecision.Skip).reason)
    }

    @Test
    fun slowRuleFailsClosed() {
        val rules = BuiltInRules.all + RedactionRule(
            id = "custom.slow",
            name = "Slow regex",
            pattern = "(a+){10}$",
            action = RuleAction.SKIP
        )
        val decision = decide("a".repeat(80) + "!", rules, sweep = false)
        assertTrue(decision is RedactionDecision.Skip)
    }

    @Test
    fun overlappingRulesMergeToOneRedaction() {
        val rules = listOf(
            RedactionRule("r4", "Four", "(?<code>\\d{4})", RuleAction.MASK, group = 1, priority = 1),
            RedactionRule("r6", "Six", "(?<code>\\d{6})", RuleAction.MASK, group = 1, priority = 2)
        )
        val decision = decide("123456", rules, sweep = false)
        assertTrue(decision is RedactionDecision.Mask)
        decision as RedactionDecision.Mask
        assertEquals("[REDACTED]", decision.body)
        assertEquals(1, decision.redactions)
    }

    @Test
    fun maskedBodyNeverContainsRawCode() {
        val bodies = listOf(
            "Your OTP is 123456",
            "OTP: 4821",
            "Use 918273 to verify",
            "123456 is your OTP"
        )
        for (body in bodies) {
            val decision = decide(body)
            assertTrue(decision is RedactionDecision.Mask)
            val maskedBody = (decision as RedactionDecision.Mask).body
            assertFalse(maskedBody.contains("123456"))
            assertFalse(maskedBody.contains("4821"))
            assertFalse(maskedBody.contains("918273"))
        }
    }

    @Test
    fun normalizationKeepsLength() {
        val samples = listOf(
            "OTP １２３４５６",
            "OTP १२३४५६",
            "OTP 123\u200B456",
            "ＯＴＰ 123456"
        )
        for (sample in samples) {
            assertEquals(sample.length, TextNormalizer.normalizePreservingLength(sample).length)
        }
    }

    @Test
    fun normalizationMapsDigits() {
        assertEquals("OTP 123456", TextNormalizer.normalizePreservingLength("OTP １２３４５６"))
        assertEquals("OTP 123456", TextNormalizer.normalizePreservingLength("OTP १२३४५६"))
    }

    @Test
    fun validateRejectsBadPatterns() {
        assertNotNull(engine.validate(RedactionRule("x", "x", "(", RuleAction.SKIP)))
        assertNotNull(engine.validate(RedactionRule("x", "x", "(a)", RuleAction.MASK, group = 3)))
        assertNotNull(engine.validate(RedactionRule("x", "x", ".*", RuleAction.SKIP)))
        assertNotNull(engine.validate(RedactionRule("x", "x", "a".repeat(600), RuleAction.SKIP)))
        assertNotNull(engine.validate(RedactionRule("x", "x", "(a+){10}$", RuleAction.SKIP)))
    }

    @Test
    fun validateAcceptsBuiltIns() {
        for (rule in BuiltInRules.all) {
            val error = engine.validate(rule)
            assertEquals("Rule ${rule.id} is invalid: $error", null, error)
        }
    }
}
