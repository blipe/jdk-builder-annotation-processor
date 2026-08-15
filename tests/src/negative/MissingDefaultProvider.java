package negative;

import io.github.jdkbuilder.Buildable;
import io.github.jdkbuilder.BuilderDefault;

@Buildable
public record MissingDefaultProvider(@BuilderDefault("missing") String value) {
}
