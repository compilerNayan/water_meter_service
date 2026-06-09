package com.vswitch.watermeter;

import java.util.List;

public record DashboardResponse(
        String tenantId,
        String buildingName,
        StructureDto structure,
        String generatedAt,
        List<DashboardDeviceEntry> devices) {}
