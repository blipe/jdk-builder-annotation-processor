package negative;

import io.github.jdkbuilder.Buildable;
import io.github.jdkbuilder.BuilderAdder;

import java.util.List;

@Buildable
record ErasedAdderCollision(
        @BuilderAdder("item") List<List<String>> strings,
        @BuilderAdder("item") List<List<Integer>> integers
) {
}
