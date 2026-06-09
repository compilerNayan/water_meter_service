package com.vswitch.watermeter.device;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.vswitch.watermeter.CurrentReadingResponse;
import com.vswitch.watermeter.DailyUsageRecord;
import com.vswitch.watermeter.DeviceStateRecord;
import com.vswitch.watermeter.MinuteUsageRecord;
import com.vswitch.watermeter.QuotaCalculator;
import com.vswitch.watermeter.QuotaStepDto;
import com.vswitch.watermeter.QuotaStepsJson;
import com.vswitch.watermeter.QuotaUpdateRequest;
import com.vswitch.watermeter.UnitRecord;
import com.vswitch.watermeter.ValveStateResponse;
import com.vswitch.watermeter.ValveUpdateRequest;

@Service
public class MockDeviceFacade implements DeviceFacade {

    private static final Duration OFFLINE_THRESHOLD = Duration.ofMinutes(15);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final DeviceStore deviceStore;

    MockDeviceFacade(DeviceStore deviceStore) {
        this.deviceStore = deviceStore;
    }

    // --- Quota ---

    @Override
    public DeviceQuotaConfig getQuotaConfig(String deviceId) {
        DeviceConfigRecord record = requireConfig(deviceId);
        return record.toQuotaConfig(QuotaStepsJson.fromJson(record.quotaStepsJson()));
    }

    @Override
    public DeviceQuotaConfig setQuota(
            String deviceId, String tenantId, QuotaUpdateRequest request) {
        validateQuotaRequest(request);
        DeviceConfigRecord existing =
                deviceStore
                        .findDeviceConfig(deviceId)
                        .orElseGet(
                                () ->
                                        DeviceConfigRecord.defaults(
                                                deviceId, tenantId, Instant.now().toString()));

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

        deviceStore.putDeviceConfig(updated);
        return updated.toQuotaConfig(steps);
    }

    @Override
    public void initializeDeviceConfig(String deviceId, String tenantId) {
        if (deviceStore.findDeviceConfig(deviceId).isPresent()) {
            return;
        }
        String now = Instant.now().toString();
        deviceStore.putDeviceConfig(DeviceConfigRecord.defaults(deviceId, tenantId, now));
    }

    // --- Init ---

    @Override
    public void initializeDeviceState(String deviceId, String tenantId) {
        if (deviceStore.findDeviceState(deviceId).isPresent()) {
            return;
        }
        String now = Instant.now().toString();
        double cumulative = 10000 + Math.abs(deviceId.hashCode() % 50000);
        String mockProfile = DeviceStore.mockProfileName(deviceId);
        DeviceStateRecord state =
                new DeviceStateRecord(
                        deviceId,
                        tenantId,
                        cumulative,
                        0,
                        DeviceStateRecord.STATUS_IDLE,
                        100,
                        100,
                        100,
                        now,
                        mockProfile,
                        now);
        deviceStore.putDeviceState(state);
    }

    // --- Ingest ---

    @Override
    public void ingestSecondPulse(String tenantId, String deviceId, Instant ts, double ml) {
        DeviceStateRecord current = requireDeviceState(deviceId, tenantId);
        double flowLpm = ml / 1000.0 * 60;
        String now = Instant.now().toString();
        DeviceStateRecord updated =
                new DeviceStateRecord(
                        deviceId,
                        tenantId,
                        current.cumulativeLiters(),
                        flowLpm,
                        flowLpm > 0.2
                                ? DeviceStateRecord.STATUS_FLOWING
                                : DeviceStateRecord.STATUS_IDLE,
                        current.valveTargetPercent(),
                        current.valveActualPercent(),
                        current.lastUserPressurePercent(),
                        ts.toString(),
                        current.mockProfile(),
                        now);
        deviceStore.putDeviceState(updated);
    }

