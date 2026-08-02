package testcases;

import io.github.jdkbuilder.Buildable;
import io.github.jdkbuilder.JakartaValidationMode;
import jakarta.validation.constraints.NotBlank;

@Buildable(jakartaValidation = JakartaValidationMode.REQUIRED)
public record RequiredCollectionChild(@NotBlank String value) {
}
