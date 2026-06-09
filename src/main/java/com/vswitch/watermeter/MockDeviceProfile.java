package com.vswitch.watermeter;

public record MockDeviceProfile(
        String deviceId,
        double dailyTargetLiters,
        AnomalyType anomalyType,
        int seed) {

    public enum AnomalyType {
        NORMAL,
        LEAK_BURST,
        VALVE_MISMATCH,
        OFFLINE
    }
}
