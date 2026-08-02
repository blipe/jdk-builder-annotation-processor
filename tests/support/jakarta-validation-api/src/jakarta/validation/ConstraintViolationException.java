package jakarta.validation;

import java.util.LinkedHashSet;
import java.util.Set;

public class ConstraintViolationException extends ValidationException {
    private static final long serialVersionUID = 1L;
    private final transient Set<ConstraintViolation<?>> constraintViolations;

    public ConstraintViolationException(
            Set<? extends ConstraintViolation<?>> constraintViolations
    ) {
        this.constraintViolations = constraintViolations == null
                ? null
                : Set.copyOf(new LinkedHashSet<>(constraintViolations));
    }

    public ConstraintViolationException(
            String message,
            Set<? extends ConstraintViolation<?>> constraintViolations
    ) {
        super(message);
        this.constraintViolations = constraintViolations == null
                ? null
                : Set.copyOf(new LinkedHashSet<>(constraintViolations));
    }

    public Set<ConstraintViolation<?>> getConstraintViolations() {
        return constraintViolations;
    }
}
