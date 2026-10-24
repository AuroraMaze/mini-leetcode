package jsd.toiyeujava.mini_leetcode;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jsd.toiyeujava.mini_leetcode.auth.dto.LoginRequest;
import jsd.toiyeujava.mini_leetcode.auth.dto.LogoutRequest;
import jsd.toiyeujava.mini_leetcode.auth.dto.RefreshTokenRequest;
import jsd.toiyeujava.mini_leetcode.auth.dto.RegisterRequest;
import jsd.toiyeujava.mini_leetcode.entity.Role;
import jsd.toiyeujava.mini_leetcode.auth.entity.RefreshToken;
import jsd.toiyeujava.mini_leetcode.auth.entity.User;
import jsd.toiyeujava.mini_leetcode.entity.UserStatus;
import jsd.toiyeujava.mini_leetcode.auth.repository.RefreshTokenRepository;
import jsd.toiyeujava.mini_leetcode.auth.repository.UserRepository;
import jsd.toiyeujava.mini_leetcode.auth.security.JwtTokenProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    @DisplayName("Verification Gate 2: Full Auth lifecycle - Register, Login, Refresh, Revoke, Role 403")
    void fullAuthLifecycleTest() throws Exception {
        // 1. Register a standard user
        RegisterRequest registerReq = new RegisterRequest("testuser", "testuser@example.com", "Password123!", Role.USER);
        MvcResult registerResult = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.user.username").value("testuser"))
                .andExpect(jsonPath("$.user.role").value("USER"))
                .andReturn();

        JsonNode registerJson = objectMapper.readTree(registerResult.getResponse().getContentAsString());
        String initialAccessToken = registerJson.get("accessToken").asText();
        String initialRefreshToken = registerJson.get("refreshToken").asText();

        // 2. Duplicate registration returns 409 Conflict (RFC 7807 ProblemDetail)
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerReq)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Resource Conflict"))
                .andExpect(jsonPath("$.status").value(409));

        // 3. Login with wrong password returns 401 Unauthorized (RFC 7807 ProblemDetail)
        LoginRequest badLogin = new LoginRequest("testuser", "WrongPassword!");
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badLogin)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Authentication Failed"))
                .andExpect(jsonPath("$.status").value(401));

        // 4. Login with valid credentials returns 200 OK + JWT
        LoginRequest goodLogin = new LoginRequest("testuser", "Password123!");
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(goodLogin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andReturn();

        JsonNode loginJson = objectMapper.readTree(loginResult.getResponse().getContentAsString());
        String validRefreshToken = loginJson.get("refreshToken").asText();

        // 5. Refresh token rotates and gives new access & refresh tokens
        RefreshTokenRequest refreshReq = new RefreshTokenRequest(validRefreshToken);
        MvcResult refreshResult = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refreshReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andReturn();

        JsonNode refreshJson = objectMapper.readTree(refreshResult.getResponse().getContentAsString());
        String rotatedRefreshToken = refreshJson.get("refreshToken").asText();
        assertThat(rotatedRefreshToken).isNotEqualTo(validRefreshToken);

        // 6. Old refresh token was rotated, using it again returns 401 Unauthorized
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refreshReq)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        // 7. Logout revokes the rotated refresh token
        LogoutRequest logoutReq = new LogoutRequest(rotatedRefreshToken);
        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(logoutReq)))
                .andExpect(status().isOk());

        // Subsequent refresh with logged out token returns 401
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshTokenRequest(rotatedRefreshToken))))
                .andExpect(status().isUnauthorized());

        // 8. Accessing Instructor/Admin endpoint with USER token returns 403 Forbidden ProblemDetail
        mockMvc.perform(post("/api/v1/problems")
                        .header("Authorization", "Bearer " + initialAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Test Problem","description":"Desc","difficulty":"EASY"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.title").value("Forbidden"));

        // 9. Accessing protected endpoint without token returns 401 Unauthorized ProblemDetail
        mockMvc.perform(post("/api/v1/problems")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Test Problem","description":"Desc","difficulty":"EASY"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.title").value("Unauthorized"));
    }

    @Test
    @DisplayName("Validation failure returns HTTP 400 with RFC 7807 problem details and invalid_params map")
    void register_invalidInput_returns400() throws Exception {
        RegisterRequest invalidReq = new RegisterRequest("", "invalid-email", "short", Role.USER);
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Bad Request"))
                .andExpect(jsonPath("$.invalid_params.username").isNotEmpty())
                .andExpect(jsonPath("$.invalid_params.email").isNotEmpty())
                .andExpect(jsonPath("$.invalid_params.password").isNotEmpty());
    }

    @Test
    @DisplayName("Login with non-existent user returns HTTP 401 ProblemDetail")
    void login_nonExistentUser_returns401() throws Exception {
        LoginRequest req = new LoginRequest("nonexistent", "Password123!");
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @DisplayName("Login with blocked account returns HTTP 401 ProblemDetail")
    void login_blockedUser_returns401() throws Exception {
        User blockedUser = User.builder()
                .username("blockeduser")
                .email("blocked@example.com")
                .passwordHash(passwordEncoder.encode("Password123!"))
                .role(Role.USER)
                .status(UserStatus.BLOCKED)
                .build();
        userRepository.save(blockedUser);

        LoginRequest req = new LoginRequest("blockeduser", "Password123!");
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.detail").value("Account is blocked"));
    }

    @Test
    @DisplayName("Refresh token for blocked user returns HTTP 401 ProblemDetail")
    void refresh_blockedUser_returns401() throws Exception {
        User blockedUser = User.builder()
                .username("blockedrefresh")
                .email("blockedrefresh@example.com")
                .passwordHash(passwordEncoder.encode("Password123!"))
                .role(Role.USER)
                .status(UserStatus.BLOCKED)
                .build();
        blockedUser = userRepository.save(blockedUser);

        String plainToken = "some-random-refresh-token-blocked";
        RefreshToken rt = RefreshToken.builder()
                .user(blockedUser)
                .tokenHash(JwtTokenProvider.hashToken(plainToken))
                .expiresAt(Instant.now().plus(7, ChronoUnit.DAYS))
                .revoked(false)
                .build();
        refreshTokenRepository.save(rt);

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshTokenRequest(plainToken))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.detail").value("Account is blocked"));
    }

    @Test
    @DisplayName("Refresh with expired token returns HTTP 401 ProblemDetail")
    void refresh_expiredToken_returns401() throws Exception {
        User activeUser = User.builder()
                .username("expiredusertok")
                .email("expired@example.com")
                .passwordHash(passwordEncoder.encode("Password123!"))
                .role(Role.USER)
                .status(UserStatus.ACTIVE)
                .build();
        activeUser = userRepository.save(activeUser);

        String plainToken = "expired-token-val";
        RefreshToken rt = RefreshToken.builder()
                .user(activeUser)
                .tokenHash(JwtTokenProvider.hashToken(plainToken))
                .expiresAt(Instant.now().minus(1, ChronoUnit.HOURS))
                .revoked(false)
                .build();
        refreshTokenRepository.save(rt);

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshTokenRequest(plainToken))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.detail").value("Refresh token has expired"));
    }
}
