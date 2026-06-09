package com.vswitch.watermeter.device;

import java.time.Instant;

public record MinuteBucketEntry(Instant t, double ml) {}
