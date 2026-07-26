package ec.edu.ups.academicevents.categories.dto;

import java.time.Instant;

public record CategoryResponse(
        Long id,
        String name,
        String description,
        Boolean active,
        Instant createdAt,
        Instant updatedAt) {
}
