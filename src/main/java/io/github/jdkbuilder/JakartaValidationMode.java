package io.github.jdkbuilder;

/** Controls Jakarta Validation integration in a generated builder. */
public enum JakartaValidationMode {
    /** Do not reference Jakarta Validation from the generated builder. */
    NONE,

    /**
     * Keep the ordinary unvalidated {@code build()} method and additionally generate
     * {@code build(Validator, Class<?>...)}.
     */
    OPTIONAL,

    /**
     * Generate only {@code build(Validator, Class<?>...)}. This makes supplying a
     * Jakarta {@code Validator} a compile-time requirement for every build.
     */
    REQUIRED
}
