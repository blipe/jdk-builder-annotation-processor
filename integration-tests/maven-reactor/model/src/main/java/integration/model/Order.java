package integration.model;

import io.github.jdkbuilder.Buildable;
import io.github.jdkbuilder.BuilderAdder;
import io.github.jdkbuilder.JakartaValidationMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.groups.ConvertGroup;
import jakarta.validation.groups.Default;

import java.util.List;
import java.util.Map;

@Buildable(jakartaValidation = JakartaValidationMode.REQUIRED)
public record Order(
        @NotBlank String id,
        @Valid @ConvertGroup(from = Default.class, to = FullChecks.class) Address address,
        @Size(min = 1) @BuilderAdder("line") List<@Valid Line> lines,
        @BuilderAdder("indexedLine") Map<@NotBlank String, @Valid Line> indexedLines
) {
}
