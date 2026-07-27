package ec.edu.ups.academicevents.shared.security;

import ec.edu.ups.academicevents.shared.exception.ErrorCode;
import ec.edu.ups.academicevents.shared.exception.InvalidTokenException;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET = base64("clave-secreta-de-pruebas-hs256-con-mas-de-32-bytes");
    private static final String OTHER_SECRET = base64("otra-clave-secreta-distinta-de-pruebas-hs256-1234");
    private static final String ISSUER = "academic-events-api";
    private static final long ACCESS_EXPIRATION = 900_000L;
    private static final long REFRESH_EXPIRATION = 604_800_000L;

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(SECRET, ACCESS_EXPIRATION, REFRESH_EXPIRATION, ISSUER);
    }

    @Test
    @DisplayName("genera y valida un access token correctamente")
    void generatesAndValidatesAccessToken() {
        String token = jwtService.generateAccessToken(7L, "ana.torres@academic.test", List.of("PARTICIPANT"));

        Claims claims = jwtService.parseAndValidate(token);

        assertThat(token).isNotBlank();
        assertThat(claims.getIssuer()).isEqualTo(ISSUER);
        assertThat(claims.getExpiration()).isAfter(claims.getIssuedAt());
    }

    @Test
    @DisplayName("extrae userId, email y roles de los claims")
    void extractsClaims() {
        String token = jwtService.generateAccessToken(
                42L, "organizador@academic.test", List.of("ORGANIZER", "PARTICIPANT"));

        Claims claims = jwtService.parseAndValidate(token);

        assertThat(jwtService.extractUserId(claims)).isEqualTo(42L);
        assertThat(claims.get("email", String.class)).isEqualTo("organizador@academic.test");
        assertThat(jwtService.extractRoles(claims)).containsExactly("ORGANIZER", "PARTICIPANT");
    }

    @Test
    @DisplayName("extrae el jti del refresh token")
    void extractsRefreshTokenId() {
        UUID tokenId = UUID.randomUUID();

        String token = jwtService.generateRefreshToken(3L, tokenId);
        Claims claims = jwtService.parseAndValidate(token);

        assertThat(jwtService.extractJti(claims)).isEqualTo(tokenId.toString());
        assertThat(claims.get("type", String.class)).isEqualTo("refresh");
    }

    @Test
    @DisplayName("rechaza un token con firma inválida")
    void rejectsTokenSignedWithAnotherKey() {
        JwtService intruder = new JwtService(OTHER_SECRET, ACCESS_EXPIRATION, REFRESH_EXPIRATION, ISSUER);
        String forgedToken = intruder.generateAccessToken(1L, "intruso@academic.test", List.of("ADMIN"));

        assertThatThrownBy(() -> jwtService.parseAndValidate(forgedToken))
                .isInstanceOf(InvalidTokenException.class)
                .extracting(exception -> ((InvalidTokenException) exception).getCode())
                .isEqualTo(ErrorCode.INVALID_TOKEN);
    }

    @Test
    @DisplayName("rechaza un token expirado")
    void rejectsExpiredToken() {
        // Expiración negativa: el token nace ya vencido.
        JwtService expiringService = new JwtService(SECRET, -1_000L, REFRESH_EXPIRATION, ISSUER);
        String expiredToken = expiringService.generateAccessToken(5L, "ana@academic.test", List.of("PARTICIPANT"));

        assertThatThrownBy(() -> jwtService.parseAndValidate(expiredToken))
                .isInstanceOf(InvalidTokenException.class)
                .extracting(exception -> ((InvalidTokenException) exception).getCode())
                .isEqualTo(ErrorCode.TOKEN_EXPIRED);
    }

    @Test
    @DisplayName("rechaza un token con emisor distinto")
    void rejectsTokenFromAnotherIssuer() {
        JwtService otherIssuer = new JwtService(SECRET, ACCESS_EXPIRATION, REFRESH_EXPIRATION, "otra-api");
        String token = otherIssuer.generateAccessToken(1L, "ana@academic.test", List.of("PARTICIPANT"));

        assertThatThrownBy(() -> jwtService.parseAndValidate(token))
                .isInstanceOf(InvalidTokenException.class);
    }

    private static String base64(String raw) {
        return Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }
}
