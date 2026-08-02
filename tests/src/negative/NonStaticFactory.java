package negative;

import io.github.jdkbuilder.Buildable;
import io.github.jdkbuilder.BuilderFactory;

@Buildable
public final class NonStaticFactory {
    private NonStaticFactory(String value) {
    }

    @BuilderFactory
    public NonStaticFactory create(String value) {
        return new NonStaticFactory(value);
    }

    public String value() {
        return "";
    }
}
