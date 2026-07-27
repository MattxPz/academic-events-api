package ec.edu.ups.academicevents.users.dto;

import java.time.Instant;
import java.util.List;

public record UserResponse(
        Long id,
        String firstName,
        String lastName,
        String email,
        String status,
        List<String> roles,
        Instant createdAt) {
}
