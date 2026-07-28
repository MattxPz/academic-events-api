package ec.edu.ups.academicevents.sessions.dto;

import ec.edu.ups.academicevents.sessions.validation.ValidSessionDates;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

@ValidSessionDates
public record SessionRequest(
        @NotBlank(message = "El título es obligatorio.")
        @Size(max = 160, message = "El título no puede superar los 160 caracteres.")
        String title,

        String description,

        @NotNull(message = "La fecha de inicio es obligatoria.")
        Instant startAt,

        @NotNull(message = "La fecha de fin es obligatoria.")
        Instant endAt,

        @Size(max = 200, message = "El lugar no puede superar los 200 caracteres.")
        String location,

        @Size(max = 500, message = "El enlace virtual no puede superar los 500 caracteres.")
        String virtualUrl) {
}
