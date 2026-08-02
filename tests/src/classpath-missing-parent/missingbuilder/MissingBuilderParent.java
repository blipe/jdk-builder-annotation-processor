package missingbuilder;

import io.github.jdkbuilder.Buildable;

@Buildable
public record MissingBuilderParent(NoGeneratedChild child) {
}
