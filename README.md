# JDK Builder Annotation Processor

A dependency-free JDK 21 annotation processor that generates companion builders without javac AST mutation.

Supported targets and features:

- Java records
- Constructor-based classes
- Static-factory-backed classes, records, abstract classes, and interfaces
- Private target constructors when construction goes through `@BuilderFactory`
- Generic and bounded-generic target types
- Static nested records and classes
- Lazy provider-based defaults
- Defensive array copying by default
- Immutable, detached collection snapshots by default
- Collection add, put, remove, remove-all, and clear helpers
- Copy builders through configurable `from`, `toBuilder`, and `copyFrom` equivalents
- Configurable builder name, package, visibility, setter prefix, and terminal/factory method names
- Explicit required-property checks
- Checked constructor and factory exceptions
- Optional or mandatory Jakarta Validation integration
- Elasticsearch-style root, nested-value, collection, and map builder callbacks
- Consumer-based customization of an existing builder
- Collision-safe generated type and member names
- Annotation-isolated generated signatures

The processor generates ordinary Java source. It does not inject methods into the annotated type.

## Basic example

```java
package example;

import io.github.jdkbuilder.Buildable;
import io.github.jdkbuilder.BuilderAdder;
import io.github.jdkbuilder.BuilderRequired;

import java.util.List;

@Buildable
public record Customer(
        @BuilderRequired String id,
        String name,
        @BuilderAdder("role") List<String> roles
) {
}
```

Generated usage:

```java
Customer customer = CustomerBuilder.builder()
        .id("customer-1")
        .name("Ada")
        .addRole("admin")
        .build();

Customer changed = CustomerBuilder.toBuilder(customer)
        .name("Grace")
        .removeRole("admin")
        .addRole("reviewer")
        .build();
```

A standard annotation processor cannot add `customer.toBuilder()` directly to `Customer`, so the generated equivalent is `CustomerBuilder.toBuilder(customer)`.

## Build and test

Core requirements: JDK 21 and a POSIX shell.

```bash
./scripts/test.sh
./scripts/run-example.sh
```

The core suite compiles the processor with `-Xlint:all -Werror`, uses service auto-discovery as well as explicit processor selection, compiles generated source, executes runtime tests, validates expected compiler failures, checks cross-module generated APIs, and verifies Gradle incremental-processor metadata.

Real-provider and build-tool integration requires Maven 3.9+, Gradle 9.6.1+, and dependency access on the first run:

```bash
./scripts/test-external.sh
```

That suite executes the same generated models through a two-module Maven reactor and a Gradle multi-project build using Hibernate Validator 9.1.3.Final. It also repeats the Gradle build and changes a child builder terminal method to verify dependent generated-source invalidation.

## `@Buildable`

```java
@Buildable(
    generateFrom = true,
    collectionAdders = true,
    collectionRemovers = true,
    defensiveCopyCollections = true,
    defensiveCopyArrays = true,
    jakartaValidation = JakartaValidationMode.NONE,
    builderCallbacks = true,
    callbackMethod = "of",
    customizeMethod = "customize",
    nestedCallbackSuffix = "Using",
    builderClassName = "",
    builderPackage = "",
    setterPrefix = "",
    builderMethod = "builder",
    buildMethod = "build",
    fromMethod = "from",
    toBuilderMethod = "toBuilder",
    copyFromMethod = "copyFrom",
    access = BuilderAccess.PUBLIC
)
```

### Core options

- `generateFrom`: generates source-copy factory, alias, and instance copy methods.
- `collectionAdders`: generates `addX`, `addAllX`, `putX`, and `putAllX`.
- `collectionRemovers`: generates `removeX`, `removeAllX`, and `clearX`.
- `defensiveCopyCollections`: snapshots supported collections at setter/copy time and creates another unmodifiable snapshot at build time.
- `defensiveCopyArrays`: clones arrays at setter/copy time and clones again at build time.
- `jakartaValidation`: `NONE`, `OPTIONAL`, or `REQUIRED`.
- `builderCallbacks`: enables root, nested-value, collection-element, map-value, and customization callbacks.
- `callbackMethod`: names the static root callback factory.
- `customizeMethod`: names the instance `Consumer` customization method.
- `nestedCallbackSuffix`: distinguishes callback methods from direct-value setters. The default generates `addressUsing`, `addLineUsing`, and `putProductUsing`.

## Builder callbacks

Callbacks provide the Elasticsearch Java API Client style of configuring nested objects without manually creating each child builder.

