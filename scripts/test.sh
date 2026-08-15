#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
"$ROOT/scripts/build.sh"

JAR="$ROOT/build/jdk-builder-processor.jar"
TEST_BUILD="$ROOT/build/tests"
TEST_CLASSES="$TEST_BUILD/classes"
GENERATED="$TEST_BUILD/generated"
NEGATIVE="$TEST_BUILD/negative"
CORE_ONLY_CLASSES="$TEST_BUILD/core-only-classes"
CORE_ONLY_GENERATED="$TEST_BUILD/core-only-generated"
VALIDATION_SUPPORT_CLASSES="$TEST_BUILD/jakarta-validation-api-classes"
VALIDATION_API_JAR="$TEST_BUILD/jakarta-validation-api-test-stub.jar"

rm -rf "$TEST_BUILD"
mkdir -p \
  "$TEST_CLASSES" \
  "$GENERATED" \
  "$NEGATIVE" \
  "$CORE_ONLY_CLASSES" \
  "$CORE_ONLY_GENERATED" \
  "$VALIDATION_SUPPORT_CLASSES"

mapfile -t CORE_ONLY_SOURCES < <(
  find "$ROOT/tests/src/coreonly" -name '*.java' -print | sort
)

javac \
  --release 21 \
  -Xlint:all \
  -Werror \
  -cp "$JAR" \
  -processorpath "$JAR" \
  -processor io.github.jdkbuilder.processor.BuildableProcessor \
  -s "$CORE_ONLY_GENERATED" \
  -d "$CORE_ONLY_CLASSES" \
  "${CORE_ONLY_SOURCES[@]}"

java -ea -cp "$JAR:$CORE_ONLY_CLASSES" coreonly.CoreOnlyMain

if grep -Rq "jakarta.validation" "$CORE_ONLY_GENERATED"; then
  echo "Default mode generated an unexpected Jakarta Validation dependency" >&2
  exit 1
fi

mapfile -t VALIDATION_SUPPORT_SOURCES < <(
  find "$ROOT/tests/support/jakarta-validation-api/src" -name '*.java' -print | sort
)

javac \
  --release 21 \
  -proc:none \
  -Xlint:all \
  -Werror \
  -d "$VALIDATION_SUPPORT_CLASSES" \
  "${VALIDATION_SUPPORT_SOURCES[@]}"

jar --create --file "$VALIDATION_API_JAR" -C "$VALIDATION_SUPPORT_CLASSES" .

mapfile -t TEST_SOURCES < <(find "$ROOT/tests/src/testcases" -name '*.java' -print | sort)

javac \
  --release 21 \
  -Xlint:all,-processing \
  -Werror \
  -cp "$JAR:$VALIDATION_API_JAR" \
  -processorpath "$JAR" \
  -processor io.github.jdkbuilder.processor.BuildableProcessor \
  -s "$GENERATED" \
  -d "$TEST_CLASSES" \
  "${TEST_SOURCES[@]}"

java -ea -cp "$JAR:$VALIDATION_API_JAR:$TEST_CLASSES" testcases.TestMain

compile_must_fail() {
  local source="$1"
  local expected="$2"
  local classpath="${3:-$JAR}"
  local name
  name="$(basename "$source" .java)"
  local log="$NEGATIVE/$name.log"

  if javac \
      --release 21 \
      -cp "$classpath" \
      -processorpath "$JAR" \
      -processor io.github.jdkbuilder.processor.BuildableProcessor \
      -d "$NEGATIVE" \
      "$source" >"$log" 2>&1; then
    echo "Expected compilation failure for $source" >&2
    exit 1
  fi

  if ! grep -Fq "$expected" "$log"; then
    echo "Compilation failed, but expected diagnostic was absent: $expected" >&2
    cat "$log" >&2
    exit 1
  fi
}

compile_must_fail \
  "$ROOT/tests/src/negative/MissingAccessor.java" \
  "Cannot generate from/copyFrom"

compile_must_fail \
  "$ROOT/tests/src/negative/PrivateConstructor.java" \
  "builder constructor cannot be private"

compile_must_fail \
  "$ROOT/tests/src/negative/NonStaticInner.java" \
  "non-static inner class"

compile_must_fail \
  "$ROOT/tests/src/negative/UnsupportedQueue.java" \
  "cannot be copied to a guaranteed immutable value"

