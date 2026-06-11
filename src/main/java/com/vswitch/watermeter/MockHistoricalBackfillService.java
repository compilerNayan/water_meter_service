package com.vswitch.watermeter;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.vswitch.watermeter.device.DeviceFacade;

/**
 * Seeds DynamoDB with realistic day-history when a device completes mock enrollment.
 */
@Service
@ConditionalOnProperty(name = "mock.telemetry.enabled", havingValue = "true")
public class MockHistoricalBackfillService {

    private static final Logger log = LoggerFactory.getLogger(MockHistoricalBackfillService.class);

    private final DeviceFacade deviceFacade;
    private final MockDeviceProfileFactory profileFactory;
    private final int backfillDays;
    private final long historyTtlSeconds;

    MockHistoricalBackfillService(
            DeviceFacade deviceFacade,
            MockDeviceProfileFactory profileFactory,
            @Value("${mock.history.backfill.days:30}") int backfillDays,
            @Value("${day.history.ttl.days:400}") int historyTtlDays) {
        this.deviceFacade = deviceFacade;
        this.profileFactory = profileFactory;
        this.backfillDays = Math.max(1, backfillDays);
        this.historyTtlSeconds = Math.max(30L, historyTtlDays) * 24 * 3600;
    }

    void backfillIfNeeded(UnitRecord unit) {
        if (!UnitRecord.STATUS_ENROLLED.equals(unit.enrollmentStatus())) {
            return;
        }

        LocalDate oldestDay = LocalDate.now(ZoneOffset.UTC).minusDays(backfillDays);
        if (deviceFacade.hasDayHistory(unit.deviceId(), oldestDay)) {
            log.debug("History already backfilled for device {}", unit.deviceId());
            return;
        }

        log.info(
                "Backfilling {} days of mock history for device {}",
                backfillDays,
                unit.deviceId());

        double totalHistoricalLiters = 0;
        Instant lastDay = null;
        long expiresAt = Instant.now().getEpochSecond() + historyTtlSeconds;

        for (int dayOffset = backfillDays; dayOffset >= 1; dayOffset--) {
            LocalDate date = LocalDate.now(ZoneOffset.UTC).minusDays(dayOffset);
            double dailyTarget = dailyTargetLiters(unit.deviceId(), date);
            int[] milliliters = minuteVolumesForDay(unit.deviceId(), date, dailyTarget);
            double totalLiters = MinuteVolumeCsv.sumLiters(milliliters);

            deviceFacade.writeDayHistory(
                    new DayHistoryRecord(
                            unit.deviceId(),
                            DayHistoryRecord.dayKeyFor(date),
                            unit.tenantId(),
                            MinuteVolumeCsv.encodeMl(milliliters),
                            totalLiters,
                            "UTC",
                            expiresAt));

            totalHistoricalLiters += totalLiters;
            lastDay = date.atStartOfDay(ZoneOffset.UTC).toInstant();
        }

        if (lastDay != null) {
            deviceFacade.applyHistoricalCumulative(
                    unit.deviceId(), totalHistoricalLiters, lastDay);
        }
    }

    static double dailyTargetLiters(String deviceId, LocalDate date) {
        int mixed = deviceId.hashCode() ^ date.hashCode();
        return 600 + (Math.abs(mixed) % 601);
    }

    private int[] minuteVolumesForDay(String deviceId, LocalDate date, double dailyTarget) {
        double[] hourVolumes = hourlyVolumesForDay(deviceId, date, dailyTarget);
        int[] milliliters = new int[MinuteVolumeCsv.MINUTES_PER_DAY];

        for (int hour = 0; hour < 24; hour++) {
            double hourLiters = hourVolumes[hour];
            double perMinute = hourLiters / 60.0;
            int start = hour * 60;
            for (int m = 0; m < 60; m++) {
                double noise = 0.85 + pseudoRandom(deviceId, date, hour, m) * 0.3;
                milliliters[start + m] = (int) Math.round(perMinute * noise * 1000);
            }
        }
        return milliliters;
    }

    private double[] hourlyVolumesForDay(String deviceId, LocalDate date, double dailyTarget) {
        double[] weights = new double[24];
        double weekendFactor = date.getDayOfWeek().getValue() >= 6 ? 1.1 : 1.0;
        for (int hour = 0; hour < 24; hour++) {
            double noise = 0.9 + pseudoRandom(deviceId, date, hour, 0) * 0.2;
            weights[hour] = profileFactory.hourlyPatternLiters(hour) * weekendFactor * noise;
        }

        double sum = Arrays.stream(weights).sum();
        double[] volumes = new double[24];
        for (int hour = 0; hour < 24; hour++) {
            volumes[hour] = dailyTarget * (weights[hour] / sum);
        }
        return volumes;
    }

    private static double pseudoRandom(String deviceId, LocalDate date, int hour, int minute) {
        int mixed = deviceId.hashCode() ^ date.hashCode() ^ (hour * 31) ^ minute;
        return (mixed & 0xFFFF) / 65535.0;
    }
}
