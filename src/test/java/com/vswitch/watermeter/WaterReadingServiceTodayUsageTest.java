package com.vswitch.watermeter;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.vswitch.watermeter.device.DeviceFacade;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WaterReadingServiceTodayUsageTest {

    @Mock private DynamoDbClient dynamoDbClient;
    @Mock private TelemetryIngestionService telemetryIngestionService;
    @Mock private DeviceFacade deviceFacade;

    private WaterReadingService service;

    @BeforeEach
    void setUp() {
        service =
                new WaterReadingService(
                        dynamoDbClient,
                        telemetryIngestionService,
                        deviceFacade,
                        "WaterMeterMinuteUsage",
                        "WaterMeterDailyUsage");
    }

    @Test
    void getTodayUsedLitersSumsMinuteBucketsForLocalDay() {
        Instant minute =
                Instant.now().atZone(ZoneOffset.UTC).truncatedTo(ChronoUnit.HOURS).toInstant();
        when(telemetryIngestionService.findDailyUsage(eq("k3m9x2a"), any()))
                .thenReturn(Optional.empty());

        MinuteUsageRecord minuteRecord =
                new MinuteUsageRecord(
                        "WM000001",
                        MinuteUsageRecord.minuteKeyFor(minute),
                        "k3m9x2a",
                        326.0,
                        1.2,
                        100,
                        0);

        when(dynamoDbClient.query(any(QueryRequest.class)))
                .thenReturn(QueryResponse.builder().items(minuteRecord.toItem()).build());

        double liters = service.getTodayUsedLiters("WM000001", "k3m9x2a", "UTC");

        assertEquals(326.0, liters, 0.01);
    }
}
