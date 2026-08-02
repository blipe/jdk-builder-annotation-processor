package integration.model;

import io.github.jdkbuilder.Buildable;
import io.github.jdkbuilder.JakartaValidationMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@USPostal
@Buildable(jakartaValidation = JakartaValidationMode.REQUIRED)
public record Address(
        @NotBlank(groups = Basic.class) String city,
        @Pattern(regexp = "\\d{5}", groups = Strict.class) String postalCode
) {
}
