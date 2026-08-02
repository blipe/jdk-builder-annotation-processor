package negative;

import io.github.jdkbuilder.Buildable;
import io.github.jdkbuilder.BuilderFactory;

@Buildable(generateFrom = false)
public final class MultipleFactories {
    @BuilderFactory
    public static MultipleFactories first(String value) {
        return new MultipleFactories();
    }

    @BuilderFactory
    public static MultipleFactories second(int value) {
        return new MultipleFactories();
    }
}
