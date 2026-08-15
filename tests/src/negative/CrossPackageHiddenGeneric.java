package negative;

import io.github.jdkbuilder.Buildable;

import java.util.List;

@Buildable(builderPackage = "negative.generated", generateFrom = false)
public final class CrossPackageHiddenGeneric {
    public CrossPackageHiddenGeneric(List<HiddenGenericArgument> values) {
    }
}

final class HiddenGenericArgument {
}
