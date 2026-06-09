package com.vswitch.watermeter;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class DashboardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DashboardService dashboardService;

    @MockBean
    private UserService userService;

    @Test
    void getDashboardReturnsTelemetryAndMetadataHash() throws Exception {
        when(dashboardService.getDashboard("k3m9x2a"))
                .thenReturn(
                        new DashboardResponse(
                                "abc123hash",
                                "2026-06-10T12:00:00Z",
                                List.of(
                                        new DashboardTelemetryEntry(
                                                "wm-WM000001",
                                                "WM000001",
                                                45.2,
                                                1200.0,
                                                true,
                                                "2026-06-10T11:58:00Z",
                                                DeviceStateRecord.STATUS_IDLE,
                                                0.0,
                                                true,
                                                500,
                                                45.2,
                                                0.0904,
                                                100,
                                                false,
                                                false))));

        mockMvc.perform(
                        get("/v2/tenants/k3m9x2a/dashboard")
                                .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.metadataHash").value("abc123hash"))
                .andExpect(jsonPath("$.generatedAt").value("2026-06-10T12:00:00Z"))
                .andExpect(jsonPath("$.devices[0].deviceId").value("WM000001"))
                .andExpect(jsonPath("$.devices[0].todayLiters").value(45.2))
                .andExpect(jsonPath("$.devices[0].valveOpenPercent").value(100))
                .andExpect(jsonPath("$.buildingName").doesNotExist());

        verify(userService).requireTenantMember("user-123", "k3m9x2a");
    }
}
