# Validation

Validated with OpenJDK 21.0.10 using:

```bash
./scripts/test.sh
./scripts/run-example.sh
```

Observed result:

```text
ALL TESTS PASSED
PROCESSOR TESTS PASSED
```

## Covered behavior

### Existing core

- Record and constructor-based class builders
- Explicit constructor selection
- Generic and bounded-generic targets
- Static nested types
- Binary-name-safe nested builder names
- Copy factories and instance copy operations
- Inherited generic accessor resolution
- Required reference and primitive properties
- Checked constructor exceptions
- Collision detection after JVM erasure
- Generated-type collision diagnostics
- Type-use annotation stripping
- Jakarta `OPTIONAL` and `REQUIRED` modes
- Constructor parameter, return-value, and completed-bean validation phases
- Dependency-free default mode

### Defaults

- Scalar defaults
- Collection defaults
- Lazy default evaluation
- Fresh untouched defaults during builder reuse
- Explicit-null suppression
- Default materialization before collection helpers
- Immutable final collection defaults
- Defaults suppressed by `copyFrom`
- Missing provider diagnostics
- Required/default conflict diagnostics
- Checked-provider-exception diagnostics

### Arrays

- Primitive arrays
- Reference arrays
- Generic arrays
- Multidimensional arrays
- Setter-time snapshots
- `copyFrom` snapshots
- Fresh build-time clones
- Explicit `defensiveCopyArrays = false` reference-sharing opt-out

Array validation confirms top-level isolation. It intentionally does not claim recursive deep copying or immutable array contents.

### Collection helpers

- Add and add-all
- Put and put-all
- Remove and remove-all
- Map key removal
- Clear for collections, sets, and maps
- Copy-on-write mutation after setters and copy builders
- Mutation of default-backed collections
- Immutable final values
- Helper collision detection against customized method names

### Generated API customization

- Builder simple name
- Builder package
- Setter prefix
- Static builder factory name
- Terminal build name
- Source-copy factory name
- Source-copy alias name
- Instance copy name
- Public and package-private access
- Cross-package accessibility diagnostics


### Builder callbacks

- Static root callback construction
- Instance `customize(Consumer)`
- Nested singular object callbacks
- Collection element callbacks with deferred construction
- Map value callbacks with deferred construction
- Ordered replay of direct add/put, callback add/put, bulk mutation, remove, and clear operations
- Direct-value versus nested-builder precedence
- Unambiguous explicit-null and functional-interface setters through distinct callback names
- Null callback and null callback-result rejection
- Generic nested types
- Recursive model graphs
- Custom child builder package, class, factory, and terminal method names
- Checked child construction
- Transitive checked exceptions through multiple nested builders
- Required-property checks with deferred nested builders
- Validator and validation-group propagation to `OPTIONAL` and `REQUIRED` nested builders
- Ordinary callback path for `OPTIONAL` validation
- Callback API collision diagnostics after erasure
- Nested-builder backing-field collision diagnostics
- Per-type callback opt-out
- Child callback-target opt-out
- Cross-module discovery from a previously compiled child JAR
- Cross-module factory, selected-constructor, interface-factory, and checked-exception preservation
- Missing generated child-builder diagnostics
- Stale classpath builder API diagnostics
- Validator/group propagation through deferred collection and map children
- Root callback composition with checked collection/map child construction


### Correction and compiler-hardening regressions

- Explicit `null` on a buildable child property
- Functional-interface direct setter versus nested callback
- Legal source identifiers resembling generated local names
- Deferred operation-log field collisions
- Configured terminal method collision with inherited `Object` methods
- Cross-package inaccessible direct and nested generic property types
- Checked copy accessor propagation
- Corresponding generic factory method construction
- Generic factory mismatch diagnostics
- Raw generic child suppression under `-Xlint:all -Werror`
- Classpath child annotated with `@Buildable` but compiled without its generated builder
- Existing classpath builder with stale custom factory metadata

### Static factories and polymorphic targets

- Private target constructor
- Static factory invocation instead of direct construction
- Factory parameter defaults
- Copy builder through factory parameters
- Checked factory exceptions
- Non-null factory-result enforcement
- Abstract target returning a concrete subtype
- Interface target returning an implementation
- Non-static factory diagnostic
- Incompatible factory return diagnostic

### Jakarta Validation with static factories

- Required validator-accepting terminal method
- Completed-bean validation
- Violation conversion to `ConstraintViolationException`
- Exactly-once factory construction
- No generated `ExecutableValidator` call for static factories

This is intentional: static executable constraints are not portable under Jakarta Validation. Constructor-backed tests continue to verify pre-construction executable validation.

## Build-tool and real-provider integration suite

The repository now contains an executable external suite:

```bash
./scripts/test-external.sh
```

It provides:

- A two-module Maven reactor using processor service auto-discovery.
- A Gradle multi-project consumer using the `annotationProcessor` configuration.
- Hibernate Validator 9.1.3.Final with Jakarta Validation 3.1.1.
- Runtime assertions for built-in and custom constraints, cascaded singular/list/map values, group conversion, group sequences, constructor parameter and cross-parameter validation, constructor return validation on a package-private constructor, optional validation, and factory-backed validation.
- A repeat no-change Gradle build assertion.
- A child builder API mutation asserting that Gradle regenerates both the child builder and dependent parent generated source.

The packaging environment used for this artifact had JDK 21 but did not have Maven, Gradle, or dependency-network access. Consequently, `scripts/test.sh` compiled all external integration sources and generated APIs offline against the test-only Jakarta API surface, but the actual Hibernate Validator/Maven/Gradle execution remains delegated to `scripts/test-external.sh` or CI.

## Jakarta compatibility test surface

The processor JAR has no Jakarta dependency. Tests compile generated constructor-validation source against a test-only API surface matching the Jakarta Validation 3.1 signatures used by the processor and exercise calls through dynamic proxies.

A consuming application should compile against `jakarta.validation:jakarta.validation-api:3.1.1` and provide a compatible implementation or framework-managed `Validator`.
