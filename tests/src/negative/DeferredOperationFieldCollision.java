package negative;

import io.github.jdkbuilder.Buildable;
import io.github.jdkbuilder.BuilderAdder;

import java.util.List;

@Buildable
record DeferredOperationFieldCollision(
        @BuilderAdder("child") List<DeferredOperationChild> children,
        String $jdkBuilder$ops0
) {
}

@Buildable
record DeferredOperationChild(String value) {
}
