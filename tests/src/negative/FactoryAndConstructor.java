package negative;

import io.github.jdkbuilder.Buildable;
import io.github.jdkbuilder.BuilderConstructor;
import io.github.jdkbuilder.BuilderFactory;

@Buildable(generateFrom = false)
public final class FactoryAndConstructor {
    @BuilderConstructor
    public FactoryAndConstructor(String value) {
    }

    @BuilderFactory
    public static FactoryAndConstructor create(String value) {
        return new FactoryAndConstructor(value);
    }
}
