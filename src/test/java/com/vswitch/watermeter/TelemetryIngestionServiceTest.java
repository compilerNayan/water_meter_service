package com.vswitch.watermeter;

import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.vswitch.watermeter.device.DeviceStore;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TelemetryIngestionServiceTest {

    @Mock private DynamoDbClient dynamoDbClient;

    private TelemetryIngestionService service;

    @BeforeEach
    void setUp() {
        DeviceStore store =
                new DeviceStore(
                        dynamoDbClient,
                        "WaterMeterDeviceState",
                        "WaterMeterTodaySlots",
                        "WaterMeterDayHistory",
                        "WaterMeterDeviceConfig");
        service = new TelemetryIngestionService(store);
    }

    @Test
    void findDeviceStateReturnsRecordWhenPresent() {
        DeviceStateRecord state =
                new DeviceStateRecord(
                        "WM000001",
                        "k3m9x2a",
                        1000,
                        0,
                        DeviceStateRecord.STATUS_IDLE,
                        100,
                        99,
                        100,
                        "2026-06-09T10:00:00Z",
                        "NORMAL",
                        "2026-06-09T10:00:00Z");

        when(dynamoDbClient.getItem(any(GetItemRequest.class)))
                .thenReturn(GetItemResponse.builder().item(state.toItem()).build());

        Optional<DeviceStateRecord> found = service.findDeviceState("WM000001");
        assertTrue(found.isPresent());
        assertTrue(found.get().deviceId().equals("WM000001"));
    }
}
