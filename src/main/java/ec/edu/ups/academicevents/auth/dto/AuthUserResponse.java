package ec.edu.ups.academicevents.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record AuthUserResponse(
        @Schema(example = "1")
        Long id,

        @Schema(example = "Ana")
        String firstName,

        @Schema(example = "Torres")
        String lastName,

        @Schema(example = "estudiante@ups.edu.ec")
        String email,

        @Schema(example = "ACTIVE")
        String status,

        @Schema(example = "[\"PARTICIPANT\"]")
        List<String> roles) {
}
