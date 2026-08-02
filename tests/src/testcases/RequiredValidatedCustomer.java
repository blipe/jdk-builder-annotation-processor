package testcases;

import io.github.jdkbuilder.Buildable;
import io.github.jdkbuilder.JakartaValidationMode;
import io.github.jdkbuilder.BuilderRequired;
import jakarta.validation.constraints.NotBlank;

@Buildable(jakartaValidation = JakartaValidationMode.REQUIRED)
public record RequiredValidatedCustomer(@BuilderRequired @NotBlank String name) {
}
