package negative;

import io.github.jdkbuilder.Buildable;

@Buildable(buildMethod = "toString")
record ObjectMethodCollision(String value) {
}
