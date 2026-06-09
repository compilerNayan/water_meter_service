package com.vswitch.watermeter;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MockHistoricalBackfillServiceTest {

    @Test
    void dailyTargetIsBetween600And1200Liters() {
        for (int i = 0; i < 50; i++) {
            double liters =
                    MockHistoricalBackfillService.dailyTargetLiters(
                            "WM00000" + i, LocalDate.of(2026, 6, 1));
            assertTrue(liters >= 600, "too low: " + liters);
            assertTrue(liters <= 1200, "too high: " + liters);
        }
    }

    @Test
    void dailyTargetIsStableForSameDeviceAndDate() {
        LocalDate date = LocalDate.of(2026, 6, 5);
        double first = MockHistoricalBackfillService.dailyTargetLiters("WM123", date);
        double second = MockHistoricalBackfillService.dailyTargetLiters("WM123", date);
        assertEquals(first, second);
    }
}
