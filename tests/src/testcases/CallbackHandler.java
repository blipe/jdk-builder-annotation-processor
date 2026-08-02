package testcases;

import io.github.jdkbuilder.Buildable;
import io.github.jdkbuilder.BuilderFactory;

@Buildable(generateFrom = false)
@FunctionalInterface
public interface CallbackHandler {
    String apply(String value);

    @BuilderFactory
    static CallbackHandler create(String prefix) {
        return value -> prefix + value;
    }
}