```java
@Buildable
public record Address(String city, String postalCode) {
}

@Buildable
public record Line(String sku, int quantity) {
}

@Buildable
public record Order(
        String id,
        Address address,
        @BuilderAdder("line") List<Line> lines,
        @BuilderAdder("indexedLine") Map<String, Line> indexedLines
) {
}
```

Root and nested construction:

```java
Order order = OrderBuilder.of(builder -> builder
        .id("order-1")
        .addressUsing(address -> address
                .city("Chicago")
                .postalCode("60601"))
        .addLineUsing(line -> line.sku("A").quantity(2))
        .putIndexedLineUsing("primary", line -> line.sku("A").quantity(2))
        .customize(current -> addStandardValues(current))
);
```

The generated callback type is:

```java
Function<ChildBuilder, ? extends ChildBuilder>
```

rather than forcing every builder through one `ObjectBuilder<T>` interface. This preserves custom terminal method names, checked construction exceptions, and Jakarta `REQUIRED` builders that intentionally have no zero-argument terminal method.

Semantics:

- The root callback method creates a builder, applies the function, rejects a null function/result, and invokes the configured terminal method.
- `customize(Consumer)` mutates the current builder and returns that same builder.
- Singular nested object builders are deferred until the parent is built.
- A validated parent build forwards the same `Validator` and validation groups to an `OPTIONAL` or `REQUIRED` nested object builder.
- Direct-value and callback methods have distinct names, so `address(null)` and functional-interface values remain unambiguous.
- A direct setter clears a previously configured nested builder; a later nested callback clears the direct value.
- Singular, collection-element, and map-value child builders are deferred until the parent terminal build.
- Collection/map add, remove, clear, and callback operations are replayed in exact invocation order.
- Checked exceptions from deferred child builders propagate through every parent build level, including root callbacks.
- A validated parent forwards the same validator and groups through deferred singular, collection, and map children when their validation modes are compatible.
- Generic, recursive, factory-backed, custom-package, and custom-method child builders are supported when their generated API is accessible.
- `@Buildable` is CLASS-retained, so a parent compiled in a later module can discover callback metadata from a previously compiled child JAR.
- A child with `builderCallbacks = false` is not exposed through parent nested callback overloads.

Validated root callback:

```java
Order order = OrderBuilder.of(
        validator,
        builder -> builder.addressUsing(address -> address.city("Chicago")),
        StrictChecks.class
);
```

`JakartaValidationMode.REQUIRED` generates only the validator-accepting root callback. `OPTIONAL` generates both ordinary and validator-accepting root callbacks.

Disable all callback APIs for a type:

```java
@Buildable(builderCallbacks = false)
public record PlainOrder(Address address) {
}
```

### Generated API customization

```java
@Buildable(
    builderClassName = "OrderDraft",
    builderPackage = "com.acme.generated",
    setterPrefix = "with",
    builderMethod = "newDraft",
    buildMethod = "create",
    callbackMethod = "make",
    customizeMethod = "apply",
    nestedCallbackSuffix = "ConfiguredBy",
    fromMethod = "copyOf",
    toBuilderMethod = "edit",
    copyFromMethod = "load"
)
public record Order(String name, List<String> items) {
}
```

Usage:

```java
Order order = OrderDraft.make(builder -> builder
        .apply(current -> current.withName("Q3 order"))
        .withItems(List.of("one"))
        .addItem("two"));

Order edited = OrderDraft.edit(order)
        .withName("Revised order")
        .create();
```

`BuilderAccess.PACKAGE_PRIVATE` generates a package-private builder type and package-private fluent methods. A builder generated in a different package requires the target, its enclosing types, selected constructor/factory, copy accessors, and default providers to be publicly accessible.

All configured names are validated as Java identifiers and participate in the same pre-generation collision analysis as default names. Generated methods are also checked against inherited `Object` methods, JVM-erased signatures, internal backing fields, and generated operation-log fields.

For a custom callback suffix, a property `address` uses `addressConfiguredBy(...)`; a singular collection helper `addLine` uses `addLineConfiguredBy(...)`.

## Compilation and cross-module hardening

The processor performs pre-generation checks rather than relying on cascaded javac errors:

