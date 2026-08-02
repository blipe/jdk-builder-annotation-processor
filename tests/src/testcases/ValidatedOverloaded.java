package testcases;

import io.github.jdkbuilder.Buildable;
import io.github.jdkbuilder.BuilderConstructor;
import io.github.jdkbuilder.JakartaValidationMode;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Buildable(jakartaValidation = JakartaValidationMode.REQUIRED)
public final class ValidatedOverloaded {
    private final int count;
    private final String[] names;

    public ValidatedOverloaded() {
        this(0, new String[0]);
    }

    @BuilderConstructor
    public ValidatedOverloaded(
            int count,
            @NotNull @Size(min = 1) String[] names
    ) {
        this.count = count;
        this.names = names.clone();
    }

    public int count() {
        return count;
    }

    public String[] names() {
        return names.clone();
    }
}
