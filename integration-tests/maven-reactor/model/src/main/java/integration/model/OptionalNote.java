package integration.model;

import io.github.jdkbuilder.Buildable;
import io.github.jdkbuilder.JakartaValidationMode;
import jakarta.validation.constraints.NotBlank;

@Buildable(jakartaValidation = JakartaValidationMode.OPTIONAL)
public record OptionalNote(@NotBlank String text) {
}
