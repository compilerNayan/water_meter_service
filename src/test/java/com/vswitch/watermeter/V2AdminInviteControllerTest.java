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
class V2AdminInviteControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AdminInviteService adminInviteService;

    @Test
    void createAdminInviteReturnsCode() throws Exception {
        when(adminInviteService.createAdminInvite(eq("user-123"), eq("k3m9x2a")))
                .thenReturn(new AdminInviteResponse("ADMIN-7X2K", "2026-07-01T00:00:00Z"));

        mockMvc.perform(
                        post("/v2/tenants/k3m9x2a/admin-invites")
                                .header("Authorization", "Bearer test-token")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inviteCode").value("ADMIN-7X2K"));

        verify(adminInviteService).createAdminInvite("user-123", "k3m9x2a");
    }

    @Test
    void joinAsAdminReturnsTenant() throws Exception {
        when(adminInviteService.joinAsAdmin(eq("user-123"), any()))
                .thenReturn(new JoinAdminResponse("k3m9x2a", true, false));

        mockMvc.perform(
                        post("/v2/tenants/join/admin")
                                .header("Authorization", "Bearer test-token")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "inviteCode": "ADMIN-7X2K"
                                        }
                                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value("k3m9x2a"))
                .andExpect(jsonPath("$.onboardingComplete").value(true))
                .andExpect(jsonPath("$.isTenantOwner").value(false));

        verify(adminInviteService).joinAsAdmin(eq("user-123"), any());
    }
}
