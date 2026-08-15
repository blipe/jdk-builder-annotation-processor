package integration.model;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Target(ElementType.CONSTRUCTOR)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ValidRangeValidator.class)
public @interface ValidRange {
    String message() default "start must be less than or equal to end";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
