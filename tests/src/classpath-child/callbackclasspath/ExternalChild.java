package callbackclasspath;

import io.github.jdkbuilder.Buildable;

@Buildable(
        builderClassName = "ExternalChildDraft",
        builderMethod = "start",
        buildMethod = "finish"
)
public record ExternalChild(String value) {
}
