package testcases;

import io.github.jdkbuilder.Buildable;
import io.github.jdkbuilder.JakartaValidationMode;
import jakarta.validation.constraints.NotNull;

@Buildable(jakartaValidation = JakartaValidationMode.REQUIRED)
public record ValidatedGeneric<T extends Comparable<T>>(@NotNull T value) {
}
