package negative;

import io.github.jdkbuilder.Buildable;

public class NonStaticInner {
    @Buildable
    public class Value {
        public Value(String name) {
        }

        public String name() {
            return "x";
        }
    }
}
