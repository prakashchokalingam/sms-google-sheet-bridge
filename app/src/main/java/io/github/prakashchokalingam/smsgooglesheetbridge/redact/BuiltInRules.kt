package io.github.prakashchokalingam.smsgooglesheetbridge.redact

object BuiltInRules {

    private const val OTP_WORDS =
        "(?:otp|o\\s*\\.?\\s*t\\s*\\.?\\s*p|one[\\s-]?time(?:\\s+(?:password|passcode|code))?|verification\\s+code|security\\s+code|authentication\\s+code|auth(?:entication)?\\s+code|confirmation\\s+code|access\\s+code|login\\s+code|passcode|code)"

    private const val STRONG_OTP_WORDS =
        "(?:otp|o\\s*\\.?\\s*t\\s*\\.?\\s*p|one[\\s-]?time(?:\\s+(?:password|passcode|code))?|verification\\s+code|security\\s+code|authentication\\s+code|auth(?:entication)?\\s+code|login\\s+code|passcode)"

    private const val DIGIT_CODE = "(?<!\\d)\\d(?:[\\s\\-.]?\\d){3,9}(?!\\d)"
    private const val DIGIT_CODE_GROUP = "(?<code>(?<!\\d)\\d(?:[\\s\\-.]?\\d){3,9}(?!\\d))"

    val all: List<RedactionRule> = listOf(
        RedactionRule(
            id = "skip.password",
            name = "Password or passcode",
            pattern = "(?i)(?<!one-time )(?<!one time )(?<!otp )\\b(?:password|passcode|pass\\s*code|pwd)\\b",
            action = RuleAction.SKIP,
            priority = 10,
            builtIn = true
        ),
        RedactionRule(
            id = "skip.pin-cvv",
            name = "PIN, CVV or CVC",
            pattern = "(?i)\\b(?:pin|cvv|cvc|cvv2|card\\s*verification(?:\\s*(?:value|code))?)\\b",
            action = RuleAction.SKIP,
            priority = 11,
            builtIn = true
        ),
        RedactionRule(
            id = "skip.do-not-share",
            name = "Do not share or disclose",
            pattern = "(?i)\\b(?:do\\s+not|never|don'?t|dont)\\s+(?:share|disclose|reveal|forward|give)\\b",
            action = RuleAction.SKIP,
            priority = 12,
            builtIn = true
        ),
        RedactionRule(
            id = "skip.confidential",
            name = "Confidential or sensitive",
            pattern = "(?i)\\b(?:confidential|strictly\\s+private|sensitive\\s+information)\\b",
            action = RuleAction.SKIP,
            priority = 13,
            builtIn = true
        ),
        RedactionRule(
            id = "mask.otp.before",
            name = "Code after OTP keyword",
            pattern = "(?i)\\b$OTP_WORDS\\b[^\\d]{0,40}?$DIGIT_CODE_GROUP",
            action = RuleAction.MASK,
            group = 1,
            priority = 100,
            builtIn = true
        ),
        RedactionRule(
            id = "mask.otp.after",
            name = "Code before OTP keyword",
            pattern = "(?i)$DIGIT_CODE_GROUP[^\\d]{0,40}?\\b(?:is|as)\\s+(?:your|the|a|an)\\s+[^\\d]{0,24}?$OTP_WORDS\\b",
            action = RuleAction.MASK,
            group = 1,
            priority = 101,
            builtIn = true
        ),
        RedactionRule(
            id = "mask.otp.action",
            name = "Code used to verify or login",
            pattern = "(?i)(?<!\\d)(?<code>\\d{4,8})(?!\\d)\\s+(?:is\\s+)?(?:required\\s+)?(?:to|for)\\s+(?:verify|verification|authenticate|authentication|login|log\\s*in|sign\\s*in|confirm|confirmation|validate|complete|reset)\\b",
            action = RuleAction.MASK,
            group = 1,
            priority = 102,
            builtIn = true
        ),
        RedactionRule(
            id = "mask.otp.enter",
            name = "Code after use or enter",
            pattern = "(?i)\\b(?:use|enter|submit|type|provide|share|send)\\s+(?:the\\s+)?(?:otp\\s+|code\\s+|pin\\s+)?(?<!\\d)(?<code>\\d{4,8})(?!\\d)",
            action = RuleAction.MASK,
            group = 1,
            priority = 103,
            builtIn = true
        ),
        RedactionRule(
            id = "mask.otp.alnum",
            name = "Alphanumeric code after keyword",
            pattern = "(?i)\\b$OTP_WORDS\\b[^\\d]{0,24}?(?<code>(?=[A-Z0-9]{4,10}\\b)(?=[A-Z0-9]*\\d)(?=[A-Z0-9]*[A-Z])[A-Z0-9]{4,10})",
            action = RuleAction.MASK,
            group = 1,
            priority = 104,
            builtIn = true
        ),
        RedactionRule(
            id = "mask.otp.hyphen",
            name = "Hyphenated code after keyword",
            pattern = "(?i)\\b(?:otp|code|passcode)\\b[^\\dA-Za-z]{0,24}?(?<code>[A-Z]-\\d{4,8})(?!\\w)",
            action = RuleAction.MASK,
            group = 1,
            priority = 105,
            builtIn = true
        ),
        RedactionRule(
            id = "mask.card",
            name = "Card number",
            pattern = "(?<!\\d)(?<code>(?:\\d[ -]?){13,19})(?!\\d)",
            action = RuleAction.MASK,
            group = 1,
            enabled = false,
            priority = 200,
            builtIn = true
        ),
        RedactionRule(
            id = "mask.aadhaar",
            name = "Aadhaar number",
            pattern = "(?<!\\d)(?<code>\\d{4}\\s?\\d{4}\\s?\\d{4})(?!\\d)",
            action = RuleAction.MASK,
            group = 1,
            enabled = false,
            priority = 201,
            builtIn = true
        ),
        RedactionRule(
            id = "mask.pan",
            name = "PAN number",
            pattern = "(?<!\\w)(?<code>[A-Z]{5}\\d{4}[A-Z])(?!\\w)",
            action = RuleAction.MASK,
            group = 1,
            enabled = false,
            priority = 202,
            builtIn = true
        ),
        RedactionRule(
            id = "mask.email",
            name = "Email address",
            pattern = "(?<!\\w)(?<code>[\\w.+-]+@[\\w-]+\\.[\\w.-]+)(?!\\w)",
            action = RuleAction.MASK,
            group = 1,
            enabled = false,
            priority = 203,
            builtIn = true
        )
    )

    val strongOtpWords: String = STRONG_OTP_WORDS
}
