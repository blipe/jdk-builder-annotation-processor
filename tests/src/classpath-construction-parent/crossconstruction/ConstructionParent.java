package crossconstruction;

import io.github.jdkbuilder.Buildable;

@Buildable
public record ConstructionParent(
        FactoryChild factory,
        ConstructorChild constructor,
        FactoryContract contract
) {
}
