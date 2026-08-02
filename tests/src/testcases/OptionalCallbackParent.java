package testcases;

import io.github.jdkbuilder.Buildable;
import io.github.jdkbuilder.JakartaValidationMode;

@Buildable(jakartaValidation = JakartaValidationMode.OPTIONAL)
public record OptionalCallbackParent(OptionalCallbackChild child) {
}
