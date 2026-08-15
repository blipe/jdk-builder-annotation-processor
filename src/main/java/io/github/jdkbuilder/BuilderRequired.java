package io.github.jdkbuilder;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Requires a builder property to be explicitly supplied; reference values must also be non-null. */
@Target({ElementType.RECORD_COMPONENT, ElementType.PARAMETER})
@Retention(RetentionPolicy.CLASS)
public @interface BuilderRequired {
}
