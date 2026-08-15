package testcases;

import io.github.jdkbuilder.Buildable;
import io.github.jdkbuilder.BuilderAdder;
import io.github.jdkbuilder.BuilderRequired;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Buildable
public record Person(
        @BuilderRequired String name,
        int age,
        @BuilderAdder("role") List<String> roles,
        Set<String> permissions,
        @BuilderAdder("attribute") Map<String, String> attributes
) {
}