    @Override
    public void ingestMinuteBucket(
            UnitRecord unit,
            Instant minute,
            double volumeLiters,
            double avgFlowRateLpm,
            double valveTargetPercent,
            String status) {
        String minuteKey = MinuteUsageRecord.minuteKeyFor(minute);
        long expiresAt = minute.getEpochSecond() + 48 * 3600;

        deviceStore.putMinuteUsage(
                new MinuteUsageRecord(
                        unit.deviceId(),
                        minuteKey,
                        unit.tenantId(),
                        volumeLiters,
                        avgFlowRateLpm,
                        valveTargetPercent,
                        expiresAt));

        DeviceStateRecord current = requireDeviceState(unit.deviceId(), unit.tenantId());
        double cumulative = current.cumulativeLiters() + volumeLiters;
        double actualPercent = computeActualPercent(unit.deviceId(), valveTargetPercent);
        String now = Instant.now().toString();

        DeviceStateRecord updated =
                new DeviceStateRecord(
                        unit.deviceId(),
                        unit.tenantId(),
                        cumulative,
                        avgFlowRateLpm,
                        status,
                        valveTargetPercent,
                        actualPercent,
                        resolveLastUserPressure(unit.deviceId(), current),
                        now,
                        current.mockProfile(),
                        now);

        deviceStore.putDeviceState(updated);
        deviceStore.updateDailyRollup(unit, minute, volumeLiters);
    }

    @Override
    public void ingest30MinuteBucket(ThirtyMinuteBucketPayload payload) {
        DeviceStateRecord current =
                requireDeviceState(payload.deviceId(), payload.tenantId());
        double actualPercent = computeActualPercent(payload.deviceId(), payload.valveTargetPercent());
        String now = Instant.now().toString();

        DeviceStateRecord updated =
                new DeviceStateRecord(
                        payload.deviceId(),
                        payload.tenantId(),
                        payload.cumulativeLiters(),
                        current.flowRateLpm(),
                        current.status(),
                        payload.valveTargetPercent(),
                        actualPercent,
                        resolveLastUserPressure(payload.deviceId(), current),
                        now,
                        current.mockProfile(),
                        now);

        deviceStore.putDeviceState(updated);
    }

    @Override
    public void ingestValveStateReport(String tenantId, String deviceId, double target, double actual) {
        DeviceStateRecord current = requireDeviceState(deviceId, tenantId);
        String now = Instant.now().toString();
        DeviceStateRecord updated =
                new DeviceStateRecord(
                        deviceId,
                        tenantId,
                        current.cumulativeLiters(),
                        current.flowRateLpm(),
                        current.status(),
                        target,
                        actual,
                        resolveLastUserPressure(deviceId, current),
                        current.lastSeenAt(),
                        current.mockProfile(),
                        now);
        deviceStore.putDeviceState(updated);
    }

    // --- Historical seed ---

    @Override
    public void writeHistoricalHour(
            UnitRecord unit,
            Instant hourStart,
            double volumeLiters,
            double avgFlowRateLpm,
            double valveTargetPercent,
            String status,
            long expiresAtEpochSeconds) {
        deviceStore.writeHistoricalHour(
                unit, hourStart, volumeLiters, avgFlowRateLpm, valveTargetPercent, expiresAtEpochSeconds);
    }

    @Override
    public void writeHistoricalDaily(
            UnitRecord unit,
            LocalDate date,
            double totalLiters,
            int peakHour,
            double peakHourLiters) {
        deviceStore.writeHistoricalDaily(unit, date, totalLiters, peakHour, peakHourLiters);
    }

    @Override
    public void applyHistoricalCumulative(
            String deviceId, double additionalLiters, Instant lastHour) {
        deviceStore.applyHistoricalCumulative(deviceId, additionalLiters, lastHour);
    }

    @Override
    public Optional<DailyUsageRecord> findDailyUsage(String tenantId, String usageKey) {
        return deviceStore.findDailyUsage(tenantId, usageKey);
    }

    // --- Live reads ---

    @Override
    public CurrentReadingResponse getCurrentReading(String deviceId) {
        DeviceStateRecord state = requireDeviceState(deviceId);
        return new CurrentReadingResponse(
                deviceId,
                state.lastSeenAt(),
                state.flowRateLpm(),
                state.cumulativeLiters(),
                resolveStatus(state));
    }

    @Override
    public ValveStateResponse getValveState(String deviceId, String tenantId) {
        DeviceStateRecord state = requireDeviceState(deviceId);
        DeviceConfigRecord config = configOrDefaults(deviceId, tenantId, state);

        double target = config.valveTargetPercent();
        double actual = state.valveActualPercent();
        double lastUser = config.lastUserPressurePercent();
        boolean isOff = target <= 0;
        double effective = isOff ? 0 : actual;
        String controlMode = "manual";
        Double quotaCapPercent = null;

        try {
            DeviceQuotaConfig quota = getQuotaConfig(deviceId);
            if (quota.enabled()) {
                double used = getTodayUsedLiters(deviceId, tenantId);
                QuotaCalculator.QuotaCapResult cap =
                        QuotaCalculator.computeCap(
                                quota.steps(), used, quota.dailyLimitLiters());
                quotaCapPercent = cap.capPercent();
                if (quotaCapPercent != null) {
                    controlMode = "quota";
                    if (quotaCapPercent == 0) {
                        effective = 0;
                    } else if (!isOff) {
                        effective = Math.min(effective, quotaCapPercent);
                    }
                }
            }
        } catch (ResponseStatusException ignored) {
            // Quota config missing — valve without quota cap.
        }

        return new ValveStateResponse(
                deviceId,
                state.updatedAt(),
                target,
                actual,
                lastUser,
                isOff,
                controlMode,
                quotaCapPercent,
                effective);
    }

