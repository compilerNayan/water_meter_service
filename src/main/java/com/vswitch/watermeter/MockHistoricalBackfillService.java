package com.vswitch.watermeter;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.vswitch.watermeter.device.DeviceFacade;

/**
 * Seeds DynamoDB with realistic usage history when a device completes mock enrollment.
 */
@Service
public class MockHistoricalBackfillService {

    private static final Logger log = LoggerFactory.getLogger(MockHistoricalBackfillService.class);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final DeviceFacade deviceFacade;
    private final MockDeviceProfileFactory profileFactory;
    private final int backfillDays;
    private final long historyTtlSeconds;

    MockHistoricalBackfillService(
            DeviceFacade deviceFacade,
            MockDeviceProfileFactory profileFactory,
            @Value("${mock.history.backfill.days:10}") int backfillDays,
            @Value("${mock.history.ttl.days:15}") int historyTtlDays) {
        this.deviceFacade = deviceFacade;
        this.profileFactory = profileFactory;
        this.backfillDays = Math.max(1, backfillDays);
        this.historyTtlSeconds = Math.max(2L, historyTtlDays) * 24 * 3600;
    }

    void backfillIfNeeded(UnitRecord unit) {
        if (!UnitRecord.STATUS_ENROLLED.equals(unit.enrollmentStatus())) {
            return;
        }

        LocalDate oldestDay = LocalDate.now(ZoneOffset.UTC).minusDays(backfillDays);
        String oldestKey =
                DailyUsageRecord.usageKeyFor(oldestDay.format(DATE_FORMAT), unit.deviceId());
        if (deviceFacade.findDailyUsage(unit.tenantId(), oldestKey).isPresent()) {
            log.debug("History already backfilled for device {}", unit.deviceId());
            return;
        }

        log.info(
                "Backfilling {} days of mock history for device {}",
                backfillDays,
                unit.deviceId());

        double totalHistoricalLiters = 0;
        Instant lastHour = null;
        long expiresAt = Instant.now().getEpochSecond() + historyTtlSeconds;

        for (int dayOffset = backfillDays; dayOffset >= 1; dayOffset--) {
            LocalDate date = LocalDate.now(ZoneOffset.UTC).minusDays(dayOffset);
            double dailyTarget = dailyTargetLiters(unit.deviceId(), date);
            double[] hourVolumes = hourlyVolumesForDay(unit.deviceId(), date, dailyTarget);

            int peakHour = 0;
            double peakHourLiters = 0;
            for (int hour = 0; hour < 24; hour++) {
                double volume = hourVolumes[hour];
                if (volume > peakHourLiters) {
                    peakHourLiters = volume;
                    peakHour = hour;
                }

                Instant hourStart = date.atTime(hour, 0).toInstant(ZoneOffset.UTC);
                lastHour = hourStart;
                double avgFlow = volume / 60.0;
                String status =
                        avgFlow > 0.2
                                ? DeviceStateRecord.STATUS_FLOWING
                                : DeviceStateRecord.STATUS_IDLE;

                deviceFacade.writeHistoricalHour(
                        unit,
                        hourStart,
                        volume,
                        avgFlow,
                        100,
                        status,
                        expiresAt);
            }

            deviceFacade.writeHistoricalDaily(
                    unit, date, dailyTarget, peakHour, peakHourLiters);

            totalHistoricalLiters += dailyTarget;
        }

        if (lastHour != null) {
            deviceFacade.applyHistoricalCumulative(
                    unit.deviceId(), totalHistoricalLiters, lastHour);
        }
    }

    static double dailyTargetLiters(String deviceId, LocalDate date) {
        int mixed = deviceId.hashCode() ^ date.hashCode();
        return 600 + (Math.abs(mixed) % 601);
    }

    private double[] hourlyVolumesForDay(String deviceId, LocalDate date, double dailyTarget) {
        double[] weights = new double[24];
        double weekendFactor = date.getDayOfWeek().getValue() >= 6 ? 1.1 : 1.0;
        for (int hour = 0; hour < 24; hour++) {
            double noise = 0.9 + pseudoRandom(deviceId, date, hour) * 0.2;
            weights[hour] = profileFactory.hourlyPatternLiters(hour) * weekendFactor * noise;
        }

        double sum = Arrays.stream(weights).sum();
        double[] volumes = new double[24];
        for (int hour = 0; hour < 24; hour++) {
            volumes[hour] = dailyTarget * (weights[hour] / sum);
        }
        return volumes;
    }

    private static double pseudoRandom(String deviceId, LocalDate date, int hour) {
        int mixed = deviceId.hashCode() ^ date.hashCode() ^ (hour * 31);
        return (mixed & 0xFFFF) / 65535.0;
    }
}
