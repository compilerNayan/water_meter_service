package com.vswitch.watermeter;

import java.util.List;

public record BuildingDailyResponse(String timezone, List<BuildingDailyEntry> days) {}
