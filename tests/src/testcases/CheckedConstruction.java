package testcases;

import io.github.jdkbuilder.Buildable;

import java.io.IOException;

@Buildable(generateFrom = false)
public final class CheckedConstruction {
    private final String value;

    public CheckedConstruction(String value) throws IOException {
        if (value == null) {
            throw new IOException("value");
        }
        this.value = value;
    }

    public String value() {
        return value;
    }
}