compile_must_fail \
  "$ROOT/tests/src/negative/JakartaValidationApiMissing.java" \
  "requires jakarta.validation-api on the compilation classpath"

compile_must_fail \
  "$ROOT/tests/src/negative/ExistingBuilderType.java" \
  "conflicts with an existing source or classpath type"

compile_must_fail \
  "$ROOT/tests/src/negative/GeneratedFromCollision.java" \
  "Generated builder method collision after type erasure for 'from(negative.GeneratedFromCollision)'"

compile_must_fail \
  "$ROOT/tests/src/negative/RequiredFieldCollision.java" \
  "Generated builder field collision for 'value\$set'"

compile_must_fail \
  "$ROOT/tests/src/negative/OwnedFieldCollision.java" \
  "Generated builder field collision for 'values\$owned'"

compile_must_fail \
  "$ROOT/tests/src/negative/ErasedAdderCollision.java" \
  "Generated builder method collision after type erasure for 'addItem(java.util.List)'"

compile_must_fail \
  "$ROOT/tests/src/negative/ValidationReservedFieldCollision.java" \
  "Generated builder field collision for '\$JDK_BUILDER_VALIDATION_CONSTRUCTOR'" \
  "$JAR:$VALIDATION_API_JAR"


compile_must_fail \
  "$ROOT/tests/src/negative/MissingDefaultProvider.java" \
  "@BuilderDefault provider 'missing()'"

compile_must_fail \
  "$ROOT/tests/src/negative/RequiredDefaultConflict.java" \
  "cannot be both @BuilderRequired and @BuilderDefault"

compile_must_fail \
  "$ROOT/tests/src/negative/NonStaticFactory.java" \
  "@BuilderFactory method must be static"

compile_must_fail \
  "$ROOT/tests/src/negative/WrongFactoryReturn.java" \
  "@BuilderFactory return type java.lang.String is not assignable"

compile_must_fail \
  "$ROOT/tests/src/negative/CrossPackageConstructor.java" \
  "constructor used from another builder package must be public"

compile_must_fail \
  "$ROOT/tests/src/negative/CustomizedMethodCollision.java" \
  "Generated builder method collision after type erasure for 'clearRoles()'"

compile_must_fail \
  "$ROOT/tests/src/negative/CheckedDefaultProvider.java" \
  "@BuilderDefault provider must not declare checked exception"


compile_must_fail \
  "$ROOT/tests/src/negative/MultipleFactories.java" \
  "Exactly one static method may be annotated @BuilderFactory"

compile_must_fail \
  "$ROOT/tests/src/negative/FactoryAndConstructor.java" \
  "Use either @BuilderFactory or @BuilderConstructor, not both"

compile_must_fail \
  "$ROOT/tests/src/negative/CallbackBuilderFieldCollision.java" \
  "Generated builder field collision for 'child\$builder'"

compile_must_fail \
  "$ROOT/tests/src/negative/CallbackMethodCollision.java" \
  "Generated builder method collision after type erasure for 'childUsing(java.util.function.Function)'"

compile_must_fail \
  "$ROOT/tests/src/negative/CustomizeMethodCollision.java" \
  "Generated builder method collision after type erasure for 'hook(java.util.function.Consumer)'"

compile_must_fail \
  "$ROOT/tests/src/negative/ObjectMethodCollision.java" \
  "conflicts with inherited java.lang.Object method"

compile_must_fail \
  "$ROOT/tests/src/negative/CrossPackageHiddenType.java" \
  "is not accessible from generated builder package"

compile_must_fail \
  "$ROOT/tests/src/negative/CrossPackageHiddenGeneric.java" \
  "is not accessible from generated builder package"

compile_must_fail \
  "$ROOT/tests/src/negative/GenericFactoryMismatch.java" \
  "must match target type parameter"

compile_must_fail \
  "$ROOT/tests/src/negative/DeferredOperationFieldCollision.java" \
  "Generated builder field collision for '\$jdkBuilder\$ops0'"

# Structural checks ensure the expected API was actually generated.
grep -Fq "public static PersonBuilder builder()" \
  "$GENERATED/testcases/PersonBuilder.java"
grep -Fq "public static PersonBuilder from(testcases.Person source)" \
  "$GENERATED/testcases/PersonBuilder.java"
