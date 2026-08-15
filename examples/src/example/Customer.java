package example;

import io.github.jdkbuilder.Buildable;
import io.github.jdkbuilder.BuilderAdder;
import io.github.jdkbuilder.BuilderRequired;

import java.util.List;

@Buildable
public record Customer(
        @BuilderRequired String id,
        String name,
        @BuilderAdder("role") List<String> roles
) {
}
