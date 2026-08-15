package testcases;

import io.github.jdkbuilder.Buildable;

@Buildable(
        builderClassName = "CustomChildDraft",
        builderPackage = "testcases.generated",
        builderMethod = "start",
        buildMethod = "finish"
)
public record CustomCallbackChild(String value) {
}