grep -Fq "public static PersonBuilder toBuilder(testcases.Person source)" \
  "$GENERATED/testcases/PersonBuilder.java"
grep -Fq "addRole(java.lang.String value)" \
  "$GENERATED/testcases/PersonBuilder.java"
grep -Fq "putAttribute(java.lang.String key, java.lang.String value)" \
  "$GENERATED/testcases/PersonBuilder.java"
grep -Fq "public static <T extends java.lang.Comparable<T>> BoxBuilder<T> builder()" \
  "$GENERATED/testcases/BoxBuilder.java"

grep -Fq "public static testcases.CallbackOrder of(java.util.function.Function" \
  "$GENERATED/testcases/CallbackOrderBuilder.java"
grep -Fq "customize(java.util.function.Consumer<? super CallbackOrderBuilder> customizer)" \
  "$GENERATED/testcases/CallbackOrderBuilder.java"
grep -Fq "addressUsing(java.util.function.Function<? super testcases.CallbackAddressBuilder" \
  "$GENERATED/testcases/CallbackOrderBuilder.java"
grep -Fq "addLineUsing(java.util.function.Function<? super testcases.CallbackLineBuilder" \
  "$GENERATED/testcases/CallbackOrderBuilder.java"
grep -Fq "putIndexedLineUsing(java.lang.String key, java.util.function.Function<? super testcases.CallbackLineBuilder" \
  "$GENERATED/testcases/CallbackOrderBuilder.java"
grep -Fq "this.address\$builder.build()" \
  "$GENERATED/testcases/CallbackOrderBuilder.java"
grep -Fq "this.child\$builder.build(validator, groups)" \
  "$GENERATED/testcases/RequiredCallbackParentBuilder.java"
grep -Fq "public static testcases.RequiredCallbackParent of(jakarta.validation.Validator validator" \
  "$GENERATED/testcases/RequiredCallbackParentBuilder.java"
grep -Fq "throws java.io.IOException" \
  "$GENERATED/testcases/CallbackCheckedTopBuilder.java"
grep -Fq "testcases.generated.CustomChildDraft.start()" \
  "$GENERATED/testcases/CustomCallbackParentBuilder.java"
grep -Fq "this.child\$builder.finish()" \
  "$GENERATED/testcases/CustomCallbackParentBuilder.java"
if grep -Fq "childUsing(java.util.function.Function" \
    "$GENERATED/testcases/NoCallbackParentBuilder.java"; then
  echo "A child with builderCallbacks=false still received a nested callback" >&2
  exit 1
fi

grep -Fq "public testcases.ValidatedCustomer build()" \
  "$GENERATED/testcases/ValidatedCustomerBuilder.java"
grep -Fq "build(jakarta.validation.Validator validator, java.lang.Class<?>... groups)" \
  "$GENERATED/testcases/ValidatedCustomerBuilder.java"
grep -Fq "build(jakarta.validation.Validator validator, java.lang.Class<?>... groups)" \
  "$GENERATED/testcases/RequiredValidatedCustomerBuilder.java"
grep -Fq "public testcases.ValidatedGeneric<T> build(jakarta.validation.Validator validator" \
  "$GENERATED/testcases/ValidatedGenericBuilder.java"
grep -Fq "build(jakarta.validation.Validator validator, java.lang.Class<?>... groups) throws java.io.IOException" \
  "$GENERATED/testcases/ValidatedCheckedConstructionBuilder.java"
grep -Fq ".validateConstructorParameters(" \
  "$GENERATED/testcases/ConstructorValidatedClassBuilder.java"
grep -Fq ".validateConstructorReturnValue(" \
  "$GENERATED/testcases/ConstructorValidatedClassBuilder.java"
grep -Fq "ConstructorValidatedClass.class.getDeclaredConstructor(java.lang.String.class)" \
  "$GENERATED/testcases/ConstructorValidatedClassBuilder.java"
grep -Fq "ValidatedOverloaded.class.getDeclaredConstructor(int.class, java.lang.String[].class)" \
  "$GENERATED/testcases/ValidatedOverloadedBuilder.java"
grep -Fq "public final class Envelope\$ItemBuilder<T>" \
  "$GENERATED/testcases/Envelope\$ItemBuilder.java"
grep -Fq "public final class CollisionSafeNames\$NestedBuilder" \
  "$GENERATED/testcases/CollisionSafeNames\$NestedBuilder.java"
