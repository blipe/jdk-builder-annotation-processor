package testcases;

import io.github.jdkbuilder.Buildable;
import io.github.jdkbuilder.BuilderFactory;

@Buildable(generateFrom = false)
public final class NullFactoryProduct {
    private NullFactoryProduct() {
    }

    @BuilderFactory
    public static NullFactoryProduct create(String ignored) {
        return null;
    }
}
