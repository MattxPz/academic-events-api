package ec.edu.ups.academicevents.users.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record UserStatusRequest(
        @NotBlank(message = "El estado es obligatorio.")
        @Pattern(regexp = "ACTIVE|BLOCKED", message = "El estado debe ser ACTIVE o BLOCKED.")
        String status) {
}
