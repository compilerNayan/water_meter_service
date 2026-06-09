package com.vswitch.watermeter;

public record MockDeviceProfile(
        String deviceId,
        double dailyTargetLiters,
        AnomalyType anomalyType,
        int seed) {

    enum AnomalyType {
        NORMAL,
        LEAK_BURST,
        VALVE_MISMATCH,
        OFFLINE
    }
}