grep -Fq "public final class CollisionSafeNames_NestedBuilder" \
  "$GENERATED/testcases/CollisionSafeNames_NestedBuilder.java"

grep -Fq "removeRole(java.lang.String value)" \
  "$GENERATED/testcases/PersonBuilder.java"
grep -Fq "removeAllRoles(java.util.Collection<?> values)" \
  "$GENERATED/testcases/PersonBuilder.java"
grep -Fq "clearRoles()" \
  "$GENERATED/testcases/PersonBuilder.java"
grep -Fq "removeAttribute(java.lang.String key)" \
  "$GENERATED/testcases/PersonBuilder.java"

grep -Fq 'this.name$set ? this.name : testcases.DefaultsAndArrays.defaultName()' \
  "$GENERATED/testcases/DefaultsAndArraysBuilder.java"
grep -Fq 'this.roles$set ? this.roles : testcases.DefaultsAndArrays.defaultRoles()' \
  "$GENERATED/testcases/DefaultsAndArraysBuilder.java"
grep -Fq 'this.numbers = numbers == null ? null : numbers.clone();' \
  "$GENERATED/testcases/DefaultsAndArraysBuilder.java"
grep -Fq '$jdkBuilder$value2 = $jdkBuilder$raw2 == null ? null : $jdkBuilder$raw2.clone();' \
  "$GENERATED/testcases/DefaultsAndArraysBuilder.java"

grep -Fq "package testcases.generated;" \
  "$GENERATED/testcases/generated/OrderDraft.java"
grep -Fq "public final class OrderDraft" \
  "$GENERATED/testcases/generated/OrderDraft.java"
grep -Fq "public static OrderDraft newDraft()" \
  "$GENERATED/testcases/generated/OrderDraft.java"
grep -Fq "public OrderDraft withName(java.lang.String name)" \
  "$GENERATED/testcases/generated/OrderDraft.java"
grep -Fq "public testcases.CustomModel create()" \
  "$GENERATED/testcases/generated/OrderDraft.java"
grep -Fq "public static OrderDraft copyOf(testcases.CustomModel source)" \
  "$GENERATED/testcases/generated/OrderDraft.java"

if grep -Fq "public final class LocalDraft" "$GENERATED/testcases/LocalDraft.java"; then
  echo "PACKAGE_PRIVATE customization generated a public builder" >&2
  exit 1
fi
grep -Fq "final class LocalDraft" "$GENERATED/testcases/LocalDraft.java"
grep -Fq "static LocalDraft start()" "$GENERATED/testcases/LocalDraft.java"
grep -Fq "testcases.LocalModel finish()" "$GENERATED/testcases/LocalDraft.java"

grep -Fq "java.util.Objects.requireNonNull(testcases.FactoryProduct.create(" \
  "$GENERATED/testcases/FactoryProductBuilder.java"
if grep -Fq "new testcases.FactoryProduct(" "$GENERATED/testcases/FactoryProductBuilder.java"; then
  echo "Factory-backed builder bypassed the selected factory" >&2
  exit 1
fi
grep -Fq "java.util.Objects.requireNonNull(testcases.AbstractMessage.create(" \
  "$GENERATED/testcases/AbstractMessageBuilder.java"
grep -Fq "throws java.io.IOException" \
  "$GENERATED/testcases/FactoryCheckedBuilder.java"
grep -Fq "validator.validate(value, groups)" \
  "$GENERATED/testcases/ValidatedFactoryProductBuilder.java"
if grep -Eq "forExecutables|VALIDATION_METHOD|validateParameters" \
    "$GENERATED/testcases/ValidatedFactoryProductBuilder.java"; then
  echo "Static factory builder generated non-portable executable validation" >&2
  exit 1
fi

# Type-use and Jakarta constraint annotations remain on the target model only. They must not
# accidentally become annotations on generated builder fields, setters, bounds, or arrays.
ANNOTATED_BUILDER="$GENERATED/testcases/AnnotatedTypesBuilder.java"
if grep -Eq "NotBlank|TypeUseMarker" "$ANNOTATED_BUILDER"; then
  echo "Source/type-use annotations leaked into generated builder signatures" >&2
  cat "$ANNOTATED_BUILDER" >&2
  exit 1
