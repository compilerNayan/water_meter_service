package com.vswitch.watermeter;

public record QuotaStatusResponse(
        String date,
        double usedLiters,
        int activeStepIndex,
        Double quotaCapPercent,
        double remainingLiters,
        Double nextStepAtLiters) {}
