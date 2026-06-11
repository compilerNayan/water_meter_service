package com.vswitch.watermeter.device;

import java.time.Instant;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Lazy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.vswitch.watermeter.DeviceStateRecord;
import com.vswitch.watermeter.VolumeReadingService;

@Service
@ConditionalOnProperty(name = "mock.telemetry.enabled", havingValue = "true")
public class MockDeviceFacade extends DynamoDbDeviceFacade implements DeviceFacade {

    public MockDeviceFacade(
            DeviceStore deviceStore,
            @Lazy VolumeReadingService volumeReadingService,
            @Value("${today.slots.ttl.hours:72}") int slotTtlHours) {
        super(deviceStore, volumeReadingService, slotTtlHours);
    }

    @Override
    public void initializeDeviceState(String deviceId, String tenantId) {
        if (deviceStore.findDeviceState(deviceId).isPresent()) {
            return;
        }
        String now = Instant.now().toString();
        double cumulative = 10000 + Math.abs(deviceId.hashCode() % 50000);
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
                        profileTag(deviceId),
                        now);
        deviceStore.putDeviceState(state);
    }

    @Override
    protected String profileTag(String deviceId) {
        return DeviceStore.mockProfileName(deviceId);
    }

    @Override
    protected double computeActualPercent(String deviceId, double target) {
        return target <= 0 ? 0 : Math.max(0, target - 1 + (deviceId.hashCode() % 3));
    }
}
