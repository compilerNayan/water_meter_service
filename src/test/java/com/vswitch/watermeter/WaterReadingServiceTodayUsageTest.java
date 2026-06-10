package com.vswitch.watermeter;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.vswitch.watermeter.device.DeviceFacade;
import com.vswitch.watermeter.device.DeviceStore;
import com.vswitch.watermeter.device.TodaySlotRecord;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WaterReadingServiceTodayUsageTest {

    @Mock private DynamoDbClient dynamoDbClient;
    @Mock private DeviceFacade deviceFacade;
    @Mock private TelemetryIngestionService telemetryIngestionService;

    private WaterReadingService waterReadingService;

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
        waterReadingService =
                new WaterReadingService(
                        volumeReadingService, telemetryIngestionService, deviceFacade);
    }

    @Test
    void getTodayUsedLitersSumsTodaySlots() {
        Instant periodStart = LocalDate.now(ZoneOffset.UTC).atStartOfDay().toInstant(ZoneOffset.UTC);
        TodaySlotRecord slot =
                new TodaySlotRecord(
                        "WM000001",
                        TodaySlotRecord.slotKeyFor(periodStart),
                        "k3m9x2a",
                        LocalDate.now(ZoneOffset.UTC).toString(),
                        "1000,2000,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0",
                        0,
                        0);

        when(dynamoDbClient.query(any(QueryRequest.class)))
                .thenReturn(
                        QueryResponse.builder()
                                .items(List.of(slot.toItem()))
                                .build());

        double liters = waterReadingService.getTodayUsedLiters("WM000001", "k3m9x2a", "UTC");
        assertEquals(3.0, liters, 0.001);
    }
}
