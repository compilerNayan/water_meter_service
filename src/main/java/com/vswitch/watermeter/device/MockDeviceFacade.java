package com.vswitch.watermeter.device;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.vswitch.watermeter.QuotaStepDto;
import com.vswitch.watermeter.QuotaStepsJson;
import com.vswitch.watermeter.QuotaUpdateRequest;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

@Service
public class MockDeviceFacade implements DeviceFacade {

    private final DynamoDbClient dynamoDbClient;
    private final String tableName;

    MockDeviceFacade(
            DynamoDbClient dynamoDbClient,
            @Value("${device.config.table.name:WaterMeterDeviceConfig}") String tableName) {
        this.dynamoDbClient = dynamoDbClient;
        this.tableName = tableName;
    }

    @Override
    public DeviceQuotaConfig getQuotaConfig(String deviceId) {
        DeviceConfigRecord record = requireConfig(deviceId);
        return record.toQuotaConfig(QuotaStepsJson.fromJson(record.quotaStepsJson()));
    }

    @Override
    public DeviceQuotaConfig setQuota(
            String deviceId, String tenantId, QuotaUpdateRequest request) {
        validateQuotaRequest(request);
        DeviceConfigRecord existing = findConfig(deviceId).orElseGet(
                () -> DeviceConfigRecord.defaults(deviceId, tenantId, Instant.now().toString()));

        String now = Instant.now().toString();
        List<QuotaStepDto> steps = QuotaStepsJson.sortSteps(request.steps());
        DeviceConfigRecord updated =
                new DeviceConfigRecord(
                        deviceId,
                        tenantId,
                        request.enabled(),
                        request.dailyLimitLiters(),
                        QuotaStepsJson.toJson(steps),
                        existing.timezone(),
                        existing.valveTargetPercent(),
                        existing.lastUserPressurePercent(),
                        now);

        dynamoDbClient.putItem(
                PutItemRequest.builder().tableName(tableName).item(updated.toItem()).build());
        return updated.toQuotaConfig(steps);
    }

    @Override
    public void initializeDeviceConfig(String deviceId, String tenantId) {
        if (findConfig(deviceId).isPresent()) {
            return;
        }
        String now = Instant.now().toString();
        DeviceConfigRecord record = DeviceConfigRecord.defaults(deviceId, tenantId, now);
        dynamoDbClient.putItem(
                PutItemRequest.builder().tableName(tableName).item(record.toItem()).build());
    }

    Optional<DeviceConfigRecord> findConfig(String deviceId) {
        var response =
                dynamoDbClient.getItem(
                        GetItemRequest.builder()
                                .tableName(tableName)
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

    private DeviceConfigRecord requireConfig(String deviceId) {
        return findConfig(deviceId)
                .orElseThrow(
                        () ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND, "Device config not found"));
    }

    private static void validateQuotaRequest(QuotaUpdateRequest request) {
        if (request.dailyLimitLiters() <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "dailyLimitLiters must be positive");
        }
        for (QuotaStepDto step : request.steps()) {
            if (step.atLitersUsed() < 0) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "step atLitersUsed must be non-negative");
            }
            if ("reduce_pressure".equals(step.action())
                    && (step.value() == null || step.value() <= 0)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "reduce_pressure step requires positive value");
            }
            if (!"reduce_pressure".equals(step.action()) && !"turn_off".equals(step.action())) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "step action must be reduce_pressure or turn_off");
            }
        }
    }
}
