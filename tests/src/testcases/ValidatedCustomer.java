package testcases;

import io.github.jdkbuilder.Buildable;
import io.github.jdkbuilder.JakartaValidationMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

@Buildable(jakartaValidation = JakartaValidationMode.OPTIONAL)
public record ValidatedCustomer(
        @NotBlank(groups = StrictValidation.class) String name,
        @NotNull @Valid Address address,
        @Size(min = 1) List<@NotBlank String> tags
) {
    public record Address(@NotBlank String city) {
    }
}
