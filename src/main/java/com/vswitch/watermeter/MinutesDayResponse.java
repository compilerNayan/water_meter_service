package com.vswitch.watermeter;

public record MinutesDayResponse(String date, String startAt, double[] v) {}
