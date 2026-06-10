package com.vswitch.watermeter;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.vswitch.watermeter.device.DeviceFacade;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;

@Service
public class WaterReadingService {

    private static final int MAX_MINUTE_RECORDS_PER_QUERY = 4_000;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final DynamoDbClient dynamoDbClient;
    private final TelemetryIngestionService telemetryIngestionService;
    private final DeviceFacade deviceFacade;
    private final String minuteUsageTable;
    private final String dailyUsageTable;

    WaterReadingService(
            DynamoDbClient dynamoDbClient,
            TelemetryIngestionService telemetryIngestionService,
            DeviceFacade deviceFacade,
            @Value("${minute.usage.table.name:WaterMeterMinuteUsage}") String minuteUsageTable,
            @Value("${daily.usage.table.name:WaterMeterDailyUsage}") String dailyUsageTable) {
        this.dynamoDbClient = dynamoDbClient;
        this.telemetryIngestionService = telemetryIngestionService;
        this.deviceFacade = deviceFacade;
        this.minuteUsageTable = minuteUsageTable;
        this.dailyUsageTable = dailyUsageTable;
    }

    CurrentReadingResponse getCurrentReading(String deviceId) {
        return deviceFacade.getCurrentReading(deviceId);
    }

    WaterUsageResponse getUsage(
            String deviceId, Instant from, Instant to, String granularity, String timezone) {
        UsageGranularity g = UsageGranularity.fromApiValue(granularity);
        List<MinuteUsageRecord> minutes = queryMinutes(deviceId, from, to);
        List<UsageDataPointResponse> points = aggregateMinutes(minutes, from, to, g, timezone);

        double total = points.stream().mapToDouble(UsageDataPointResponse::volumeLiters).sum();
        double avg = points.isEmpty() ? 0 : total / points.size();
        UsageDataPointResponse peak =
                points.stream()
                        .max(Comparator.comparingDouble(UsageDataPointResponse::volumeLiters))
                        .orElse(new UsageDataPointResponse(from.toString(), 0, 0));

        Duration range = Duration.between(from, to);
        Instant prevFrom = from.minus(range);
        double prevTotal = sumUsageTotal(deviceId, prevFrom, from, g, timezone);
        double deltaPercent =
                prevTotal <= 0 ? 0 : ((total - prevTotal) / prevTotal) * 100.0;

        return new WaterUsageResponse(
                deviceId,
                from.toString(),
                to.toString(),
                g.apiValue(),
                "liters",
                points,
                new UsageSummaryResponse(
                        total,
                        avg,
                        new PeakBucketResponse(peak.timestamp(), peak.volumeLiters()),
                        prevTotal,
                        deltaPercent));
    }

