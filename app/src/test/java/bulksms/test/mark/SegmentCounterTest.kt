package bulksms.test.mark

import bulksms.test.mark.data.sms.SegmentCounter
import org.junit.Assert.assertEquals
import org.junit.Test

class SegmentCounterTest {

    @Test fun empty_is_one_gsm_segment() {
        val r = SegmentCounter.count("")
        assertEquals(SegmentCounter.Encoding.GSM_7, r.encoding)
        assertEquals(1, r.segments)
    }

    @Test fun ascii_under_160_one_segment() {
        val r = SegmentCounter.count("Hello, this is a test message.")
        assertEquals(SegmentCounter.Encoding.GSM_7, r.encoding)
        assertEquals(1, r.segments)
    }

    @Test fun ascii_at_160_one_segment() {
        val r = SegmentCounter.count("a".repeat(160))
        assertEquals(SegmentCounter.Encoding.GSM_7, r.encoding)
        assertEquals(1, r.segments)
    }

    @Test fun ascii_at_161_two_segments() {
        val r = SegmentCounter.count("a".repeat(161))
        assertEquals(SegmentCounter.Encoding.GSM_7, r.encoding)
        assertEquals(2, r.segments)
    }

    @Test fun ascii_at_306_two_segments() {
        // 153 + 153 = 306
        val r = SegmentCounter.count("a".repeat(306))
        assertEquals(SegmentCounter.Encoding.GSM_7, r.encoding)
        assertEquals(2, r.segments)
    }

    @Test fun ascii_at_307_three_segments() {
        val r = SegmentCounter.count("a".repeat(307))
        assertEquals(SegmentCounter.Encoding.GSM_7, r.encoding)
        assertEquals(3, r.segments)
    }

    @Test fun curly_brace_extension_costs_two_septets() {
        // 158 'a's + "{}" = 158 + 2*2 = 162 septets → 2 segments
        val r = SegmentCounter.count("a".repeat(158) + "{}")
        assertEquals(SegmentCounter.Encoding.GSM_7, r.encoding)
        assertEquals(2, r.segments)
    }

    @Test fun amharic_forces_ucs2() {
        val r = SegmentCounter.count("ሰላም")  // Amharic "hello"
        assertEquals(SegmentCounter.Encoding.UCS_2, r.encoding)
        assertEquals(1, r.segments)
    }

    @Test fun ucs2_at_70_one_segment() {
        val r = SegmentCounter.count("ሀ".repeat(70))
        assertEquals(SegmentCounter.Encoding.UCS_2, r.encoding)
        assertEquals(1, r.segments)
    }

    @Test fun ucs2_at_71_two_segments() {
        val r = SegmentCounter.count("ሀ".repeat(71))
        assertEquals(SegmentCounter.Encoding.UCS_2, r.encoding)
        assertEquals(2, r.segments)
    }

    @Test fun emoji_forces_ucs2() {
        val r = SegmentCounter.count("Hello 😊")
        assertEquals(SegmentCounter.Encoding.UCS_2, r.encoding)
    }

    @Test fun mixed_amharic_english() {
        val r = SegmentCounter.count("Order #1234 ተቀብለናል")
        assertEquals(SegmentCounter.Encoding.UCS_2, r.encoding)
        assertEquals(1, r.segments)
    }
}
