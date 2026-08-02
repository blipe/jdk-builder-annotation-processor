package testcases;

import io.github.jdkbuilder.Buildable;
import io.github.jdkbuilder.BuilderAdder;
import io.github.jdkbuilder.BuilderDefault;

import java.util.ArrayList;
import java.util.List;

@Buildable
public record DefaultsAndArrays(
        @BuilderDefault("defaultName") String name,
        @BuilderDefault("defaultRoles") @BuilderAdder("role") List<String> roles,
        int[] numbers,
        String[][] matrix
) {
    private static int nameDefaultCalls;
    private static int rolesDefaultCalls;

    public static String defaultName() {
        nameDefaultCalls++;
        return "default-name-" + nameDefaultCalls;
    }

    public static List<String> defaultRoles() {
        rolesDefaultCalls++;
        return new ArrayList<>(List.of("reader"));
    }

    public static void resetDefaultCalls() {
        nameDefaultCalls = 0;
        rolesDefaultCalls = 0;
    }

    public static int nameDefaultCalls() {
        return nameDefaultCalls;
    }

    public static int rolesDefaultCalls() {
        return rolesDefaultCalls;
    }
}
