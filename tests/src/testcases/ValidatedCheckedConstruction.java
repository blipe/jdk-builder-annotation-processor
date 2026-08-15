package testcases;

import io.github.jdkbuilder.Buildable;
import io.github.jdkbuilder.JakartaValidationMode;
import jakarta.validation.constraints.NotBlank;

import java.io.IOException;

@Buildable(jakartaValidation = JakartaValidationMode.REQUIRED)
public final class ValidatedCheckedConstruction {
    private final String value;

    public ValidatedCheckedConstruction(@NotBlank String value) throws IOException {
        if (value.equals("io-failure")) {
            throw new IOException("construction failed");
        }
        this.value = value;
    }

    public String value() {
        return value;
    }
}
