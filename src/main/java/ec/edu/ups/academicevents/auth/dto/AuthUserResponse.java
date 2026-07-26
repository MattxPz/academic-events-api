package ec.edu.ups.academicevents.auth.dto;

import java.util.List;

public record AuthUserResponse(
        Long id,
        String firstName,
        String lastName,
        String email,
        String status,
        List<String> roles) {
}
