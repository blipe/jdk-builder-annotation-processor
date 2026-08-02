package testcases;

import io.github.jdkbuilder.Buildable;
import io.github.jdkbuilder.BuilderFactory;

@Buildable(generateFrom = false)
@FunctionalInterface
public interface GenericFactoryBox<T> {
    T value();

    @BuilderFactory
    static <T> GenericFactoryBox<T> create(T value) {
        return () -> value;
    }
}