fi
grep -Fq "public final class AnnotatedTypesBuilder<T extends java.lang.Comparable<T>>" \
  "$ANNOTATED_BUILDER"
grep -Fq "private java.util.List<java.lang.String> tags;" "$ANNOTATED_BUILDER"
grep -Fq "private java.lang.String[] aliases;" "$ANNOTATED_BUILDER"
grep -Fq "private java.util.List<? extends java.lang.Number> numbers;" "$ANNOTATED_BUILDER"

if grep -Fq "public testcases.RequiredValidatedCustomer build()" \
    "$GENERATED/testcases/RequiredValidatedCustomerBuilder.java"; then
  echo "REQUIRED Jakarta Validation mode exposed an unvalidated build()" >&2
  exit 1
fi

# Wildcard collection properties intentionally receive only a setter, not an unsafe adder.
if grep -Fq "addValue(" "$GENERATED/testcases/WildcardsBuilder.java"; then
  echo "Unsafe wildcard collection adder was generated" >&2
  exit 1
fi

if grep -Fq "addData(" "$GENERATED/testcases/NoAdderBuilder.java"; then
  echo "@BuilderNoAdder was ignored" >&2
  exit 1
fi

if grep -Eq "removeValue\(|removeAllValues\(|clearValues\(" \
    "$GENERATED/testcases/NoRemoversBuilder.java"; then
  echo "collectionRemovers=false was ignored" >&2
  exit 1
fi

if grep -Eq "java.util.function.(Function|Consumer)" \
    "$GENERATED/testcases/CallbacksDisabledBuilder.java"; then
  echo "builderCallbacks=false still generated callback APIs" >&2
  exit 1
fi

RAW_GENERIC_BUILDER="$GENERATED/testcases/RawGenericHolderBuilder.java"
grep -Fq '@java.lang.SuppressWarnings("rawtypes")' "$RAW_GENERIC_BUILDER"
if grep -Fq "childUsing(java.util.function.Function" "$RAW_GENERIC_BUILDER"; then
  echo "Raw generic property unexpectedly received a nested builder callback" >&2
  exit 1
fi

# Cross-module proof: @Buildable metadata is CLASS-retained so a parent compiled later can
# discover the previously generated child builder API and emit a nested callback.
CLASSPATH_CALLBACK="$TEST_BUILD/classpath-callback"
CHILD_CLASSES="$CLASSPATH_CALLBACK/child-classes"
CHILD_GENERATED="$CLASSPATH_CALLBACK/child-generated"
PARENT_CLASSES="$CLASSPATH_CALLBACK/parent-classes"
PARENT_GENERATED="$CLASSPATH_CALLBACK/parent-generated"
CHILD_JAR="$CLASSPATH_CALLBACK/external-child.jar"
mkdir -p "$CHILD_CLASSES" "$CHILD_GENERATED" "$PARENT_CLASSES" "$PARENT_GENERATED"

mapfile -t CLASSPATH_CHILD_SOURCES < <(
  find "$ROOT/tests/src/classpath-child" -name '*.java' -print | sort
)
javac \
  --release 21 \
  -Xlint:all,-processing \
  -Werror \
  -cp "$JAR" \
  -processorpath "$JAR" \
  -processor io.github.jdkbuilder.processor.BuildableProcessor \
  -s "$CHILD_GENERATED" \
  -d "$CHILD_CLASSES" \
  "${CLASSPATH_CHILD_SOURCES[@]}"
jar --create --file "$CHILD_JAR" -C "$CHILD_CLASSES" .

mapfile -t CLASSPATH_PARENT_SOURCES < <(
  find "$ROOT/tests/src/classpath-parent" -name '*.java' -print | sort
)
javac \
  --release 21 \
  -Xlint:all,-processing \
  -Werror \
  -cp "$JAR:$CHILD_JAR" \
  -processorpath "$JAR" \
  -processor io.github.jdkbuilder.processor.BuildableProcessor \
  -s "$PARENT_GENERATED" \
  -d "$PARENT_CLASSES" \
  "${CLASSPATH_PARENT_SOURCES[@]}"

grep -Fq "java.util.function.Function<? super callbackclasspath.ExternalChildDraft" \
  "$PARENT_GENERATED/callbackclasspath/ExternalParentBuilder.java"
grep -Fq "callbackclasspath.ExternalChildDraft.start()" \
  "$PARENT_GENERATED/callbackclasspath/ExternalParentBuilder.java"
