package ec.edu.ups.academicevents.registrations.controller;

import ec.edu.ups.academicevents.registrations.dto.RegistrationRequest;
import ec.edu.ups.academicevents.registrations.dto.RegistrationResponse;
import ec.edu.ups.academicevents.registrations.dto.RegistrationStatusRequest;
import ec.edu.ups.academicevents.registrations.service.RegistrationService;
import ec.edu.ups.academicevents.shared.exception.ApiErrorResponse;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/registrations")
@RequiredArgsConstructor
@Tag(name = "Inscripciones", description = "Solicitud, seguimiento y gestión de inscripciones a eventos")
public class RegistrationController {

    private final RegistrationService registrationService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('PARTICIPANT')")
    @Operation(summary = "Solicitar una inscripción",
            description = "Crea una inscripción en estado PENDING para el participante autenticado. "
                    + "El cupo solo se descuenta cuando el organizador confirma la inscripción.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Inscripción creada correctamente",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = RegistrationResponse.class))),
            @ApiResponse(responseCode = "400", description = "Datos de entrada inválidos",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "El usuario autenticado no tiene rol PARTICIPANT",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "El evento no existe",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "El participante ya está inscrito o el evento no tiene cupos",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "El evento no admite inscripciones en este momento",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public RegistrationResponse create(@Valid @RequestBody RegistrationRequest request) {
        return registrationService.create(request);
    }

    @GetMapping("/me")
    @Operation(summary = "Listar mis inscripciones",
            description = "Devuelve de forma paginada las inscripciones del usuario autenticado, "
                    + "con filtro opcional por estado.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Página de inscripciones del usuario autenticado"),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public Page<RegistrationResponse> findMine(
            @RequestParam(required = false) String status,
            Pageable pageable) {
        return registrationService.findMine(status, pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener una inscripción por id",
            description = "Visible para el participante dueño de la inscripción, "
                    + "el organizador del evento o un administrador.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Inscripción encontrada",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = RegistrationResponse.class))),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "El usuario autenticado no puede ver esta inscripción",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "La inscripción no existe",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public RegistrationResponse findById(@PathVariable Long id) {
        return registrationService.findById(id);
    }

    @PatchMapping("/{id}/cancel")
    @Operation(summary = "Cancelar una inscripción",
            description = "Solo el participante dueño o un administrador. Si la inscripción estaba CONFIRMED "
                    + "se devuelve el cupo al evento.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Inscripción cancelada",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = RegistrationResponse.class))),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "La inscripción pertenece a otro participante",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "La inscripción no existe",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Otra operación modificó el recurso simultáneamente",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "La inscripción no puede cancelarse en su estado actual "
                    + "o el evento ya inició",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public RegistrationResponse cancel(@PathVariable Long id) {
        return registrationService.cancel(id);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ORGANIZER','ADMIN')")
    @Operation(summary = "Confirmar o rechazar una inscripción",
            description = "Solo el organizador dueño del evento o un administrador. "
                    + "La confirmación descuenta un cupo del evento.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Estado actualizado",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = RegistrationResponse.class))),
            @ApiResponse(responseCode = "400", description = "Estado inválido",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "El usuario autenticado no gestiona este evento",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "La inscripción no existe",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "El evento se quedó sin cupos o hubo un conflicto de concurrencia",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "Transición de estado no permitida",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public RegistrationResponse updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody RegistrationStatusRequest request) {
        return registrationService.updateStatus(id, request);
    }

    @GetMapping("/event/{eventId}")
    @Operation(summary = "Listar las inscripciones de un evento",
            description = "Devuelve de forma paginada las inscripciones del evento indicado, con filtro "
                    + "opcional por estado. Solo el organizador dueño del evento o un administrador.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Página de inscripciones del evento"),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "El usuario autenticado no es el organizador "
                    + "dueño del evento ni un administrador",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public Page<RegistrationResponse> findByEvent(
            @PathVariable Long eventId,
            @RequestParam(required = false) String status,
            Pageable pageable) {
        return registrationService.findByEvent(eventId, status, pageable);
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Listar todas las inscripciones",
            description = "Listado administrativo paginado con filtros opcionales por evento y por estado.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Página de inscripciones"),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "El usuario autenticado no tiene rol ADMIN",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public Page<RegistrationResponse> findAll(
            @RequestParam(required = false) Long eventId,
            @RequestParam(required = false) String status,
            Pageable pageable) {
        return registrationService.findAll(eventId, status, pageable);
    }
}
