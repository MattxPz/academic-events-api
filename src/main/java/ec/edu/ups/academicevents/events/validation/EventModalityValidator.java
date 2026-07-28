package ec.edu.ups.academicevents.events.validation;

import ec.edu.ups.academicevents.events.dto.EventRequest;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class EventModalityValidator implements ConstraintValidator<ValidEventModality, EventRequest> {

    @Override
    public boolean isValid(EventRequest request, ConstraintValidatorContext context) {
        if (request == null || request.modality() == null) {
            return true;
        }

        boolean hasLocation = request.location() != null && !request.location().isBlank();
        boolean hasVirtualUrl = request.virtualUrl() != null && !request.virtualUrl().isBlank();

        boolean valid = switch (request.modality()) {
            case "PRESENTIAL" -> hasLocation && !hasVirtualUrl;
            case "VIRTUAL" -> hasVirtualUrl && !hasLocation;
            case "HYBRID" -> hasLocation && hasVirtualUrl;
            default -> true;
        };

        if (valid) {
            return true;
        }

        context.disableDefaultConstraintViolation();

        switch (request.modality()) {
            case "PRESENTIAL" -> {
                if (!hasLocation) {
                    addViolation(context, "location", "El lugar es obligatorio para eventos presenciales.");
                }
                if (hasVirtualUrl) {
                    addViolation(context, "virtualUrl", "El enlace virtual no aplica para eventos presenciales.");
                }
            }
            case "VIRTUAL" -> {
                if (!hasVirtualUrl) {
                    addViolation(context, "virtualUrl", "El enlace virtual es obligatorio para eventos virtuales.");
                }
                if (hasLocation) {
                    addViolation(context, "location", "El lugar no aplica para eventos virtuales.");
                }
            }
            case "HYBRID" -> {
                if (!hasLocation) {
                    addViolation(context, "location", "El lugar es obligatorio para eventos híbridos.");
                }
                if (!hasVirtualUrl) {
                    addViolation(context, "virtualUrl", "El enlace virtual es obligatorio para eventos híbridos.");
                }
            }
            default -> {
            }
        }

        return false;
    }

    private void addViolation(ConstraintValidatorContext context, String field, String message) {
        context.buildConstraintViolationWithTemplate(message)
                .addPropertyNode(field)
                .addConstraintViolation();
    }
}
