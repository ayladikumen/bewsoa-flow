package ai.bewsoa.flow.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalTime

class BlockCodecTest {

    @Test
    fun `roundtrip preserves every field`() {
        val block = TaskBlock(
            id = "wd_gym",
            title = "Gym — long session",
            track = Track.GYM,
            start = LocalTime.of(17, 0),
            end = LocalTime.of(18, 45),
            note = "no rushing out",
            counted = true
        )
        assertEquals(block, BlockCodec.fromJson(BlockCodec.toJson(block)))
    }

    @Test
    fun `model quirks in times are tolerated`() {
        // "24:00" is how models write midnight; "9:30" drops the leading zero.
        assertEquals(LocalTime.of(23, 59), BlockCodec.parseTime("24:00"))
        assertEquals(LocalTime.of(9, 30), BlockCodec.parseTime("9:30"))
        assertEquals(LocalTime.of(9, 30), BlockCodec.parseTime(" 09:30 "))
    }
}
