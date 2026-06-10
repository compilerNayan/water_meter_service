package com.vswitch.watermeter;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class V2WaterControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private WaterReadingService waterReadingService;

    @MockBean
    private QuotaService quotaService;

    @MockBean
    private BuildingStatsService buildingStatsService;

    @MockBean
    private UnitService unitService;

    @MockBean
    private UserService userService;

    @Test
    void getValveReturnsState() throws Exception {
        when(unitService.findByTenantAndDeviceId("k3m9x2a", "WM000001"))
                .thenReturn(Optional.of(sampleUnit()));
        when(waterReadingService.getValveState("WM000001", "k3m9x2a"))
                .thenReturn(
                        new ValveStateResponse(
                                "WM000001",
                                "2026-06-09T10:30:00Z",
                                100,
                                98,
                                100,
                                false,
                                "manual",
                                null,
                                98));

        mockMvc.perform(
                        get("/v2/tenants/k3m9x2a/devices/WM000001/water/valve")
                                .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.targetPressurePercent").value(100))
                .andExpect(jsonPath("$.isOff").value(false));

        verify(userService).requireTenantMember("user-123", "k3m9x2a");
    }

    @Test
    void updateValveReturnsUpdatedState() throws Exception {
        when(unitService.findByTenantAndDeviceId("k3m9x2a", "WM000001"))
                .thenReturn(Optional.of(sampleUnit()));
        when(waterReadingService.updateValve(eq("WM000001"), any()))
                .thenReturn(
                        new ValveStateResponse(
                                "WM000001",
                                "2026-06-09T10:31:00Z",
                                0,
                                0,
                                100,
                                true,
                                "manual",
                                null,
                                0));

        mockMvc.perform(
                        put("/v2/tenants/k3m9x2a/devices/WM000001/water/valve")
                                .header("Authorization", "Bearer test-token")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"pressurePercent\": 0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isOff").value(true));

        verify(waterReadingService).updateValve(eq("WM000001"), any());
    }

    private static UnitRecord sampleUnit() {
        return new UnitRecord(
                "wm-WM000001",
                "k3m9x2a",
                "WM000001",
                "D205",
                "D205",
                "2",
                "A",
                "East",
                "Ravi",
                "+919876543210",
                "",
                UnitRecord.STATUS_PENDING,
                "D205-1234",
                "2026-06-09T00:00:00Z",
                "2026-06-09T00:00:00Z");
    }
}
