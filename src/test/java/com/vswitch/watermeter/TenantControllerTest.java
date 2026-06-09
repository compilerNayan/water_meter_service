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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class TenantControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TenantService tenantService;

    @MockBean
    private UserService userService;

    @Test
    void getTenantReturnsConfig() throws Exception {
        when(tenantService.getTenant("tenant_abc"))
                .thenReturn(
                        new TenantResponse(
                                "tenant_abc",
                                "Sunrise Apartments",
                                new StructureDto(List.of())));

        mockMvc.perform(
                        get("/tenants/tenant_abc")
                                .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value("tenant_abc"))
                .andExpect(jsonPath("$.name").value("Sunrise Apartments"));

        verify(userService).requireTenantMember("user-123", "tenant_abc");
    }

    @Test
    void createBuildingAllowsEmptyBlocks() throws Exception {
        when(tenantService.updateBuilding(eq("tenant_abc"), eq("Sunrise Apartments"), any()))
                .thenReturn(
                        new TenantResponse(
                                "tenant_abc",
                                "Sunrise Apartments",
                                new StructureDto(List.of())));

        mockMvc.perform(
                        post("/tenants/tenant_abc/building")
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

        verify(userService).requireTenantOwner("user-123", "tenant_abc");
        verify(userService).completeOnboarding("user-123");
    }

    @Test
    void updateStructureReturnsUpdatedTenant() throws Exception {
        when(tenantService.updateStructure(eq("tenant_abc"), any()))
                .thenReturn(
                        new TenantResponse(
                                "tenant_abc",
                                "Sunrise Apartments",
                                new StructureDto(
                                        List.of(
                                                new BlockDto(
                                                        "A",
                                                        "Tower A",
                                                        List.of(
                                                                new WingDto("East", 10)))))));

        mockMvc.perform(
                        put("/tenants/tenant_abc/structure")
                                .header("Authorization", "Bearer test-token")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "structure": {
                                            "blocks": [
                                              {
                                                "id": "A",
                                                "label": "Tower A",
                                                "wings": [
                                                  { "name": "East", "floorCount": 10 }
                                                ]
                                              }
                                            ]
                                          }
                                        }
                                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.structure.blocks[0].id").value("A"))
                .andExpect(jsonPath("$.structure.blocks[0].wings[0].name").value("East"));

        verify(userService).requireTenantOwner("user-123", "tenant_abc");
    }
}
