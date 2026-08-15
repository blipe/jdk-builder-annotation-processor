package io.github.jdkbuilder;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Supplies a default value through a zero-argument static method on the target type.
 * The provider is invoked for each build only when the property was never assigned.
 */
@Target({ElementType.RECORD_COMPONENT, ElementType.PARAMETER})
@Retention(RetentionPolicy.CLASS)
public @interface BuilderDefault {
    /** Name of the zero-argument static provider method on the target type. */
    String value();
}
