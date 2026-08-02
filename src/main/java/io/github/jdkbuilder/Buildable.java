package io.github.jdkbuilder;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Generates a companion builder for a record, constructor-based class, or factory-backed type. */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.CLASS)
public @interface Buildable {
    /** Generate from(T), toBuilder(T), and copyFrom(T). */
    boolean generateFrom() default true;

    /** Generate addX/putX helpers for supported collection interfaces. */
    boolean collectionAdders() default true;

    /** Generate removeX/removeAllX/clearX helpers for supported collection interfaces. */
    boolean collectionRemovers() default true;

    /**
     * Snapshot supported collection inputs and pass a separate unmodifiable copy to the
     * constructed object. Enabled by default. Set to false only for deliberate reference
     * sharing or collection types whose declared API cannot represent an immutable copy.
     */
    boolean defensiveCopyCollections() default true;

    /** Snapshot array inputs and pass a separate clone to the constructed object. */
    boolean defensiveCopyArrays() default true;

    /** Controls generated Jakarta Validation support. */
    JakartaValidationMode jakartaValidation() default JakartaValidationMode.NONE;

    /** Generate Elasticsearch-style root, nested-value, collection, and map builder callbacks. */
    boolean builderCallbacks() default true;

    /** Name of the static root callback factory, for example {@code of(builder -> ...)}. */
    String callbackMethod() default "of";

    /** Name of the instance callback used to customize the current builder. */
    String customizeMethod() default "customize";

    /**
     * Suffix appended to nested object, collection-element, and map-value callback methods.
     * The default produces names such as {@code addressUsing}, {@code addLineUsing}, and
     * {@code putProductUsing}, keeping direct-value setters unambiguous for {@code null} and
     * functional-interface values.
     */
    String nestedCallbackSuffix() default "Using";

    /** Generated builder simple name. Empty uses the target binary name plus {@code Builder}. */
    String builderClassName() default "";

    /** Generated builder package. Empty uses the target package. */
    String builderPackage() default "";

    /** Prefix for fluent property setters, for example {@code with} -> {@code withName}. */
    String setterPrefix() default "";

    /** Name of the static builder factory method. */
    String builderMethod() default "builder";

    /** Name of the ordinary or Jakarta-validated terminal build method. */
    String buildMethod() default "build";

    /** Name of the static source-copy factory. */
    String fromMethod() default "from";

    /** Name of the static source-copy alias. */
    String toBuilderMethod() default "toBuilder";

    /** Name of the instance source-copy method. */
    String copyFromMethod() default "copyFrom";

    /** Visibility of the generated type and fluent API. */
    BuilderAccess access() default BuilderAccess.PUBLIC;
}
