package testcases;

import io.github.jdkbuilder.Buildable;
import io.github.jdkbuilder.BuilderAccess;

@Buildable(
        builderClassName = "LocalDraft",
        builderMethod = "start",
        buildMethod = "finish",
        setterPrefix = "set",
        access = BuilderAccess.PACKAGE_PRIVATE
)
record LocalModel(String value) {
}
