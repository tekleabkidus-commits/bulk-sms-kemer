package bulksms.test.mark.data.sms

/**
 * Normalizes Ethiopian phone numbers to E.164 (+2519XXXXXXXX or +2517XXXXXXXX).
 * Returns null for any number that does not match the locked spec:
 *   - Ethio Telecom (mobile prefix 9)  →  +2519XXXXXXXX
 *   - Safaricom Ethiopia (prefix 7)    →  +2517XXXXXXXX
 * No other country codes, prefixes, or formats are accepted.
 */
object PhoneNormalizer {

    private const val ET_CC = "+251"
    private val ALLOWED_CARRIER_DIGITS = setOf('9', '7')

    /**
     * Strip everything except digits and a leading '+'.
     * Then attempt to coerce into +2519XXXXXXXX / +2517XXXXXXXX.
     * Returns null if the input cannot be safely normalized to that exact form.
     */
    fun normalizeEthiopian(input: String?): String? {
        if (input.isNullOrBlank()) return null
        val cleaned = buildString {
            var first = true
            for (ch in input.trim()) {
                if (first && ch == '+') append(ch)
                else if (ch.isDigit()) append(ch)
                first = false
            }
        }

        // Strip leading 00 (international dialing prefix) → +
        val withPlus = when {
            cleaned.startsWith("+") -> cleaned
            cleaned.startsWith("00") -> "+" + cleaned.drop(2)
            else -> cleaned
        }

        // Forms we accept:
        //   +2519XXXXXXXX  /  +2517XXXXXXXX                  → 13 chars
        //   2519XXXXXXXX   /  2517XXXXXXXX                   → 12 chars (add +)
        //   09XXXXXXXX     /  07XXXXXXXX                     → 10 chars (drop leading 0, prepend +251)
        //   9XXXXXXXX      /  7XXXXXXXX                      →  9 chars (prepend +251)
        val candidate: String = when {
            withPlus.startsWith("+251") && withPlus.length == 13 -> withPlus
            withPlus.startsWith("251") && withPlus.length == 12 -> "+$withPlus"
            withPlus.length == 10 && withPlus.startsWith("0") -> ET_CC + withPlus.substring(1)
            withPlus.length == 9 -> ET_CC + withPlus
            else -> return null
        }

        // Final shape check: +251 followed by exactly 9 digits, first of which must be 9 or 7.
        if (!candidate.startsWith(ET_CC)) return null
        val rest = candidate.removePrefix(ET_CC)
        if (rest.length != 9) return null
        if (!rest.all { it.isDigit() }) return null
        if (rest[0] !in ALLOWED_CARRIER_DIGITS) return null

        return candidate
    }

    /** Carrier name for a normalized number. Caller must have already normalized. */
    fun carrierOf(normalized: String): String = when {
        normalized.startsWith("+2519") -> "Ethio Telecom"
        normalized.startsWith("+2517") -> "Safaricom"
        else -> "Unknown"
    }
}
