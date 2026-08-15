package negative;

import io.github.jdkbuilder.Buildable;

@Buildable(generateFrom = false)
public final class PrivateConstructor {
    private PrivateConstructor(String value) {
    }
}
