package testcases;

import io.github.jdkbuilder.Buildable;
import io.github.jdkbuilder.JakartaValidationMode;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

@Buildable(jakartaValidation = JakartaValidationMode.OPTIONAL)
public record AnnotatedTypes<T extends @TypeUseMarker Comparable<@TypeUseMarker T>>(
        @NotBlank String name,
        List<@NotBlank String> tags,
        @TypeUseMarker T value,
        String @TypeUseMarker [] aliases,
        List<? extends @TypeUseMarker Number> numbers
) {
}
