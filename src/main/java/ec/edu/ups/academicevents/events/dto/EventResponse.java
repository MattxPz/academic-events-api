package ec.edu.ups.academicevents.events.dto;

import java.time.Instant;

public record EventResponse(
        Long id,
        String title,
        String description,
        String modality,
        String location,
        String virtualUrl,
        Integer capacity,
        Integer availableCapacity,
        Instant registrationStartAt,
        Instant registrationEndAt,
        Instant startAt,
        Instant endAt,
        String status,
        Long organizerId,
        Long categoryId,
        String categoryName,
        Instant createdAt,
        Instant updatedAt) {
}
