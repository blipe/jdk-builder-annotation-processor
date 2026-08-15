package integration.model;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.constraintvalidation.SupportedValidationTarget;
import jakarta.validation.constraintvalidation.ValidationTarget;

@SupportedValidationTarget(ValidationTarget.PARAMETERS)
public final class ValidRangeValidator implements ConstraintValidator<ValidRange, Object[]> {
    @Override
    public boolean isValid(Object[] value, ConstraintValidatorContext context) {
        if (value == null || value.length != 2) {
            return true;
        }
        return ((Integer) value[0]) <= ((Integer) value[1]);
    }
}
