package com.vswitch.watermeter;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MockDeviceProfileFactoryTest {

    private final MockDeviceProfileFactory factory = new MockDeviceProfileFactory();

    @Test
    void profileIsDeterministicForDevice() {
        MockDeviceProfile first = factory.forDevice("WM000001");
        MockDeviceProfile second = factory.forDevice("WM000001");
        assertEquals(first.dailyTargetLiters(), second.dailyTargetLiters());
        assertEquals(first.anomalyType(), second.anomalyType());
    }

    @Test
    void dailyTargetIsWithinExpectedRange() {
        MockDeviceProfile profile = factory.forDevice("WM000099");
        assertTrue(profile.dailyTargetLiters() >= 600);
        assertTrue(profile.dailyTargetLiters() <= 1200);
    }

    @Test
    void minuteVolumeIsNonNegative() {
        MockDeviceProfile profile = factory.forDevice("WM000001");
        ZonedDateTime time = ZonedDateTime.of(2026, 6, 9, 10, 30, 0, 0, ZoneOffset.UTC);
        assertTrue(factory.minuteVolumeLiters(profile, time) >= 0);
    }
}
