package dev.hark.smsbridge.redact

object TextNormalizer {

    fun normalizePreservingLength(input: String): String {
        val out = StringBuilder(input.length)
        for (ch in input) {
            out.append(normalizeChar(ch))
        }
        return out.toString()
    }

    private fun normalizeChar(ch: Char): Char {
        return when {
            ch == '\u00A0' || ch == '\u202F' || ch == '\u3000' -> ' '
            ch == '\u00AD' -> ' '
            ch.code in 0x200B..0x200F -> ' '
            ch.code in 0x202A..0x202E -> ' '
            ch.code in 0x2060..0x2069 -> ' '
            ch.code == 0xFEFF -> ' '
            ch.code in 0xFE00..0xFE0F -> ' '
            ch.code in 0xFF01..0xFF5E -> (ch.code - 0xFEE0).toChar()
            Character.isDigit(ch) && ch.code > 0x7F -> {
                val value = Character.getNumericValue(ch)
                if (value in 0..9) ('0'.code + value).toChar() else ch
            }
            else -> ch
        }
    }
}