    DailySummaryResponse getDailySummary(
            String deviceId, String tenantId, LocalDate from, LocalDate to) {
        List<DailySummaryDayResponse> days = new ArrayList<>();
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            String usageKey = DailyUsageRecord.usageKeyFor(date.format(DATE_FORMAT), deviceId);
            Optional<DailyUsageRecord> record =
                    telemetryIngestionService.findDailyUsage(tenantId, usageKey);
            if (record.isPresent()) {
                DailyUsageRecord r = record.get();
                days.add(
                        new DailySummaryDayResponse(
                                date.format(DATE_FORMAT),
                                r.totalLiters(),
                                r.peakHour(),
                                r.peakHourLiters()));
            } else {
                days.add(new DailySummaryDayResponse(date.format(DATE_FORMAT), 0, 0, 0));
            }
        }
        return new DailySummaryResponse("liters", days);
    }

    HourlyPatternResponse getHourlyPattern(
            String deviceId, LocalDate from, LocalDate to, String timezone) {
        ZoneId zone = safeZone(timezone);
        Instant start = from.atStartOfDay(zone).toInstant();
        Instant end = to.plusDays(1).atStartOfDay(zone).toInstant();
        List<MinuteUsageRecord> minutes = queryMinutes(deviceId, start, end);

        Map<Integer, List<Double>> byHour = new HashMap<>();
        for (MinuteUsageRecord minute : minutes) {
            Instant instant = parseMinuteKey(minute.minuteKey());
            int hour = instant.atZone(zone).getHour();
            byHour.computeIfAbsent(hour, h -> new ArrayList<>()).add(minute.volumeLiters());
        }

        List<HourlyPatternHourResponse> hours = new ArrayList<>();
        for (int hour = 0; hour < 24; hour++) {
            List<Double> values = byHour.getOrDefault(hour, List.of());
            double avg =
                    values.isEmpty()
                            ? 0
                            : values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            hours.add(new HourlyPatternHourResponse(hour, avg));
        }
        return new HourlyPatternResponse("liters", hours);
    }

    double getTodayUsedLiters(String deviceId, String tenantId) {
        return getTodayUsedLiters(deviceId, tenantId, "UTC");
    }

    /**
     * Today's usage from live minute buckets for the requested timezone, with daily rollup as
     * fallback. Matches {@link #getUsage} totals for the local calendar day (device dashboard).
     */
    double getTodayUsedLiters(String deviceId, String tenantId, String timezone) {
        ZoneId zone = safeZone(timezone);
        LocalDate today = LocalDate.now(zone);
        Instant start = today.atStartOfDay(zone).toInstant();
        Instant end = Instant.now();
        double fromMinutes = 0;
        if (!end.isBefore(start)) {
            fromMinutes =
                    sumUsageTotal(deviceId, start, end, UsageGranularity.H1, timezone);
        }

        String usageKey = DailyUsageRecord.usageKeyFor(today.format(DATE_FORMAT), deviceId);
        double fromDaily =
                telemetryIngestionService
                        .findDailyUsage(tenantId, usageKey)
                        .map(DailyUsageRecord::totalLiters)
                        .orElse(0.0);

        return Math.max(fromMinutes, fromDaily);
    }

    double sumMonthLiters(String deviceId, String tenantId, String timezone) {
        ZoneId zone = safeZone(timezone);
        LocalDate today = LocalDate.now(zone);
        YearMonth month = YearMonth.from(today);
        double total = 0;
        for (LocalDate date = month.atDay(1); date.isBefore(today); date = date.plusDays(1)) {
            String key = DailyUsageRecord.usageKeyFor(date.format(DATE_FORMAT), deviceId);
            total +=
                    telemetryIngestionService
                            .findDailyUsage(tenantId, key)
                            .map(DailyUsageRecord::totalLiters)
                            .orElse(0.0);
        }
        return total + getTodayUsedLiters(deviceId, tenantId, timezone);
    }

    ValveStateResponse getValveState(String deviceId, String tenantId) {
        return deviceFacade.getValveState(deviceId, tenantId);
    }

    ValveStateResponse updateValve(String deviceId, ValveUpdateRequest request) {
        String tenantId = requireTenantId(deviceId);
        return deviceFacade.setValveTarget(deviceId, tenantId, request);
    }

    private String requireTenantId(String deviceId) {
        return telemetryIngestionService
                .findDeviceState(deviceId)
                .map(DeviceStateRecord::tenantId)
                .filter(tenantId -> !tenantId.isBlank())
                .orElseThrow(
                        () ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND, "Device state not found"));
    }

    private double sumUsageTotal(
            String deviceId,
            Instant from,
            Instant to,
            UsageGranularity granularity,
            String timezone) {
        if (!Duration.between(from, to).isNegative()
                && Duration.between(from, to).compareTo(Duration.ofHours(36)) > 0) {
            return sumDailyLiters(deviceId, from, to);
        }

        List<MinuteUsageRecord> minutes = queryMinutes(deviceId, from, to);
        return aggregateMinutes(minutes, from, to, granularity, timezone).stream()
                .mapToDouble(UsageDataPointResponse::volumeLiters)
                .sum();
    }

    private double sumDailyLiters(String deviceId, Instant from, Instant to) {
        String tenantId = requireTenantId(deviceId);
        ZoneId zone = ZoneOffset.UTC;
        LocalDate startDate = from.atZone(zone).toLocalDate();
        LocalDate endDate = to.atZone(zone).toLocalDate();
        double total = 0;
        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            String usageKey = DailyUsageRecord.usageKeyFor(date.format(DATE_FORMAT), deviceId);
            total +=
                    telemetryIngestionService
                            .findDailyUsage(tenantId, usageKey)
                            .map(DailyUsageRecord::totalLiters)
                            .orElse(0.0);
        }
        return total;
    }

    private List<MinuteUsageRecord> queryMinutes(String deviceId, Instant from, Instant to) {
        String fromKey = MinuteUsageRecord.minuteKeyFor(from.truncatedTo(ChronoUnit.MINUTES));
        String toKey = MinuteUsageRecord.minuteKeyFor(to.truncatedTo(ChronoUnit.MINUTES));

        List<MinuteUsageRecord> records = new ArrayList<>();
        Map<String, AttributeValue> exclusiveStartKey = null;

        do {
            QueryRequest.Builder builder =
                    QueryRequest.builder()
                            .tableName(minuteUsageTable)
                            .keyConditionExpression(
                                    "deviceId = :deviceId AND minuteKey BETWEEN :fromKey AND :toKey")
                            .expressionAttributeValues(
                                    Map.of(
                                            ":deviceId",
                                            AttributeValue.builder().s(deviceId).build(),
                                            ":fromKey",
                                            AttributeValue.builder().s(fromKey).build(),
                                            ":toKey",
                                            AttributeValue.builder().s(toKey).build()));

            if (exclusiveStartKey != null && !exclusiveStartKey.isEmpty()) {
                builder.exclusiveStartKey(exclusiveStartKey);
            }

            var response = dynamoDbClient.query(builder.build());
            for (var item : response.items()) {
                records.add(MinuteUsageRecord.fromItem(item));
                if (records.size() >= MAX_MINUTE_RECORDS_PER_QUERY) {
                    return records;
                }
            }

            exclusiveStartKey = response.lastEvaluatedKey();
        } while (exclusiveStartKey != null && !exclusiveStartKey.isEmpty());

        return records;
    }

    private static ZoneId safeZone(String timezone) {
        try {
            return ZoneId.of(timezone);
        } catch (Exception ignored) {
            return ZoneOffset.UTC;
        }
    }

    private List<UsageDataPointResponse> aggregateMinutes(
            List<MinuteUsageRecord> minutes,
            Instant from,
            Instant to,
            UsageGranularity granularity,
            String timezone) {
        ZoneId zone = safeZone(timezone);
        Duration bucket = granularity.bucketDuration();
        Map<Instant, double[]> buckets = new HashMap<>();

        for (MinuteUsageRecord minute : minutes) {
            Instant instant = parseMinuteKey(minute.minuteKey());
            if (instant.isBefore(from) || instant.isAfter(to)) {
                continue;
            }
            Instant bucketStart = truncateToBucket(instant.atZone(zone), bucket).toInstant();
            double[] agg = buckets.computeIfAbsent(bucketStart, k -> new double[] {0, 0, 0});
            agg[0] += minute.volumeLiters();
            agg[1] += minute.avgFlowRateLpm();
            agg[2] += 1;
        }

        List<Instant> sorted = new ArrayList<>(buckets.keySet());
        sorted.sort(Comparator.naturalOrder());

        List<UsageDataPointResponse> points = new ArrayList<>();
        for (Instant bucketStart : sorted) {
            double[] agg = buckets.get(bucketStart);
            double count = Math.max(1, agg[2]);
            points.add(
                    new UsageDataPointResponse(
                            bucketStart.toString(),
                            agg[0],
                            agg[1] / count));
        }
        return points;
    }

    private static java.time.ZonedDateTime truncateToBucket(
            java.time.ZonedDateTime time, Duration bucket) {
        long bucketSeconds = bucket.getSeconds();
        long epoch = time.toEpochSecond();
        long truncated = (epoch / bucketSeconds) * bucketSeconds;
        return Instant.ofEpochSecond(truncated).atZone(time.getZone());
    }

    private static Instant parseMinuteKey(String minuteKey) {
        return Instant.parse(minuteKey.substring("minute#".length()));
    }
}
