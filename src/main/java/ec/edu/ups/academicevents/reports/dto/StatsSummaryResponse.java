package ec.edu.ups.academicevents.reports.dto;

import java.time.Instant;
import java.util.Map;

/**
 * Resumen estadístico de un rango de fechas.
 *
 * <p>Los eventos se filtran por su fecha de inicio y las inscripciones por su fecha de
 * solicitud. El alcance es {@code ALL} para administradores y {@code ORGANIZER} cuando
 * las cifras se limitan a los eventos del organizador autenticado.
 */
public record StatsSummaryResponse(
        Instant from,
        Instant to,
        String scope,
        long totalEvents,
        Map<String, Long> eventsByStatus,
        long totalRegistrations,
        Map<String, Long> registrationsByStatus,
        long uniqueParticipants,
        double averageOccupancyPercentage) {
}
