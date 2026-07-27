package ec.edu.ups.academicevents.auth.controller;

import ec.edu.ups.academicevents.auth.dto.AuthUserResponse;
import ec.edu.ups.academicevents.auth.dto.LoginRequest;
import ec.edu.ups.academicevents.auth.dto.RefreshRequest;
import ec.edu.ups.academicevents.auth.dto.RegisterRequest;
import ec.edu.ups.academicevents.auth.dto.TokenResponse;
import ec.edu.ups.academicevents.auth.service.AuthService;
import ec.edu.ups.academicevents.shared.exception.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Autenticación", description = "Registro, inicio de sesión y gestión de tokens JWT")
public class AuthController {

    private static final String FORWARDED_FOR_HEADER = "X-Forwarded-For";

    private final AuthService authService;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Registrar un nuevo usuario",
            description = "Crea una cuenta con rol PARTICIPANT por defecto.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Usuario creado correctamente",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = AuthUserResponse.class))),
            @ApiResponse(responseCode = "400", description = "Datos de entrada inválidos",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "El correo ya está registrado",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "429", description = "Se excedió el límite de registros por hora",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public AuthUserResponse register(@Valid @RequestBody RegisterRequest request, HttpServletRequest httpRequest) {
        return authService.register(request, clientIp(httpRequest));
    }

    @PostMapping("/login")
    @Operation(summary = "Iniciar sesión",
            description = "Valida las credenciales y emite un par de tokens de acceso y refresco.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Autenticación exitosa",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = TokenResponse.class))),
            @ApiResponse(responseCode = "400", description = "Datos de entrada inválidos",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Credenciales inválidas o cuenta bloqueada",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "429", description = "Se excedió el límite de intentos de login",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public TokenResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        return authService.login(request, clientIp(httpRequest));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Renovar tokens",
            description = "Intercambia un refresh token válido por un nuevo par de tokens.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tokens renovados correctamente",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = TokenResponse.class))),
            @ApiResponse(responseCode = "400", description = "Datos de entrada inválidos",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "El refresh token es inválido, expiró o ya fue revocado",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest request, HttpServletRequest httpRequest) {
        return authService.refresh(request, clientIp(httpRequest));
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Cerrar sesión",
            description = "Revoca el refresh token indicado. La operación es idempotente.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Sesión cerrada correctamente"),
            @ApiResponse(responseCode = "400", description = "Datos de entrada inválidos",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public void logout(@Valid @RequestBody RefreshRequest request) {
        authService.logout(request.refreshToken());
    }

    @GetMapping("/me")
    @Operation(summary = "Obtener el usuario autenticado",
            description = "Devuelve los datos del usuario asociado al token de acceso enviado.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Datos del usuario autenticado",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = AuthUserResponse.class))),
            @ApiResponse(responseCode = "401", description = "No autenticado o token inválido",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "El usuario del token ya no existe",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public AuthUserResponse me() {
        return authService.me();
    }

    private String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader(FORWARDED_FOR_HEADER);
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
