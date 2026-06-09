package com.vswitch.watermeter;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;

@Service
public class TelemetryIngestionService {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final DynamoDbClient dynamoDbClient;
    private final String deviceStateTable;
    private final String minuteUsageTable;
    private final String dailyUsageTable;

    TelemetryIngestionService(
            DynamoDbClient dynamoDbClient,
            @Value("${device.state.table.name:WaterMeterDeviceState}") String deviceStateTable,
            @Value("${minute.usage.table.name:WaterMeterMinuteUsage}") String minuteUsageTable,
            @Value("${daily.usage.table.name:WaterMeterDailyUsage}") String dailyUsageTable) {
        this.dynamoDbClient = dynamoDbClient;
        this.deviceStateTable = deviceStateTable;
        this.minuteUsageTable = minuteUsageTable;
        this.dailyUsageTable = dailyUsageTable;
    }

    void initializeDeviceState(String deviceId, String tenantId) {
        Optional<DeviceStateRecord> existing = findDeviceState(deviceId);
        if (existing.isPresent()) {
            return;
        }
        String now = Instant.now().toString();
        double cumulative = 10000 + Math.abs(deviceId.hashCode() % 50000);
        String mockProfile = mockProfileName(deviceId);
        DeviceStateRecord state =
                new DeviceStateRecord(
                        deviceId,
                        tenantId,
                        cumulative,
                        0,
                        DeviceStateRecord.STATUS_IDLE,
                        100,
                        100,
                        100,
                        now,
                        mockProfile,
                        now);
        dynamoDbClient.putItem(
                PutItemRequest.builder()
                        .tableName(deviceStateTable)
                        .item(state.toItem())
                        .build());
    }

    void ingestMinuteBucket(
            UnitRecord unit,
            Instant minute,
            double volumeLiters,
            double avgFlowRateLpm,
            double valveTargetPercent,
            String status) {
        String minuteKey = MinuteUsageRecord.minuteKeyFor(minute);
        long expiresAt = minute.getEpochSecond() + 48 * 3600;

        MinuteUsageRecord minuteRecord =
                new MinuteUsageRecord(
                        unit.deviceId(),
                        minuteKey,
                        unit.tenantId(),
                        volumeLiters,
                        avgFlowRateLpm,
                        valveTargetPercent,
                        expiresAt);

        dynamoDbClient.putItem(
                PutItemRequest.builder()
                        .tableName(minuteUsageTable)
                        .item(minuteRecord.toItem())
                        .build());

        DeviceStateRecord current =
                findDeviceState(unit.deviceId())
                        .orElseGet(
                                () -> {
                                    initializeDeviceState(unit.deviceId(), unit.tenantId());
                                    return findDeviceState(unit.deviceId()).orElseThrow();
                                });

        double cumulative = current.cumulativeLiters() + volumeLiters;
        double actualPercent =
                valveTargetPercent <= 0
                        ? 0
                        : Math.max(0, valveTargetPercent - 1 + (unit.deviceId().hashCode() % 3));
        String now = Instant.now().toString();

        DeviceStateRecord updated =
                new DeviceStateRecord(
                        unit.deviceId(),
                        unit.tenantId(),
                        cumulative,
                        avgFlowRateLpm,
                        status,
                        valveTargetPercent,
                        actualPercent,
                        current.lastUserPressurePercent(),
                        now,
                        current.mockProfile(),
                        now);

        dynamoDbClient.putItem(
                PutItemRequest.builder()
                        .tableName(deviceStateTable)
                        .item(updated.toItem())
                        .build());

        updateDailyRollup(unit, minute, volumeLiters);
    }

