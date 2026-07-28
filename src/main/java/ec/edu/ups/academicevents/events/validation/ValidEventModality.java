package ec.edu.ups.academicevents.events.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Constraint(validatedBy = EventModalityValidator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidEventModality {

    String message() default "La modalidad y los datos de ubicación no son consistentes.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
