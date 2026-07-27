package ec.edu.ups.academicevents.reports.controller;

import ec.edu.ups.academicevents.reports.dto.ReportFile;
import ec.edu.ups.academicevents.reports.dto.StatsSummaryResponse;
import ec.edu.ups.academicevents.reports.service.ReportService;
import ec.edu.ups.academicevents.shared.exception.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequiredArgsConstructor
@Tag(name = "Reportes", description = "Descarga de listados, certificados y estadísticas")
public class ReportController {

    private static final MediaType XLSX_MEDIA_TYPE = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final ReportService reportService;

    @GetMapping("/api/reports/events/{eventId}/registrations.pdf")
    @Operation(summary = "Descargar el listado de inscritos en PDF",
            description = "Disponible para el organizador dueño del evento o un administrador.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Archivo PDF generado",
                    content = @Content(mediaType = MediaType.APPLICATION_PDF_VALUE)),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "El usuario autenticado no gestiona este evento",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "El evento no existe",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "429", description = "Se excedió el límite de reportes por minuto",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<byte[]> registrationsPdf(@PathVariable Long eventId) {
        return download(reportService.registrationsPdf(eventId), MediaType.APPLICATION_PDF);
    }

    @GetMapping("/api/reports/events/{eventId}/registrations.xlsx")
    @Operation(summary = "Descargar el listado de inscritos en Excel",
            description = "Disponible para el organizador dueño del evento o un administrador.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Archivo XLSX generado",
                    content = @Content(
                            mediaType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "El usuario autenticado no gestiona este evento",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "El evento no existe",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "429", description = "Se excedió el límite de reportes por minuto",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<byte[]> registrationsXlsx(@PathVariable Long eventId) {
        return download(reportService.registrationsXlsx(eventId), XLSX_MEDIA_TYPE);
    }

    /**
     * La guía exige esta ruta bajo {@code /api/registrations}, por eso se mapea completa
     * aquí en lugar de usar un prefijo a nivel de clase.
     */
    @GetMapping("/api/registrations/{id}/certificate.pdf")
    @Operation(summary = "Descargar el certificado de una inscripción",
            description = "Solo el participante dueño de la inscripción o un administrador, "
                    + "y únicamente si la inscripción está CONFIRMED.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Certificado PDF generado",
                    content = @Content(mediaType = MediaType.APPLICATION_PDF_VALUE)),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "La inscripción pertenece a otro participante",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "La inscripción no existe",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "La inscripción no está confirmada",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "429", description = "Se excedió el límite de reportes por minuto",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<byte[]> certificatePdf(@PathVariable Long id) {
        return download(reportService.certificatePdf(id), MediaType.APPLICATION_PDF);
    }

    @GetMapping("/api/reports/stats/summary")
    @PreAuthorize("hasAnyRole('ORGANIZER','ADMIN')")
    @Operation(summary = "Resumen estadístico",
            description = "Totales de eventos e inscripciones por estado, participantes únicos y ocupación media. "
                    + "Un administrador ve todo el sistema y un organizador solo sus eventos. "
                    + "Sin parámetros se usan los últimos 30 días.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Resumen calculado",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = StatsSummaryResponse.class))),
            @ApiResponse(responseCode = "400", description = "El rango de fechas es inválido",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "El usuario autenticado no es organizador ni administrador",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "429", description = "Se excedió el límite de reportes por minuto",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public StatsSummaryResponse statsSummary(
            @Parameter(description = "Inicio del rango en UTC (ISO-8601), por ejemplo 2026-07-01T00:00:00Z")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @Parameter(description = "Fin del rango en UTC (ISO-8601)")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return reportService.statsSummary(from, to);
    }

    private ResponseEntity<byte[]> download(ReportFile file, MediaType mediaType) {
        ContentDisposition contentDisposition = ContentDisposition.attachment()
                .filename(file.filename())
                .build();

        return ResponseEntity.ok()
                .contentType(mediaType)
                .contentLength(file.content().length)
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString())
                .body(file.content());
    }
}
