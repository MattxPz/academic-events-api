package ec.edu.ups.academicevents.auth.service;

import ec.edu.ups.academicevents.auth.dto.LoginRequest;
import ec.edu.ups.academicevents.auth.dto.RefreshRequest;
import ec.edu.ups.academicevents.auth.dto.RegisterRequest;
import ec.edu.ups.academicevents.auth.dto.TokenResponse;
import ec.edu.ups.academicevents.auth.entity.RefreshToken;
import ec.edu.ups.academicevents.auth.repository.RefreshTokenRepository;
import ec.edu.ups.academicevents.roles.repository.RoleRepository;
import ec.edu.ups.academicevents.shared.audit.AuditService;
import ec.edu.ups.academicevents.shared.exception.DuplicateResourceException;
import ec.edu.ups.academicevents.shared.exception.InvalidTokenException;
import ec.edu.ups.academicevents.shared.ratelimit.LoginAttemptService;
import ec.edu.ups.academicevents.shared.security.JwtService;
import ec.edu.ups.academicevents.shared.security.SecurityUtils;
import ec.edu.ups.academicevents.users.entity.User;
import ec.edu.ups.academicevents.users.repository.UserRepository;
import ec.edu.ups.academicevents.users.repository.UserRoleRepository;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    private static final String EMAIL = "ana.torres@academic.test";
    private static final String RAW_PASSWORD = "Password123*";
    private static final String PASSWORD_HASH = "$2y$12$hash";
    private static final String CLIENT_IP = "192.168.10.20";
    private static final String GENERIC_MESSAGE = "Credenciales inválidas";

    @Mock
    private UserRepository userRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private UserRoleRepository userRoleRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtService jwtService;
    @Mock
    private SecurityUtils securityUtils;
    @Mock
    private LoginAttemptService loginAttemptService;
    @Mock
    private AuditService auditService;

    @InjectMocks
    private AuthServiceImpl authService;

    @Test
    @DisplayName("login correcto devuelve access y refresh token")
    void loginReturnsTokenPair() {
        User user = activeUser();
        given(loginAttemptService.isBlocked(EMAIL)).willReturn(false);
        given(userRepository.findByEmail(EMAIL)).willReturn(Optional.of(user));
        given(passwordEncoder.matches(RAW_PASSWORD, PASSWORD_HASH)).willReturn(true);
        given(userRoleRepository.findRoleNamesByUserId(user.getId())).willReturn(List.of("PARTICIPANT"));
        given(jwtService.generateAccessToken(eq(user.getId()), eq(EMAIL), anyList())).willReturn("access-token");
        given(jwtService.generateRefreshToken(eq(user.getId()), any(UUID.class))).willReturn("refresh-token");

        TokenResponse response = authService.login(new LoginRequest(EMAIL, RAW_PASSWORD), CLIENT_IP);

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("refresh-token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        verify(loginAttemptService).reset(EMAIL);
        verify(refreshTokenRepository).save(any(RefreshToken.class));
        verify(auditService).recordSuccessAs(eq(user.getId()), eq("LOGIN_SUCCESS"), eq("USER"),
                eq(user.getId()), eq(null), anyString());
    }

    @Test
    @DisplayName("contraseña incorrecta lanza excepción con mensaje genérico")
    void loginWithWrongPasswordFails() {
        User user = activeUser();
        given(loginAttemptService.isBlocked(EMAIL)).willReturn(false);
        given(userRepository.findByEmail(EMAIL)).willReturn(Optional.of(user));
        given(passwordEncoder.matches("otra-clave", PASSWORD_HASH)).willReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest(EMAIL, "otra-clave"), CLIENT_IP))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage(GENERIC_MESSAGE);

        verify(loginAttemptService).recordFailure(EMAIL);
        verify(auditService).recordFailure(eq("LOGIN_FAILED"), eq("USER"), eq(null), eq(null), anyString());
        verify(refreshTokenRepository, never()).save(any(RefreshToken.class));
    }

    @Test
    @DisplayName("usuario BLOCKED falla con el mismo mensaje genérico, sin revelar el motivo")
    void loginWithBlockedUserFailsWithGenericMessage() {
        User user = activeUser();
        user.setStatus("BLOCKED");
        given(loginAttemptService.isBlocked(EMAIL)).willReturn(false);
        given(userRepository.findByEmail(EMAIL)).willReturn(Optional.of(user));
        given(passwordEncoder.matches(RAW_PASSWORD, PASSWORD_HASH)).willReturn(true);

        assertThatThrownBy(() -> authService.login(new LoginRequest(EMAIL, RAW_PASSWORD), CLIENT_IP))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage(GENERIC_MESSAGE);

        verify(refreshTokenRepository, never()).save(any(RefreshToken.class));
    }

    @Test
    @DisplayName("un correo bloqueado por intentos fallidos no llega a consultar el usuario")
    void loginWithRateLimitedEmailFails() {
        given(loginAttemptService.isBlocked(EMAIL)).willReturn(true);

        assertThatThrownBy(() -> authService.login(new LoginRequest(EMAIL, RAW_PASSWORD), CLIENT_IP))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage(GENERIC_MESSAGE);

        verify(userRepository, never()).findByEmail(anyString());
    }

    @Test
    @DisplayName("register con email duplicado lanza DuplicateResourceException")
    void registerWithDuplicatedEmailFails() {
        given(userRepository.findByEmail(EMAIL)).willReturn(Optional.of(activeUser()));

        RegisterRequest request = new RegisterRequest("Ana", "Torres", EMAIL, RAW_PASSWORD);

        assertThatThrownBy(() -> authService.register(request, CLIENT_IP))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("correo ya está registrado");

        verify(userRepository, never()).save(any(User.class));
        verify(auditService, never()).recordSuccess(anyString(), anyString(), anyLong(), any(), any());
    }

    @Test
    @DisplayName("refresh con un token ya revocado lanza InvalidTokenException")
    void refreshWithRevokedTokenFails() {
        UUID tokenId = UUID.randomUUID();
        Claims claims = org.mockito.Mockito.mock(Claims.class);
        RefreshToken revokedToken = RefreshToken.builder()
                .tokenId(tokenId)
                .user(activeUser())
                .revokedAt(Instant.now().minusSeconds(60))
                .build();

        given(jwtService.parseAndValidate("refresh-token")).willReturn(claims);
        given(claims.get("type", String.class)).willReturn("refresh");
        given(jwtService.extractJti(claims)).willReturn(tokenId.toString());
        given(refreshTokenRepository.findByTokenId(tokenId)).willReturn(Optional.of(revokedToken));

        assertThatThrownBy(() -> authService.refresh(new RefreshRequest("refresh-token"), CLIENT_IP))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessageContaining("revocado");
    }

    private User activeUser() {
        return User.builder()
                .id(9L)
                .firstName("Ana")
                .lastName("Torres")
                .email(EMAIL)
                .passwordHash(PASSWORD_HASH)
                .status("ACTIVE")
                .build();
    }
}
