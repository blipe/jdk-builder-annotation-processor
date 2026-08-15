package testcases;

import io.github.jdkbuilder.Buildable;

public final class CollisionSafeNames {
    private CollisionSafeNames() {
    }

    @Buildable
    public static record Nested(String value) {
    }
}
