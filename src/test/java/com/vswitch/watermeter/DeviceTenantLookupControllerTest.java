package com.vswitch.watermeter;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class DeviceTenantLookupControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private DevicePreEnrollService devicePreEnrollService;

    @Test
    void lookupTenantReturnsTenantWithoutAuth() throws Exception {
        when(devicePreEnrollService.lookupTenantBySerial("WM000123"))
                .thenReturn(new DeviceTenantLookupResponse("WM000123", "k3m9x2a"));

        mockMvc.perform(get("/devices/WM000123/tenant"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.serialNumber").value("WM000123"))
                .andExpect(jsonPath("$.tenantId").value("k3m9x2a"));
    }

    @Test
    void lookupTenantReturnsNotFoundWhenSerialMissing() throws Exception {
        when(devicePreEnrollService.lookupTenantBySerial("WM999999"))
                .thenThrow(
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND, "Serial number not found"));

        mockMvc.perform(get("/devices/WM999999/tenant")).andExpect(status().isNotFound());
    }
}
