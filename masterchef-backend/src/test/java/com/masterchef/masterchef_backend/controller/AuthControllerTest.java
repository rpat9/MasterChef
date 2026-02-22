package com.masterchef.masterchef_backend.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.client.RestTestClient;

import com.masterchef.masterchef_backend.dto.AuthResponse;
import com.masterchef.masterchef_backend.dto.LoginRequest;
import com.masterchef.masterchef_backend.dto.RegisterRequest;
import com.masterchef.masterchef_backend.dto.TokenRefreshRequest;
import com.masterchef.masterchef_backend.dto.TokenRefreshResponse;
import com.masterchef.masterchef_backend.security.JwtAuthenticationFilter;
import com.masterchef.masterchef_backend.security.JwtTokenProvider;
import com.masterchef.masterchef_backend.security.UserDetailsServiceImpl;
import com.masterchef.masterchef_backend.service.AuthService;

/*
 * Controller layer tests for AuthController.
 *
 * @WebMvcTest spins up only the web layer (DispatcherServlet, filters,
 * controllers). No real database or service logic runs. Everything downstream
 * of the controller is replaced with @MockitoBean stubs.
 *
 * What is tested here:
 *   - HTTP routing       : correct URL, method, and status code
 *   - JSON parsing       : request body is deserialised correctly
 *   - @Valid enforcement : constraint violations return 400
 *   - JSON serialisation : response body contains the expected fields
 *   - Security           : auth endpoints are publicly accessible (no token needed)
 */
@WebMvcTest(AuthController.class)
@AutoConfigureRestTestClient
class AuthControllerTest {

    @Autowired
    private RestTestClient restTestClient;

    @MockitoBean
    private AuthService authService;

    // Spring Security infrastructure required by the security filter chain
    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;
    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;
    @MockitoBean
    private UserDetailsServiceImpl userDetailsService;

    private AuthResponse sampleAuthResponse;

    @BeforeEach
    void setUp() {
        sampleAuthResponse = AuthResponse.builder()
                .email("chef@example.com")
                .name("Gordon")
                .accessToken("access.jwt.token")
                .refreshToken("refresh.jwt.token")
                .tokenType("Bearer")
                .expiresIn(900L)
                .build();
    }

    // -------------------------------------------------------------------------
    // POST /api/v1/auth/register
    // -------------------------------------------------------------------------

    @Nested
    class Register {

        @Test
        void shouldReturn201WithAuthResponseOnSuccess() throws Exception {
            when(authService.register(any(RegisterRequest.class))).thenReturn(sampleAuthResponse);

            RegisterRequest request = RegisterRequest.builder()
                    .name("Gordon")
                    .email("chef@example.com")
                    .password("secret123")
                    .build();

            restTestClient.post().uri("/api/v1/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .exchange()
                    .expectStatus().isCreated()
                    .expectBody()
                    .jsonPath("$.email").isEqualTo("chef@example.com")
                    .jsonPath("$.name").isEqualTo("Gordon")
                    .jsonPath("$.accessToken").isEqualTo("access.jwt.token")
                    .jsonPath("$.tokenType").isEqualTo("Bearer");
        }

        @Test
        void shouldReturn400WhenNameIsBlank() throws Exception {
            RegisterRequest request = RegisterRequest.builder()
                    .name("")
                    .email("chef@example.com")
                    .password("secret123")
                    .build();

            restTestClient.post().uri("/api/v1/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .exchange()
                    .expectStatus().isBadRequest();
        }

        @Test
        void shouldReturn400WhenEmailIsInvalid() throws Exception {
            RegisterRequest request = RegisterRequest.builder()
                    .name("Gordon")
                    .email("not-an-email")
                    .password("secret123")
                    .build();

            restTestClient.post().uri("/api/v1/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .exchange()
                    .expectStatus().isBadRequest();
        }

        @Test
        void shouldReturn400WhenPasswordIsTooShort() throws Exception {
            RegisterRequest request = RegisterRequest.builder()
                    .name("Gordon")
                    .email("chef@example.com")
                    .password("short")
                    .build();

            restTestClient.post().uri("/api/v1/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .exchange()
                    .expectStatus().isBadRequest();
        }

        @Test
        void shouldReturn400WhenBodyIsMissing() {
            restTestClient.post().uri("/api/v1/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .exchange()
                    .expectStatus().isBadRequest();
        }
    }

    // -------------------------------------------------------------------------
    // POST /api/v1/auth/login
    // -------------------------------------------------------------------------

    @Nested
    class Login {

        @Test
        void shouldReturn200WithTokensOnSuccess() throws Exception {
            when(authService.login(any(LoginRequest.class))).thenReturn(sampleAuthResponse);

            LoginRequest request = LoginRequest.builder()
                    .email("chef@example.com")
                    .password("secret123")
                    .build();

            restTestClient.post().uri("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.accessToken").isEqualTo("access.jwt.token")
                    .jsonPath("$.refreshToken").isEqualTo("refresh.jwt.token")
                    .jsonPath("$.expiresIn").isEqualTo(900);
        }

        @Test
        void shouldReturn400WhenEmailIsBlank() throws Exception {
            LoginRequest request = LoginRequest.builder()
                    .email("")
                    .password("secret123")
                    .build();

            restTestClient.post().uri("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .exchange()
                    .expectStatus().isBadRequest();
        }

        @Test
        void shouldReturn400WhenPasswordIsBlank() throws Exception {
            LoginRequest request = LoginRequest.builder()
                    .email("chef@example.com")
                    .password("")
                    .build();

            restTestClient.post().uri("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .exchange()
                    .expectStatus().isBadRequest();
        }

        @Test
        void shouldReturn401WhenCredentialsAreWrong() throws Exception {
            when(authService.login(any(LoginRequest.class)))
                    .thenThrow(new BadCredentialsException("Bad credentials"));

            LoginRequest request = LoginRequest.builder()
                    .email("chef@example.com")
                    .password("wrongpassword")
                    .build();

            restTestClient.post().uri("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .exchange()
                    .expectStatus().isUnauthorized();
        }
    }

    // -------------------------------------------------------------------------
    // POST /api/v1/auth/refresh
    // -------------------------------------------------------------------------

    @Nested
    class RefreshToken {

        @Test
        void shouldReturn200WithNewAccessToken() throws Exception {
            TokenRefreshResponse refreshResponse = TokenRefreshResponse.builder()
                    .accessToken("new.access.token")
                    .tokenType("Bearer")
                    .expiresIn(900L)
                    .build();

            when(authService.refreshToken(any(TokenRefreshRequest.class))).thenReturn(refreshResponse);

            TokenRefreshRequest request = TokenRefreshRequest.builder()
                    .refreshToken("refresh.jwt.token")
                    .build();

            restTestClient.post().uri("/api/v1/auth/refresh")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.accessToken").isEqualTo("new.access.token")
                    .jsonPath("$.tokenType").isEqualTo("Bearer")
                    .jsonPath("$.expiresIn").isEqualTo(900);
        }

        @Test
        void shouldReturn400WhenRefreshTokenIsBlank() throws Exception {
            TokenRefreshRequest request = TokenRefreshRequest.builder()
                    .refreshToken("")
                    .build();

            restTestClient.post().uri("/api/v1/auth/refresh")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .exchange()
                    .expectStatus().isBadRequest();
        }
    }
}