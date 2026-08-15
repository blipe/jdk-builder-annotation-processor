package testcases;

import io.github.jdkbuilder.Buildable;

import java.util.List;

@Buildable(
        builderClassName = "OrderDraft",
        builderPackage = "testcases.generated",
        setterPrefix = "with",
        builderMethod = "newDraft",
        buildMethod = "create",
        fromMethod = "copyOf",
        toBuilderMethod = "edit",
        copyFromMethod = "load"
)
public record CustomModel(String name, List<String> items) {
}
