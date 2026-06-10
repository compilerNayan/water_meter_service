package com.vswitch.watermeter.device;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.vswitch.watermeter.DeviceStateRecord;
import com.vswitch.watermeter.QuotaStepDto;
import com.vswitch.watermeter.QuotaUpdateRequest;
import com.vswitch.watermeter.UnitRecord;
import com.vswitch.watermeter.VolumeReadingService;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MockDeviceFacadeTest {

    @Mock
    private DynamoDbClient dynamoDbClient;

    private MockDeviceFacade facade;

    @BeforeEach
    void setUp() {
        DeviceStore store =
                new DeviceStore(
                        dynamoDbClient,
                        "WaterMeterDeviceState",
                        "WaterMeterTodaySlots",
                        "WaterMeterDayHistory",
                        "WaterMeterDeviceConfig");
        VolumeReadingService volumeReadingService = new VolumeReadingService(store);
        facade = new MockDeviceFacade(store, volumeReadingService, 72);
    }

    @Test
    void initializeDeviceConfigWritesDefaultsWhenMissing() {
        when(dynamoDbClient.getItem(any(GetItemRequest.class)))
                .thenReturn(GetItemResponse.builder().build());

        facade.initializeDeviceConfig("WM000001", "k3m9x2a");

        ArgumentCaptor<PutItemRequest> putCaptor = ArgumentCaptor.forClass(PutItemRequest.class);
        verify(dynamoDbClient).putItem(putCaptor.capture());
        DeviceConfigRecord record = DeviceConfigRecord.fromItem(putCaptor.getValue().item());
        assertEquals("WM000001", record.deviceId());
        assertEquals("k3m9x2a", record.tenantId());
        assertFalse(record.quotaEnabled());
        assertEquals(500, record.dailyLimitLiters());
    }

    @Test
    void initializeDeviceConfigIsIdempotent() {
        String now = Instant.now().toString();
        DeviceConfigRecord existing = DeviceConfigRecord.defaults("WM000001", "k3m9x2a", now);
        when(dynamoDbClient.getItem(any(GetItemRequest.class)))
                .thenReturn(GetItemResponse.builder().item(existing.toItem()).build());

        facade.initializeDeviceConfig("WM000001", "k3m9x2a");

        verify(dynamoDbClient, never()).putItem(any(PutItemRequest.class));
    }

    @Test
    void setQuotaPersistsEnabledRulesAndSteps() {
        when(dynamoDbClient.getItem(any(GetItemRequest.class)))
                .thenReturn(GetItemResponse.builder().build());

        DeviceQuotaConfig updated =
                facade.setQuota(
                        "WM000001",
                        "k3m9x2a",
                        new QuotaUpdateRequest(
                                true,
                                600,
                                List.of(
                                        new QuotaStepDto(300, "reduce_pressure", 20.0),
                                        new QuotaStepDto(600, "turn_off", null))));

        assertTrue(updated.enabled());
        assertEquals(600, updated.dailyLimitLiters());
        assertEquals(2, updated.steps().size());
        assertEquals(300, updated.steps().get(0).atLitersUsed());

        ArgumentCaptor<PutItemRequest> putCaptor = ArgumentCaptor.forClass(PutItemRequest.class);
        verify(dynamoDbClient).putItem(putCaptor.capture());
        Map<String, AttributeValue> item = putCaptor.getValue().item();
        assertTrue(item.get("quotaEnabled").bool());
        assertEquals(600.0, Double.parseDouble(item.get("dailyLimitLiters").n()));
    }

    @Test
    void ingest30MinuteBucketWritesTodaySlot() {
        String now = Instant.parse("2026-06-09T10:00:00Z").toString();
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
                        now,
                        "NORMAL",
                        now);

        // ingest30MinuteBucket: findDeviceConfig (timezone), findDeviceState, findDeviceConfig (pressure)
        when(dynamoDbClient.getItem(any(GetItemRequest.class)))
                .thenReturn(
                        GetItemResponse.builder().build(),
                        GetItemResponse.builder().item(state.toItem()).build(),
                        GetItemResponse.builder().build());
        when(dynamoDbClient.query(any(QueryRequest.class)))
                .thenReturn(QueryResponse.builder().items(List.of()).build());

        Instant periodStart = LocalDate.of(2026, 6, 9).atTime(10, 0).toInstant(ZoneOffset.UTC);
        facade.ingest30MinuteBucket(
                new ThirtyMinuteBucketPayload(
                        "k3m9x2a",
                        "WM000001",
                        periodStart,
                        List.of(new MinuteBucketEntry(periodStart, 1500)),
                        1001.5,
                        100));

        verify(dynamoDbClient, atLeastOnce()).putItem(any(PutItemRequest.class));
    }
}
