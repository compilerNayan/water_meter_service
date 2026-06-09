package com.vswitch.watermeter;

import java.util.List;
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
class WaterControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private WaterReadingService waterReadingService;

    @MockBean
    private QuotaService quotaService;

    @MockBean
    private UnitService unitService;

    @MockBean
    private UserService userService;

    @Test
    void getCurrentReturnsReading() throws Exception {
        when(unitService.findByTenantAndDeviceId("k3m9x2a", "WM000001"))
                .thenReturn(Optional.of(sampleUnit()));
        when(waterReadingService.getCurrentReading("WM000001"))
                .thenReturn(
                        new CurrentReadingResponse(
                                "WM000001",
                                "2026-06-09T10:30:00Z",
                                2.3,
                                15420.5,
                                "flowing"));

        mockMvc.perform(
                        get("/tenants/k3m9x2a/devices/WM000001/water/current")
                                .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deviceId").value("WM000001"))
                .andExpect(jsonPath("$.flowRateLpm").value(2.3))
                .andExpect(jsonPath("$.status").value("flowing"));

        verify(userService).requireTenantMember("user-123", "k3m9x2a");
    }

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
                        get("/tenants/k3m9x2a/devices/WM000001/water/valve")
                                .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.targetPressurePercent").value(100))
                .andExpect(jsonPath("$.isOff").value(false));
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
                                50,
                                49,
                                50,
                                false,
                                "manual",
                                null,
                                49));

        mockMvc.perform(
                        put("/tenants/k3m9x2a/devices/WM000001/water/valve")
                                .header("Authorization", "Bearer test-token")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"pressurePercent\": 50}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.targetPressurePercent").value(50));
    }

    @Test
    void getUsageReturnsDataPoints() throws Exception {
        when(unitService.findByTenantAndDeviceId("k3m9x2a", "WM000001"))
                .thenReturn(Optional.of(sampleUnit()));
        when(waterReadingService.getUsage(
                        eq("WM000001"), any(), any(), eq("1h"), eq("UTC")))
                .thenReturn(
                        new WaterUsageResponse(
                                "WM000001",
                                "2026-06-09T00:00:00Z",
                                "2026-06-09T23:59:59Z",
                                "1h",
                                "liters",
                                List.of(
                                        new UsageDataPointResponse(
                                                "2026-06-09T10:00:00Z", 12.5, 0.2)),
                                new UsageSummaryResponse(
                                        12.5,
                                        12.5,
                                        new PeakBucketResponse("2026-06-09T10:00:00Z", 12.5),
                                        10.0,
                                        25.0)));

        mockMvc.perform(
                        get("/tenants/k3m9x2a/devices/WM000001/water/usage")
                                .header("Authorization", "Bearer test-token")
                                .param("from", "2026-06-09T00:00:00Z")
                                .param("to", "2026-06-09T23:59:59Z")
                                .param("granularity", "1h"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dataPoints[0].volumeLiters").value(12.5));
    }

    @Test
    void getQuotaReturnsConfigAndStatus() throws Exception {
        when(unitService.findByTenantAndDeviceId("k3m9x2a", "WM000001"))
                .thenReturn(Optional.of(sampleUnit()));
        when(quotaService.getQuota("WM000001", "k3m9x2a"))
                .thenReturn(
                        new QuotaResponse(
                                "WM000001",
                                true,
                                500,
                                "UTC",
                                List.of(new QuotaStepDto(300, "reduce_pressure", 20.0)),
                                new QuotaStatusResponse(
                                        "2026-06-09", 120, -1, null, 380, 300)));

        mockMvc.perform(
                        get("/tenants/k3m9x2a/devices/WM000001/water/quota")
                                .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deviceId").value("WM000001"))
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.dailyLimitLiters").value(500))
                .andExpect(jsonPath("$.status.usedLiters").value(120))
                .andExpect(jsonPath("$.status.remainingLiters").value(380));
    }

    @Test
    void updateQuotaReturnsUpdatedConfig() throws Exception {
        when(unitService.findByTenantAndDeviceId("k3m9x2a", "WM000001"))
                .thenReturn(Optional.of(sampleUnit()));
        when(quotaService.updateQuota(eq("WM000001"), eq("k3m9x2a"), any()))
                .thenReturn(
                        new QuotaResponse(
                                "WM000001",
                                true,
                                600,
                                "UTC",
                                List.of(new QuotaStepDto(400, "turn_off", null)),
                                new QuotaStatusResponse(
                                        "2026-06-09", 50, -1, null, 550, 400)));

        mockMvc.perform(
                        put("/tenants/k3m9x2a/devices/WM000001/water/quota")
                                .header("Authorization", "Bearer test-token")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "enabled": true,
                                          "dailyLimitLiters": 600,
                                          "steps": [
                                            { "atLitersUsed": 400, "action": "turn_off" }
                                          ]
                                        }
                                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dailyLimitLiters").value(600))
                .andExpect(jsonPath("$.steps[0].action").value("turn_off"));
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
