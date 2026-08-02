package negative;

import io.github.jdkbuilder.Buildable;
import io.github.jdkbuilder.BuilderFactory;

@Buildable(generateFrom = false)
interface GenericFactoryMismatch<T> {
    @BuilderFactory
    static <U> GenericFactoryMismatch<U> create(U value) {
        return new GenericFactoryMismatch<>() { };
    }
}
