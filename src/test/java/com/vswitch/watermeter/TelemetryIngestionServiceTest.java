package com.vswitch.watermeter;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TelemetryIngestionServiceTest {

    @Mock
    private DynamoDbClient dynamoDbClient;

    @Test
    void turnOffPreservesLastUserPressureForRestore() {
        String now = Instant.parse("2026-06-09T10:00:00Z").toString();
        DeviceStateRecord existing =
                new DeviceStateRecord(
                        "WM000001",
                        "k3m9x2a",
                        1000,
                        0,
                        DeviceStateRecord.STATUS_IDLE,
                        80,
                        78,
                        80,
                        now,
                        "normal",
                        now);

        when(dynamoDbClient.getItem(any(GetItemRequest.class)))
                .thenReturn(GetItemResponse.builder().item(existing.toItem()).build());

        TelemetryIngestionService service =
                new TelemetryIngestionService(
                        dynamoDbClient, "WaterMeterDeviceState", "m", "d");

        DeviceStateRecord updated = service.updateValveTarget("WM000001", 0);

        assertEquals(0, updated.valveTargetPercent());
        assertEquals(80, updated.lastUserPressurePercent());

        ArgumentCaptor<PutItemRequest> putCaptor = ArgumentCaptor.forClass(PutItemRequest.class);
        verify(dynamoDbClient).putItem(putCaptor.capture());
        DeviceStateRecord persisted =
                DeviceStateRecord.fromItem(putCaptor.getValue().item());
        assertEquals(80, persisted.lastUserPressurePercent());
    }

    @Test
    void setPressureUpdatesLastUserPressure() {
        String now = Instant.parse("2026-06-09T10:00:00Z").toString();
        DeviceStateRecord existing =
                new DeviceStateRecord(
                        "WM000001",
                        "k3m9x2a",
                        1000,
                        0,
                        DeviceStateRecord.STATUS_IDLE,
                        0,
                        0,
                        80,
                        now,
                        "normal",
                        now);

        when(dynamoDbClient.getItem(any(GetItemRequest.class)))
                .thenReturn(GetItemResponse.builder().item(existing.toItem()).build());

        TelemetryIngestionService service =
                new TelemetryIngestionService(
                        dynamoDbClient, "WaterMeterDeviceState", "m", "d");

        DeviceStateRecord updated = service.updateValveTarget("WM000001", 55);

        assertEquals(55, updated.valveTargetPercent());
        assertEquals(55, updated.lastUserPressurePercent());
    }
}
