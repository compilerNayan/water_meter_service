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
class UnitControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UnitService unitService;

    @MockBean
    private UserService userService;

    @Test
    void createUnitReturnsCreated() throws Exception {
        when(unitService.createUnit(eq("k3m9x2a"), any()))
                .thenReturn(
                        new UnitResponse(
                                "wm-WM000001",
                                "D205",
                                "WM000001",
                                "D205",
                                "2",
                                "A",
                                "East",
                                "Ravi Kumar",
                                "+919876543210",
                                "",
                                UnitRecord.STATUS_PENDING,
                                "D205-1234"));

        mockMvc.perform(
                        post("/tenants/k3m9x2a/units")
                                .header("Authorization", "Bearer test-token")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "deviceId": "WM000001",
                                          "name": "D205",
                                          "flatNumber": "D205",
                                          "floor": "2",
                                          "block": "A",
                                          "wing": "East",
                                          "residentName": "Ravi Kumar",
                                          "phoneNumber": "+919876543210"
                                        }
                                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("wm-WM000001"))
                .andExpect(jsonPath("$.enrollmentStatus").value("pending"));

        verify(userService).requireTenantMember("user-123", "k3m9x2a");
    }

    @Test
    void listUnitsReturnsUnits() throws Exception {
        when(unitService.listUnits("k3m9x2a"))
                .thenReturn(
                        new UnitListResponse(
                                List.of(
                                        new UnitResponse(
                                                "wm-WM000001",
                                                "D205",
                                                "WM000001",
                                                "D205",
                                                "2",
                                                "A",
                                                "East",
                                                "Ravi Kumar",
                                                "+919876543210",
                                                "",
                                                UnitRecord.STATUS_PENDING,
                                                "D205-1234"))));

        mockMvc.perform(
                        get("/tenants/k3m9x2a/units")
                                .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.units[0].deviceId").value("WM000001"));
    }

    @Test
    void enrollmentStatusPlaceholderReturnsEnrolled() throws Exception {
        when(unitService.getEnrollmentStatus("k3m9x2a", "WM000001"))
                .thenReturn(new EnrollmentStatusResponse(true, UnitRecord.STATUS_ENROLLED));

        mockMvc.perform(
                        get("/tenants/k3m9x2a/devices/WM000001/enrollment-status")
                                .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enrolled").value(true))
                .andExpect(jsonPath("$.status").value("enrolled"));
    }
}
