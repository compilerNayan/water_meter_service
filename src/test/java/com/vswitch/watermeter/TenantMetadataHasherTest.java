package com.vswitch.watermeter;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class TenantMetadataHasherTest {

    private final TenantMetadataHasher hasher = new TenantMetadataHasher(new ObjectMapper());

    @Test
    void computeHashIsStableForSameInput() {
        TenantRecord tenant = sampleTenant("2026-06-01T00:00:00Z");
        UserRecord owner = sampleOwner("2026-06-01T00:00:00Z");
        List<UnitRecord> units = List.of(sampleUnit("wm-1", "2026-06-01T00:00:00Z"));

        String first = hasher.computeHash(tenant, Optional.of(owner), units);
        String second = hasher.computeHash(tenant, Optional.of(owner), units);

        assertEquals(first, second);
        assertEquals(64, first.length());
    }

    @Test
    void computeHashChangesWhenUnitUpdatedAtChanges() {
        TenantRecord tenant = sampleTenant("2026-06-01T00:00:00Z");
        UserRecord owner = sampleOwner("2026-06-01T00:00:00Z");
        List<UnitRecord> before = List.of(sampleUnit("wm-1", "2026-06-01T00:00:00Z"));
        List<UnitRecord> after = List.of(sampleUnit("wm-1", "2026-06-02T00:00:00Z"));

        String beforeHash = hasher.computeHash(tenant, Optional.of(owner), before);
        String afterHash = hasher.computeHash(tenant, Optional.of(owner), after);

        assertNotEquals(beforeHash, afterHash);
    }

    private static TenantRecord sampleTenant(String updatedAt) {
        return new TenantRecord(
                "k3m9x2a",
                "Sunrise Apartments",
                "owner-1",
                "{\"blocks\":[{\"id\":\"A\",\"label\":\"Tower A\",\"wings\":[{\"name\":\"East\",\"floorCount\":10}]}]}",
                "2026-06-01T00:00:00Z",
                updatedAt,
                null);
    }

    private static UserRecord sampleOwner(String updatedAt) {
        return new UserRecord(
                "owner-1",
                "admin@test.com",
                "+919876543210",
                "Raj",
                "Sharma",
                "Raj Sharma",
                "k3m9x2a",
                true,
                true,
                "2026-06-01T00:00:00Z",
                updatedAt);
    }

    private static UnitRecord sampleUnit(String unitId, String updatedAt) {
        return new UnitRecord(
                unitId,
                "k3m9x2a",
                "WM000001",
                "D205",
                "D205",
                "2",
                "A",
                "East",
                "Ravi Kumar",
                "+919876543211",
                "",
                UnitRecord.STATUS_ENROLLED,
                "D205-AB12",
                "2026-06-01T00:00:00Z",
                updatedAt);
    }
}
