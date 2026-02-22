package com.masterchef.masterchef_backend.service;

import com.masterchef.masterchef_backend.dto.AuthResponse;
import com.masterchef.masterchef_backend.dto.LoginRequest;
import com.masterchef.masterchef_backend.dto.RegisterRequest;
import com.masterchef.masterchef_backend.dto.TokenRefreshRequest;
import com.masterchef.masterchef_backend.dto.TokenRefreshResponse;
import com.masterchef.masterchef_backend.models.User;
import com.masterchef.masterchef_backend.repository.UserRepository;
import com.masterchef.masterchef_backend.security.JwtTokenProvider;
import com.masterchef.masterchef_backend.security.UserDetailsServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/*
 * ╔══════════════════════════════════════════════════════════════════════════╗
 * ║                    UNIT TEST — AuthService                              ║
 * ╠══════════════════════════════════════════════════════════════════════════╣
 * ║                                                                          ║
 * ║  AuthService has 3 public methods:                                       ║
 * ║    1. register()      — creates user, returns JWT tokens                 ║
 * ║    2. login()         — authenticates, returns JWT tokens                ║
 * ║    3. refreshToken()  — validates refresh token, returns new access token ║
 * ║                                                                          ║
 * ║  Each one has a happy path AND error paths to cover.                     ║
 * ║                                                                          ║
 * ║  NEW CONCEPT — Mocking interfaces vs concrete classes:                   ║
 * ║  AuthenticationManager and PasswordEncoder are interfaces. Mockito can   ║
 * ║  mock any interface or non-final class transparently.                    ║
 * ║                                                                          ║
 * ╚══════════════════════════════════════════════════════════════════════════╝
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService — Unit Tests")
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private UserDetailsServiceImpl userDetailsService;

    @InjectMocks
    private AuthService authService;

    // ── Shared test data ───────────────────────────────────────────────────────

    private UUID userId;
    private User savedUser;
    private RegisterRequest registerRequest;
    private LoginRequest loginRequest;

    @BeforeEach
    void setUp() {
        userId = UUID.fromString("00000000-0000-0000-0000-000000000002");

        registerRequest = RegisterRequest.builder()
                .name("Alice")
                .email("alice@example.com")
                .password("securePassword123")
                .build();

        loginRequest = LoginRequest.builder()
                .email("alice@example.com")
                .password("securePassword123")
                .build();

        savedUser = User.builder()
                .id(userId)
                .name("Alice")
                .email("alice@example.com")
                .passwordHash("$2a$10$hashedpassword")
                .build();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // SECTION 1: register()
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("register()")
    class Register {

        @Test
        @DisplayName("should return AuthResponse with tokens on successful registration")
        void shouldReturnAuthResponseOnSuccess() {
            // ARRANGE
            // existsByEmail returns false → email is not taken
            when(userRepository.existsByEmail("alice@example.com")).thenReturn(false);
            // Simulate password hashing — real bcrypt is slow; return a fake hash
            when(passwordEncoder.encode("securePassword123")).thenReturn("$2a$10$hashedpassword");
            // After save, return our pre-built user with an ID
            when(userRepository.save(any(User.class))).thenReturn(savedUser);
            // Token generation returns stub strings
            when(jwtTokenProvider.generateAccessToken(any(UserDetails.class), any(UUID.class)))
                    .thenReturn("fake-access-token");
            when(jwtTokenProvider.generateRefreshToken(any(UserDetails.class), any(UUID.class)))
                    .thenReturn("fake-refresh-token");
            when(jwtTokenProvider.getAccessTokenExpirationSeconds()).thenReturn(3600L);

            // ACT
            AuthResponse response = authService.register(registerRequest);

            // ASSERT
            assertThat(response).isNotNull();
            assertThat(response.getEmail()).isEqualTo("alice@example.com");
            assertThat(response.getName()).isEqualTo("Alice");
            assertThat(response.getAccessToken()).isEqualTo("fake-access-token");
            assertThat(response.getRefreshToken()).isEqualTo("fake-refresh-token");
            assertThat(response.getTokenType()).isEqualTo("Bearer");
            assertThat(response.getUserId()).isEqualTo(userId);
        }

        @Test
        @DisplayName("should hash the password before saving the user")
        void shouldHashPasswordBeforeSaving() {
            when(userRepository.existsByEmail(anyString())).thenReturn(false);
            when(passwordEncoder.encode("securePassword123")).thenReturn("$2a$10$hashedpassword");
            when(userRepository.save(any(User.class))).thenReturn(savedUser);
            when(jwtTokenProvider.generateAccessToken(any(), any())).thenReturn("token");
            when(jwtTokenProvider.generateRefreshToken(any(), any())).thenReturn("token");

            authService.register(registerRequest);

            // Verify the encoder was called with the raw password
            verify(passwordEncoder).encode("securePassword123");

            // Use an ArgumentCaptor to inspect what User object was saved
            // This is the key assertion: the saved user must NOT contain the raw password
            ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(userCaptor.capture());

            User capturedUser = userCaptor.getValue();
            assertThat(capturedUser.getPasswordHash())
                    .isEqualTo("$2a$10$hashedpassword")
                    .isNotEqualTo("securePassword123"); // raw password must never be stored
        }

        @Test
        @DisplayName("should throw RuntimeException when email is already registered")
        void shouldThrowWhenEmailAlreadyExists() {
            // Simulate email already in use
            when(userRepository.existsByEmail("alice@example.com")).thenReturn(true);

            assertThatThrownBy(() -> authService.register(registerRequest))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Email already registered");

            // Nothing else should happen after the duplicate check fails
            verifyNoInteractions(passwordEncoder);
            verifyNoInteractions(jwtTokenProvider);
            // save() must never be called with a duplicate email
            verify(userRepository, never()).save(any(User.class));
        }

        @Test
        @DisplayName("should save exactly one user to the database per registration")
        void shouldSaveExactlyOneUser() {
            when(userRepository.existsByEmail(anyString())).thenReturn(false);
            when(passwordEncoder.encode(anyString())).thenReturn("hashed");
            when(userRepository.save(any(User.class))).thenReturn(savedUser);
            when(jwtTokenProvider.generateAccessToken(any(), any())).thenReturn("t");
            when(jwtTokenProvider.generateRefreshToken(any(), any())).thenReturn("t");

            authService.register(registerRequest);

            // times(1) is the default but stating it explicitly makes the intention clear
            verify(userRepository, times(1)).save(any(User.class));
        }

        @Test
        @DisplayName("should populate expiresIn from JwtTokenProvider")
        void shouldPopulateExpiresIn() {
            when(userRepository.existsByEmail(anyString())).thenReturn(false);
            when(passwordEncoder.encode(anyString())).thenReturn("hashed");
            when(userRepository.save(any(User.class))).thenReturn(savedUser);
            when(jwtTokenProvider.generateAccessToken(any(), any())).thenReturn("access");
            when(jwtTokenProvider.generateRefreshToken(any(), any())).thenReturn("refresh");
            when(jwtTokenProvider.getAccessTokenExpirationSeconds()).thenReturn(7200L);

            AuthResponse response = authService.register(registerRequest);

            assertThat(response.getExpiresIn()).isEqualTo(7200L);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // SECTION 2: login()
    //
    // login() delegates authentication to Spring Security's AuthenticationManager.
    // We mock that manager so we do not need a running Spring context.
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("login()")
    class Login {

        @Test
        @DisplayName("should return AuthResponse with tokens on successful login")
        void shouldReturnAuthResponseOnSuccess() {
            // Spring Security's AuthenticationManager.authenticate() returns an
            // Authentication object on success. We need to mock that object too
            // because AuthService casts its principal to UserDetails.
            UserDetails mockUserDetails = org.springframework.security.core.userdetails.User
                    .withUsername("alice@example.com")
                    .password("$2a$10$hashedpassword")
                    .authorities(new ArrayList<>())
                    .build();

            // The Authentication object wraps the UserDetails
            Authentication mockAuthentication = mock(Authentication.class);
            when(mockAuthentication.getPrincipal()).thenReturn(mockUserDetails);

            when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                    .thenReturn(mockAuthentication);
            when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(savedUser));
            when(jwtTokenProvider.generateAccessToken(any(UserDetails.class), any(UUID.class)))
                    .thenReturn("access-token");
            when(jwtTokenProvider.generateRefreshToken(any(UserDetails.class), any(UUID.class)))
                    .thenReturn("refresh-token");
            when(jwtTokenProvider.getAccessTokenExpirationSeconds()).thenReturn(3600L);

            AuthResponse response = authService.login(loginRequest);

            assertThat(response).isNotNull();
            assertThat(response.getEmail()).isEqualTo("alice@example.com");
            assertThat(response.getAccessToken()).isEqualTo("access-token");
            assertThat(response.getRefreshToken()).isEqualTo("refresh-token");
        }

        @Test
        @DisplayName("should throw when AuthenticationManager rejects credentials")
        void shouldThrowWhenCredentialsAreInvalid() {
            // AuthenticationManager throws BadCredentialsException for wrong password —
            // this is standard Spring Security behaviour.
            when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                    .thenThrow(new BadCredentialsException("Bad credentials"));

            assertThatThrownBy(() -> authService.login(loginRequest))
                    .isInstanceOf(BadCredentialsException.class);

            // If auth fails, we should never touch the repository or token provider
            verifyNoInteractions(userRepository);
            verifyNoInteractions(jwtTokenProvider);
        }

        @Test
        @DisplayName("should throw RuntimeException when authenticated user is not found in DB")
        void shouldThrowWhenUserNotFoundInDbAfterAuthentication() {
            // Edge case: authentication succeeds but the user was deleted between the auth
            // check and the DB lookup (a race condition worth protecting against).
            UserDetails mockUserDetails = org.springframework.security.core.userdetails.User
                    .withUsername("alice@example.com")
                    .password("hash")
                    .authorities(new ArrayList<>())
                    .build();

            Authentication mockAuthentication = mock(Authentication.class);
            when(mockAuthentication.getPrincipal()).thenReturn(mockUserDetails);
            when(authenticationManager.authenticate(any())).thenReturn(mockAuthentication);
            when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.login(loginRequest))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("User not found");
        }

        @Test
        @DisplayName("should pass email and password to AuthenticationManager as a token")
        void shouldPassCorrectCredentialsToAuthManager() {
            UserDetails mockUserDetails = org.springframework.security.core.userdetails.User
                    .withUsername("alice@example.com")
                    .password("hash")
                    .authorities(new ArrayList<>())
                    .build();

            Authentication mockAuthentication = mock(Authentication.class);
            when(mockAuthentication.getPrincipal()).thenReturn(mockUserDetails);
            when(authenticationManager.authenticate(any())).thenReturn(mockAuthentication);
            when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(savedUser));
            when(jwtTokenProvider.generateAccessToken(any(), any())).thenReturn("t");
            when(jwtTokenProvider.generateRefreshToken(any(), any())).thenReturn("t");

            authService.login(loginRequest);

            // Capture the authentication token passed to the AuthenticationManager
            ArgumentCaptor<UsernamePasswordAuthenticationToken> authCaptor =
                    ArgumentCaptor.forClass(UsernamePasswordAuthenticationToken.class);
            verify(authenticationManager).authenticate(authCaptor.capture());

            UsernamePasswordAuthenticationToken captured = authCaptor.getValue();
            assertThat(captured.getPrincipal()).isEqualTo("alice@example.com");
            assertThat(captured.getCredentials()).isEqualTo("securePassword123");
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // SECTION 3: refreshToken()
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("refreshToken()")
    class RefreshToken {

        private TokenRefreshRequest refreshRequest;

        @BeforeEach
        void setUpRefreshRequest() {
            refreshRequest = TokenRefreshRequest.builder()
                    .refreshToken("valid-refresh-token")
                    .build();
        }

        @Test
        @DisplayName("should return a new TokenRefreshResponse on valid refresh token")
        void shouldReturnNewAccessTokenOnValidRefreshToken() {
            UserDetails mockUserDetails = org.springframework.security.core.userdetails.User
                    .withUsername("alice@example.com")
                    .password("hash")
                    .authorities(new ArrayList<>())
                    .build();

            when(jwtTokenProvider.validateRefreshToken("valid-refresh-token")).thenReturn(true);
            when(jwtTokenProvider.extractUsername("valid-refresh-token")).thenReturn("alice@example.com");
            when(userDetailsService.loadUserByUsername("alice@example.com")).thenReturn(mockUserDetails);
            when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(savedUser));
            when(jwtTokenProvider.generateRefreshToken(any(UserDetails.class), any(UUID.class)))
                    .thenReturn("new-access-token");
            when(jwtTokenProvider.getAccessTokenExpirationSeconds()).thenReturn(3600L);

            TokenRefreshResponse response = authService.refreshToken(refreshRequest);

            assertThat(response).isNotNull();
            assertThat(response.getAccessToken()).isEqualTo("new-access-token");
            assertThat(response.getTokenType()).isEqualTo("Bearer");
            assertThat(response.getExpiresIn()).isEqualTo(3600L);
        }

        @Test
        @DisplayName("should throw RuntimeException when refresh token is invalid")
        void shouldThrowWhenRefreshTokenIsInvalid() {
            when(jwtTokenProvider.validateRefreshToken("valid-refresh-token")).thenReturn(false);

            assertThatThrownBy(() -> authService.refreshToken(refreshRequest))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Invalid refresh token");

            // No further steps should execute when the token is invalid
            verify(jwtTokenProvider, never()).extractUsername(anyString());
            verifyNoInteractions(userRepository);
        }

        @Test
        @DisplayName("should throw RuntimeException when user is not found during token refresh")
        void shouldThrowWhenUserNotFoundDuringRefresh() {
            UserDetails mockUserDetails = org.springframework.security.core.userdetails.User
                    .withUsername("alice@example.com")
                    .password("hash")
                    .authorities(new ArrayList<>())
                    .build();

            when(jwtTokenProvider.validateRefreshToken("valid-refresh-token")).thenReturn(true);
            when(jwtTokenProvider.extractUsername("valid-refresh-token")).thenReturn("alice@example.com");
            when(userDetailsService.loadUserByUsername("alice@example.com")).thenReturn(mockUserDetails);
            when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.refreshToken(refreshRequest))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("User not found");
        }

        @Test
        @DisplayName("should validate the refresh token before any other operation")
        void shouldValidateRefreshTokenFirst() {
            when(jwtTokenProvider.validateRefreshToken("valid-refresh-token")).thenReturn(false);

            assertThatThrownBy(() -> authService.refreshToken(refreshRequest))
                    .isInstanceOf(RuntimeException.class);

            // validateRefreshToken should be the FIRST call — nothing else should run
            verify(jwtTokenProvider).validateRefreshToken("valid-refresh-token");
            verifyNoMoreInteractions(jwtTokenProvider);
        }
    }
}
