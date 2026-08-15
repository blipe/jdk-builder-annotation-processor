package io.github.jdkbuilder;

/** Visibility applied to the generated builder type and its fluent API. */
public enum BuilderAccess {
    /** Generate a public builder type and public fluent methods. */
    PUBLIC,

    /** Generate a package-private builder type and package-private fluent methods. */
    PACKAGE_PRIVATE
}
