package com.vswitch.watermeter;

public record UsageDataPointResponse(
        String timestamp, double volumeLiters, double avgFlowRateLpm) {}