grep -Fq "this.child\$builder.finish()" \
  "$PARENT_GENERATED/callbackclasspath/ExternalParentBuilder.java"
java -ea -cp "$JAR:$CHILD_JAR:$PARENT_CLASSES" callbackclasspath.ClasspathCallbackMain

# Cross-module construction proof: selected factory/constructor annotations are CLASS-retained,
# so a later parent compilation preserves private-factory, overloaded-constructor, interface,
# and checked-exception behavior.
CONSTRUCTION_CP="$TEST_BUILD/classpath-construction"
CONSTRUCTION_CHILD_CLASSES="$CONSTRUCTION_CP/child-classes"
CONSTRUCTION_CHILD_GENERATED="$CONSTRUCTION_CP/child-generated"
CONSTRUCTION_PARENT_CLASSES="$CONSTRUCTION_CP/parent-classes"
CONSTRUCTION_PARENT_GENERATED="$CONSTRUCTION_CP/parent-generated"
CONSTRUCTION_CHILD_JAR="$CONSTRUCTION_CP/construction-child.jar"
mkdir -p \
  "$CONSTRUCTION_CHILD_CLASSES" \
  "$CONSTRUCTION_CHILD_GENERATED" \
  "$CONSTRUCTION_PARENT_CLASSES" \
  "$CONSTRUCTION_PARENT_GENERATED"

mapfile -t CONSTRUCTION_CHILD_SOURCES < <(
  find "$ROOT/tests/src/classpath-construction-child" -name '*.java' -print | sort
)
javac \
  --release 21 \
  -Xlint:all,-processing \
  -Werror \
  -cp "$JAR" \
  -processorpath "$JAR" \
  -processor io.github.jdkbuilder.processor.BuildableProcessor \
  -s "$CONSTRUCTION_CHILD_GENERATED" \
  -d "$CONSTRUCTION_CHILD_CLASSES" \
  "${CONSTRUCTION_CHILD_SOURCES[@]}"
jar --create --file "$CONSTRUCTION_CHILD_JAR" -C "$CONSTRUCTION_CHILD_CLASSES" .

mapfile -t CONSTRUCTION_PARENT_SOURCES < <(
  find "$ROOT/tests/src/classpath-construction-parent" -name '*.java' -print | sort
)
javac \
  --release 21 \
  -Xlint:all,-processing \
  -Werror \
  -cp "$JAR:$CONSTRUCTION_CHILD_JAR" \
  -processorpath "$JAR" \
  -processor io.github.jdkbuilder.processor.BuildableProcessor \
  -s "$CONSTRUCTION_PARENT_GENERATED" \
  -d "$CONSTRUCTION_PARENT_CLASSES" \
  "${CONSTRUCTION_PARENT_SOURCES[@]}"

grep -Fq "factoryUsing(java.util.function.Function" \
  "$CONSTRUCTION_PARENT_GENERATED/crossconstruction/ConstructionParentBuilder.java"
grep -Fq "constructorUsing(java.util.function.Function" \
  "$CONSTRUCTION_PARENT_GENERATED/crossconstruction/ConstructionParentBuilder.java"
grep -Fq "contractUsing(java.util.function.Function" \
  "$CONSTRUCTION_PARENT_GENERATED/crossconstruction/ConstructionParentBuilder.java"
grep -Fq "throws java.io.IOException" \
  "$CONSTRUCTION_PARENT_GENERATED/crossconstruction/ConstructionParentBuilder.java"
java -ea \
  -cp "$JAR:$CONSTRUCTION_CHILD_JAR:$CONSTRUCTION_PARENT_CLASSES" \
  crossconstruction.ConstructionMain

# A CLASS-retained @Buildable marker is not sufficient: the processor must verify the child
# builder artifact is actually on the classpath and provide one focused diagnostic.
MISSING_CP="$TEST_BUILD/classpath-missing-builder"
MISSING_CHILD_CLASSES="$MISSING_CP/child-classes"
MISSING_PARENT_CLASSES="$MISSING_CP/parent-classes"
MISSING_CHILD_JAR="$MISSING_CP/missing-child.jar"
MISSING_LOG="$MISSING_CP/parent.log"
mkdir -p "$MISSING_CHILD_CLASSES" "$MISSING_PARENT_CLASSES"
javac \
  --release 21 \
  -proc:none \
  -Xlint:all \
  -Werror \
  -cp "$JAR" \
  -d "$MISSING_CHILD_CLASSES" \
  "$ROOT/tests/src/classpath-missing-child/missingbuilder/NoGeneratedChild.java"
