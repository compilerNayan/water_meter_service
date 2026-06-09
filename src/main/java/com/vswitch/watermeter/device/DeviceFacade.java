package com.vswitch.watermeter.device;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import com.vswitch.watermeter.CurrentReadingResponse;
import com.vswitch.watermeter.DailyUsageRecord;
import com.vswitch.watermeter.QuotaUpdateRequest;
import com.vswitch.watermeter.UnitRecord;
import com.vswitch.watermeter.ValveStateResponse;
import com.vswitch.watermeter.ValveUpdateRequest;

public interface DeviceFacade {

    // --- Quota config (Phase 1) ---

    DeviceQuotaConfig getQuotaConfig(String deviceId);

    DeviceQuotaConfig setQuota(String deviceId, String tenantId, QuotaUpdateRequest request);

    void initializeDeviceConfig(String deviceId, String tenantId);

    // --- Device init ---

    void initializeDeviceState(String deviceId, String tenantId);

    // --- Ingest (MQTT or mock scheduler) ---

    void ingestSecondPulse(String tenantId, String deviceId, Instant ts, double ml);

    void ingestMinuteBucket(
            UnitRecord unit,
            Instant minute,
            double volumeLiters,
            double avgFlowRateLpm,
            double valveTargetPercent,
            String status);

    void ingest30MinuteBucket(ThirtyMinuteBucketPayload payload);

    void ingestValveStateReport(String tenantId, String deviceId, double target, double actual);

    // --- Historical seed (mock backfill) ---

    void writeHistoricalHour(
            UnitRecord unit,
            Instant hourStart,
            double volumeLiters,
            double avgFlowRateLpm,
            double valveTargetPercent,
            String status,
            long expiresAtEpochSeconds);

    void writeHistoricalDaily(
            UnitRecord unit,
            LocalDate date,
            double totalLiters,
            int peakHour,
            double peakHourLiters);

    void applyHistoricalCumulative(String deviceId, double additionalLiters, Instant lastHour);

    Optional<DailyUsageRecord> findDailyUsage(String tenantId, String usageKey);

    // --- Live reads (REST delegates here) ---

    CurrentReadingResponse getCurrentReading(String deviceId);

    ValveStateResponse getValveState(String deviceId, String tenantId);

    // --- Writes (REST delegates here) ---

    ValveStateResponse setValveTarget(
            String deviceId, String tenantId, ValveUpdateRequest request);
}
