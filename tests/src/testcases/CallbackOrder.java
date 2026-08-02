package testcases;

import io.github.jdkbuilder.Buildable;
import io.github.jdkbuilder.BuilderAdder;

import java.util.List;
import java.util.Map;

@Buildable
public record CallbackOrder(
        String id,
        CallbackAddress address,
        @BuilderAdder("line") List<CallbackLine> lines,
        @BuilderAdder("indexedLine") Map<String, CallbackLine> indexedLines
) {
}