- All generated fields and methods are reserved before source emission, using JVM-erased method signatures.
- Internal prepared-value locals use numeric property indexes, preventing collisions with legal source identifiers such as `raw$x`.
- Configured methods that would conflict with inherited `Object` methods are rejected.
- When a builder is generated in another package, property types, nested generic arguments, bounds, arrays, checked exceptions, accessors, factories, and default providers are recursively checked for accessibility.
- Copy accessors that declare checked exceptions propagate those exceptions through `copyFrom`, `from`, and `toBuilder`.
- Generic static factories are supported when their type-parameter list and parameterized return type correspond to the target type.
- Raw generic child properties retain their direct setter but do not receive an unsafe nested builder callback.

All builder-relevant annotations use `CLASS` retention. When a buildable child comes from a previously compiled artifact, the processor verifies that the expected builder type, static builder factory, and terminal method are actually present. Missing or stale builders produce one targeted diagnostic instead of generated-source `cannot find symbol` cascades.

## Defaults

`@BuilderDefault` names a zero-argument static provider on the target type:

```java
@Buildable
public record Request(
        @BuilderDefault("defaultLocale") String locale,
        @BuilderDefault("defaultRoles")
        @BuilderAdder("role") List<String> roles
) {
    public static String defaultLocale() {
        return "en-US";
    }

    public static List<String> defaultRoles() {
        return new ArrayList<>(List.of("reader"));
    }
}
```

Semantics:

- The provider is used only while the property remains unassigned.
- Explicit assignment, including explicit `null`, suppresses the default.
- An untouched default is evaluated during each build, so builder reuse can receive a fresh value.
- A collection helper such as `addRole` first materializes the collection default into builder state, then mutates a private copy.
- Collection defaults still receive the final immutable build-time copy.
- Array defaults still receive the final array clone.
- `copyFrom` marks copied values as assigned, so defaults do not replace source values.
- A property cannot combine `@BuilderDefault` and `@BuilderRequired`.

Provider requirements:

- Static
- Zero arguments
- Accessible from the generated builder package
- Return type assignable to the property
- No method type parameters
- No checked exceptions

The provider approach avoids attempting to parse or reproduce arbitrary Java field initializers.

## Arrays

Array values are isolated by default:

1. Setters and `copyFrom` clone the supplied array.
2. Every build clones builder state again before construction.

This prevents caller mutation from changing builder state and prevents builder reuse from changing a previously built object's array. Array copying is shallow: nested arrays and mutable array elements are not recursively cloned.

Explicit opt-out:

```java
@Buildable(defensiveCopyArrays = false)
public record SharedBuffer(byte[] bytes) {
}
```

Arrays themselves cannot be made unmodifiable; the guarantee is reference isolation, not immutable array contents.

## Collection helpers

For a collection property:

```java
builder.addRole("admin");
builder.addAllRoles(List.of("reader", "writer"));
builder.removeRole("reader");
builder.removeAllRoles(List.of("writer"));
builder.clearRoles();
```

For a map property:

```java
builder.putAttribute("region", "us");
builder.putAllAttributes(Map.of("tier", "gold"));
builder.removeAttribute("tier");
builder.removeAllAttributes(List.of("region"));
builder.clearAttributes();
```

Mutation helpers are copy-on-write. They never mutate a collection supplied through a setter, copied from a source object, or returned by a default provider.

`@BuilderAdder("role")` customizes the singular helper suffix. `@BuilderNoAdder` suppresses generated mutation helpers for the property while retaining its fluent setter.

Supported exact declared interfaces:

- `Collection<E>`, `List<E>`, `SequencedCollection<E>`
- `Set<E>`, `SequencedSet<E>`, `SortedSet<E>`, `NavigableSet<E>`
- `Map<K,V>`, `SequencedMap<K,V>`, `SortedMap<K,V>`, `NavigableMap<K,V>`

Wildcard collection properties retain their setter and immutable copying but do not receive unsafe mutation helpers.

Other collection declarations, including `Queue`, `Deque`, concrete collection classes, and custom collection interfaces, fail compilation while defensive copying is enabled. The processor does not silently claim immutability when an immutable value cannot be assigned to the declared type.

Deliberate mutable escape hatch:

```java
@Buildable(defensiveCopyCollections = false)
public record MutableQueue(Queue<Job> jobs) {
}
```

Collection copying is shallow. Containers are detached and unmodifiable; elements are not cloned.

## Static factory construction

Annotate one static method with `@BuilderFactory`:

```java
@Buildable
public final class Account {
    private final String id;

    private Account(String id) {
        this.id = id;
    }

    @BuilderFactory
    public static Account create(String id) {
        return new Account(id);
    }

    public String id() {
        return id;
    }
}
```

