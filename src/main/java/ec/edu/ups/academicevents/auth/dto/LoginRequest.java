package ec.edu.ups.academicevents.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank @Email
        @Schema(example = "estudiante@ups.edu.ec")
        String email,

        @NotBlank
        @Schema(example = "Password123!")
        String password) {
}
