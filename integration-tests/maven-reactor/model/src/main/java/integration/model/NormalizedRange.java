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
@Constraint(validatedBy = NormalizedRangeValidator.class)
public @interface NormalizedRange {
    String message() default "range width must be even";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
