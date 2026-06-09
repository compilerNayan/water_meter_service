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
class TenantMetadataControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TenantMetadataService tenantMetadataService;

    @MockBean
    private UserService userService;

    @Test
    void getMetadataHashReturnsHash() throws Exception {
        when(tenantMetadataService.getHash("k3m9x2a"))
                .thenReturn(new TenantMetadataHashResponse("abc123hash"));

        mockMvc.perform(
                        get("/v2/tenants/k3m9x2a/metadata/hash")
                                .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.metadataHash").value("abc123hash"));

        verify(userService).requireTenantMember("user-123", "k3m9x2a");
    }

    @Test
    void getMetadataReturnsBuildingAndDevices() throws Exception {
        when(tenantMetadataService.getMetadata("k3m9x2a"))
                .thenReturn(
                        new TenantMetadataResponse(
                                "abc123hash",
                                "k3m9x2a",
                                "Sunrise Apartments",
                                new StructureDto(
                                        List.of(
                                                new BlockDto(
                                                        "A",
                                                        "Tower A",
                                                        List.of(new WingDto("East", 10))))),
                                new TenantMetadataOwnerEntry(
                                        "owner-1",
                                        "Raj Sharma",
                                        "admin@test.com",
                                        "+919876543210",
                                        "Raj",
                                        "Sharma"),
                                List.of(
                                        new TenantMetadataDeviceEntry(
                                                "wm-WM000001",
                                                "D205",
                                                "WM000001",
                                                "D205",
                                                "2",
                                                "A",
                                                "East",
                                                "Ravi Kumar",
                                                "+919876543211",
                                                null,
                                                UnitRecord.STATUS_ENROLLED,
                                                false,
                                                null,
                                                "D205-AB12"))));

        mockMvc.perform(
                        get("/v2/tenants/k3m9x2a/metadata")
                                .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.metadataHash").value("abc123hash"))
                .andExpect(jsonPath("$.buildingName").value("Sunrise Apartments"))
                .andExpect(jsonPath("$.devices[0].name").value("D205"));

        verify(userService).requireTenantMember("user-123", "k3m9x2a");
    }
}
