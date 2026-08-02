package negative;

import io.github.jdkbuilder.Buildable;

@Buildable
record CallbackBuilderFieldCollision(
        CallbackChild child,
        String child$builder
) {
}

@Buildable
record CallbackChild(String value) {
}
