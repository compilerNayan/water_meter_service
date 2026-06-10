package com.vswitch.watermeter;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.vswitch.watermeter.device.DeviceFacade;
import com.vswitch.watermeter.device.MinuteBucketEntry;
import com.vswitch.watermeter.device.ThirtyMinuteBucketPayload;

@Service
public class MockTelemetrySchedulerService {

    private static final Logger log = LoggerFactory.getLogger(MockTelemetrySchedulerService.class);

    private final UnitService unitService;
    private final DeviceFacade deviceFacade;
    private final MockHistoricalBackfillService historicalBackfillService;
    private final MockDeviceProfileFactory profileFactory;
    private final boolean enabled;

    MockTelemetrySchedulerService(
            UnitService unitService,
            DeviceFacade deviceFacade,
            MockHistoricalBackfillService historicalBackfillService,
            MockDeviceProfileFactory profileFactory,
            @Value("${mock.telemetry.enabled:true}") boolean enabled) {
        this.unitService = unitService;
        this.deviceFacade = deviceFacade;
        this.historicalBackfillService = historicalBackfillService;
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

        units.parallelStream()
                .filter(unit -> UnitRecord.STATUS_ENROLLED.equals(unit.enrollmentStatus()))
                .forEach(
                        unit -> {
                            try {
                                historicalBackfillService.backfillIfNeeded(unit);
                                ingestForUnit(unit, now, zoned);
                            } catch (Exception e) {
                                log.warn(
                                        "Failed mock ingestion for device {}",
                                        unit.deviceId(),
                                        e);
                            }
                        });
    }

    private void ingestForUnit(UnitRecord unit, Instant minute, ZonedDateTime zoned) {
        MockDeviceProfile profile = profileFactory.forDevice(unit.deviceId());

        if (profileFactory.isOfflineWindow(profile, minute)) {
            return;
        }

        deviceFacade.initializeDeviceState(unit.deviceId(), unit.tenantId());

        double valveTarget =
                deviceFacade.getValveState(unit.deviceId(), unit.tenantId()).targetPressurePercent();
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

        if (avgFlow > 0.2) {
            deviceFacade.ingestSecondPulse(
                    unit.tenantId(), unit.deviceId(), minute, volumeLiters * 1000 / 60);
        }

        deviceFacade.ingestLiveTick(
                unit, minute, volumeLiters, avgFlow, valveTarget, status);

        if (profileFactory.isValveMismatchMinute(profile, zoned)) {
            deviceFacade.ingestValveStateReport(unit.tenantId(), unit.deviceId(), 0, avgFlow > 0 ? 2 : 0);
        }

        if (minute.atZone(ZoneOffset.UTC).getMinute() % 30 == 0) {
            ingest30MinuteBoundary(unit, minute, valveTarget, profile);
        }
    }

    private void ingest30MinuteBoundary(
            UnitRecord unit, Instant minute, double valveTarget, MockDeviceProfile profile) {
        Instant periodStart = minute.minus(29, ChronoUnit.MINUTES);
        List<MinuteBucketEntry> minutes = new ArrayList<>();
        double slotLiters = 0;

        for (int i = 0; i < 30; i++) {
            Instant t = periodStart.plus(i, ChronoUnit.MINUTES);
            ZonedDateTime zoned = t.atZone(ZoneOffset.UTC);
            double liters = profileFactory.minuteVolumeLiters(profile, zoned);
            if (profileFactory.isLeakBurstMinute(profile, zoned)) {
                liters = 15 + (profile.seed() % 10);
            }
            slotLiters += liters;
            minutes.add(new MinuteBucketEntry(t, liters * 1000));
        }

        double cumulative =
                deviceFacade.getCurrentReading(unit.deviceId()).cumulativeLiters() + slotLiters;

        deviceFacade.ingest30MinuteBucket(
                new ThirtyMinuteBucketPayload(
                        unit.tenantId(),
                        unit.deviceId(),
                        periodStart,
                        minutes,
                        cumulative,
                        valveTarget));
    }
}