jar --create --file "$MISSING_CHILD_JAR" -C "$MISSING_CHILD_CLASSES" .
if javac \
    --release 21 \
    -Xlint:all,-processing \
    -Werror \
    -cp "$JAR:$MISSING_CHILD_JAR" \
    -processorpath "$JAR" \
    -processor io.github.jdkbuilder.processor.BuildableProcessor \
    -d "$MISSING_PARENT_CLASSES" \
    "$ROOT/tests/src/classpath-missing-parent/missingbuilder/MissingBuilderParent.java" \
    >"$MISSING_LOG" 2>&1; then
  echo "Expected missing classpath child builder compilation failure" >&2
  exit 1
fi
grep -Fq "declares @Buildable, but generated builder 'missingbuilder.NoGeneratedChildBuilder' was not found" \
  "$MISSING_LOG"

# Existing but stale classpath builders receive an explicit compatibility diagnostic rather than
# a cascade of generated-source cannot-find-symbol errors.
STALE_CP="$TEST_BUILD/classpath-stale-builder"
STALE_CHILD_CLASSES="$STALE_CP/child-classes"
STALE_PARENT_CLASSES="$STALE_CP/parent-classes"
STALE_CHILD_JAR="$STALE_CP/stale-child.jar"
STALE_LOG="$STALE_CP/parent.log"
mkdir -p "$STALE_CHILD_CLASSES" "$STALE_PARENT_CLASSES"
mapfile -t STALE_CHILD_SOURCES < <(
  find "$ROOT/tests/src/classpath-stale-child" -name '*.java' -print | sort
)
javac \
  --release 21 \
  -proc:none \
  -Xlint:all \
  -Werror \
  -cp "$JAR" \
  -d "$STALE_CHILD_CLASSES" \
  "${STALE_CHILD_SOURCES[@]}"
jar --create --file "$STALE_CHILD_JAR" -C "$STALE_CHILD_CLASSES" .
if javac \
    --release 21 \
    -Xlint:all,-processing \
    -Werror \
    -cp "$JAR:$STALE_CHILD_JAR" \
    -processorpath "$JAR" \
    -processor io.github.jdkbuilder.processor.BuildableProcessor \
    -d "$STALE_PARENT_CLASSES" \
    "$ROOT/tests/src/classpath-stale-parent/stalebuilder/StaleBuilderParent.java" \
    >"$STALE_LOG" 2>&1; then
  echo "Expected stale classpath child builder compilation failure" >&2
  exit 1
fi
grep -Fq "is stale or incompatible: expected static start()" "$STALE_LOG"


# Service auto-discovery proof: javac receives only the processor path and discovers the
# processor through META-INF/services. No explicit -processor argument is allowed here.
AUTO_DISCOVERY="$TEST_BUILD/auto-discovery"
AUTO_CLASSES="$AUTO_DISCOVERY/classes"
AUTO_GENERATED="$AUTO_DISCOVERY/generated"
mkdir -p "$AUTO_CLASSES" "$AUTO_GENERATED"
mapfile -t AUTO_SOURCES < <(
  find "$ROOT/tests/src/autodiscovery" -name '*.java' -print | sort
)
javac \
  --release 21 \
  -Xlint:all,-processing \
  -Werror \
  -cp "$JAR" \
  -processorpath "$JAR" \
  -s "$AUTO_GENERATED" \
  -d "$AUTO_CLASSES" \
  "${AUTO_SOURCES[@]}"
test -f "$AUTO_GENERATED/autodiscovery/AutoDiscoveredBuilder.java"
java -ea -cp "$JAR:$AUTO_CLASSES" autodiscovery.AutoDiscoveryMain

# Gradle incremental annotation processing metadata is part of the published processor jar.
jar --list --file "$JAR" | grep -Fxq 'META-INF/gradle/incremental.annotation.processors'
unzip -p "$JAR" META-INF/gradle/incremental.annotation.processors \
  | grep -Fxq 'io.github.jdkbuilder.processor.BuildableProcessor,isolating'

