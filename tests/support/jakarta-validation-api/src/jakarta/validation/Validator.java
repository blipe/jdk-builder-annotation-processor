package jakarta.validation;

import jakarta.validation.executable.ExecutableValidator;
import jakarta.validation.metadata.BeanDescriptor;

import java.util.Set;

/** Test-only signature-compatible subset of the Jakarta Validation 3.1 Validator API. */
public interface Validator {
    <T> Set<ConstraintViolation<T>> validate(T object, Class<?>... groups);

    <T> Set<ConstraintViolation<T>> validateProperty(
            T object,
            String propertyName,
            Class<?>... groups
    );

    <T> Set<ConstraintViolation<T>> validateValue(
            Class<T> beanType,
            String propertyName,
            Object value,
            Class<?>... groups
    );

    BeanDescriptor getConstraintsForClass(Class<?> clazz);

    <T> T unwrap(Class<T> type);

    ExecutableValidator forExecutables();
}
