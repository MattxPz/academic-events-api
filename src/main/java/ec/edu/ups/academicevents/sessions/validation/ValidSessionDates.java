package ec.edu.ups.academicevents.sessions.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Constraint(validatedBy = SessionDatesValidator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidSessionDates {

    String message() default "Las fechas de la sesión no son consistentes.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
