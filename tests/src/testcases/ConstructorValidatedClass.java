package testcases;

import io.github.jdkbuilder.Buildable;
import io.github.jdkbuilder.JakartaValidationMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Buildable(jakartaValidation = JakartaValidationMode.REQUIRED)
public final class ConstructorValidatedClass {
    private static int constructionCount;
    private final String value;

    @NotNull
    public ConstructorValidatedClass(@NotBlank String value) {
        constructionCount++;
        this.value = value;
    }

    @NotBlank
    public String value() {
        return value;
    }

    static void resetConstructionCount() {
        constructionCount = 0;
    }

    static int constructionCount() {
        return constructionCount;
    }
}
