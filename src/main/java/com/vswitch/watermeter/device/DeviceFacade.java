package com.vswitch.watermeter.device;

import com.vswitch.watermeter.QuotaUpdateRequest;

public interface DeviceFacade {

    DeviceQuotaConfig getQuotaConfig(String deviceId);

    DeviceQuotaConfig setQuota(String deviceId, String tenantId, QuotaUpdateRequest request);

    void initializeDeviceConfig(String deviceId, String tenantId);
}
