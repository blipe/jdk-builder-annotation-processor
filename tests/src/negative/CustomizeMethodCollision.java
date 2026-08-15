package negative;

import io.github.jdkbuilder.Buildable;

import java.util.function.Consumer;

@Buildable(customizeMethod = "hook")
record CustomizeMethodCollision(Consumer<String> hook) {
}