    // --- Writes ---

    @Override
    public ValveStateResponse setValveTarget(
            String deviceId, String tenantId, ValveUpdateRequest request) {
        DeviceConfigRecord config = configOrDefaults(deviceId, tenantId, null);
        double target;

        if (request.action() != null && "restore".equalsIgnoreCase(request.action())) {
            target = config.lastUserPressurePercent();
        } else if (request.pressurePercent() != null) {
            target = request.pressurePercent();
        } else {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "pressurePercent or restore action required");
        }

        double clamped = Math.max(0, Math.min(100, target));
        double lastUser =
                clamped <= 0
                        ? (config.valveTargetPercent() > 0
                                ? config.valveTargetPercent()
                                : config.lastUserPressurePercent())
                        : clamped;

        String now = Instant.now().toString();
        DeviceConfigRecord updated =
                new DeviceConfigRecord(
                        deviceId,
                        tenantId,
                        config.quotaEnabled(),
                        config.dailyLimitLiters(),
                        config.quotaStepsJson(),
                        config.timezone(),
                        clamped,
                        lastUser,
                        now);

        deviceStore.putDeviceConfig(updated);
        return getValveState(deviceId, tenantId);
    }

    // --- Helpers ---

    private double getTodayUsedLiters(String deviceId, String tenantId) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        String usageKey = DailyUsageRecord.usageKeyFor(today.format(DATE_FORMAT), deviceId);
        return deviceStore
                .findDailyUsage(tenantId, usageKey)
                .map(DailyUsageRecord::totalLiters)
                .orElse(0.0);
    }

    private double resolveLastUserPressure(String deviceId, DeviceStateRecord state) {
        return deviceStore
                .findDeviceConfig(deviceId)
                .map(DeviceConfigRecord::lastUserPressurePercent)
                .orElse(state.lastUserPressurePercent());
    }

    private static double computeActualPercent(String deviceId, double target) {
        return target <= 0 ? 0 : Math.max(0, target - 1 + (deviceId.hashCode() % 3));
    }

    private DeviceConfigRecord configOrDefaults(
            String deviceId, String tenantId, DeviceStateRecord state) {
        return deviceStore
                .findDeviceConfig(deviceId)
                .orElseGet(
                        () -> {
                            double target =
                                    state != null ? state.valveTargetPercent() : 100;
                            double lastUser =
                                    state != null ? state.lastUserPressurePercent() : 100;
                            return DeviceConfigRecord.defaults(
                                    deviceId,
                                    tenantId,
                                    Instant.now().toString(),
                                    target,
                                    lastUser);
                        });
    }

    private DeviceStateRecord requireDeviceState(String deviceId) {
        return deviceStore
                .findDeviceState(deviceId)
                .orElseThrow(
                        () ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND, "Device state not found"));
    }

    private DeviceStateRecord requireDeviceState(String deviceId, String tenantId) {
        return deviceStore
                .findDeviceState(deviceId)
                .orElseGet(
                        () -> {
                            initializeDeviceState(deviceId, tenantId);
                            return deviceStore.findDeviceState(deviceId).orElseThrow();
                        });
    }

    private DeviceConfigRecord requireConfig(String deviceId) {
        return deviceStore
                .findDeviceConfig(deviceId)
                .orElseThrow(
                        () ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND, "Device config not found"));
    }

    private static String resolveStatus(DeviceStateRecord state) {
        if (state.lastSeenAt() == null || state.lastSeenAt().isBlank()) {
            return DeviceStateRecord.STATUS_OFFLINE;
        }
        Instant lastSeen = Instant.parse(state.lastSeenAt());
        if (Duration.between(lastSeen, Instant.now()).compareTo(OFFLINE_THRESHOLD) > 0) {
            return DeviceStateRecord.STATUS_OFFLINE;
        }
        return state.status();
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
