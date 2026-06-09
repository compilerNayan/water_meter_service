package com.vswitch.watermeter;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class QuotaCalculatorTest {

    @Test
    void noStepsTriggeredReturnsNoCap() {
        var result =
                QuotaCalculator.computeCap(
                        List.of(
                                new QuotaStepDto(300, "reduce_pressure", 20.0),
                                new QuotaStepDto(500, "turn_off", null)),
                        120,
                        500);

        assertEquals(-1, result.activeStepIndex());
        assertNull(result.capPercent());
        assertEquals(300, result.nextStepAtLiters());
        assertEquals(380, result.remainingLiters());
    }

    @Test
    void reducePressureStepAppliesCap() {
        var result =
                QuotaCalculator.computeCap(
                        List.of(
                                new QuotaStepDto(300, "reduce_pressure", 20.0),
                                new QuotaStepDto(500, "turn_off", null)),
                        350,
                        500);

        assertEquals(0, result.activeStepIndex());
        assertEquals(80.0, result.capPercent());
        assertEquals(500, result.nextStepAtLiters());
        assertEquals(150, result.remainingLiters());
    }

    @Test
    void turnOffStepSetsZeroCap() {
        var result =
                QuotaCalculator.computeCap(
                        List.of(new QuotaStepDto(500, "turn_off", null)), 520, 500);

        assertEquals(0, result.activeStepIndex());
        assertEquals(0.0, result.capPercent());
        assertNull(result.nextStepAtLiters());
        assertEquals(0, result.remainingLiters());
    }
}
