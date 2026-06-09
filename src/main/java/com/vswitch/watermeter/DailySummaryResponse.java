package com.vswitch.watermeter;

import java.util.List;

public record DailySummaryResponse(String unit, List<DailySummaryDayResponse> days) {}
