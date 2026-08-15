package negative;

import io.github.jdkbuilder.Buildable;
import io.github.jdkbuilder.JakartaValidationMode;

@Buildable(jakartaValidation = JakartaValidationMode.REQUIRED)
public record JakartaValidationApiMissing(String value) {
}
