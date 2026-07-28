package ec.edu.ups.academicevents.sessions.validation;

import ec.edu.ups.academicevents.sessions.dto.SessionRequest;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class SessionDatesValidator implements ConstraintValidator<ValidSessionDates, SessionRequest> {

    @Override
    public boolean isValid(SessionRequest request, ConstraintValidatorContext context) {
        if (request == null || request.startAt() == null || request.endAt() == null) {
            return true;
        }

        if (request.startAt().isBefore(request.endAt())) {
            return true;
        }

        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate("La fecha de fin debe ser posterior a la fecha de inicio.")
                .addPropertyNode("endAt")
                .addConstraintViolation();

        return false;
    }
}
