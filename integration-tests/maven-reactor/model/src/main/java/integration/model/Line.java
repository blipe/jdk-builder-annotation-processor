package integration.model;

import io.github.jdkbuilder.Buildable;
import io.github.jdkbuilder.JakartaValidationMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

@Buildable(jakartaValidation = JakartaValidationMode.REQUIRED)
public record Line(
        @NotBlank String sku,
        @Positive int quantity
) {
}
