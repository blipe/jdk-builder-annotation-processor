package testcases;

import io.github.jdkbuilder.Buildable;
import io.github.jdkbuilder.BuilderFactory;

import java.io.IOException;

@Buildable
public final class FactoryChecked {
    private final String value;

    private FactoryChecked(String value) {
        this.value = value;
    }

    @BuilderFactory
    public static FactoryChecked open(String value) throws IOException {
        if (value.equals("fail")) {
            throw new IOException("factory failure");
        }
        return new FactoryChecked(value);
    }

    public String value() {
        return value;
    }
}
