package com.vswitch.watermeter;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.vswitch.watermeter.device.DeviceFacade;
import com.vswitch.watermeter.device.DeviceQuotaConfig;

@Service
public class DashboardService {

    private static final Duration OFFLINE_THRESHOLD = Duration.ofMinutes(15);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final UnitService unitService;
    private final TelemetryIngestionService telemetryIngestionService;
    private final WaterReadingService waterReadingService;
    private final DeviceFacade deviceFacade;
    private final TenantMetadataService tenantMetadataService;

    DashboardService(
            UnitService unitService,
            TelemetryIngestionService telemetryIngestionService,
            WaterReadingService waterReadingService,
            DeviceFacade deviceFacade,
            TenantMetadataService tenantMetadataService) {
        this.unitService = unitService;
        this.telemetryIngestionService = telemetryIngestionService;
        this.waterReadingService = waterReadingService;
        this.deviceFacade = deviceFacade;
        this.tenantMetadataService = tenantMetadataService;
    }

    DashboardResponse getDashboard(String tenantId) {
        List<UnitRecord> units = unitService.listUnitRecords(tenantId);
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        YearMonth month = YearMonth.from(today);
        String generatedAt = Instant.now().toString();
        String metadataHash = tenantMetadataService.getHash(tenantId).metadataHash();

        List<DashboardTelemetryEntry> devices = new ArrayList<>();
        for (UnitRecord unit : units) {
            devices.add(buildTelemetryEntry(unit, tenantId, today, month));
        }

        return new DashboardResponse(metadataHash, generatedAt, devices);
    }

    private DashboardTelemetryEntry buildTelemetryEntry(
            UnitRecord unit, String tenantId, LocalDate today, YearMonth month) {
        String deviceId = unit.deviceId();
        double todayLiters = waterReadingService.getTodayUsedLiters(deviceId, tenantId);
        double monthLiters = sumMonthLiters(tenantId, deviceId, today, month);

        Optional<DeviceStateRecord> stateOpt =
                telemetryIngestionService.findDeviceState(deviceId);
        boolean isOnline = stateOpt.isPresent() && !isOffline(stateOpt.get());
        String lastSeenAt = stateOpt.map(DeviceStateRecord::lastSeenAt).orElse("");
        String status = resolveStatus(stateOpt.orElse(null), isOnline);
        double flowRateLpm = stateOpt.map(DeviceStateRecord::flowRateLpm).orElse(0.0);

        DeviceQuotaConfig quotaConfig = loadQuotaConfig(deviceId, tenantId);
        Double quotaPercent = null;
        if (quotaConfig.enabled() && quotaConfig.dailyLimitLiters() > 0) {
            quotaPercent =
                    Math.min(1.0, todayLiters / quotaConfig.dailyLimitLiters());
        }

        ValveStateResponse valve = waterReadingService.getValveState(deviceId, tenantId);
        boolean hasAlert =
                DeviceStateRecord.STATUS_LEAK_SUSPECTED.equals(status) || !isOnline;

        return new DashboardTelemetryEntry(
                unit.unitId(),
                deviceId,
                todayLiters,
                monthLiters,
                isOnline,
                emptyToNull(lastSeenAt),
                status,
                flowRateLpm,
                quotaConfig.enabled(),
                quotaConfig.dailyLimitLiters(),
                todayLiters,
                quotaPercent,
                valve.effectivePressurePercent(),
                valve.isOff(),
                hasAlert);
    }

    private double sumMonthLiters(
            String tenantId, String deviceId, LocalDate today, YearMonth month) {
        double total = 0;
        for (LocalDate date = month.atDay(1); !date.isAfter(today); date = date.plusDays(1)) {
            String key = DailyUsageRecord.usageKeyFor(date.format(DATE_FORMAT), deviceId);
            total +=
                    telemetryIngestionService
                            .findDailyUsage(tenantId, key)
                            .map(DailyUsageRecord::totalLiters)
                            .orElse(0.0);
        }
        return total;
    }

    private DeviceQuotaConfig loadQuotaConfig(String deviceId, String tenantId) {
        try {
            return deviceFacade.getQuotaConfig(deviceId);
        } catch (ResponseStatusException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                deviceFacade.initializeDeviceConfig(deviceId, tenantId);
                return deviceFacade.getQuotaConfig(deviceId);
            }
            throw e;
        }
    }

    private static String resolveStatus(DeviceStateRecord state, boolean isOnline) {
        if (state == null || !isOnline) {
            return DeviceStateRecord.STATUS_OFFLINE;
        }
        String status = state.status();
        if (status == null || status.isBlank()) {
            return DeviceStateRecord.STATUS_IDLE;
        }
        return status;
    }

    private static boolean isOffline(DeviceStateRecord state) {
        if (state.lastSeenAt() == null || state.lastSeenAt().isBlank()) {
            return true;
        }
        return Duration.between(Instant.parse(state.lastSeenAt()), Instant.now())
                        .compareTo(OFFLINE_THRESHOLD)
                > 0;
    }

    private static String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
