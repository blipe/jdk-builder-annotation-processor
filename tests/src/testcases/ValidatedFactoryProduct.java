package testcases;

import io.github.jdkbuilder.Buildable;
import io.github.jdkbuilder.BuilderFactory;
import io.github.jdkbuilder.JakartaValidationMode;

@Buildable(jakartaValidation = JakartaValidationMode.REQUIRED)
public final class ValidatedFactoryProduct {
    private static int constructionCount;
    private final String value;

    private ValidatedFactoryProduct(String value) {
        constructionCount++;
        this.value = value;
    }

    @BuilderFactory
    public static ValidatedFactoryProduct create(String value) {
        return new ValidatedFactoryProduct(value);
    }

    public String value() {
        return value;
    }

    public static void resetConstructionCount() {
        constructionCount = 0;
    }

    public static int constructionCount() {
        return constructionCount;
    }
}
