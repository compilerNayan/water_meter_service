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
class V2DevicePreEnrollControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DevicePreEnrollService devicePreEnrollService;

    @Test
    void preEnrollDeviceReturnsCreated() throws Exception {
        when(devicePreEnrollService.preEnroll(eq("user-123"), eq("k3m9x2a"), any()))
                .thenReturn(
                        new DevicePreEnrollResponse(
                                "k3m9x2a",
                                "WM000123",
                                "pending",
                                "2026-06-08T15:00:00Z"));

        mockMvc.perform(
                        post("/v2/tenants/k3m9x2a/devices/pre-enroll")
                                .header("Authorization", "Bearer test-token")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "serialNumber": "WM000123"
                                        }
                                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tenantId").value("k3m9x2a"))
                .andExpect(jsonPath("$.serialNumber").value("WM000123"))
                .andExpect(jsonPath("$.status").value("pending"));

        verify(devicePreEnrollService).preEnroll(eq("user-123"), eq("k3m9x2a"), any());
    }
}
