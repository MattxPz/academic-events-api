package ec.edu.ups.academicevents.users.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record UserRolesRequest(
        @NotEmpty(message = "Debe especificar al menos un rol.")
        List<Long> roleIds) {
}
