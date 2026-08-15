package integration.model;

import io.github.jdkbuilder.Buildable;
import io.github.jdkbuilder.BuilderConstructor;
import io.github.jdkbuilder.JakartaValidationMode;
import jakarta.validation.constraints.Min;

@Buildable(jakartaValidation = JakartaValidationMode.REQUIRED)
public final class Range {
    private static int constructionCount;

    private final int start;
    private final int end;

    @BuilderConstructor
    @ValidRange
    @NormalizedRange
    Range(@Min(0) int start, @Min(0) int end) {
        constructionCount++;
        this.start = start;
        this.end = end;
    }

    public int start() {
        return start;
    }

    public int end() {
        return end;
    }

    public int width() {
        return end - start;
    }

    public static int constructionCount() {
        return constructionCount;
    }

    public static void resetConstructionCount() {
        constructionCount = 0;
    }
}
