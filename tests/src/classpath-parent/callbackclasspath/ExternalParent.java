package callbackclasspath;

import io.github.jdkbuilder.Buildable;

@Buildable
public record ExternalParent(ExternalChild child) {
}
