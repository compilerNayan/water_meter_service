package com.vswitch.watermeter;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.vswitch.watermeter.device.DeviceConfigRecord;
import com.vswitch.watermeter.device.DeviceStore;
import com.vswitch.watermeter.device.MockDeviceFacade;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TelemetryIngestionServiceTest {

    @Mock
    private DynamoDbClient dynamoDbClient;

    private MockDeviceFacade facade;

    @BeforeEach
    void setUp() {
        DeviceStore store =
                new DeviceStore(
                        dynamoDbClient,
                        "WaterMeterDeviceState",
                        "WaterMeterMinuteUsage",
                        "WaterMeterDailyUsage",
                        "WaterMeterDeviceConfig");
        facade = new MockDeviceFacade(store);
    }

    @Test
    void turnOffPreservesLastUserPressureForRestore() {
        String now = Instant.parse("2026-06-09T10:00:00Z").toString();
        DeviceStateRecord state =
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
                        "NORMAL",
                        now);
        AtomicReference<DeviceConfigRecord> configRef =
                new AtomicReference<>(
                        DeviceConfigRecord.defaults("WM000001", "k3m9x2a", now, 80.0, 80.0));

        stubConfigAndStateReads(state, configRef);
        stubConfigWrites(configRef);

        ValveStateResponse response =
                facade.setValveTarget(
                        "WM000001", "k3m9x2a", new ValveUpdateRequest(0.0, null));

        assertEquals(0, response.targetPressurePercent());
        assertEquals(80, response.lastUserPressurePercent());

        ArgumentCaptor<PutItemRequest> putCaptor = ArgumentCaptor.forClass(PutItemRequest.class);
        verify(dynamoDbClient).putItem(putCaptor.capture());
        DeviceConfigRecord persisted =
                DeviceConfigRecord.fromItem(putCaptor.getValue().item());
        assertEquals(0, persisted.valveTargetPercent());
        assertEquals(80, persisted.lastUserPressurePercent());
    }

    @Test
    void setPressureUpdatesLastUserPressure() {
        String now = Instant.parse("2026-06-09T10:00:00Z").toString();
        DeviceStateRecord state =
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
                        "NORMAL",
                        now);
        AtomicReference<DeviceConfigRecord> configRef =
                new AtomicReference<>(
                        DeviceConfigRecord.defaults("WM000001", "k3m9x2a", now, 0.0, 80.0));

        stubConfigAndStateReads(state, configRef);
        stubConfigWrites(configRef);

        ValveStateResponse response =
                facade.setValveTarget(
                        "WM000001", "k3m9x2a", new ValveUpdateRequest(55.0, null));

        assertEquals(55, response.targetPressurePercent());
        assertEquals(55, response.lastUserPressurePercent());
    }

    private void stubConfigAndStateReads(
            DeviceStateRecord state, AtomicReference<DeviceConfigRecord> configRef) {
        when(dynamoDbClient.getItem(any(GetItemRequest.class)))
                .thenAnswer(
                        invocation -> {
                            GetItemRequest req = invocation.getArgument(0);
                            if ("WaterMeterDeviceConfig".equals(req.tableName())) {
                                return GetItemResponse.builder()
                                        .item(configRef.get().toItem())
                                        .build();
                            }
                            if (req.key().containsKey("usageKey")) {
                                return GetItemResponse.builder().build();
                            }
                            return GetItemResponse.builder().item(state.toItem()).build();
                        });
    }

    private void stubConfigWrites(AtomicReference<DeviceConfigRecord> configRef) {
        doAnswer(
                        invocation -> {
                            PutItemRequest put = invocation.getArgument(0);
                            if ("WaterMeterDeviceConfig".equals(put.tableName())) {
                                configRef.set(DeviceConfigRecord.fromItem(put.item()));
                            }
                            return null;
                        })
                .when(dynamoDbClient)
                .putItem(any(PutItemRequest.class));
    }
}
