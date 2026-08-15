package testcases;

import io.github.jdkbuilder.Buildable;
import io.github.jdkbuilder.BuilderFactory;

@Buildable
public interface FactoryContract {
    @BuilderFactory
    static FactoryContract create(String value) {
        return () -> value;
    }

    String value();
}
