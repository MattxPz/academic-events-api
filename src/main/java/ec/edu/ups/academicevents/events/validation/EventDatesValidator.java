package ec.edu.ups.academicevents.events.validation;

import ec.edu.ups.academicevents.events.dto.EventRequest;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class EventDatesValidator implements ConstraintValidator<ValidEventDates, EventRequest> {

    @Override
    public boolean isValid(EventRequest request, ConstraintValidatorContext context) {
        if (request == null
                || request.registrationStartAt() == null
                || request.registrationEndAt() == null
                || request.startAt() == null
                || request.endAt() == null) {
            return true;
        }

        boolean registrationOrderValid = request.registrationStartAt().isBefore(request.registrationEndAt());
        boolean registrationBeforeStartValid = !request.registrationEndAt().isAfter(request.startAt());
        boolean eventOrderValid = request.startAt().isBefore(request.endAt());

        if (registrationOrderValid && registrationBeforeStartValid && eventOrderValid) {
            return true;
        }

        context.disableDefaultConstraintViolation();

        if (!registrationOrderValid) {
            addViolation(context, "registrationEndAt",
                    "El fin de inscripciones debe ser posterior al inicio de inscripciones.");
        }
        if (!registrationBeforeStartValid) {
            addViolation(context, "startAt",
                    "El inicio del evento debe ser posterior o igual al fin de inscripciones.");
        }
        if (!eventOrderValid) {
            addViolation(context, "endAt", "El fin del evento debe ser posterior al inicio del evento.");
        }

        return false;
    }

    private void addViolation(ConstraintValidatorContext context, String field, String message) {
        context.buildConstraintViolationWithTemplate(message)
                .addPropertyNode(field)
                .addConstraintViolation();
    }
}