    Optional<DeviceStateRecord> findDeviceState(String deviceId) {
        var response =
                dynamoDbClient.getItem(
                        GetItemRequest.builder()
                                .tableName(deviceStateTable)
                                .key(
                                        Map.of(
                                                "deviceId",
                                                AttributeValue.builder().s(deviceId).build()))
                                .build());
        if (response.item() == null || response.item().isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(DeviceStateRecord.fromItem(response.item()));
    }

    DeviceStateRecord updateValveTarget(String deviceId, double targetPercent) {
        DeviceStateRecord current =
                findDeviceState(deviceId)
                        .orElseThrow(
                                () ->
                                        new org.springframework.web.server.ResponseStatusException(
                                                org.springframework.http.HttpStatus.NOT_FOUND,
                                                "Device state not found"));

        double clamped = Math.max(0, Math.min(100, targetPercent));
        String now = Instant.now().toString();
        double actual =
                clamped <= 0 ? 0 : Math.max(0, clamped - 1 + (deviceId.hashCode() % 3));
        String status =
                clamped <= 0
                        ? DeviceStateRecord.STATUS_IDLE
                        : current.status().equals(DeviceStateRecord.STATUS_OFFLINE)
                                ? DeviceStateRecord.STATUS_OFFLINE
                                : current.flowRateLpm() > 0.2
                                        ? DeviceStateRecord.STATUS_FLOWING
                                        : DeviceStateRecord.STATUS_IDLE;

        DeviceStateRecord updated =
                new DeviceStateRecord(
                        deviceId,
                        current.tenantId(),
                        current.cumulativeLiters(),
                        current.flowRateLpm(),
                        status,
                        clamped,
                        actual,
                        clamped,
                        current.lastSeenAt(),
                        current.mockProfile(),
                        now);

        dynamoDbClient.putItem(
                PutItemRequest.builder()
                        .tableName(deviceStateTable)
                        .item(updated.toItem())
                        .build());
        return updated;
    }

    private void updateDailyRollup(UnitRecord unit, Instant minute, double volumeLiters) {
        String date = minute.atZone(ZoneOffset.UTC).format(DATE_FORMAT);
        String usageKey = DailyUsageRecord.usageKeyFor(date, unit.deviceId());
        int hour = minute.atZone(ZoneOffset.UTC).getHour();
        String now = Instant.now().toString();

        Map<String, AttributeValue> key =
                Map.of(
                        "tenantId", AttributeValue.builder().s(unit.tenantId()).build(),
                        "usageKey", AttributeValue.builder().s(usageKey).build());

        Map<String, AttributeValue> values = new HashMap<>();
        values.put(":vol", AttributeValue.builder().n(Double.toString(volumeLiters)).build());
        values.put(":uid", AttributeValue.builder().s(unit.unitId()).build());
        values.put(":name", AttributeValue.builder().s(unit.name()).build());
        values.put(":block", AttributeValue.builder().s(unit.block()).build());
        values.put(":wing", AttributeValue.builder().s(unit.wing()).build());
        values.put(":hour", AttributeValue.builder().n(Integer.toString(hour)).build());
        values.put(":hourVol", AttributeValue.builder().n(Double.toString(volumeLiters)).build());
        values.put(":zero", AttributeValue.builder().n("0").build());
        values.put(":updatedAt", AttributeValue.builder().s(now).build());

        dynamoDbClient.updateItem(
                UpdateItemRequest.builder()
                        .tableName(dailyUsageTable)
                        .key(key)
                        .updateExpression(
                                "ADD totalLiters :vol "
                                        + "SET unitId = :uid, #name = :name, #block = :block, "
                                        + "#wing = :wing, updatedAt = :updatedAt, "
                                        + "peakHour = if_not_exists(peakHour, :hour), "
                                        + "peakHourLiters = if_not_exists(peakHourLiters, :zero)")
                        .expressionAttributeNames(
                                Map.of("#name", "name", "#block", "block", "#wing", "wing"))
                        .expressionAttributeValues(values)
                        .build());

        // Update peak hour if this hour's volume exceeds stored peak (approximate via re-read)
        Optional<DailyUsageRecord> daily = findDailyUsage(unit.tenantId(), usageKey);
        if (daily.isPresent() && volumeLiters > daily.get().peakHourLiters()) {
            dynamoDbClient.updateItem(
                    UpdateItemRequest.builder()
                            .tableName(dailyUsageTable)
                            .key(key)
                            .updateExpression(
                                    "SET peakHour = :hour, peakHourLiters = :hourVol")
                            .expressionAttributeValues(
                                    Map.of(
                                            ":hour",
                                                    AttributeValue.builder()
                                                            .n(Integer.toString(hour))
                                                            .build(),
                                            ":hourVol",
                                                    AttributeValue.builder()
                                                            .n(Double.toString(volumeLiters))
                                                            .build()))
                            .build());
        }
    }

    Optional<DailyUsageRecord> findDailyUsage(String tenantId, String usageKey) {
        var response =
                dynamoDbClient.getItem(
                        GetItemRequest.builder()
                                .tableName(dailyUsageTable)
                                .key(
                                        Map.of(
                                                "tenantId",
                                                AttributeValue.builder().s(tenantId).build(),
                                                "usageKey",
                                                AttributeValue.builder().s(usageKey).build()))
                                .build());
        if (response.item() == null || response.item().isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(DailyUsageRecord.fromItem(response.item()));
    }

    void writeHistoricalHour(
            UnitRecord unit,
            Instant hourStart,
            double volumeLiters,
            double avgFlowRateLpm,
            double valveTargetPercent,
            String status,
            long expiresAtEpochSeconds) {
        String minuteKey = MinuteUsageRecord.minuteKeyFor(hourStart.truncatedTo(ChronoUnit.HOURS));

        MinuteUsageRecord minuteRecord =
                new MinuteUsageRecord(
                        unit.deviceId(),
                        minuteKey,
                        unit.tenantId(),
                        volumeLiters,
                        avgFlowRateLpm,
                        valveTargetPercent,
                        expiresAtEpochSeconds);

        dynamoDbClient.putItem(
                PutItemRequest.builder()
                        .tableName(minuteUsageTable)
                        .item(minuteRecord.toItem())
                        .build());
    }

    void writeHistoricalDaily(
            UnitRecord unit,
            java.time.LocalDate date,
            double totalLiters,
            int peakHour,
            double peakHourLiters) {
        String usageKey = DailyUsageRecord.usageKeyFor(date.format(DATE_FORMAT), unit.deviceId());
        String now = Instant.now().toString();
        DailyUsageRecord record =
                new DailyUsageRecord(
                        unit.tenantId(),
                        usageKey,
                        unit.unitId(),
                        unit.name(),
                        unit.block(),
                        unit.wing(),
                        totalLiters,
                        peakHour,
                        peakHourLiters,
                        now);

        dynamoDbClient.putItem(
                PutItemRequest.builder()
                        .tableName(dailyUsageTable)
                        .item(record.toItem())
                        .build());
    }

    void applyHistoricalCumulative(String deviceId, double additionalLiters, Instant lastHour) {
        DeviceStateRecord current =
                findDeviceState(deviceId)
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "Device state missing for " + deviceId));

        String timestamp = lastHour.toString();
        DeviceStateRecord updated =
                new DeviceStateRecord(
                        deviceId,
                        current.tenantId(),
                        current.cumulativeLiters() + additionalLiters,
                        0,
                        DeviceStateRecord.STATUS_IDLE,
                        current.valveTargetPercent(),
                        current.valveActualPercent(),
                        current.lastUserPressurePercent(),
                        timestamp,
                        current.mockProfile(),
                        timestamp);

        dynamoDbClient.putItem(
                PutItemRequest.builder()
                        .tableName(deviceStateTable)
                        .item(updated.toItem())
                        .build());
    }

    private static String mockProfileName(String deviceId) {
        int seed = Math.abs(deviceId.hashCode());
        int mod = seed % 100;
        if (mod < 8) {
            return MockDeviceProfile.AnomalyType.LEAK_BURST.name();
        }
        if (mod < 12) {
            return MockDeviceProfile.AnomalyType.VALVE_MISMATCH.name();
        }
        if (mod < 18) {
            return MockDeviceProfile.AnomalyType.OFFLINE.name();
        }
        return MockDeviceProfile.AnomalyType.NORMAL.name();
    }
}
