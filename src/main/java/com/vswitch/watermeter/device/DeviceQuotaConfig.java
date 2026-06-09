package com.vswitch.watermeter.device;

import java.util.List;

import com.vswitch.watermeter.QuotaStepDto;

public record DeviceQuotaConfig(
        String deviceId,
        String tenantId,
        boolean enabled,
        double dailyLimitLiters,
        String timezone,
        List<QuotaStepDto> steps) {}
