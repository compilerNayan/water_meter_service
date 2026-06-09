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
class BuildingStatsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private BuildingStatsService buildingStatsService;

    @MockBean
    private UserService userService;

    @Test
    void getSummaryReturnsAggregates() throws Exception {
        when(buildingStatsService.getSummary("k3m9x2a"))
                .thenReturn(new BuildingSummaryResponse(12450.5, 312000, 42, 3, 45, 7));

        mockMvc.perform(
                        get("/tenants/k3m9x2a/building/summary")
                                .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalTodayLiters").value(12450.5))
                .andExpect(jsonPath("$.unitsTotal").value(45))
                .andExpect(jsonPath("$.activeAlerts").value(7));

        verify(userService).requireTenantMember("user-123", "k3m9x2a");
    }

    @Test
    void getRankingsReturnsTopConsumers() throws Exception {
        when(buildingStatsService.getRankings("k3m9x2a", "today", "overall", null, 5))
                .thenReturn(
                        new BuildingRankingsResponse(
                                List.of(
                                        new BuildingRankingEntry(
                                                "wm-WM000001",
                                                "D205",
                                                180.5,
                                                null,
                                                "A",
                                                "East"))));

        mockMvc.perform(
                        get("/tenants/k3m9x2a/building/rankings")
                                .header("Authorization", "Bearer test-token")
                                .param("period", "today")
                                .param("groupBy", "overall")
                                .param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rankings[0].name").value("D205"))
                .andExpect(jsonPath("$.rankings[0].liters").value(180.5));
    }
}