# Compile the real-provider integration source offline against the signature-compatible API
# surface. This catches Java, generated API, module boundary, callback, and build-layout drift
# even when Maven Central is unavailable. scripts/test-external.sh performs provider execution.
OFFLINE_INTEGRATION="$TEST_BUILD/offline-external-integration"
OFFLINE_MODEL_CLASSES="$OFFLINE_INTEGRATION/model-classes"
OFFLINE_MODEL_GENERATED="$OFFLINE_INTEGRATION/model-generated"
OFFLINE_APP_CLASSES="$OFFLINE_INTEGRATION/app-classes"
OFFLINE_APP_GENERATED="$OFFLINE_INTEGRATION/app-generated"
OFFLINE_MODEL_JAR="$OFFLINE_INTEGRATION/validation-model.jar"
mkdir -p \
  "$OFFLINE_MODEL_CLASSES" \
  "$OFFLINE_MODEL_GENERATED" \
  "$OFFLINE_APP_CLASSES" \
  "$OFFLINE_APP_GENERATED"

mapfile -t OFFLINE_MODEL_SOURCES < <(
  find "$ROOT/integration-tests/maven-reactor/model/src/main/java" -name '*.java' -print | sort
)
javac \
  --release 21 \
  -parameters \
  -Xlint:all,-processing \
  -Werror \
  -cp "$JAR:$VALIDATION_API_JAR" \
  -processorpath "$JAR" \
  -s "$OFFLINE_MODEL_GENERATED" \
  -d "$OFFLINE_MODEL_CLASSES" \
  "${OFFLINE_MODEL_SOURCES[@]}"
jar --create --file "$OFFLINE_MODEL_JAR" -C "$OFFLINE_MODEL_CLASSES" .

mapfile -t OFFLINE_APP_SOURCES < <(
  find "$ROOT/integration-tests/maven-reactor/app/src/main/java" -name '*.java' -print | sort
)
javac \
  --release 21 \
  -parameters \
  -Xlint:all,-processing \
  -Werror \
  -cp "$JAR:$VALIDATION_API_JAR:$OFFLINE_MODEL_JAR" \
  -processorpath "$JAR" \
  -s "$OFFLINE_APP_GENERATED" \
  -d "$OFFLINE_APP_CLASSES" \
  "${OFFLINE_APP_SOURCES[@]}"

grep -Fq 'orderUsing(java.util.function.Function<? super integration.model.OrderBuilder' \
  "$OFFLINE_APP_GENERATED/integration/app/PurchaseBuilder.java"
grep -Fq 'addOrderUsing(java.util.function.Function<? super integration.model.OrderBuilder' \
  "$OFFLINE_APP_GENERATED/integration/app/PurchaseBuilder.java"
grep -Fq 'putIndexedOrderUsing(java.lang.String key, java.util.function.Function<? super integration.model.OrderBuilder' \
  "$OFFLINE_APP_GENERATED/integration/app/PurchaseBuilder.java"
grep -Fq '.validateConstructorParameters(' \
  "$OFFLINE_MODEL_GENERATED/integration/model/RangeBuilder.java"
grep -Fq '.validateConstructorReturnValue(' \
  "$OFFLINE_MODEL_GENERATED/integration/model/RangeBuilder.java"
grep -Fq 'validator.validate(value, groups)' \
  "$OFFLINE_MODEL_GENERATED/integration/model/FactoryTokenBuilder.java"

python3 - "$ROOT" <<'PY_XML'
from pathlib import Path
import sys
import xml.etree.ElementTree as ET
root = Path(sys.argv[1])
for relative in (
    'pom.xml',
    'integration-tests/maven-reactor/pom.xml',
    'integration-tests/maven-reactor/model/pom.xml',
    'integration-tests/maven-reactor/app/pom.xml',
):
    ET.parse(root / relative)
PY_XML

grep -Fq 'annotationProcessor' \
  "$ROOT/integration-tests/gradle-multiproject/build.gradle.kts"
grep -Fq 'val hibernateValidatorVersion = "9.1.3.Final"' \
  "$ROOT/integration-tests/gradle-multiproject/build.gradle.kts"
grep -Fq '<hibernate.validator.version>9.1.3.Final</hibernate.validator.version>' \
  "$ROOT/integration-tests/maven-reactor/pom.xml"

echo "PROCESSOR TESTS PASSED"
