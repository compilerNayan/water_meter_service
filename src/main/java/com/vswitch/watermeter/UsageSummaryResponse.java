package com.vswitch.watermeter;

public record UsageSummaryResponse(
        double totalVolumeLiters,
        double averagePerBucketLiters,
        PeakBucketResponse peakBucket,
        double previousPeriodTotalLiters,
        double deltaPercent) {}