The builder calls `Account.create(...)`; it never bypasses the factory with `new Account(...)`.

Factory rules:

- Exactly one factory may be annotated.
- It must be static and non-private.
- Its return type must be assignable to the annotated target.
- Generic factory methods are rejected; target-level generics remain supported where the static factory can represent them.
- Checked exceptions are propagated from generated build methods.
- A `null` result is rejected immediately with `NullPointerException`.
- `@BuilderConstructor` and `@BuilderFactory` cannot both select construction.

Factory construction permits:

- Private target constructors
- Abstract target classes whose factory returns a concrete subtype
- Interface targets whose factory returns an implementation
- Constructor/factory encapsulation and caching decisions inside the target

This is static-factory support, not generated builder inheritance. Builders do not extend or merge parent builders.

## Constructor construction

For an ordinary class with one constructor, that constructor is selected automatically. With multiple constructors, annotate exactly one:

```java
@BuilderConstructor
public Account(String id, boolean active) {
}
```

Selected constructors cannot be private and cannot declare constructor-level type parameters. Use `@BuilderFactory` when the target must keep its constructor private.

## Copy accessors

Copy generation requires an accessible zero-argument accessor for each constructor or factory parameter. The processor searches in this order:

1. `property()`
2. `getProperty()`
3. `isProperty()` for booleans

Inherited generic accessors are resolved as members of the target type.

Override discovery with:

```java
@BuilderAccessor("id") String accountId
```

Set `generateFrom = false` when source-copy APIs are not required.

## Required properties

`@BuilderRequired` means a property must be explicitly assigned. Reference values must additionally be non-null. Primitive properties track assignment separately, so explicitly setting `0` or `false` is valid.

`@BuilderRequired` is deliberately separate from Jakarta `@NotNull`: one describes builder assignment, while the other describes object validity.

## Jakarta Validation

Constructor-backed targets support the full generated executable-validation sequence:

1. Builder-required checks
2. Final collection/array preparation
3. Constructor parameter and cross-parameter validation
4. Exactly-once construction
5. Constructor return-value validation
6. Completed-bean validation
7. `ConstraintViolationException` on violations

Modes:

- `NONE`: no Jakarta references
- `OPTIONAL`: ordinary terminal method plus `build(Validator, Class<?>...)`
- `REQUIRED`: only the validator-accepting terminal method

Factory-backed targets use portable completed-bean validation after the static factory returns. Jakarta Validation does not portably validate static-method parameters or return constraints, so the generated builder does not call `ExecutableValidator` for `@BuilderFactory`. Put precondition checks inside the factory, model them as bean constraints, or use constructor-backed construction when pre-construction executable validation is required.

The processor does not bootstrap a provider or retain a `ValidatorFactory`. Applications supply their shared framework-managed `Validator`.

## Collision safety

Before writing source, the processor reserves every generated field and method. Method signatures are compared after JVM erasure; static and instance methods share the same overload namespace.

Direct diagnostics cover:

- Generated builder type conflicts
- Custom builder-name conflicts
- Property setters colliding with configured terminal/copy/factory methods
- Generic helper erasure collisions
- Default/required assignment-state field collisions
- Collection ownership-state field collisions
- Jakarta metadata field collisions
- Helper methods colliding with configured names

A successful compilation cannot silently omit a requested builder.

## Annotation isolation

Generated field, setter, helper, bound, wildcard, array, and checked-exception types recursively omit source/type-use annotations. Constraints and framework annotations remain on the target model rather than accidentally creating a second validation surface on generated fluent methods.

## Generated names

Defaults:

- Top-level `Person` -> `PersonBuilder`
- Static nested `Envelope.Item` -> `Envelope$ItemBuilder`

Nested types use the JVM binary name, preventing collisions between `A.B` and a top-level `A_B`.

## Direct javac usage

```bash
./scripts/build.sh

javac \
  --release 21 \
  -cp build/jdk-builder-processor.jar \
  -processorpath build/jdk-builder-processor.jar \
  -d app-classes \
  $(find app-src -name '*.java')
```

Processor discovery uses `META-INF/services/javax.annotation.processing.Processor`.

## Scope deliberately excluded

The processor does not:

- Modify existing source or javac ASTs
- Inject instance methods into records/classes
- Deep-copy collection elements or nested arrays
- Generate mutable JavaBean setters on the target
- Compose generated builders through class inheritance
- Validate static factory executable constraints through Jakarta Validation
- Bootstrap a Jakarta Validation provider
