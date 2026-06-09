package com.vswitch.watermeter;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
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

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;

@Service
public class WaterReadingService {

    private static final Duration OFFLINE_THRESHOLD = Duration.ofMinutes(15);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final DynamoDbClient dynamoDbClient;
    private final TelemetryIngestionService telemetryIngestionService;
    private final String minuteUsageTable;
    private final String dailyUsageTable;

    WaterReadingService(
            DynamoDbClient dynamoDbClient,
            TelemetryIngestionService telemetryIngestionService,
            @Value("${minute.usage.table.name:WaterMeterMinuteUsage}") String minuteUsageTable,
            @Value("${daily.usage.table.name:WaterMeterDailyUsage}") String dailyUsageTable) {
        this.dynamoDbClient = dynamoDbClient;
        this.telemetryIngestionService = telemetryIngestionService;
        this.minuteUsageTable = minuteUsageTable;
        this.dailyUsageTable = dailyUsageTable;
    }

    CurrentReadingResponse getCurrentReading(String deviceId) {
        DeviceStateRecord state = requireDeviceState(deviceId);
        String status = resolveStatus(state);
        return new CurrentReadingResponse(
                deviceId,
                state.lastSeenAt(),
                state.flowRateLpm(),
                state.cumulativeLiters(),
                status);
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
        List<MinuteUsageRecord> prevMinutes = queryMinutes(deviceId, prevFrom, from);
        List<UsageDataPointResponse> prevPoints =
                aggregateMinutes(prevMinutes, prevFrom, from, g, timezone);
        double prevTotal =
                prevPoints.stream().mapToDouble(UsageDataPointResponse::volumeLiters).sum();
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
        ZoneId zone = ZoneId.of(timezone);
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

    ValveStateResponse getValveState(String deviceId) {
        DeviceStateRecord state = requireDeviceState(deviceId);
        return toValveResponse(state);
    }

    ValveStateResponse updateValve(String deviceId, ValveUpdateRequest request) {
        if (request.action() != null && "restore".equalsIgnoreCase(request.action())) {
            DeviceStateRecord state = requireDeviceState(deviceId);
            return toValveResponse(
                    telemetryIngestionService.updateValveTarget(
                            deviceId, state.lastUserPressurePercent()));
        }
        if (request.pressurePercent() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "pressurePercent required");
        }
        return toValveResponse(
                telemetryIngestionService.updateValveTarget(deviceId, request.pressurePercent()));
    }

    private DeviceStateRecord requireDeviceState(String deviceId) {
        return telemetryIngestionService
                .findDeviceState(deviceId)
                .orElseThrow(
                        () ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND, "Device state not found"));
    }

    private String resolveStatus(DeviceStateRecord state) {
        if (state.lastSeenAt() == null || state.lastSeenAt().isBlank()) {
            return DeviceStateRecord.STATUS_OFFLINE;
        }
        Instant lastSeen = Instant.parse(state.lastSeenAt());
        if (Duration.between(lastSeen, Instant.now()).compareTo(OFFLINE_THRESHOLD) > 0) {
            return DeviceStateRecord.STATUS_OFFLINE;
        }
        return state.status();
    }

    private ValveStateResponse toValveResponse(DeviceStateRecord state) {
        boolean isOff = state.valveTargetPercent() <= 0;
        double effective = isOff ? 0 : state.valveActualPercent();
        return new ValveStateResponse(
                state.deviceId(),
                state.updatedAt(),
                state.valveTargetPercent(),
                state.valveActualPercent(),
                state.lastUserPressurePercent(),
                isOff,
                "manual",
                null,
                effective);
    }

    private List<MinuteUsageRecord> queryMinutes(String deviceId, Instant from, Instant to) {
        String fromKey = MinuteUsageRecord.minuteKeyFor(from.truncatedTo(ChronoUnit.MINUTES));
        String toKey = MinuteUsageRecord.minuteKeyFor(to.truncatedTo(ChronoUnit.MINUTES));

        var response =
                dynamoDbClient.query(
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
                                                AttributeValue.builder().s(toKey).build()))
                                .build());

        List<MinuteUsageRecord> records = new ArrayList<>();
        for (var item : response.items()) {
            records.add(MinuteUsageRecord.fromItem(item));
        }
        return records;
    }

    private List<UsageDataPointResponse> aggregateMinutes(
            List<MinuteUsageRecord> minutes,
            Instant from,
            Instant to,
            UsageGranularity granularity,
            String timezone) {
        ZoneId zone = ZoneId.of(timezone);
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
