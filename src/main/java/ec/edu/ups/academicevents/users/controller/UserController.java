package ec.edu.ups.academicevents.users.controller;

import ec.edu.ups.academicevents.shared.exception.ApiErrorResponse;
import ec.edu.ups.academicevents.users.dto.UserResponse;
import ec.edu.ups.academicevents.users.dto.UserRolesRequest;
import ec.edu.ups.academicevents.users.dto.UserStatusRequest;
import ec.edu.ups.academicevents.users.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Usuarios", description = "Administración de usuarios: consulta, estados y roles")
public class UserController {

    private final UserService userService;

    @GetMapping
    @Operation(summary = "Listar usuarios",
            description = "Búsqueda paginada de usuarios por nombre o correo, con filtro opcional por estado.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Página de usuarios"),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "El usuario autenticado no tiene rol ADMIN",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public Page<UserResponse> findAll(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String status,
            Pageable pageable) {
        return userService.findAll(q, status, pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener un usuario por id")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Usuario encontrado",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = UserResponse.class))),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "El usuario autenticado no tiene rol ADMIN",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "El usuario no existe",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public UserResponse findById(@PathVariable Long id) {
        return userService.findById(id);
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Cambiar el estado de un usuario",
            description = "Activa o bloquea la cuenta del usuario indicado.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Estado actualizado",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = UserResponse.class))),
            @ApiResponse(responseCode = "400", description = "Estado inválido",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "El usuario autenticado no tiene rol ADMIN",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "El usuario no existe",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public UserResponse updateStatus(@PathVariable Long id, @Valid @RequestBody UserStatusRequest request) {
        return userService.updateStatus(id, request);
    }

    @PutMapping("/{id}/roles")
    @Operation(summary = "Reemplazar los roles de un usuario",
            description = "Elimina los roles actuales del usuario y asigna los indicados.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Roles actualizados",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = UserResponse.class))),
            @ApiResponse(responseCode = "400", description = "La lista de roles está vacía o mal formada",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "El usuario autenticado no tiene rol ADMIN",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "El usuario o alguno de los roles no existe",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public UserResponse replaceRoles(@PathVariable Long id, @Valid @RequestBody UserRolesRequest request) {
        return userService.replaceRoles(id, request);
    }
}
