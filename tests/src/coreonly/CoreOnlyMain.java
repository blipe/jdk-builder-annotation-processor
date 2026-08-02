package coreonly;

public final class CoreOnlyMain {
    public static void main(String[] args) {
        CoreOnly value = CoreOnlyBuilder.builder().value("jdk-only").build();
        if (!value.value().equals("jdk-only")) {
            throw new AssertionError("core-only build failed");
        }
    }
}
