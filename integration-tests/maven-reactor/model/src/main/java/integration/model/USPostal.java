package integration.model;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = USPostalValidator.class)
public @interface USPostal {
    String message() default "Chicago postal code must begin with 606";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
