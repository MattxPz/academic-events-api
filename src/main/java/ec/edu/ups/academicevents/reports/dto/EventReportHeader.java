package ec.edu.ups.academicevents.reports.dto;

import java.time.Instant;

/** Datos del evento usados en la cabecera de los reportes. */
public record EventReportHeader(
        Long eventId,
        String title,
        String modality,
        Instant startAt,
        Instant endAt,
        Integer capacity,
        Integer availableCapacity,
        Long organizerId,
        String organizerFirstName,
        String organizerLastName) {

    public String organizerFullName() {
        return organizerFirstName + " " + organizerLastName;
    }

    /** Cupos ya consumidos por inscripciones confirmadas. */
    public int occupiedSeats() {
        if (capacity == null || availableCapacity == null) {
            return 0;
        }
        return capacity - availableCapacity;
    }
}
