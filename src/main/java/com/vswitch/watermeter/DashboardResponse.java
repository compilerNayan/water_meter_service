package com.vswitch.watermeter;

import java.util.List;

public record DashboardResponse(
        String metadataHash,
        String generatedAt,
        List<DashboardTelemetryEntry> devices) {}
