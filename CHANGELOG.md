# Changelog

## 1.5.0-SNAPSHOT

- Added real-provider integration projects for Hibernate Validator 9.1.3.Final and Jakarta Validation 3.1.1.
- Added two-module Maven and Gradle consumer builds using annotation-processor service auto-discovery.
- Added executable integration assertions for built-in, custom class-level, constructor parameter, cross-parameter, constructor return, package-private constructor, cascaded, container-element, group-conversion, group-sequence, optional, and factory-backed validation.
- Registered the processor as Gradle `isolating` incremental annotation processor metadata.
- Added a direct javac service auto-discovery regression that does not pass `-processor`.
- Added an external verification script with an isolated Maven repository and repeat Gradle build check.
- Added Gradle child-builder API mutation coverage to verify dependent generated-source invalidation.
- Added GitHub Actions core testing on JDK 21/25 and real-provider Maven/Gradle testing on JDK 21.
- Expanded the test-only Jakarta API surface so integration source and generated APIs are compiled offline before external provider execution.

## 1.4.0-SNAPSHOT

- Renamed nested callback overloads with configurable `nestedCallbackSuffix` (`Using` by default), preserving unambiguous direct `null` and functional-interface setters.
- Deferred collection-element and map-value builders until parent construction, including ordered mutation replay, Jakarta validator/group propagation, and checked-exception propagation.
- Retained all builder-selection/property annotations in class files for cross-module processing.
- Added classpath child-builder existence and stale-API verification.
- Added cross-module support tests for `@BuilderFactory`, `@BuilderConstructor`, factory-backed interfaces, and checked exceptions.
- Added generated-symbol checks for deferred operation fields, numeric prepared-value locals, and inherited `Object` methods.
- Added recursive cross-package accessibility checks for nested generic types, arrays, bounds, accessors, and checked exceptions.
- Propagated checked copy-accessor exceptions through `copyFrom`, `from`, and `toBuilder`.
- Added corresponding generic static-factory support and raw-generic callback suppression.
- Added positive and negative regression tests for every corrected defect.

## 1.3.0-SNAPSHOT

- Added Elasticsearch-style static root builder callbacks.
- Added nested object callbacks with deferred child construction.
- Added collection-element and map-value builder callback overloads.
- Added `customize(Consumer)` for conditional/current-builder customization.
- Added callback method naming and a global callback opt-out.
- Added Jakarta validator/group propagation through nested object builders.
- Added transitive checked-exception propagation through callback graphs.
- Added generic, recursive, custom-package, custom-method, and factory-backed callback support.
- Changed `@Buildable` retention to CLASS for cross-module callback discovery.
- Added callback symbol/field collision diagnostics and null-contract enforcement.
- Added cross-JAR compilation tests for previously compiled child models.

## 1.2.0-SNAPSHOT

- Added lazy `@BuilderDefault` static providers.
- Added defensive array copying with explicit opt-out.
- Added collection/map remove, remove-all, and clear helpers.
- Added generated builder name, package, visibility, setter-prefix, and method-name customization.
- Added `@BuilderFactory` static factory construction.
- Added factory-backed abstract-class and interface targets.
- Added checked factory exception propagation and null-result rejection.
- Added portable completed-bean Jakarta Validation for factory-backed targets.
- Preserved collision preflight, annotation isolation, immutable collection defaults, and JDK-only processor dependencies.
