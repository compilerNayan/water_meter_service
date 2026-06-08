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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserService userService;

    @Test
    void getMeReturnsProfile() throws Exception {
        when(userService.getMe("user-123"))
                .thenReturn(
                        new UserResponse(
                                "user-123",
                                "admin@building.com",
                                "Raj Sharma",
                                "+919876543210",
                                "Raj",
                                "Sharma",
                                "tenant_abc",
                                true,
                                true));

        mockMvc.perform(get("/users/me").header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("user-123"))
                .andExpect(jsonPath("$.tenantId").value("tenant_abc"))
                .andExpect(jsonPath("$.isTenantOwner").value(true));
    }

    @Test
    void registerUserReturnsCreated() throws Exception {
        when(userService.registerUser(eq("user-123"), eq("admin@building.com"), any()))
                .thenReturn(
                        new UserService.UserRegistrationResult(
                                new UserResponse(
                                        "user-123",
                                        "admin@building.com",
                                        "Raj Sharma",
                                        "+919876543210",
                                        "Raj",
                                        "Sharma",
                                        "tenant_abc",
                                        true,
                                        true),
                                true));

        mockMvc.perform(
                        post("/users")
                                .header("Authorization", "Bearer test-token")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "email": "admin@building.com",
                                          "phone": "+919876543210",
                                          "firstName": "Raj",
                                          "lastName": "Sharma",
                                          "tenantName": "Sunrise Apartments"
                                        }
                                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tenantId").value("tenant_abc"));

        verify(userService).registerUser(eq("user-123"), eq("admin@building.com"), any());
    }

    @Test
    void registerUserReturnsOkWhenExisting() throws Exception {
        when(userService.registerUser(eq("user-123"), eq("admin@building.com"), any()))
                .thenReturn(
                        new UserService.UserRegistrationResult(
                                new UserResponse(
                                        "user-123",
                                        "admin@building.com",
                                        "Raj Sharma",
                                        "",
                                        "Raj",
                                        "Sharma",
                                        "tenant_abc",
                                        true,
                                        true),
                                false));

        mockMvc.perform(
                        post("/users")
                                .header("Authorization", "Bearer test-token")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "email": "admin@building.com",
                                          "firstName": "Raj",
                                          "lastName": "Sharma",
                                          "tenantName": "Sunrise Apartments"
                                        }
                                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("user-123"));
    }
}
