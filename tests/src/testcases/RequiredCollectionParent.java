package testcases;

import io.github.jdkbuilder.Buildable;
import io.github.jdkbuilder.BuilderAdder;
import io.github.jdkbuilder.JakartaValidationMode;

import java.util.List;
import java.util.Map;

@Buildable(jakartaValidation = JakartaValidationMode.REQUIRED)
public record RequiredCollectionParent(
        @BuilderAdder("child") List<RequiredCollectionChild> children,
        @BuilderAdder("childByKey") Map<String, RequiredCollectionChild> childrenByKey
) {
}
