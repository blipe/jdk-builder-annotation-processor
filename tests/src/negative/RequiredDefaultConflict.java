package negative;

import io.github.jdkbuilder.Buildable;
import io.github.jdkbuilder.BuilderDefault;
import io.github.jdkbuilder.BuilderRequired;

@Buildable
public record RequiredDefaultConflict(
        @BuilderRequired @BuilderDefault("defaultValue") String value
) {
    public static String defaultValue() {
        return "x";
    }
}
