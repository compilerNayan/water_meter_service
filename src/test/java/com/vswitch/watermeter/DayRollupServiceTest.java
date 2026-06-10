package com.vswitch.watermeter;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.vswitch.watermeter.device.DeviceStore;
import com.vswitch.watermeter.device.TodaySlotRecord;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.DeleteItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DayRollupServiceTest {

    @Mock private DynamoDbClient dynamoDbClient;
    @Mock private UnitService unitService;

    private DayRollupService rollupService;

    @BeforeEach
    void setUp() {
        DeviceStore store =
                new DeviceStore(
                        dynamoDbClient,
                        "WaterMeterDeviceState",
                        "WaterMeterTodaySlots",
                        "WaterMeterDayHistory",
                        "WaterMeterDeviceConfig");
        rollupService = new DayRollupService(unitService, store, 400);
    }

    @Test
    void finalizeDayWritesHistoryAndDeletesSlots() {
        LocalDate yesterday = LocalDate.now(ZoneOffset.UTC).minusDays(1);
        String localDate = yesterday.toString();
        Instant periodStart = yesterday.atStartOfDay().toInstant(ZoneOffset.UTC);

        UnitRecord unit =
                new UnitRecord(
                        "wm-WM000001",
                        "k3m9x2a",
                        "WM000001",
                        "D205",
                        "D205",
                        "2",
                        "A",
                        "East",
                        "",
                        "",
                        "",
                        UnitRecord.STATUS_ENROLLED,
                        "D205-1234",
                        "2026-06-01T00:00:00Z",
                        "2026-06-01T00:00:00Z");

        TodaySlotRecord slot =
                new TodaySlotRecord(
                        "WM000001",
                        TodaySlotRecord.slotKeyFor(periodStart),
                        "k3m9x2a",
                        localDate,
                        "1000,2000,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0",
                        0,
                        0);

        when(unitService.listAllUnits()).thenReturn(List.of(unit));
        when(dynamoDbClient.query(any(QueryRequest.class)))
                .thenReturn(QueryResponse.builder().items(List.of(slot.toItem())).build());

        rollupService.runRollup(ZoneOffset.UTC);

        ArgumentCaptor<PutItemRequest> putCaptor = ArgumentCaptor.forClass(PutItemRequest.class);
        verify(dynamoDbClient, atLeastOnce()).putItem(putCaptor.capture());
        DayHistoryRecord history = DayHistoryRecord.fromItem(putCaptor.getValue().item());
        assertEquals("WM000001", history.deviceId());
        assertEquals(3.0, history.totalLiters(), 0.001);

        verify(dynamoDbClient, atLeastOnce()).deleteItem(any(DeleteItemRequest.class));
    }
}
