package com.vswitch.watermeter;

public record BuildingSummaryResponse(
        double totalTodayLiters,
        double totalMonthLiters,
        int unitsOnline,
        int unitsOffline,
        int unitsTotal,
        int activeAlerts) {}
