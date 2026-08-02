package integration.model;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public final class NormalizedRangeValidator implements ConstraintValidator<NormalizedRange, Range> {
    @Override
    public boolean isValid(Range value, ConstraintValidatorContext context) {
        return value == null || value.width() % 2 == 0;
    }
}
