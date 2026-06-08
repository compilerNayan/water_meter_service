package com.vswitch.watermeter;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class DevicePreEnrollControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DevicePreEnrollService devicePreEnrollService;

    @Test
    void preEnrollDeviceReturnsCreated() throws Exception {
        when(devicePreEnrollService.preEnroll(eq("user-123"), eq("tenant_abc"), any()))
                .thenReturn(
                        new DevicePreEnrollResponse(
                                "tenant_abc",
                                "WM000123",
                                "pending",
                                "2026-06-08T15:00:00Z"));

        mockMvc.perform(
                        post("/tenants/tenant_abc/devices/pre-enroll")
                                .header("Authorization", "Bearer test-token")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "serialNumber": "WM000123"
                                        }
                                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tenantId").value("tenant_abc"))
                .andExpect(jsonPath("$.serialNumber").value("WM000123"))
                .andExpect(jsonPath("$.status").value("pending"));

        verify(devicePreEnrollService).preEnroll(eq("user-123"), eq("tenant_abc"), any());
    }
}
