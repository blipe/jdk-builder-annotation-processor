package jakarta.validation.executable;

import jakarta.validation.ConstraintViolation;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Set;

/** Test-only signature-compatible Jakarta Validation 3.1 executable API. */
public interface ExecutableValidator {
    <T> Set<ConstraintViolation<T>> validateParameters(
            T object,
            Method method,
            Object[] parameterValues,
            Class<?>... groups
    );

    <T> Set<ConstraintViolation<T>> validateReturnValue(
            T object,
            Method method,
            Object returnValue,
            Class<?>... groups
    );

    <T> Set<ConstraintViolation<T>> validateConstructorParameters(
            Constructor<? extends T> constructor,
            Object[] parameterValues,
            Class<?>... groups
    );

    <T> Set<ConstraintViolation<T>> validateConstructorReturnValue(
            Constructor<? extends T> constructor,
            T createdObject,
            Class<?>... groups
    );
}
