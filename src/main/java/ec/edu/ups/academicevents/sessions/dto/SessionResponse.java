package ec.edu.ups.academicevents.sessions.dto;

import java.time.Instant;

public record SessionResponse(
        Long id,
        Long eventId,
        String title,
        String description,
        Instant startAt,
        Instant endAt,
        String location,
        String virtualUrl,
        Instant createdAt,
        Instant updatedAt) {
}
