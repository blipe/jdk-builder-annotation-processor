package negative;

import io.github.jdkbuilder.Buildable;
import io.github.jdkbuilder.JakartaValidationMode;

@Buildable(jakartaValidation = JakartaValidationMode.OPTIONAL)
record ValidationReservedFieldCollision(
        String $JDK_BUILDER_VALIDATION_CONSTRUCTOR
) {
}
