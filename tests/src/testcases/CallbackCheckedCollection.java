package testcases;

import io.github.jdkbuilder.Buildable;
import io.github.jdkbuilder.BuilderAdder;

import java.util.List;
import java.util.Map;

@Buildable
public record CallbackCheckedCollection(
        @BuilderAdder("child") List<CallbackCheckedChild> children,
        @BuilderAdder("childByKey") Map<String, CallbackCheckedChild> childrenByKey
) {
}
