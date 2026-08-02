package testcases;

import io.github.jdkbuilder.Buildable;
import io.github.jdkbuilder.BuilderAdder;
import io.github.jdkbuilder.BuilderDefault;
import io.github.jdkbuilder.BuilderFactory;

import java.util.List;

@Buildable
public final class FactoryProduct {
    private final String id;
    private final List<String> tags;

    private FactoryProduct(String id, List<String> tags) {
        this.id = id;
        this.tags = tags;
    }

    @BuilderFactory
    public static FactoryProduct create(
            @BuilderDefault("defaultId") String id,
            @BuilderAdder("tag") List<String> tags
    ) {
        return new FactoryProduct(id, tags);
    }

    public static String defaultId() {
        return "factory-default";
    }

    public String id() {
        return id;
    }

    public List<String> tags() {
        return tags;
    }
}
