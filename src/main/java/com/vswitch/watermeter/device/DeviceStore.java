package com.vswitch.watermeter.device;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import com.vswitch.watermeter.DailyUsageRecord;
import com.vswitch.watermeter.DeviceStateRecord;
import com.vswitch.watermeter.MinuteUsageRecord;
import com.vswitch.watermeter.MockDeviceProfile;
import com.vswitch.watermeter.UnitRecord;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;

@Repository
public class DeviceStore {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final DynamoDbClient dynamoDbClient;
    private final String deviceStateTable;
    private final String minuteUsageTable;
    private final String dailyUsageTable;
    private final String deviceConfigTable;

    DeviceStore(
            DynamoDbClient dynamoDbClient,
            @Value("${device.state.table.name:WaterMeterDeviceState}") String deviceStateTable,
            @Value("${minute.usage.table.name:WaterMeterMinuteUsage}") String minuteUsageTable,
            @Value("${daily.usage.table.name:WaterMeterDailyUsage}") String dailyUsageTable,
            @Value("${device.config.table.name:WaterMeterDeviceConfig}") String deviceConfigTable) {
        this.dynamoDbClient = dynamoDbClient;
        this.deviceStateTable = deviceStateTable;
        this.minuteUsageTable = minuteUsageTable;
        this.dailyUsageTable = dailyUsageTable;
        this.deviceConfigTable = deviceConfigTable;
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

    void putDeviceState(DeviceStateRecord state) {
        dynamoDbClient.putItem(
                PutItemRequest.builder()
                        .tableName(deviceStateTable)
                        .item(state.toItem())
                        .build());
    }

    Optional<DeviceConfigRecord> findDeviceConfig(String deviceId) {
        var response =
                dynamoDbClient.getItem(
                        GetItemRequest.builder()
                                .tableName(deviceConfigTable)
                                .key(
                                        Map.of(
                                                "deviceId",
                                                AttributeValue.builder().s(deviceId).build()))
                                .build());
        if (response.item() == null || response.item().isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(DeviceConfigRecord.fromItem(response.item()));
    }

    void putDeviceConfig(DeviceConfigRecord config) {
        dynamoDbClient.putItem(
                PutItemRequest.builder()
                        .tableName(deviceConfigTable)
                        .item(config.toItem())
                        .build());
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

    void putMinuteUsage(MinuteUsageRecord record) {
        dynamoDbClient.putItem(
                PutItemRequest.builder()
                        .tableName(minuteUsageTable)
                        .item(record.toItem())
                        .build());
    }

    void updateDailyRollup(UnitRecord unit, Instant minute, double volumeLiters) {
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

        Optional<DailyUsageRecord> daily = findDailyUsage(unit.tenantId(), usageKey);
        if (daily.isPresent() && volumeLiters > daily.get().peakHourLiters()) {
            dynamoDbClient.updateItem(
                    UpdateItemRequest.builder()
                            .tableName(dailyUsageTable)
                            .key(key)
                            .updateExpression("SET peakHour = :hour, peakHourLiters = :hourVol")
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

    void writeHistoricalHour(
            UnitRecord unit,
            Instant hourStart,
            double volumeLiters,
            double avgFlowRateLpm,
            double valveTargetPercent,
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

        putMinuteUsage(minuteRecord);
    }

    void writeHistoricalDaily(
            UnitRecord unit,
            LocalDate date,
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

        putDeviceState(updated);
    }

    static String mockProfileName(String deviceId) {
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
