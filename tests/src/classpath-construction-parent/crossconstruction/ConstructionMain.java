package crossconstruction;

import java.io.IOException;

public final class ConstructionMain {
    private ConstructionMain() {
    }

    public static void main(String[] args) throws Exception {
        ConstructionParent value = ConstructionParentBuilder.of(parent -> parent
                .factoryUsing(child -> child.value("a"))
                .constructorUsing(child -> child.value("b"))
                .contractUsing(child -> child.value("c")));

        assert value.factory().value().equals("factory:a");
        assert value.constructor().value().equals("constructor:b");
        assert value.contract().value().equals("contract:c");

        try {
            ConstructionParentBuilder.of(parent -> parent
                    .factoryUsing(child -> child.value("fail")));
            throw new AssertionError("Expected checked factory exception");
        } catch (IOException expected) {
            assert expected.getMessage().equals("factory failure");
        }
    }
}
