package ec.edu.ups.academicevents.auth.service;

import ec.edu.ups.academicevents.auth.dto.AuthUserResponse;
import ec.edu.ups.academicevents.auth.dto.LoginRequest;
import ec.edu.ups.academicevents.auth.dto.RefreshRequest;
import ec.edu.ups.academicevents.auth.dto.RegisterRequest;
import ec.edu.ups.academicevents.auth.dto.TokenResponse;
import ec.edu.ups.academicevents.auth.entity.RefreshToken;
import ec.edu.ups.academicevents.auth.repository.RefreshTokenRepository;
import ec.edu.ups.academicevents.roles.entity.Role;
import ec.edu.ups.academicevents.roles.repository.RoleRepository;
import ec.edu.ups.academicevents.shared.exception.DuplicateResourceException;
import ec.edu.ups.academicevents.shared.exception.ErrorCode;
import ec.edu.ups.academicevents.shared.exception.InvalidTokenException;
import ec.edu.ups.academicevents.shared.exception.ResourceNotFoundException;
import ec.edu.ups.academicevents.shared.security.JwtService;
import ec.edu.ups.academicevents.shared.security.SecurityUtils;
import ec.edu.ups.academicevents.users.entity.User;
import ec.edu.ups.academicevents.users.entity.UserRole;
import ec.edu.ups.academicevents.users.entity.UserRoleId;
import ec.edu.ups.academicevents.users.repository.UserRepository;
import ec.edu.ups.academicevents.users.repository.UserRoleRepository;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String PARTICIPANT_ROLE = "PARTICIPANT";
    private static final String REFRESH_TOKEN_TYPE = "refresh";
    private static final String CLAIM_TYPE = "type";
    private static final String GENERIC_CREDENTIALS_MESSAGE = "Credenciales inválidas";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final SecurityUtils securityUtils;

    @Value("${app.jwt.access-expiration}")
    private long accessExpirationMillis;

    @Value("${app.jwt.refresh-expiration}")
    private long refreshExpirationMillis;

    @Override
    @Transactional
    public AuthUserResponse register(RegisterRequest request, String ip) {
        String normalizedEmail = request.email().toLowerCase();

        if (userRepository.findByEmail(normalizedEmail).isPresent()) {
            throw new DuplicateResourceException(ErrorCode.DUPLICATE_RESOURCE, "El correo ya está registrado.");
        }

        User user = User.builder()
                .firstName(request.firstName())
                .lastName(request.lastName())
                .email(normalizedEmail)
                .passwordHash(passwordEncoder.encode(request.password()))
                .status(STATUS_ACTIVE)
                .build();
        user = userRepository.save(user);

        Role participantRole = roleRepository.findByName(PARTICIPANT_ROLE)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.RESOURCE_NOT_FOUND, "El rol PARTICIPANT no está configurado."));

        UserRole userRole = UserRole.builder()
                .id(new UserRoleId(user.getId(), participantRole.getId()))
                .user(user)
                .role(participantRole)
                .build();
        userRoleRepository.save(userRole);

        return new AuthUserResponse(
                user.getId(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                user.getStatus(),
                List.of(PARTICIPANT_ROLE));
    }

    @Override
    @Transactional
    public TokenResponse login(LoginRequest request, String ip) {
        User user = userRepository.findByEmail(request.email().toLowerCase())
                .orElseThrow(() -> new BadCredentialsException(GENERIC_CREDENTIALS_MESSAGE));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadCredentialsException(GENERIC_CREDENTIALS_MESSAGE);
        }

        if (!STATUS_ACTIVE.equals(user.getStatus())) {
            throw new BadCredentialsException(GENERIC_CREDENTIALS_MESSAGE);
        }

        List<String> roles = userRoleRepository.findRoleNamesByUserId(user.getId());

        return issueTokenPair(user, roles, ip);
    }

    @Override
    @Transactional
    public TokenResponse refresh(RefreshRequest request, String ip) {
        Claims claims = jwtService.parseAndValidate(request.refreshToken());

        if (!REFRESH_TOKEN_TYPE.equals(claims.get(CLAIM_TYPE, String.class))) {
            throw new InvalidTokenException(ErrorCode.INVALID_TOKEN, "El token proporcionado no es un refresh token.");
        }

        UUID tokenId = UUID.fromString(jwtService.extractJti(claims));

        RefreshToken currentToken = refreshTokenRepository.findByTokenId(tokenId)
                .orElseThrow(() -> new InvalidTokenException(ErrorCode.INVALID_TOKEN, "El refresh token no es válido."));

        if (currentToken.getRevokedAt() != null) {
            throw new InvalidTokenException(ErrorCode.INVALID_TOKEN, "El refresh token ya fue revocado.");
        }

        User user = currentToken.getUser();
        List<String> roles = userRoleRepository.findRoleNamesByUserId(user.getId());

        UUID newTokenId = UUID.randomUUID();
        currentToken.setRevokedAt(Instant.now());
        currentToken.setReplacedByTokenId(newTokenId);
        refreshTokenRepository.save(currentToken);

        return issueTokenPair(user, roles, newTokenId, ip);
    }

    @Override
    @Transactional
    public void logout(String refreshToken) {
        try {
            Claims claims = jwtService.parseAndValidate(refreshToken);
            UUID tokenId = UUID.fromString(jwtService.extractJti(claims));

            refreshTokenRepository.findByTokenId(tokenId).ifPresent(token -> {
                if (token.getRevokedAt() == null) {
                    token.setRevokedAt(Instant.now());
                    refreshTokenRepository.save(token);
                }
            });
        } catch (InvalidTokenException ex) {
            // El refresh token ya es inválido o expiró; el logout es idempotente.
        }
    }

    @Override
    @Transactional(readOnly = true)
    public AuthUserResponse me() {
        Long userId = securityUtils.currentUserId();

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.RESOURCE_NOT_FOUND, "Usuario no encontrado."));

        List<String> roles = userRoleRepository.findRoleNamesByUserId(userId);

        return new AuthUserResponse(
                user.getId(), user.getFirstName(), user.getLastName(), user.getEmail(), user.getStatus(), roles);
    }

    private TokenResponse issueTokenPair(User user, List<String> roles, String ip) {
        return issueTokenPair(user, roles, UUID.randomUUID(), ip);
    }

    private TokenResponse issueTokenPair(User user, List<String> roles, UUID tokenId, String ip) {
        String accessToken = jwtService.generateAccessToken(user.getId(), user.getEmail(), roles);
        String refreshToken = jwtService.generateRefreshToken(user.getId(), tokenId);

        RefreshToken refreshTokenEntity = RefreshToken.builder()
                .tokenId(tokenId)
                .user(user)
                .tokenHash(hash(refreshToken))
                .expiresAt(Instant.now().plusMillis(refreshExpirationMillis))
                .createdByIp(ip)
                .build();
        refreshTokenRepository.save(refreshTokenEntity);

        return new TokenResponse(accessToken, refreshToken, "Bearer", accessExpirationMillis / 1000);
    }

    private String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 no está disponible en esta JVM.", ex);
        }
    }
}
