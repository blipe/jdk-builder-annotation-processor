# External integration tests

These tests exercise the processor through real build tools and a real Jakarta Validation provider.

Run from the project root:

```bash
./scripts/test-external.sh
```

Requirements:

- JDK 21
- Maven 3.9+
- Gradle 9.6.1+
- Network access to Maven Central on the first run

The script uses an isolated Maven repository under `build/external-m2`, installs the processor there, and then runs:

1. A two-module Maven reactor using annotation-processor service auto-discovery.
2. Hibernate Validator 9.1.3.Final with Jakarta Validation 3.1.1 and Expressly 6.0.0.
3. A two-project Gradle build using the `annotationProcessor` configuration.
4. A second Gradle build that checks incremental/up-to-date compilation behavior.

The Maven and Gradle builds share the same integration model and executable assertions. Coverage includes built-in constraints, custom class-level constraints, constructor parameter and cross-parameter constraints, constructor return constraints on a package-private constructor, group conversion, group sequences, cascaded object/list/map validation, cross-module builder callbacks, `OPTIONAL` validation, and factory-backed validation.

The final external step temporarily changes `Line` from `build(...)` to `finish(...)`, reruns Gradle, and asserts that `LineBuilder` and the dependent `OrderBuilder` are regenerated. The source file is restored through a shell trap even when the build fails.
