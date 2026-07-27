package ec.edu.ups.academicevents.registrations.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record RegistrationStatusRequest(
        @NotBlank(message = "El estado es obligatorio.")
        @Pattern(regexp = "CONFIRMED|REJECTED", message = "El estado debe ser CONFIRMED o REJECTED.")
        String status) {
}
