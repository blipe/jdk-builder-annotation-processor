package integration.model;

import io.github.jdkbuilder.Buildable;
import io.github.jdkbuilder.BuilderFactory;
import io.github.jdkbuilder.JakartaValidationMode;
import jakarta.validation.constraints.NotBlank;

@Buildable(jakartaValidation = JakartaValidationMode.REQUIRED)
public record FactoryToken(@NotBlank String value) {
    @BuilderFactory
    public static FactoryToken create(String value) {
        return new FactoryToken(value);
    }
}
