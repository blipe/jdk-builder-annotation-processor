package crossconstruction;

import io.github.jdkbuilder.Buildable;
import io.github.jdkbuilder.BuilderFactory;

@Buildable
public interface FactoryContract {
    String value();

    @BuilderFactory
    static FactoryContract create(String value) {
        return new Implementation("contract:" + value);
    }

    final class Implementation implements FactoryContract {
        private final String value;

        private Implementation(String value) {
            this.value = value;
        }

        @Override
        public String value() {
            return value;
        }
    }
}
