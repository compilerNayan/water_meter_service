package com.vswitch.watermeter;

import java.util.List;

public record QuotaUpdateRequest(
        boolean enabled, double dailyLimitLiters, List<QuotaStepDto> steps) {}
