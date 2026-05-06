package bulksms.test.mark.data.sms

/**
 * Computes SMS encoding (GSM-7 vs UCS-2) and number of segments. Mirrors the platform's
 * SmsManager.divideMessage() behavior closely enough for cost/preview estimation.
 *
 *   GSM-7 single segment:           160 chars
 *   GSM-7 multi-segment (UDH):      153 chars/segment
 *   UCS-2 single segment:            70 chars
 *   UCS-2 multi-segment (UDH):       67 chars/segment
 *
 * Any character not in the GSM-7 default + extension table forces UCS-2 (e.g. Amharic).
 */
object SegmentCounter {

    enum class Encoding { GSM_7, UCS_2 }

    data class Result(val encoding: Encoding, val length: Int, val segments: Int)

    private val GSM7_BASIC = setOf(
        '@','£','$','¥','è','é','ù','ì','ò','Ç','\n','Ø','ø','\r','Å','å',
        'Δ','_','Φ','Γ','Λ','Ω','Π','Ψ','Σ','Θ','Ξ',' ','!','"','#','¤',
        '%','&','\'','(',')','*','+',',','-','.','/','0','1','2','3','4',
        '5','6','7','8','9',':',';','<','=','>','?','¡','A','B','C','D',
        'E','F','G','H','I','J','K','L','M','N','O','P','Q','R','S','T',
        'U','V','W','X','Y','Z','Ä','Ö','Ñ','Ü','§','¿','a','b','c','d',
        'e','f','g','h','i','j','k','l','m','n','o','p','q','r','s','t',
        'u','v','w','x','y','z','ä','ö','ñ','ü','à'
    )

    // GSM-7 extension chars cost 2 septets each.
    private val GSM7_EXT = setOf('^','{','}','\\','[',']','~','|','€','\u000C')

    fun count(text: String): Result {
        val isGsm = text.all { it in GSM7_BASIC || it in GSM7_EXT }
        return if (isGsm) {
            // count septets — extension chars cost 2.
            var septets = 0
            for (c in text) septets += if (c in GSM7_EXT) 2 else 1
            val segments = when {
                septets == 0 -> 1
                septets <= 160 -> 1
                else -> ((septets + 152) / 153)
            }
            Result(Encoding.GSM_7, text.length, segments)
        } else {
            val units = text.codePoints().count().toInt().let {
                // Java String.length already counts UTF-16 code units, which matches what UCS-2 SMS uses.
                text.length
            }
            val segments = when {
                units == 0 -> 1
                units <= 70 -> 1
                else -> ((units + 66) / 67)
            }
            Result(Encoding.UCS_2, units, segments)
        }
    }
}
