package jakarta.validation;

/** Test-only signature-compatible subset used for compilation and proxy tests. */
public interface ConstraintViolation<T> {
    String getMessage();

    Path getPropertyPath();
}
