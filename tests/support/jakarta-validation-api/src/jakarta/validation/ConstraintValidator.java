package jakarta.validation;

public interface ConstraintValidator<A extends java.lang.annotation.Annotation, T> {
    default void initialize(A constraintAnnotation) {
    }

    boolean isValid(T value, ConstraintValidatorContext context);
}
