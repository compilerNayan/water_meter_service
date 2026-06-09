package com.vswitch.watermeter;

import java.util.Optional;

import org.springframework.stereotype.Service;

import com.vswitch.watermeter.device.DeviceStore;

/**
 * Read-only access to telemetry tables for historical and tenant-wide queries.
 * All ingest and device writes go through {@link com.vswitch.watermeter.device.DeviceFacade}.
 */
@Service
public class TelemetryIngestionService {

    private final DeviceStore deviceStore;

    TelemetryIngestionService(DeviceStore deviceStore) {
        this.deviceStore = deviceStore;
    }

    Optional<DeviceStateRecord> findDeviceState(String deviceId) {
        return deviceStore.findDeviceState(deviceId);
    }

    Optional<DailyUsageRecord> findDailyUsage(String tenantId, String usageKey) {
        return deviceStore.findDailyUsage(tenantId, usageKey);
    }
}
