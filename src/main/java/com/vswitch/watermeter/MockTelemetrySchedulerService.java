package com.vswitch.watermeter;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class MockTelemetrySchedulerService {

    private static final Logger log = LoggerFactory.getLogger(MockTelemetrySchedulerService.class);

    private final UnitService unitService;
    private final TelemetryIngestionService ingestionService;
    private final MockDeviceProfileFactory profileFactory;
    private final boolean enabled;

    MockTelemetrySchedulerService(
            UnitService unitService,
            TelemetryIngestionService ingestionService,
            MockDeviceProfileFactory profileFactory,
            @Value("${mock.telemetry.enabled:true}") boolean enabled) {
        this.unitService = unitService;
        this.ingestionService = ingestionService;
        this.profileFactory = profileFactory;
        this.enabled = enabled;
    }

    void runScheduledIngestion() {
        if (!enabled) {
            log.info("Mock telemetry ingestion disabled");
            return;
        }

        List<UnitRecord> units = unitService.listAllUnits();
        Instant now = Instant.now().truncatedTo(ChronoUnit.MINUTES);
        ZonedDateTime zoned = now.atZone(ZoneOffset.UTC);

        log.info("Mock telemetry ingestion for {} units at {}", units.size(), now);

        for (UnitRecord unit : units) {
            try {
                if (!UnitRecord.STATUS_ENROLLED.equals(unit.enrollmentStatus())) {
                    continue;
                }
                ingestForUnit(unit, now, zoned);
            } catch (Exception e) {
                log.warn("Failed mock ingestion for device {}", unit.deviceId(), e);
            }
        }
    }

    private void ingestForUnit(UnitRecord unit, Instant minute, ZonedDateTime zoned) {
        MockDeviceProfile profile = profileFactory.forDevice(unit.deviceId());

        if (profileFactory.isOfflineWindow(profile, minute)) {
            return;
        }

        DeviceStateRecord state =
                ingestionService
                        .findDeviceState(unit.deviceId())
                        .orElseGet(
                                () -> {
                                    ingestionService.initializeDeviceState(
                                            unit.deviceId(), unit.tenantId());
                                    return ingestionService
                                            .findDeviceState(unit.deviceId())
                                            .orElseThrow();
                                });

        double valveTarget = state.valveTargetPercent();
        double volumeLiters = profileFactory.minuteVolumeLiters(profile, zoned);
        double avgFlow = volumeLiters;

        if (profileFactory.isLeakBurstMinute(profile, zoned)) {
            avgFlow = 15 + (profile.seed() % 10);
            volumeLiters = avgFlow;
        }

        if (profileFactory.isValveMismatchMinute(profile, zoned)) {
            valveTarget = 0;
            avgFlow = 2.5 + (profile.seed() % 5);
            volumeLiters = avgFlow;
        }

        String status;
        if (profileFactory.isValveMismatchMinute(profile, zoned)
                || (valveTarget <= 0 && avgFlow > 0.2)) {
            status = DeviceStateRecord.STATUS_LEAK_SUSPECTED;
        } else if (avgFlow > 0.2) {
            status = DeviceStateRecord.STATUS_FLOWING;
        } else {
            status = DeviceStateRecord.STATUS_IDLE;
        }

        ingestionService.ingestMinuteBucket(
                unit, minute, volumeLiters, avgFlow, valveTarget, status);
    }
}
