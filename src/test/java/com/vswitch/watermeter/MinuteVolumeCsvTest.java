package com.vswitch.watermeter;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.vswitch.watermeter.device.TodaySlotRecord;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MinuteVolumeCsvTest {

    @Test
    void stitchDayFromSlotsFillsThirtyMinuteWindows() {
        Instant periodStart = Instant.parse("2026-06-09T10:00:00Z");
        TodaySlotRecord slot =
                new TodaySlotRecord(
                        "WM000001",
                        TodaySlotRecord.slotKeyFor(periodStart),
                        "k3m9x2a",
                        "2026-06-09",
                        "1000,2000,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0",
                        0,
                        0);

        int[] day = MinuteVolumeCsv.stitchDayFromSlots(List.of(slot), ZoneOffset.UTC);
        assertEquals(1.0, day[600] / 1000.0, 0.001);
        assertEquals(2.0, day[601] / 1000.0, 0.001);
    }

    @Test
    void encodeDecodeRoundTrip() {
        int[] original = {0, 120, 50, 0};
        String csv = MinuteVolumeCsv.encodeMl(original);
        int[] decoded = MinuteVolumeCsv.decodeMl(csv);
        assertEquals(4, decoded.length);
        assertEquals(120, decoded[1]);
    }
}
