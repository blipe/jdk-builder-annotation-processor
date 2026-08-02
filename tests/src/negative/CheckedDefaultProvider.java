package negative;

import io.github.jdkbuilder.Buildable;
import io.github.jdkbuilder.BuilderDefault;

import java.io.IOException;

@Buildable
public record CheckedDefaultProvider(@BuilderDefault("defaultValue") String value) {
    public static String defaultValue() throws IOException {
        return "x";
    }
}
