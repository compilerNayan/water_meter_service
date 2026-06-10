package com.vswitch.watermeter;

import java.util.List;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class V2TenantOnboardingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TenantService tenantService;

    @MockBean
    private UserService userService;

    @Test
    void getTenantReturnsConfig() throws Exception {
        when(tenantService.getTenant("k3m9x2a"))
                .thenReturn(
                        new TenantResponse(
                                "k3m9x2a",
                                "Sunrise Apartments",
                                new StructureDto(List.of())));

        mockMvc.perform(
                        get("/v2/tenants/k3m9x2a")
                                .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value("k3m9x2a"))
                .andExpect(jsonPath("$.name").value("Sunrise Apartments"));

        verify(userService).requireTenantMember("user-123", "k3m9x2a");
    }

    @Test
    void createBuildingAllowsEmptyBlocks() throws Exception {
        when(tenantService.updateBuilding(eq("k3m9x2a"), eq("Sunrise Apartments"), any()))
                .thenReturn(
                        new TenantResponse(
                                "k3m9x2a",
                                "Sunrise Apartments",
                                new StructureDto(List.of())));

        mockMvc.perform(
                        post("/v2/tenants/k3m9x2a/building")
                                .header("Authorization", "Bearer test-token")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "name": "Sunrise Apartments",
                                          "structure": { "blocks": [] }
                                        }
                                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Sunrise Apartments"));

        verify(userService).requireTenantOwner("user-123", "k3m9x2a");
        verify(userService).completeOnboarding("user-123");
    }
}
