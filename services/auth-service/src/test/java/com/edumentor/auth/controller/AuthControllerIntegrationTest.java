package com.edumentor.auth.controller;

import com.edumentor.auth.entity.Role;
import com.edumentor.auth.entity.User;
import com.edumentor.auth.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerIntegrationTest {

    private static final String PASSWORD = "Passw0rdX";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void cleanDatabase() {
        userRepository.deleteAll();
    }

    // ---------- Registration ----------

    @Test
    void registrationSucceedsAndNeverReturnsPassword() throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Asha", "email", "asha@example.com",
                                "password", PASSWORD, "role", "STUDENT"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("asha@example.com"))
                .andExpect(jsonPath("$.role").value("STUDENT"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        User saved = userRepository.findByEmail("asha@example.com").orElseThrow();
        assertThat(saved.getPasswordHash()).isNotEqualTo(PASSWORD).startsWith("$2");
    }

    @Test
    void duplicateEmailIsRejectedWith409() throws Exception {
        register("dup@example.com", "STUDENT").andExpect(status().isCreated());

        register("dup@example.com", "MENTOR")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("EMAIL_ALREADY_EXISTS"));
    }

    @Test
    void invalidEmailIsRejected() throws Exception {
        register("not-an-email", "STUDENT")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.email").exists());
    }

    @Test
    void weakPasswordIsRejected() throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Asha", "email", "weak@example.com",
                                "password", "weak", "role", "STUDENT"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.password").exists());
    }

    @Test
    void selfRegisteringAsAdminIsRejected() throws Exception {
        register("evil@example.com", "ADMIN")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("ROLE_NOT_ALLOWED"));
        assertThat(userRepository.existsByEmail("evil@example.com")).isFalse();
    }

    // ---------- Login & JWT ----------

    @Test
    void loginSucceedsAndReturnsJwt() throws Exception {
        register("login@example.com", "STUDENT").andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "login@example.com", "password", PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.user.email").value("login@example.com"));
    }

    @Test
    void loginWithWrongPasswordReturns401() throws Exception {
        register("wrong@example.com", "STUDENT").andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "wrong@example.com", "password", "Wrong1234"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("INVALID_CREDENTIALS"));
    }

    @Test
    void meReturnsCurrentUserWithValidToken() throws Exception {
        register("me@example.com", "MENTOR").andExpect(status().isCreated());
        String token = loginAndGetToken("me@example.com");

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("me@example.com"))
                .andExpect(jsonPath("$.role").value("MENTOR"));
    }

    @Test
    void meWithoutTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
    }

    @Test
    void meWithGarbageTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer not.a.jwt"))
                .andExpect(status().isUnauthorized());
    }

    // ---------- Authorization ----------

    @Test
    void studentCannotAccessAdminEndpoint() throws Exception {
        register("student@example.com", "STUDENT").andExpect(status().isCreated());
        String token = loginAndGetToken("student@example.com");

        mockMvc.perform(get("/api/auth/admin/users").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("ACCESS_DENIED"));
    }

    @Test
    void mentorCannotAccessAdminEndpoint() throws Exception {
        register("mentor@example.com", "MENTOR").andExpect(status().isCreated());
        String token = loginAndGetToken("mentor@example.com");

        mockMvc.perform(get("/api/auth/admin/users").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanListUsers() throws Exception {
        userRepository.save(User.builder().name("Admin").email("admin@example.com")
                .passwordHash(passwordEncoder.encode(PASSWORD)).role(Role.ADMIN).enabled(true).build());
        String token = loginAndGetToken("admin@example.com");

        mockMvc.perform(get("/api/auth/admin/users").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].email").value("admin@example.com"))
                .andExpect(jsonPath("$[0].password").doesNotExist());
    }
    @Test
    void adminCanGetUserByIdAndUnknownIdReturns404() throws Exception {
        User admin = userRepository.save(User.builder().name("Admin").email("admin2@example.com")
                .passwordHash(passwordEncoder.encode(PASSWORD)).role(Role.ADMIN).enabled(true).build());
        String token = loginAndGetToken("admin2@example.com");

        mockMvc.perform(get("/api/auth/admin/users/" + admin.getId()).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("admin2@example.com"));
        mockMvc.perform(get("/api/auth/admin/users/999999").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("USER_NOT_FOUND"));
    }
    @Test
    void internalLookupRequiresTheApiKey() throws Exception {
        User user = userRepository.save(User.builder().name("Asha").email("asha-int@example.com")
                .passwordHash(passwordEncoder.encode(PASSWORD)).role(Role.STUDENT).enabled(true).build());
        String url = "/api/auth/internal/users/" + user.getId();

        mockMvc.perform(get(url)).andExpect(status().isForbidden());
        mockMvc.perform(get(url).header("X-Internal-Api-Key", "wrong")).andExpect(status().isForbidden());
        mockMvc.perform(get(url).header("X-Internal-Api-Key", "test-internal-api-key-123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("asha-int@example.com"));
    }

    // ---------- helpers ----------

    private org.springframework.test.web.servlet.ResultActions register(String email, String role) throws Exception {
        return mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("name", "Test User", "email", email, "password", PASSWORD, "role", role))));
    }

    private String loginAndGetToken(String email) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "password", PASSWORD))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("accessToken").asText();
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}