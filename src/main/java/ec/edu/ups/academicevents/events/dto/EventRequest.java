package ec.edu.ups.academicevents.events.dto;

import ec.edu.ups.academicevents.events.validation.ValidEventDates;
import ec.edu.ups.academicevents.events.validation.ValidEventModality;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;

@ValidEventModality
@ValidEventDates
public record EventRequest(
        @NotBlank(message = "El título es obligatorio.")
        @Size(max = 160, message = "El título no puede superar los 160 caracteres.")
        String title,

        @NotBlank(message = "La descripción es obligatoria.")
        String description,

        @NotBlank(message = "La modalidad es obligatoria.")
        @Pattern(regexp = "PRESENTIAL|VIRTUAL|HYBRID", message = "La modalidad debe ser PRESENTIAL, VIRTUAL o HYBRID.")
        String modality,

        @Size(max = 200, message = "El lugar no puede superar los 200 caracteres.")
        String location,

        @Size(max = 500, message = "El enlace virtual no puede superar los 500 caracteres.")
        String virtualUrl,

        @NotNull(message = "El cupo es obligatorio.")
        @Min(value = 1, message = "El cupo debe ser mayor a cero.")
        Integer capacity,

        @NotNull(message = "La fecha de inicio de inscripciones es obligatoria.")
        Instant registrationStartAt,

        @NotNull(message = "La fecha de fin de inscripciones es obligatoria.")
        Instant registrationEndAt,

        @NotNull(message = "La fecha de inicio del evento es obligatoria.")
        Instant startAt,

        @NotNull(message = "La fecha de fin del evento es obligatoria.")
        Instant endAt,

        @NotNull(message = "La categoría es obligatoria.")
        Long categoryId) {
}
