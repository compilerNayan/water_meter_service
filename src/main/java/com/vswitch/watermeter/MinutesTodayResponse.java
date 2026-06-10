package com.vswitch.watermeter;

public record MinutesTodayResponse(
        String deviceId,
        String date,
        String timezone,
        int slotMinutes,
        String startAt,
        double[] v) {}
