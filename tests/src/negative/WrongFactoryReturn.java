package negative;

import io.github.jdkbuilder.Buildable;
import io.github.jdkbuilder.BuilderFactory;

@Buildable(generateFrom = false)
public final class WrongFactoryReturn {
    @BuilderFactory
    public static String create(String value) {
        return value;
    }
}
