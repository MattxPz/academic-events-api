package ec.edu.ups.academicevents.events.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Constraint(validatedBy = EventDatesValidator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidEventDates {

    String message() default "Las fechas del evento no son consistentes.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
