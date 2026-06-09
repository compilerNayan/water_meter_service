package com.vswitch.watermeter;

public record DailySummaryDayResponse(
        String date, double totalLiters, int peakHour, double peakHourLiters) {}
