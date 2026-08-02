package testcases;

import io.github.jdkbuilder.Buildable;
import io.github.jdkbuilder.JakartaValidationMode;
import io.github.jdkbuilder.BuilderRequired;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

@Buildable(jakartaValidation = JakartaValidationMode.REQUIRED)
public record RequiredCallbackParent(@BuilderRequired @NotNull @Valid RequiredCallbackChild child) {
}
