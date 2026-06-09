package com.vswitch.watermeter;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

@Service
public class BuildingStatsService {

    private static final Duration OFFLINE_THRESHOLD = Duration.ofMinutes(15);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final UnitService unitService;
    private final TelemetryIngestionService telemetryIngestionService;

    BuildingStatsService(UnitService unitService, TelemetryIngestionService telemetryIngestionService) {
        this.unitService = unitService;
        this.telemetryIngestionService = telemetryIngestionService;
    }

    BuildingSummaryResponse getSummary(String tenantId) {
        List<UnitRecord> unitRecords = unitService.listUnitRecords(tenantId);
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        YearMonth month = YearMonth.from(today);

        double todayTotal = 0;
        double monthTotal = 0;
        int online = 0;
        int offline = 0;
        int alerts = 0;

        for (UnitRecord unit : unitRecords) {
            String todayKey =
                    DailyUsageRecord.usageKeyFor(today.format(DATE_FORMAT), unit.deviceId());
            todayTotal +=
                    telemetryIngestionService
                            .findDailyUsage(tenantId, todayKey)
                            .map(DailyUsageRecord::totalLiters)
                            .orElse(0.0);

            for (LocalDate date = month.atDay(1); !date.isAfter(today); date = date.plusDays(1)) {
                String key =
                        DailyUsageRecord.usageKeyFor(date.format(DATE_FORMAT), unit.deviceId());
                monthTotal +=
                        telemetryIngestionService
                                .findDailyUsage(tenantId, key)
                                .map(DailyUsageRecord::totalLiters)
                                .orElse(0.0);
            }

            Optional<DeviceStateRecord> state =
                    telemetryIngestionService.findDeviceState(unit.deviceId());
            if (state.isEmpty() || isOffline(state.get())) {
                offline++;
            } else {
                online++;
            }
            if (state.isPresent()
                    && (DeviceStateRecord.STATUS_LEAK_SUSPECTED.equals(state.get().status())
                            || isOffline(state.get()))) {
                alerts++;
            }
        }

        return new BuildingSummaryResponse(
                todayTotal,
                monthTotal,
                online,
                offline,
                unitRecords.size(),
                alerts);
    }

    BuildingRankingsResponse getRankings(
            String tenantId, String period, String groupBy, String blockId, int limit) {
        final int topN = Math.max(1, limit);
        List<UnitRecord> units = unitService.listUnitRecords(tenantId);
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate from =
                switch (period) {
                    case "week" -> today.minusDays(6);
                    case "month" -> today.withDayOfMonth(1);
                    default -> today;
                };

        Map<String, AggregatedUnit> aggregated = new HashMap<>();
        for (UnitRecord unit : units) {
            double liters = 0;
            for (LocalDate date = from; !date.isAfter(today); date = date.plusDays(1)) {
                String key =
                        DailyUsageRecord.usageKeyFor(date.format(DATE_FORMAT), unit.deviceId());
                liters +=
                        telemetryIngestionService
                                .findDailyUsage(tenantId, key)
                                .map(DailyUsageRecord::totalLiters)
                                .orElse(0.0);
            }
            aggregated.put(unit.unitId(), new AggregatedUnit(unit, liters));
        }

        List<BuildingRankingEntry> rankings = new ArrayList<>();

        if ("block".equalsIgnoreCase(groupBy)) {
            Map<String, List<AggregatedUnit>> byBlock =
                    aggregated.values().stream()
                            .collect(Collectors.groupingBy(a -> nullToDefault(a.unit().block(), "—")));
            for (var entry : byBlock.entrySet()) {
                entry.getValue().stream()
                        .sorted(Comparator.comparingDouble(AggregatedUnit::liters).reversed())
                        .limit(topN)
                        .forEach(a -> rankings.add(toRankingEntry(a, entry.getKey(), null)));
            }
        } else if ("wing".equalsIgnoreCase(groupBy)) {
            String block = blockId == null ? "" : blockId;
            aggregated.values().stream()
                    .filter(a -> block.isEmpty() || block.equals(a.unit().block()))
                    .collect(Collectors.groupingBy(a -> nullToDefault(a.unit().wing(), "—")))
                    .forEach(
                            (wing, list) ->
                                    list.stream()
                                            .sorted(
                                                    Comparator.comparingDouble(AggregatedUnit::liters)
                                                            .reversed())
                                            .limit(topN)
                                            .forEach(
                                                    a ->
                                                            rankings.add(
                                                                    toRankingEntry(
                                                                            a,
                                                                            a.unit().block(),
                                                                            wing))));
        } else {
            aggregated.values().stream()
                    .sorted(Comparator.comparingDouble(AggregatedUnit::liters).reversed())
                    .limit(topN)
                    .forEach(a -> rankings.add(toRankingEntry(a, null, null)));
        }

        rankings.sort(Comparator.comparingDouble(BuildingRankingEntry::liters).reversed());
        List<BuildingRankingEntry> result =
                rankings.size() > topN ? rankings.subList(0, topN) : rankings;
        return new BuildingRankingsResponse(result);
    }

    private static BuildingRankingEntry toRankingEntry(
            AggregatedUnit aggregated, String block, String wing) {
        UnitRecord unit = aggregated.unit();
        return new BuildingRankingEntry(
                unit.unitId(),
                unit.name(),
                aggregated.liters(),
                null,
                block != null && !block.isBlank() ? block : emptyToNull(unit.block()),
                wing != null && !wing.isBlank() ? wing : emptyToNull(unit.wing()));
    }

    private static boolean isOffline(DeviceStateRecord state) {
        if (state.lastSeenAt() == null || state.lastSeenAt().isBlank()) {
            return true;
        }
        return Duration.between(Instant.parse(state.lastSeenAt()), Instant.now())
                        .compareTo(OFFLINE_THRESHOLD)
                > 0;
    }

    private static String nullToDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private static String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private record AggregatedUnit(UnitRecord unit, double liters) {}
}
