package testcases;

import io.github.jdkbuilder.Buildable;
import io.github.jdkbuilder.BuilderFactory;

@Buildable
public record FactoryRecord(String value) {
    @BuilderFactory
    public static FactoryRecord normalized(String value) {
        return new FactoryRecord(value == null ? null : value.trim());
    }
}
