package ec.edu.ups.academicevents.events.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record EventStatusRequest(
        @NotBlank(message = "El estado es obligatorio.")
        @Pattern(regexp = "DRAFT|PUBLISHED|FINISHED|CANCELLED",
                message = "El estado debe ser DRAFT, PUBLISHED, FINISHED o CANCELLED.")
        String status) {
}
