package com.vswitch.watermeter.device;

import java.time.Instant;
import java.time.LocalDate;

import com.vswitch.watermeter.CurrentReadingResponse;
import com.vswitch.watermeter.DayHistoryRecord;
import com.vswitch.watermeter.QuotaUpdateRequest;
import com.vswitch.watermeter.UnitRecord;
import com.vswitch.watermeter.ValveStateResponse;
import com.vswitch.watermeter.ValveUpdateRequest;

public interface DeviceFacade {

    DeviceQuotaConfig getQuotaConfig(String deviceId);

    DeviceQuotaConfig setQuota(String deviceId, String tenantId, QuotaUpdateRequest request);

    void initializeDeviceConfig(String deviceId, String tenantId);

    void initializeDeviceState(String deviceId, String tenantId);

    void ingestSecondPulse(String tenantId, String deviceId, Instant ts, double ml);

    void ingestLiveTick(
            UnitRecord unit,
            Instant minute,
            double volumeLiters,
            double avgFlowRateLpm,
            double valveTargetPercent,
            String status);

    void ingest30MinuteBucket(ThirtyMinuteBucketPayload payload);

    void ingestValveStateReport(String tenantId, String deviceId, double target, double actual);

    void writeDayHistory(DayHistoryRecord record);

    boolean hasDayHistory(String deviceId, LocalDate date);

    void applyHistoricalCumulative(String deviceId, double additionalLiters, Instant lastHour);

    CurrentReadingResponse getCurrentReading(String deviceId);

    ValveStateResponse getValveState(String deviceId, String tenantId);

    ValveStateResponse setValveTarget(String deviceId, String tenantId, ValveUpdateRequest request);
}
