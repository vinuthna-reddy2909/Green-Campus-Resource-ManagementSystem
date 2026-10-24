package edu.campus.green;

import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ApiController.class)
@Import(SecurityConfig.class)
class ApiAuthorizationTest {
    @Autowired private MockMvc mockMvc;
    @MockBean private CampusService service;
    @MockBean private UserRepository users;
    @MockBean private LocationRepository locations;

    @Test
    void userDirectoryIsForbiddenForViewerRole() throws Exception {
        mockMvc.perform(get("/api/users").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("viewer").roles("VIEWER")))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "VIEWER")
    void viewerCannotCreateWasteRecords() throws Exception {
        mockMvc.perform(post("/api/records").with(csrf())
                .contentType("application/json")
                .content("""
                    {"category":"PLASTIC","subtype":"Bottles","quantity":2.5,"unit":"KG","locationId":"location-id","recordedAt":"2026-10-22"}
                    """))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanReadUserDirectory() throws Exception {
        when(users.findAll()).thenReturn(List.of());
        mockMvc.perform(get("/api/users")).andExpect(status().isOk());
    }

    @Test
    void protectedApiRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/records")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void userCreationNeverReturnsPassword() throws Exception {
        when(service.createUser(any(), any())).thenReturn(
            new AppUser("new.staff", "$2a$10$hash-only", "New Staff", Role.STAFF));
        mockMvc.perform(post("/api/users").with(csrf())
                .contentType("application/json")
                .content("""
                    {"username":"new.staff","password":"long-enough-password","displayName":"New Staff","role":"STAFF"}
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.username").value("new.staff"))
            .andExpect(jsonPath("$.password").doesNotExist())
            .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }
}
