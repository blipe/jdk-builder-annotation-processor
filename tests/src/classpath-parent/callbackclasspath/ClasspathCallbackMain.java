package callbackclasspath;

public final class ClasspathCallbackMain {
    public static void main(String[] args) {
        ExternalParent value = ExternalParentBuilder.of(parent -> parent
                .childUsing(child -> child.value("classpath")));
        if (!value.child().value().equals("classpath")) {
            throw new AssertionError("classpath callback failed");
        }
    }
}
