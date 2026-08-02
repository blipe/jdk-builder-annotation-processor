package negative;

import io.github.jdkbuilder.Buildable;

@Buildable(builderPackage = "negative.generated", generateFrom = false)
public final class CrossPackageHiddenType {
    public CrossPackageHiddenType(HiddenType value) {
    }
}

final class HiddenType {
}
