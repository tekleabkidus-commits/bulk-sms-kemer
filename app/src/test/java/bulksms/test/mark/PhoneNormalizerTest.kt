package bulksms.test.mark

import bulksms.test.mark.data.sms.PhoneNormalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhoneNormalizerTest {

    // ------- Acceptance forms -------

    @Test fun e164_full() =
        assertEquals("+251911234567", PhoneNormalizer.normalizeEthiopian("+251911234567"))

    @Test fun e164_full_safaricom() =
        assertEquals("+251711234567", PhoneNormalizer.normalizeEthiopian("+251711234567"))

    @Test fun no_plus_full() =
        assertEquals("+251911234567", PhoneNormalizer.normalizeEthiopian("251911234567"))

    @Test fun zero_prefix_local() =
        assertEquals("+251911234567", PhoneNormalizer.normalizeEthiopian("0911234567"))

    @Test fun bare_nine_digits() =
        assertEquals("+251911234567", PhoneNormalizer.normalizeEthiopian("911234567"))

    @Test fun double_zero_intl_prefix() =
        assertEquals("+251911234567", PhoneNormalizer.normalizeEthiopian("00251911234567"))

    @Test fun spaces_and_dashes_stripped() =
        assertEquals("+251911234567", PhoneNormalizer.normalizeEthiopian("+251 91-123-4567"))

    @Test fun parens_stripped() =
        assertEquals("+251711234567", PhoneNormalizer.normalizeEthiopian("(0711) 234 567"))

    // ------- Rejection forms -------

    @Test fun null_in_returns_null() = assertNull(PhoneNormalizer.normalizeEthiopian(null))
    @Test fun blank_returns_null()   = assertNull(PhoneNormalizer.normalizeEthiopian(""))
    @Test fun spaces_only_returns_null() = assertNull(PhoneNormalizer.normalizeEthiopian("   "))

    @Test fun foreign_country_code_rejected() =
        assertNull(PhoneNormalizer.normalizeEthiopian("+254911234567"))   // Kenya

    @Test fun us_number_rejected() =
        assertNull(PhoneNormalizer.normalizeEthiopian("+14155551234"))

    @Test fun et_landline_rejected() =
        assertNull(PhoneNormalizer.normalizeEthiopian("+251111234567"))   // starts with 1

    @Test fun et_invalid_carrier_8_rejected() =
        assertNull(PhoneNormalizer.normalizeEthiopian("+251811234567"))   // starts with 8

    @Test fun too_short_rejected() =
        assertNull(PhoneNormalizer.normalizeEthiopian("+25191123"))

    @Test fun too_long_rejected() =
        assertNull(PhoneNormalizer.normalizeEthiopian("+2519112345678"))

    @Test fun letters_in_input_rejected() =
        assertNull(PhoneNormalizer.normalizeEthiopian("+251 91 ABC 4567"))  // letters drop, then too short

    @Test fun bare_eight_digits_rejected() =
        assertNull(PhoneNormalizer.normalizeEthiopian("11234567"))

    // ------- Carrier helper -------

    @Test fun carrier_ethio() =
        assertEquals("Ethio Telecom", PhoneNormalizer.carrierOf("+251911234567"))

    @Test fun carrier_safaricom() =
        assertEquals("Safaricom", PhoneNormalizer.carrierOf("+251711234567"))
}
