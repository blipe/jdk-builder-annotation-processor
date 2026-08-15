package integration.model;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public final class USPostalValidator implements ConstraintValidator<USPostal, Address> {
    @Override
    public boolean isValid(Address value, ConstraintValidatorContext context) {
        if (value == null || value.city() == null || value.postalCode() == null) {
            return true;
        }
        return !"Chicago".equals(value.city()) || value.postalCode().startsWith("606");
    }
}
