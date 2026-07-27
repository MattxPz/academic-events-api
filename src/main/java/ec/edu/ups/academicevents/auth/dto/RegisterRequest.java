package ec.edu.ups.academicevents.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Size(max = 80)
        @Schema(example = "Ana")
        String firstName,

        @NotBlank @Size(max = 80)
        @Schema(example = "Torres")
        String lastName,

        @NotBlank @Email @Size(max = 160)
        @Schema(example = "estudiante@ups.edu.ec")
        String email,

        @NotBlank @Size(min = 8, max = 100)
        @Schema(example = "Password123!")
        String password) {
}
