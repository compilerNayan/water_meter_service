package com.vswitch.watermeter;

import java.util.List;

public record HourlyPatternResponse(String unit, List<HourlyPatternHourResponse> hours) {}
