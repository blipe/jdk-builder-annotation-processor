package io.github.jdkbuilder.processor;

import io.github.jdkbuilder.Buildable;
import io.github.jdkbuilder.BuilderAccess;
import io.github.jdkbuilder.BuilderAccessor;
import io.github.jdkbuilder.BuilderAdder;
import io.github.jdkbuilder.BuilderConstructor;
import io.github.jdkbuilder.BuilderDefault;
import io.github.jdkbuilder.BuilderFactory;
import io.github.jdkbuilder.BuilderNoAdder;
import io.github.jdkbuilder.BuilderRequired;
import io.github.jdkbuilder.JakartaValidationMode;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.FilerException;
import javax.annotation.processing.Generated;
import javax.annotation.processing.Messager;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.NestingKind;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.RecordComponentElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.IntersectionType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.type.UnionType;
import javax.lang.model.type.WildcardType;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.StringJoiner;

/**
 * JSR 269 processor that generates companion builders without modifying javac ASTs.
 */
@SupportedAnnotationTypes({
        "io.github.jdkbuilder.Buildable",
        "io.github.jdkbuilder.BuilderConstructor",
        "io.github.jdkbuilder.BuilderFactory",
        "io.github.jdkbuilder.BuilderDefault",
        "io.github.jdkbuilder.BuilderRequired",
        "io.github.jdkbuilder.BuilderAccessor",
        "io.github.jdkbuilder.BuilderAdder",
        "io.github.jdkbuilder.BuilderNoAdder",
        "javax.annotation.processing.Generated"
})
@SupportedSourceVersion(SourceVersion.RELEASE_21)
public final class BuildableProcessor extends AbstractProcessor {
    private static final String A_BUILDABLE = Buildable.class.getCanonicalName();
    private static final String A_CONSTRUCTOR = BuilderConstructor.class.getCanonicalName();
    private static final String A_FACTORY = BuilderFactory.class.getCanonicalName();
    private static final String A_DEFAULT = BuilderDefault.class.getCanonicalName();
    private static final String A_REQUIRED = BuilderRequired.class.getCanonicalName();
    private static final String A_ACCESSOR = BuilderAccessor.class.getCanonicalName();
    private static final String A_ADDER = BuilderAdder.class.getCanonicalName();
    private static final String A_NO_ADDER = BuilderNoAdder.class.getCanonicalName();
    private static final String JAKARTA_VALIDATOR = "jakarta.validation.Validator";
    private static final String JAKARTA_CONSTRAINT_VIOLATION = "jakarta.validation.ConstraintViolation";
    private static final String JAKARTA_CONSTRAINT_VIOLATION_EXCEPTION =
            "jakarta.validation.ConstraintViolationException";
    private static final String JAKARTA_EXECUTABLE_VALIDATOR =
            "jakarta.validation.executable.ExecutableValidator";

    private Elements elements;
    private Types types;
    private Messager messager;
    private final Map<String, String> generatedTypeOwners = new HashMap<>();
    private final Set<String> sourceBuildableTypes = new LinkedHashSet<>();

    @Override
    public synchronized void init(javax.annotation.processing.ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        this.elements = processingEnv.getElementUtils();
        this.types = processingEnv.getTypeUtils();
        this.messager = processingEnv.getMessager();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        for (Element element : roundEnv.getElementsAnnotatedWith(Buildable.class)) {
            if (element instanceof TypeElement type) {
                sourceBuildableTypes.add(type.getQualifiedName().toString());
            }
        }
        for (Element element : roundEnv.getElementsAnnotatedWith(Buildable.class)) {
            if (!(element instanceof TypeElement type)) {
                error(element, "@Buildable can only be applied to a class or record");
                continue;
            }
            try {
                generate(type);
            } catch (ModelException ex) {
                error(ex.element == null ? type : ex.element, ex.getMessage());
            } catch (FilerException ex) {
                error(type, "Could not create generated builder type '" + generatedName(type)
                        + "' for '" + type.getQualifiedName() + "': " + ex.getMessage());
            } catch (IOException ex) {
                error(type, "Could not write builder source: " + ex.getMessage());
            } catch (RuntimeException ex) {
                error(type, "Unexpected builder processor failure: " + ex);
            }
        }
        return true;
    }

    private void generate(TypeElement type) throws IOException {
        Config config = readConfig(type);
        validateConfig(type, config);
        Construction construction = selectConstruction(type, config);
        validateTarget(type, construction, config);
        validateJakartaValidationAvailability(type, config, construction);

        List<Property> properties;
        if (construction.kind == ConstructionKind.RECORD_CONSTRUCTOR) {
            properties = propertiesForRecord(type, config);
        } else {
            properties = propertiesForExecutable(type, construction.executable, config);
        }

        validateGeneratedTypeAccessibility(type, properties, construction, config);
        validateGeneratedSymbols(type, properties, construction, config);

        String packageName = config.builderPackage;
        String builderSimpleName = config.builderClassName;
        String generatedName = packageName.isEmpty()
                ? builderSimpleName
                : packageName + "." + builderSimpleName;

        String targetName = type.getQualifiedName().toString();
        String previousOwner = generatedTypeOwners.putIfAbsent(generatedName, targetName);
        if (previousOwner != null && previousOwner.equals(targetName)) {
            return;
        }
        if (previousOwner != null) {
            throw fail(type, "Generated builder type collision: '" + generatedName
                    + "' is requested by both '" + previousOwner + "' and '" + targetName + "'.");
        }

        TypeElement existingType = elements.getTypeElement(generatedName);
        if (existingType != null) {
            throw fail(type, "Generated builder type '" + generatedName
                    + "' conflicts with an existing source or classpath type. "
                    + "Remove or rename the existing type before generating a builder for '"
                    + targetName + "'.");
        }

        JavaFileObject file = processingEnv.getFiler().createSourceFile(generatedName, type);
        try (Writer writer = file.openWriter()) {
            writer.write(render(type, properties, construction, config));
        }
    }

    private void validateTarget(TypeElement type, Construction construction, Config config) {
        boolean factoryBacked = construction.kind == ConstructionKind.FACTORY;
        if (type.getKind() != ElementKind.CLASS
                && type.getKind() != ElementKind.RECORD
                && !(factoryBacked && type.getKind() == ElementKind.INTERFACE)) {
            throw fail(type, factoryBacked
                    ? "A factory-backed @Buildable target must be a class, record, or interface"
                    : "@Buildable supports only classes and records unless @BuilderFactory is used");
        }
        if (type.getModifiers().contains(Modifier.PRIVATE)) {
            throw fail(type, "A private type cannot have an external generated builder");
        }
        if (!factoryBacked && type.getModifiers().contains(Modifier.ABSTRACT)) {
            throw fail(type, "An abstract class requires a @BuilderFactory that returns a concrete value");
        }
        if (type.getNestingKind() == NestingKind.MEMBER
                && !type.getModifiers().contains(Modifier.STATIC)) {
            throw fail(type, "A non-static inner class requires an enclosing instance and is not supported");
        }

        String targetPackage = packageName(type);
        if (!config.builderPackage.equals(targetPackage)) {
            ensureTypePubliclyAccessible(type);
            if (construction.executable != null
                    && !construction.executable.getModifiers().contains(Modifier.PUBLIC)) {
                throw fail(construction.executable,
                        "A builder generated in package '" + config.builderPackage
                                + "' requires a public constructor or factory method");
            }
        }
    }

    private void ensureTypePubliclyAccessible(TypeElement type) {
        Element cursor = type;
        while (cursor instanceof TypeElement current) {
            if (!current.getModifiers().contains(Modifier.PUBLIC)) {
                throw fail(type, "A builder generated in a different package requires the target "
                        + "and every enclosing type to be public");
            }
            cursor = current.getEnclosingElement();
        }
    }

    private Config readConfig(TypeElement type) {
        AnnotationMirror mirror = annotation(type, A_BUILDABLE)
                .orElseThrow(() -> fail(type, "Internal error: missing @Buildable mirror"));
        Map<String, AnnotationValue> values = valuesWithDefaults(mirror);
        String targetPackage = packageName(type);
        String requestedBuilderName = stringValue(values, "builderClassName");
        String requestedBuilderPackage = stringValue(values, "builderPackage");
        return new Config(
                booleanValue(values, "generateFrom"),
                booleanValue(values, "collectionAdders"),
                booleanValue(values, "collectionRemovers"),
                booleanValue(values, "defensiveCopyCollections"),
                booleanValue(values, "defensiveCopyArrays"),
                enumValue(values, "jakartaValidation", JakartaValidationMode.class),
                booleanValue(values, "builderCallbacks"),
                stringValue(values, "callbackMethod"),
                stringValue(values, "customizeMethod"),
                stringValue(values, "nestedCallbackSuffix"),
                requestedBuilderName.isBlank() ? defaultBuilderSimpleName(type) : requestedBuilderName,
                requestedBuilderPackage.isBlank() ? targetPackage : requestedBuilderPackage,
                stringValue(values, "setterPrefix"),
                stringValue(values, "builderMethod"),
                stringValue(values, "buildMethod"),
                stringValue(values, "fromMethod"),
                stringValue(values, "toBuilderMethod"),
                stringValue(values, "copyFromMethod"),
                enumValue(values, "access", BuilderAccess.class)
        );
    }

    private void validateConfig(TypeElement type, Config config) {
        validateIdentifier(type, "builderClassName", config.builderClassName);
        validatePackageName(type, config.builderPackage);
        if (packageName(type).isEmpty() && !config.builderPackage.isEmpty()) {
            throw fail(type, "A type in the unnamed package cannot use a named builderPackage");
        }
        if (!config.setterPrefix.isEmpty() && !isJavaIdentifier(config.setterPrefix)) {
            throw fail(type, "@Buildable setterPrefix must be empty or a Java identifier: "
                    + config.setterPrefix);
        }
        validateIdentifier(type, "builderMethod", config.builderMethod);
        validateIdentifier(type, "buildMethod", config.buildMethod);
        if (config.builderCallbacks) {
            validateIdentifier(type, "callbackMethod", config.callbackMethod);
            validateIdentifier(type, "customizeMethod", config.customizeMethod);
            validateIdentifier(type, "nestedCallbackSuffix", config.nestedCallbackSuffix);
        }
        if (config.generateFrom) {
            validateIdentifier(type, "fromMethod", config.fromMethod);
            validateIdentifier(type, "toBuilderMethod", config.toBuilderMethod);
            validateIdentifier(type, "copyFromMethod", config.copyFromMethod);
        }
    }

    private void validateIdentifier(Element element, String member, String value) {
        if (!isJavaIdentifier(value)) {
            throw fail(element, "@Buildable " + member + " must be a Java identifier: " + value);
        }
    }

    private void validatePackageName(Element element, String value) {
        if (value.isEmpty()) {
            return;
        }
        for (String part : value.split("\\.", -1)) {
            if (!isJavaIdentifier(part)) {
                throw fail(element, "@Buildable builderPackage must be a valid package name: " + value);
            }
        }
    }

    private Construction selectConstruction(TypeElement type, Config config) {
        List<ExecutableElement> factories = ElementFilter.methodsIn(type.getEnclosedElements()).stream()
                .filter(method -> hasAnnotation(method, A_FACTORY))
                .toList();
        if (factories.size() > 1) {
            throw fail(type, "Exactly one static method may be annotated @BuilderFactory");
        }

        List<ExecutableElement> selectedConstructors = ElementFilter
                .constructorsIn(type.getEnclosedElements()).stream()
                .filter(constructor -> hasAnnotation(constructor, A_CONSTRUCTOR))
                .toList();
        if (!factories.isEmpty() && !selectedConstructors.isEmpty()) {
            throw fail(type, "Use either @BuilderFactory or @BuilderConstructor, not both");
        }
        if (!factories.isEmpty()) {
            ExecutableElement factory = factories.get(0);
            validateFactory(type, factory, config);
            return new Construction(ConstructionKind.FACTORY, factory);
        }
        if (type.getKind() == ElementKind.INTERFACE) {
            throw fail(type, "An interface requires exactly one static @BuilderFactory method");
        }
        if (type.getKind() == ElementKind.RECORD) {
            return new Construction(ConstructionKind.RECORD_CONSTRUCTOR, null);
        }

        ExecutableElement constructor = selectConstructor(type);
        validateConstructor(type, constructor, config);
        return new Construction(ConstructionKind.CONSTRUCTOR, constructor);
    }

    private void validateFactory(TypeElement type, ExecutableElement factory, Config config) {
        if (!factory.getModifiers().contains(Modifier.STATIC)) {
            throw fail(factory, "@BuilderFactory method must be static");
        }
        if (factory.getModifiers().contains(Modifier.PRIVATE)) {
            throw fail(factory, "@BuilderFactory method cannot be private");
        }
        if (!factory.getTypeParameters().isEmpty()) {
            validateGenericFactory(type, factory);
        } else if (!types.isAssignable(factory.getReturnType(), type.asType())) {
            throw fail(factory, "@BuilderFactory return type " + factory.getReturnType()
                    + " is not assignable to " + type.asType());
        }
        if (!config.builderPackage.equals(packageName(type))
                && !factory.getModifiers().contains(Modifier.PUBLIC)) {
            throw fail(factory, "A factory used from another builder package must be public");
        }
    }

    private void validateGenericFactory(TypeElement type, ExecutableElement factory) {
        List<? extends TypeParameterElement> targetParameters = type.getTypeParameters();
        List<? extends TypeParameterElement> factoryParameters = factory.getTypeParameters();
        if (targetParameters.size() != factoryParameters.size()) {
            throw fail(factory, "Generic @BuilderFactory must declare the same type parameters as "
                    + type.getQualifiedName());
        }
        for (int index = 0; index < targetParameters.size(); index++) {
            TypeParameterElement targetParameter = targetParameters.get(index);
            TypeParameterElement factoryParameter = factoryParameters.get(index);
            if (!targetParameter.getSimpleName().contentEquals(factoryParameter.getSimpleName())) {
                throw fail(factory, "Generic @BuilderFactory type parameter " + factoryParameter
                        + " must match target type parameter " + targetParameter
                        + " by name and position");
            }
            List<String> targetBounds = targetParameter.getBounds().stream()
                    .map(this::sourceType).filter(bound -> !bound.equals("java.lang.Object")).toList();
            List<String> factoryBounds = factoryParameter.getBounds().stream()
                    .map(this::sourceType).filter(bound -> !bound.equals("java.lang.Object")).toList();
            if (!targetBounds.equals(factoryBounds)) {
                throw fail(factory, "Generic @BuilderFactory type parameter bounds for "
                        + factoryParameter.getSimpleName() + " must match the target type bounds");
            }
        }
        if (!(factory.getReturnType() instanceof DeclaredType returnType)
                || !types.isSameType(types.erasure(returnType), types.erasure(type.asType()))) {
            throw fail(factory, "Generic @BuilderFactory must return " + type.getQualifiedName()
                    + " parameterized by its factory type parameters");
        }
        List<? extends TypeMirror> returnArguments = returnType.getTypeArguments();
        if (returnArguments.size() != factoryParameters.size()) {
            throw fail(factory, "Generic @BuilderFactory return type must preserve all target type parameters");
        }
        for (int index = 0; index < returnArguments.size(); index++) {
            if (!(returnArguments.get(index) instanceof TypeVariable variable)
                    || !variable.asElement().equals(factoryParameters.get(index))) {
                throw fail(factory, "Generic @BuilderFactory return type must preserve type parameter '"
                        + factoryParameters.get(index).getSimpleName() + "'");
            }
        }
    }

    private void validateJakartaValidationAvailability(
            TypeElement type,
            Config config,
            Construction construction
    ) {
        if (config.jakartaValidation == JakartaValidationMode.NONE) {
            return;
        }
        List<String> missing = new ArrayList<>();
        List<String> requiredTypes = new ArrayList<>(List.of(
                JAKARTA_VALIDATOR,
                JAKARTA_CONSTRAINT_VIOLATION,
                JAKARTA_CONSTRAINT_VIOLATION_EXCEPTION
        ));
        if (construction.kind != ConstructionKind.FACTORY) {
            requiredTypes.add(JAKARTA_EXECUTABLE_VALIDATOR);
        }
        for (String requiredType : requiredTypes) {
            if (elements.getTypeElement(requiredType) == null) {
                missing.add(requiredType);
            }
        }
        if (!missing.isEmpty()) {
            throw fail(type,
                    "Jakarta Validation mode " + config.jakartaValidation
                            + " requires jakarta.validation-api on the compilation classpath; "
                            + "missing " + String.join(", ", missing));
        }
    }

    private List<Property> propertiesForRecord(TypeElement type, Config config) {
        List<Property> result = new ArrayList<>();
        int index = 0;
        for (RecordComponentElement component : type.getRecordComponents()) {
            String name = component.getSimpleName().toString();
            result.add(property(type, component, index++, name,
                    component.asType(), new Accessor(name, List.of()), config));
        }
        return List.copyOf(result);
    }

    private ExecutableElement selectConstructor(TypeElement type) {
        List<ExecutableElement> constructors = ElementFilter.constructorsIn(type.getEnclosedElements());
        List<ExecutableElement> selected = constructors.stream()
                .filter(c -> hasAnnotation(c, A_CONSTRUCTOR))
                .toList();

        if (selected.size() > 1) {
            throw fail(type, "Exactly one constructor may be annotated @BuilderConstructor");
        }
        if (selected.size() == 1) {
            return selected.get(0);
        }
        if (constructors.size() == 1) {
            return constructors.get(0);
        }
        throw fail(type,
                "Class has multiple constructors; annotate exactly one with @BuilderConstructor");
    }

    private void validateConstructor(
            TypeElement type,
            ExecutableElement constructor,
            Config config
    ) {
        if (constructor.getModifiers().contains(Modifier.PRIVATE)) {
            throw fail(constructor, "The builder constructor cannot be private");
        }
        if (!constructor.getTypeParameters().isEmpty()) {
            throw fail(constructor, "Generic constructors are not supported; put type parameters on the class");
        }
        if (!config.builderPackage.equals(packageName(type))
                && !constructor.getModifiers().contains(Modifier.PUBLIC)) {
            throw fail(constructor, "A constructor used from another builder package must be public");
        }
    }

    private List<Property> propertiesForExecutable(
            TypeElement type,
            ExecutableElement executable,
            Config config
    ) {
        List<Property> result = new ArrayList<>();
        int index = 0;
        for (VariableElement parameter : executable.getParameters()) {
            String name = parameter.getSimpleName().toString();
            Accessor accessor = config.generateFrom
                    ? resolveAccessor(type, parameter, name, config.builderPackage)
                    : Accessor.NONE;
            result.add(property(type, parameter, index++, name, parameter.asType(), accessor, config));
        }
        return List.copyOf(result);
    }

    private Property property(
            TypeElement owner,
            Element source,
            int index,
            String name,
            TypeMirror type,
            Accessor accessor,
            Config config
    ) {
        boolean required = hasAnnotation(source, A_REQUIRED);
        String defaultProvider = stringAnnotationValue(source, A_DEFAULT, "value");
        if (defaultProvider != null) {
            if (defaultProvider.isBlank() || !isJavaIdentifier(defaultProvider)) {
                throw fail(source, "@BuilderDefault value must be a Java method name: "
                        + defaultProvider);
            }
            if (required) {
                throw fail(source, "A property cannot be both @BuilderRequired and @BuilderDefault");
            }
            validateDefaultProvider(owner, source, type, defaultProvider, config.builderPackage);
        }

        String explicitAdder = stringAnnotationValue(source, A_ADDER, "value");
        boolean suppressAdder = hasAnnotation(source, A_NO_ADDER);
        CollectionKind collectionKind = collectionKind(type);
        boolean array = type.getKind() == TypeKind.ARRAY;
        if (config.defensiveCopyCollections && collectionKind == CollectionKind.UNSUPPORTED) {
            throw fail(source,
                    "Collection type '" + type + "' cannot be copied to a guaranteed immutable "
                            + "value while preserving its declared type. Declare the property as one of "
                            + "Collection, List, Set, SequencedCollection, SequencedSet, SortedSet, "
                            + "NavigableSet, Map, SequencedMap, SortedMap, or NavigableMap; or explicitly "
                            + "set @Buildable(defensiveCopyCollections = false).");
        }
        String adderWord = explicitAdder != null ? explicitAdder : singularize(name);

        if (explicitAdder != null && explicitAdder.isBlank()) {
            throw fail(source, "@BuilderAdder value must not be blank");
        }
        if (explicitAdder != null && !isJavaIdentifier(explicitAdder)) {
            throw fail(source, "@BuilderAdder value must be a valid Java identifier: " + explicitAdder);
        }
        if (explicitAdder != null
                && (collectionKind == CollectionKind.NONE || collectionKind == CollectionKind.UNSUPPORTED)) {
            throw fail(source, "@BuilderAdder is only valid for supported collection properties");
        }

        TypeArguments arguments = typeArguments(type, collectionKind);
        boolean helperCapable = !suppressAdder
                && collectionKind != CollectionKind.NONE
                && collectionKind != CollectionKind.UNSUPPORTED
                && supportsAdderMutation(type);
        boolean adders = config.collectionAdders && helperCapable;
        boolean removers = config.collectionRemovers && helperCapable;

        CallbackTarget valueCallback = null;
        CallbackTarget elementCallback = null;
        CallbackTarget mapValueCallback = null;
        if (config.builderCallbacks) {
            if (collectionKind == CollectionKind.NONE && !array) {
                valueCallback = callbackTarget(type, config,
                        config.jakartaValidation == JakartaValidationMode.REQUIRED, source);
            } else if (type instanceof DeclaredType declared) {
                List<? extends TypeMirror> declaredArguments = declared.getTypeArguments();
                if (isMapKind(collectionKind) && declaredArguments.size() == 2) {
                    mapValueCallback = callbackTarget(
                            declaredArguments.get(1),
                            config,
                            config.jakartaValidation == JakartaValidationMode.REQUIRED,
                            source
                    );
                } else if (!isMapKind(collectionKind)
                        && collectionKind != CollectionKind.UNSUPPORTED
                        && declaredArguments.size() == 1) {
                    elementCallback = callbackTarget(
                            declaredArguments.get(0),
                            config,
                            config.jakartaValidation == JakartaValidationMode.REQUIRED,
                            source
                    );
                }
            }
        }

        return new Property(
                index,
                name,
                type,
                sourceType(type),
                erasedTypeName(type),
                type.getKind().isPrimitive(),
                array,
                required,
                defaultProvider,
                accessor.name,
                accessor.thrownTypes,
                collectionKind,
                arguments.first,
                arguments.firstErased,
                arguments.second,
                arguments.secondErased,
                adders,
                removers,
                adderWord,
                valueCallback,
                elementCallback,
                mapValueCallback,
                source
        );
    }

    private CallbackTarget callbackTarget(
            TypeMirror candidateType,
            Config parentConfig,
            boolean allowRequiredValidation,
            Element context
    ) {
        return callbackTarget(
                candidateType,
                parentConfig,
                allowRequiredValidation,
                context,
                new LinkedHashSet<>()
        );
    }

    private CallbackTarget callbackTarget(
            TypeMirror candidateType,
            Config parentConfig,
            boolean allowRequiredValidation,
            Element context,
            Set<String> visiting
    ) {
        if (!(candidateType instanceof DeclaredType declared)
                || declared.getTypeArguments().stream()
                .anyMatch(argument -> argument.getKind() == TypeKind.WILDCARD)) {
            return null;
        }
        if (!(declared.asElement() instanceof TypeElement target)
                || !hasAnnotation(target, A_BUILDABLE)) {
            return null;
        }

        if (!target.getTypeParameters().isEmpty() && declared.getTypeArguments().isEmpty()) {
            return null;
        }

        Config childConfig = readConfig(target);
        validateConfig(target, childConfig);
        if (!childConfig.builderCallbacks) {
            return null;
        }
        if (childConfig.access == BuilderAccess.PACKAGE_PRIVATE
                && !childConfig.builderPackage.equals(parentConfig.builderPackage)) {
            return null;
        }
        if (childConfig.jakartaValidation == JakartaValidationMode.REQUIRED
                && !allowRequiredValidation) {
            return null;
        }

        Construction childConstruction = selectConstruction(target, childConfig);
        String qualifiedBuilderName = childConfig.builderPackage.isEmpty()
                ? childConfig.builderClassName
                : childConfig.builderPackage + "." + childConfig.builderClassName;
        verifyClasspathBuilder(target, childConfig, qualifiedBuilderName, context);
        StringBuilder builderType = new StringBuilder(qualifiedBuilderName);
        if (!declared.getTypeArguments().isEmpty()) {
            StringJoiner arguments = new StringJoiner(", ", "<", ">");
            for (TypeMirror argument : declared.getTypeArguments()) {
                arguments.add(sourceType(argument));
            }
            builderType.append(arguments);
        }
        return new CallbackTarget(
                sourceType(candidateType),
                qualifiedBuilderName,
                builderType.toString(),
                childConfig.builderMethod,
                childConfig.buildMethod,
                childConfig.jakartaValidation,
                callbackBuildThrownTypes(target, childConfig, childConstruction, visiting)
        );
    }

    private void verifyClasspathBuilder(
            TypeElement target,
            Config config,
            String qualifiedBuilderName,
            Element context
    ) {
        if (sourceBuildableTypes.contains(target.getQualifiedName().toString())) {
            return;
        }
        TypeElement builder = elements.getTypeElement(qualifiedBuilderName);
        if (builder == null) {
            throw fail(context, "Callback target '" + target.getQualifiedName()
                    + "' declares @Buildable, but generated builder '" + qualifiedBuilderName
                    + "' was not found. Compile and publish the generated builder with the child artifact.");
        }
        boolean hasBuilderFactory = ElementFilter.methodsIn(builder.getEnclosedElements()).stream()
                .anyMatch(method -> method.getSimpleName().contentEquals(config.builderMethod)
                        && method.getModifiers().contains(Modifier.STATIC)
                        && method.getParameters().isEmpty());
        if (!hasBuilderFactory) {
            throw fail(context, "Generated builder '" + qualifiedBuilderName
                    + "' is stale or incompatible: expected static " + config.builderMethod + "().");
        }
        boolean hasPlainBuild = ElementFilter.methodsIn(builder.getEnclosedElements()).stream()
                .anyMatch(method -> method.getSimpleName().contentEquals(config.buildMethod)
                        && !method.getModifiers().contains(Modifier.STATIC)
                        && method.getParameters().isEmpty());
        boolean hasValidatedBuild = ElementFilter.methodsIn(builder.getEnclosedElements()).stream()
                .anyMatch(method -> method.getSimpleName().contentEquals(config.buildMethod)
                        && !method.getModifiers().contains(Modifier.STATIC)
                        && method.getParameters().size() == 2
                        && erasedTypeName(method.getParameters().get(0).asType()).equals(JAKARTA_VALIDATOR)
                        && erasedTypeName(method.getParameters().get(1).asType())
                        .equals("java.lang.Class[]"));
        if (config.jakartaValidation != JakartaValidationMode.REQUIRED && !hasPlainBuild) {
            throw fail(context, "Generated builder '" + qualifiedBuilderName
                    + "' is stale or incompatible: expected " + config.buildMethod + "().");
        }
        if (config.jakartaValidation != JakartaValidationMode.NONE && !hasValidatedBuild) {
            throw fail(context, "Generated builder '" + qualifiedBuilderName
                    + "' is stale or incompatible: expected " + config.buildMethod
                    + "(jakarta.validation.Validator, Class<?>...).");
        }
    }

    private List<? extends TypeMirror> callbackBuildThrownTypes(
            TypeElement target,
            Config config,
            Construction construction,
            Set<String> visiting
    ) {
        Map<String, TypeMirror> unique = new LinkedHashMap<>();
        for (TypeMirror thrown : construction.thrownTypes()) {
            unique.putIfAbsent(sourceType(thrown), thrown);
        }

        String key = target.getQualifiedName() + "|" + config.builderPackage;
        if (!config.builderCallbacks || !visiting.add(key)) {
            return List.copyOf(unique.values());
        }
        try {
            List<? extends Element> inputs;
            if (construction.kind == ConstructionKind.RECORD_CONSTRUCTOR) {
                inputs = target.getRecordComponents();
            } else {
                inputs = construction.executable.getParameters();
            }
            for (Element input : inputs) {
                TypeMirror inputType = input.asType();
                if (inputType.getKind() == TypeKind.ARRAY) {
                    continue;
                }
                CollectionKind kind = collectionKind(inputType);
                List<TypeMirror> callbackCandidates = new ArrayList<>();
                if (kind == CollectionKind.NONE) {
                    callbackCandidates.add(inputType);
                } else if (inputType instanceof DeclaredType declared) {
                    List<? extends TypeMirror> arguments = declared.getTypeArguments();
                    if (isMapKind(kind) && arguments.size() == 2) {
                        callbackCandidates.add(arguments.get(1));
                    } else if (kind != CollectionKind.UNSUPPORTED && arguments.size() == 1) {
                        callbackCandidates.add(arguments.get(0));
                    }
                }
                for (TypeMirror callbackCandidate : callbackCandidates) {
                    CallbackTarget nested = callbackTarget(
                            callbackCandidate,
                            config,
                            config.jakartaValidation == JakartaValidationMode.REQUIRED,
                            input,
                            visiting
                    );
                    if (nested == null) {
                        continue;
                    }
                    for (TypeMirror thrown : nested.thrownTypes) {
                        unique.putIfAbsent(sourceType(thrown), thrown);
                    }
                }
            }
            return List.copyOf(unique.values());
        } finally {
            visiting.remove(key);
        }
    }

    private void validateDefaultProvider(
            TypeElement owner,
            Element property,
            TypeMirror propertyType,
            String methodName,
            String builderPackage
    ) {
        ExecutableElement provider = null;
        for (ExecutableElement method : ElementFilter.methodsIn(elements.getAllMembers(owner))) {
            if (method.getSimpleName().contentEquals(methodName)
                    && method.getParameters().isEmpty()
                    && method.getModifiers().contains(Modifier.STATIC)) {
                provider = method;
                break;
            }
        }
        if (provider == null) {
            throw fail(property, "@BuilderDefault provider '" + methodName
                    + "()' must be a zero-argument static method on " + owner.getQualifiedName());
        }
        TypeElement declaringType = enclosingType(provider);
        String declaringPackage = declaringType == null ? packageName(owner) : packageName(declaringType);
        if (provider.getModifiers().contains(Modifier.PRIVATE)
                || (!builderPackage.equals(declaringPackage)
                && !provider.getModifiers().contains(Modifier.PUBLIC))) {
            throw fail(property, "@BuilderDefault provider '" + methodName
                    + "()' is not accessible from generated package '" + builderPackage + "'");
        }
        if (!provider.getTypeParameters().isEmpty()) {
            throw fail(property, "Generic @BuilderDefault providers are not supported");
        }
        if (!types.isAssignable(provider.getReturnType(), propertyType)) {
            throw fail(property, "@BuilderDefault provider return type " + provider.getReturnType()
                    + " is not assignable to property type " + propertyType);
        }
        for (TypeMirror thrown : provider.getThrownTypes()) {
            if (!isUncheckedException(thrown)) {
                throw fail(property, "@BuilderDefault provider must not declare checked exception "
                        + thrown);
            }
        }
    }

    private boolean isUncheckedException(TypeMirror type) {
        TypeElement runtime = elements.getTypeElement("java.lang.RuntimeException");
        TypeElement errorType = elements.getTypeElement("java.lang.Error");
        return (runtime != null && types.isAssignable(type, runtime.asType()))
                || (errorType != null && types.isAssignable(type, errorType.asType()));
    }

    private Accessor resolveAccessor(
            TypeElement type,
            VariableElement parameter,
            String propertyName,
            String builderPackage
    ) {
        String explicit = stringAnnotationValue(parameter, A_ACCESSOR, "value");
        if (explicit != null) {
            if (!isJavaIdentifier(explicit)) {
                throw fail(parameter, "@BuilderAccessor value must be a Java method name: " + explicit);
            }
            ExecutableElement method = findAccessibleZeroArgMethod(type, explicit, builderPackage);
            if (method == null) {
                throw fail(parameter,
                        "Configured copy accessor '" + explicit + "()' does not exist or is inaccessible");
            }
            ensureAccessorCompatible(type, parameter, method);
            return new Accessor(explicit, checkedThrownTypes(accessorThrownTypes(type, method)));
        }

        String cap = capitalize(propertyName);
        List<String> candidates = new ArrayList<>();
        candidates.add(propertyName);
        candidates.add("get" + cap);
        if (isBoolean(parameter.asType())) {
            candidates.add("is" + cap);
        }

        for (String candidate : candidates) {
            ExecutableElement method = findAccessibleZeroArgMethod(type, candidate, builderPackage);
            if (method != null && types.isAssignable(accessorReturnType(type, method), parameter.asType())) {
                return new Accessor(candidate, checkedThrownTypes(accessorThrownTypes(type, method)));
            }
        }

        throw fail(parameter,
                "Cannot generate from/copyFrom: no accessible zero-argument accessor for constructor "
                        + "parameter '" + propertyName + "'. Expected " + String.join("(), ", candidates)
                        + "(), or annotate it with @BuilderAccessor. Alternatively set "
                        + "@Buildable(generateFrom = false).");
    }

    private void ensureAccessorCompatible(
            TypeElement target,
            VariableElement parameter,
            ExecutableElement method
    ) {
        TypeMirror returnType = accessorReturnType(target, method);
        if (!types.isAssignable(returnType, parameter.asType())) {
            throw fail(parameter,
                    "Accessor return type " + returnType
                            + " is not assignable to constructor parameter type " + parameter.asType());
        }
    }

    private TypeMirror accessorReturnType(TypeElement target, ExecutableElement method) {
        javax.lang.model.type.ExecutableType executableType = accessorExecutableType(target, method);
        return executableType == null ? method.getReturnType() : executableType.getReturnType();
    }

    private List<? extends TypeMirror> accessorThrownTypes(
            TypeElement target,
            ExecutableElement method
    ) {
        javax.lang.model.type.ExecutableType executableType = accessorExecutableType(target, method);
        return executableType == null ? method.getThrownTypes() : executableType.getThrownTypes();
    }

    private javax.lang.model.type.ExecutableType accessorExecutableType(
            TypeElement target,
            ExecutableElement method
    ) {
        if (!(target.asType() instanceof DeclaredType declaredTarget)) {
            return null;
        }
        TypeMirror member = types.asMemberOf(declaredTarget, method);
        return member instanceof javax.lang.model.type.ExecutableType executableType
                ? executableType
                : null;
    }

    private List<? extends TypeMirror> checkedThrownTypes(List<? extends TypeMirror> thrownTypes) {
        return thrownTypes.stream().filter(thrown -> !isUncheckedException(thrown)).toList();
    }

    private ExecutableElement findAccessibleZeroArgMethod(
            TypeElement target,
            String name,
            String builderPackage
    ) {
        String targetPackage = packageName(target);
        for (ExecutableElement method : ElementFilter.methodsIn(elements.getAllMembers(target))) {
            if (!method.getSimpleName().contentEquals(name)
                    || !method.getParameters().isEmpty()
                    || method.getModifiers().contains(Modifier.STATIC)) {
                continue;
            }
            if (method.getModifiers().contains(Modifier.PUBLIC)) {
                return method;
            }
            if (method.getModifiers().contains(Modifier.PRIVATE)) {
                continue;
            }
            TypeElement declaring = enclosingType(method);
            if (declaring != null
                    && packageName(declaring).equals(targetPackage)
                    && builderPackage.equals(targetPackage)) {
                return method;
            }
        }
        return null;
    }

    private void validateGeneratedTypeAccessibility(
            TypeElement type,
            List<Property> properties,
            Construction construction,
            Config config
    ) {
        if (config.builderPackage.equals(packageName(type))) {
            return;
        }
        for (TypeParameterElement parameter : type.getTypeParameters()) {
            for (TypeMirror bound : parameter.getBounds()) {
                ensureTypeMirrorAccessible(bound, config.builderPackage, parameter);
            }
        }
        for (Property property : properties) {
            ensureTypeMirrorAccessible(property.mirror, config.builderPackage, property.source);
            for (TypeMirror thrown : property.accessorThrownTypes) {
                ensureTypeMirrorAccessible(thrown, config.builderPackage, property.source);
            }
        }
        for (TypeMirror thrown : construction.thrownTypes()) {
            ensureTypeMirrorAccessible(thrown, config.builderPackage,
                    construction.executable == null ? type : construction.executable);
        }
        if (construction.executable != null) {
            for (TypeParameterElement parameter : construction.executable.getTypeParameters()) {
                for (TypeMirror bound : parameter.getBounds()) {
                    ensureTypeMirrorAccessible(bound, config.builderPackage, parameter);
                }
            }
        }
    }

    private void ensureTypeMirrorAccessible(
            TypeMirror mirror,
            String generatedPackage,
            Element context
    ) {
        ensureTypeMirrorAccessible(mirror, generatedPackage, context, new LinkedHashSet<>());
    }

    private void ensureTypeMirrorAccessible(
            TypeMirror mirror,
            String generatedPackage,
            Element context,
            Set<String> visiting
    ) {
        String key = mirror.getKind() + ":" + mirror;
        if (!visiting.add(key)) {
            return;
        }
        try {
            switch (mirror.getKind()) {
                case ARRAY -> ensureTypeMirrorAccessible(
                        ((ArrayType) mirror).getComponentType(), generatedPackage, context, visiting);
                case DECLARED, ERROR -> {
                    DeclaredType declared = (DeclaredType) mirror;
                    if (declared.asElement() instanceof TypeElement type) {
                        ensureDeclaredTypeAccessible(type, generatedPackage, context);
                    }
                    if (declared.getEnclosingType().getKind() != TypeKind.NONE) {
                        ensureTypeMirrorAccessible(
                                declared.getEnclosingType(), generatedPackage, context, visiting);
                    }
                    for (TypeMirror argument : declared.getTypeArguments()) {
                        ensureTypeMirrorAccessible(argument, generatedPackage, context, visiting);
                    }
                }
                case TYPEVAR -> {
                    TypeVariable variable = (TypeVariable) mirror;
                    if (variable.getUpperBound().getKind() != TypeKind.NULL
                            && variable.getUpperBound().getKind() != TypeKind.NONE) {
                        ensureTypeMirrorAccessible(
                                variable.getUpperBound(), generatedPackage, context, visiting);
                    }
                    if (variable.getLowerBound().getKind() != TypeKind.NULL
                            && variable.getLowerBound().getKind() != TypeKind.NONE) {
                        ensureTypeMirrorAccessible(
                                variable.getLowerBound(), generatedPackage, context, visiting);
                    }
                }
                case WILDCARD -> {
                    WildcardType wildcard = (WildcardType) mirror;
                    if (wildcard.getExtendsBound() != null) {
                        ensureTypeMirrorAccessible(
                                wildcard.getExtendsBound(), generatedPackage, context, visiting);
                    }
                    if (wildcard.getSuperBound() != null) {
                        ensureTypeMirrorAccessible(
                                wildcard.getSuperBound(), generatedPackage, context, visiting);
                    }
                }
                case INTERSECTION -> {
                    for (TypeMirror bound : ((IntersectionType) mirror).getBounds()) {
                        ensureTypeMirrorAccessible(bound, generatedPackage, context, visiting);
                    }
                }
                case UNION -> {
                    for (TypeMirror alternative : ((UnionType) mirror).getAlternatives()) {
                        ensureTypeMirrorAccessible(alternative, generatedPackage, context, visiting);
                    }
                }
                default -> {
                    // Primitive, void, null, none, and package-independent pseudo-types.
                }
            }
        } finally {
            visiting.remove(key);
        }
    }

    private void ensureDeclaredTypeAccessible(
            TypeElement type,
            String generatedPackage,
            Element context
    ) {
        if (packageName(type).equals(generatedPackage)) {
            return;
        }
        Element cursor = type;
        while (cursor instanceof TypeElement current) {
            if (!current.getModifiers().contains(Modifier.PUBLIC)) {
                throw fail(context, "Type '" + type.getQualifiedName()
                        + "' is not accessible from generated builder package '"
                        + generatedPackage + "'. Every referenced type and enclosing type must be public.");
            }
            cursor = current.getEnclosingElement();
        }
    }

    private void validateGeneratedSymbols(
            TypeElement type,
            List<Property> properties,
            Construction construction,
            Config config
    ) {
        Map<String, GeneratedSymbol> fields = new LinkedHashMap<>();
        Map<MethodKey, GeneratedSymbol> methods = new LinkedHashMap<>();

        if (config.jakartaValidation != JakartaValidationMode.NONE
                && construction.kind != ConstructionKind.FACTORY) {
            String fieldName = validationExecutableField(construction);
            putField(fields, fieldName,
                    new GeneratedSymbol(type, "Jakarta Validation executable metadata"));
        }

        for (Property property : properties) {
            putField(fields, property.name,
                    new GeneratedSymbol(property.source, "property '" + property.name + "'"));
            if (tracksAssignment(property)) {
                putField(fields, setFlag(property),
                        new GeneratedSymbol(property.source,
                                "assignment-state field for property '" + property.name + "'"));
            }
            if (needsOwnership(property)) {
                putField(fields, ownedFlag(property),
                        new GeneratedSymbol(property.source,
                                "collection-ownership field for property '" + property.name + "'"));
            }
            if (property.valueCallback != null) {
                putField(fields, callbackBuilderField(property),
                        new GeneratedSymbol(property.source,
                                "nested-builder field for property '" + property.name + "'"));
            }
            if (usesDeferredCollectionCallbacks(property)) {
                putField(fields, deferredOperationsField(property),
                        new GeneratedSymbol(property.source,
                                "deferred collection operation field for property '"
                                        + property.name + "'"));
            }
        }

        putMethod(methods, config.builderMethod, List.of(),
                new GeneratedSymbol(type, "builder factory"));
        if (config.builderCallbacks) {
            putMethod(methods, config.customizeMethod, List.of("java.util.function.Consumer"),
                    new GeneratedSymbol(type, "builder customization callback"));
            if (config.jakartaValidation != JakartaValidationMode.REQUIRED) {
                putMethod(methods, config.callbackMethod, List.of("java.util.function.Function"),
                        new GeneratedSymbol(type, "root builder callback"));
            }
            if (config.jakartaValidation != JakartaValidationMode.NONE) {
                putMethod(methods, config.callbackMethod,
                        List.of(JAKARTA_VALIDATOR, "java.util.function.Function", "java.lang.Class[]"),
                        new GeneratedSymbol(type, "Jakarta-validated root builder callback"));
            }
        }

        String targetErasure = erasedTypeName(type.asType());
        if (config.generateFrom) {
            putMethod(methods, config.fromMethod, List.of(targetErasure),
                    new GeneratedSymbol(type, "copy factory '" + config.fromMethod + "'"));
            putMethod(methods, config.toBuilderMethod, List.of(targetErasure),
                    new GeneratedSymbol(type, "copy factory '" + config.toBuilderMethod + "'"));
            putMethod(methods, config.copyFromMethod, List.of(targetErasure),
                    new GeneratedSymbol(type, "copy method '" + config.copyFromMethod + "'"));
        }

        for (Property property : properties) {
            putMethod(methods, setterName(property, config), List.of(property.erasedType),
                    new GeneratedSymbol(property.source,
                            "setter for property '" + property.name + "'"));
            if (property.valueCallback != null) {
                putMethod(methods, nestedValueCallbackName(property, config),
                        List.of("java.util.function.Function"),
                        new GeneratedSymbol(property.source,
                                "nested builder callback for property '" + property.name + "'"));
            }
            if (!hasCollectionHelpers(property)) {
                continue;
            }

            String singularSuffix = capitalize(property.adderWord);
            String pluralSuffix = capitalize(property.name);
            if (needsOwnership(property)) {
                putMethod(methods, "ensure" + pluralSuffix + "Owned", List.of(),
                        new GeneratedSymbol(property.source,
                                "collection ownership helper for property '" + property.name + "'"));
            }
            switch (property.collectionKind) {
                case COLLECTION, LIST, SEQUENCED_COLLECTION, SET, SEQUENCED_SET,
                        SORTED_SET, NAVIGABLE_SET -> {
                    if (property.generateAdders) {
                        putMethod(methods, "add" + singularSuffix,
                                List.of(property.elementErasedType),
                                new GeneratedSymbol(property.source,
                                        "singular adder for property '" + property.name + "'"));
                        putMethod(methods, "addAll" + pluralSuffix,
                                List.of("java.util.Collection"),
                                new GeneratedSymbol(property.source,
                                        "bulk adder for property '" + property.name + "'"));
                        if (property.elementCallback != null) {
                            putMethod(methods, collectionCallbackName(property, config),
                                    List.of("java.util.function.Function"),
                                    new GeneratedSymbol(property.source,
                                            "nested builder collection adder for property '"
                                                    + property.name + "'"));
                        }
                    }
                    if (property.generateRemovers) {
                        putMethod(methods, "remove" + singularSuffix,
                                List.of(property.elementErasedType),
                                new GeneratedSymbol(property.source,
                                        "singular remover for property '" + property.name + "'"));
                        putMethod(methods, "removeAll" + pluralSuffix,
                                List.of("java.util.Collection"),
                                new GeneratedSymbol(property.source,
                                        "bulk remover for property '" + property.name + "'"));
                        putMethod(methods, "clear" + pluralSuffix, List.of(),
                                new GeneratedSymbol(property.source,
                                        "clear helper for property '" + property.name + "'"));
                    }
                }
                case MAP, SEQUENCED_MAP, SORTED_MAP, NAVIGABLE_MAP -> {
                    if (property.generateAdders) {
                        putMethod(methods, "put" + singularSuffix,
                                List.of(property.keyErasedType, property.elementErasedType),
                                new GeneratedSymbol(property.source,
                                        "map entry adder for property '" + property.name + "'"));
                        putMethod(methods, "putAll" + pluralSuffix,
                                List.of("java.util.Map"),
                                new GeneratedSymbol(property.source,
                                        "bulk map adder for property '" + property.name + "'"));
                        if (property.mapValueCallback != null) {
                            putMethod(methods, mapCallbackName(property, config),
                                    List.of(property.keyErasedType, "java.util.function.Function"),
                                    new GeneratedSymbol(property.source,
                                            "nested builder map-value callback for property '"
                                                    + property.name + "'"));
                        }
                    }
                    if (property.generateRemovers) {
                        putMethod(methods, "remove" + singularSuffix,
                                List.of(property.keyErasedType),
                                new GeneratedSymbol(property.source,
                                        "map entry remover for property '" + property.name + "'"));
                        putMethod(methods, "removeAll" + pluralSuffix,
                                List.of("java.util.Collection"),
                                new GeneratedSymbol(property.source,
                                        "bulk map remover for property '" + property.name + "'"));
                        putMethod(methods, "clear" + pluralSuffix, List.of(),
                                new GeneratedSymbol(property.source,
                                        "clear helper for property '" + property.name + "'"));
                    }
                }
                case NONE, UNSUPPORTED -> {
                }
            }
            if (usesDeferredCollectionCallbacks(property)) {
                if (config.jakartaValidation != JakartaValidationMode.REQUIRED) {
                    putMethod(methods, deferredPrepareMethod(property), List.of(),
                            new GeneratedSymbol(property.source,
                                    "deferred collection preparation for property '"
                                            + property.name + "'"));
                }
                if (config.jakartaValidation != JakartaValidationMode.NONE) {
                    putMethod(methods, deferredPrepareMethod(property),
                            List.of(JAKARTA_VALIDATOR, "java.lang.Class[]"),
                            new GeneratedSymbol(property.source,
                                    "validated deferred collection preparation for property '"
                                            + property.name + "'"));
                }
            }
        }

        putMethod(methods, "validateRequiredProperties", List.of(),
                new GeneratedSymbol(type, "required-property validation helper"));
        if (config.jakartaValidation != JakartaValidationMode.REQUIRED) {
            putMethod(methods, "construct", List.of(),
                    new GeneratedSymbol(type, "construction helper"));
        }

        if (config.jakartaValidation != JakartaValidationMode.NONE
                && construction.kind != ConstructionKind.FACTORY) {
            putMethod(methods, "resolveValidationExecutable", List.of(),
                    new GeneratedSymbol(type, "Jakarta Validation executable resolver"));
            putMethod(methods, config.buildMethod,
                    List.of(JAKARTA_VALIDATOR, "java.lang.Class[]"),
                    new GeneratedSymbol(type, "Jakarta-validated build method"));
        }
        if (config.jakartaValidation != JakartaValidationMode.REQUIRED) {
            putMethod(methods, config.buildMethod, List.of(),
                    new GeneratedSymbol(type, "build method"));
        }
    }

    private void putField(
            Map<String, GeneratedSymbol> fields,
            String name,
            GeneratedSymbol symbol
    ) {
        GeneratedSymbol previous = fields.putIfAbsent(name, symbol);
        if (previous != null) {
            throw fail(symbol.element, "Generated builder field collision for '" + name + "': "
                    + previous.description + " conflicts with " + symbol.description + ".");
        }
    }

    private void putMethod(
            Map<MethodKey, GeneratedSymbol> methods,
            String name,
            List<String> erasedParameterTypes,
            GeneratedSymbol symbol
    ) {
        MethodKey key = new MethodKey(name, List.copyOf(erasedParameterTypes));
        ExecutableElement objectMethod = inheritedObjectMethod(key);
        if (objectMethod != null) {
            throw fail(symbol.element, "Generated builder method '" + key.display()
                    + "' conflicts with inherited java.lang.Object method '"
                    + objectMethod + "'. Configure a different generated method name.");
        }
        GeneratedSymbol previous = methods.putIfAbsent(key, symbol);
        if (previous != null) {
            throw fail(symbol.element, "Generated builder method collision after type erasure for '"
                    + key.display() + "': " + previous.description + " conflicts with "
                    + symbol.description + ". Rename the property or configure an unambiguous "
                    + "@BuilderAdder value.");
        }
    }


    private ExecutableElement inheritedObjectMethod(MethodKey key) {
        TypeElement objectType = elements.getTypeElement("java.lang.Object");
        if (objectType == null) {
            return null;
        }
        for (ExecutableElement method : ElementFilter.methodsIn(objectType.getEnclosedElements())) {
            if (!method.getSimpleName().contentEquals(key.name)
                    || method.getParameters().size() != key.erasedParameterTypes.size()) {
                continue;
            }
            List<String> parameters = method.getParameters().stream()
                    .map(parameter -> erasedTypeName(parameter.asType()))
                    .toList();
            if (parameters.equals(key.erasedParameterTypes)) {
                return method;
            }
        }
        return null;
    }

    private String render(
            TypeElement target,
            List<Property> properties,
            Construction construction,
            Config config
    ) {
        StringBuilder out = new StringBuilder(12288);
        if (!config.builderPackage.isEmpty()) {
            out.append("package ").append(config.builderPackage).append(";\n\n");
        }
        out.append("@").append(Generated.class.getCanonicalName())
                .append("(\"").append(getClass().getCanonicalName()).append("\")\n");
        if (properties.stream().anyMatch(property -> containsRawDeclaredType(property.mirror))) {
            out.append("@java.lang.SuppressWarnings(\"rawtypes\")\n");
        }
        out.append(apiAccess(config)).append("final class ").append(config.builderClassName)
                .append(typeParameterDeclaration(target)).append(" {\n");

        String rawTargetType = target.getQualifiedName().toString();
        if (config.jakartaValidation != JakartaValidationMode.NONE
                && construction.kind != ConstructionKind.FACTORY) {
            renderValidationExecutableField(out, construction);
        }

        for (Property property : properties) {
            out.append("    private ").append(property.type).append(' ')
                    .append(property.name).append(";\n");
            if (tracksAssignment(property)) {
                out.append("    private boolean ").append(setFlag(property)).append(";\n");
            }
            if (needsOwnership(property)) {
                out.append("    private boolean ").append(ownedFlag(property)).append(";\n");
            }
            if (property.valueCallback != null) {
                out.append("    private ").append(property.valueCallback.builderType).append(' ')
                        .append(callbackBuilderField(property)).append(";\n");
            }
            if (usesDeferredCollectionCallbacks(property)) {
                out.append("    private java.util.List<")
                        .append(deferredOperationType(property)).append("> ")
                        .append(deferredOperationsField(property)).append(";\n");
            }
        }
        if (!properties.isEmpty()) {
            out.append('\n');
        }
        for (Property property : properties) {
            if (usesDeferredCollectionCallbacks(property)) {
                renderDeferredOperationType(out, property);
            }
        }

        out.append("    private ").append(config.builderClassName).append("() {\n    }\n\n");

        String methodTypeParams = staticMethodTypeParameters(target);
        String builderType = config.builderClassName + typeArgumentUse(target);
        String targetType = rawTargetType + typeArgumentUse(target);
        String access = apiAccess(config);

        out.append("    ").append(access).append("static ").append(methodTypeParams)
                .append(builderType).append(' ').append(config.builderMethod).append("() {\n")
                .append("        return new ").append(config.builderClassName)
                .append(typeArgumentDiamond(target)).append("();\n")
                .append("    }\n\n");

        if (config.builderCallbacks) {
            renderRootCallbacks(
                    out,
                    target,
                    builderType,
                    targetType,
                    properties,
                    construction,
                    config
            );
        }

        if (config.generateFrom) {
            List<? extends TypeMirror> copyThrownTypes = copyThrownTypes(properties);
            out.append("    ").append(access).append("static ").append(methodTypeParams)
                    .append(builderType).append(' ').append(config.fromMethod).append('(')
                    .append(targetType).append(" source)")
                    .append(throwsClause(copyThrownTypes)).append(" {\n")
                    .append("        return ").append(config.builderClassName)
                    .append(staticBuilderInvocationTypeArguments(target))
                    .append(config.builderMethod).append("().")
                    .append(config.copyFromMethod).append("(source);\n")
                    .append("    }\n\n");

            out.append("    ").append(access).append("static ").append(methodTypeParams)
                    .append(builderType).append(' ').append(config.toBuilderMethod).append('(')
                    .append(targetType).append(" source)")
                    .append(throwsClause(copyThrownTypes)).append(" {\n")
                    .append("        return ").append(config.fromMethod).append("(source);\n")
                    .append("    }\n\n");

            out.append("    ").append(access).append(builderType).append(' ')
                    .append(config.copyFromMethod).append('(')
                    .append(targetType).append(" source)")
                    .append(throwsClause(copyThrownTypes)).append(" {\n")
                    .append("        java.util.Objects.requireNonNull(source, \"source\");\n");
            for (Property property : properties) {
                String sourceValue = "source." + property.accessor + "()";
                out.append("        this.").append(property.name).append(" = ")
                        .append(builderStoredValue(property, sourceValue, config)).append(";\n");
                if (tracksAssignment(property)) {
                    out.append("        this.").append(setFlag(property)).append(" = true;\n");
                }
                if (property.valueCallback != null) {
                    out.append("        this.").append(callbackBuilderField(property))
                            .append(" = null;\n");
                }
                if (usesDeferredCollectionCallbacks(property)) {
                    out.append("        this.").append(deferredOperationsField(property))
                            .append(" = null;\n");
                }
                if (needsOwnership(property)) {
                    if (config.defensiveCopyCollections) {
                        out.append("        this.").append(ownedFlag(property))
                                .append(" = this.").append(property.name).append(" != null;\n");
                    } else {
                        out.append("        this.").append(ownedFlag(property)).append(" = false;\n");
                    }
                }
            }
            out.append("        return this;\n")
                    .append("    }\n\n");
        }

        for (Property property : properties) {
            renderSetter(out, builderType, property, config);
            if (property.valueCallback != null) {
                renderNestedValueCallback(out, builderType, property, config);
            }
            if (hasCollectionHelpers(property)) {
                renderCollectionHelpers(out, builderType, property, config, rawTargetType);
            }
        }

        for (Property property : properties) {
            if (usesDeferredCollectionCallbacks(property)) {
                renderDeferredPreparationMethods(out, property, rawTargetType, config);
            }
        }

        renderRequiredChecksMethod(out, properties);
        if (config.jakartaValidation != JakartaValidationMode.NONE
                && construction.kind != ConstructionKind.FACTORY) {
            renderValidationExecutableResolver(out, rawTargetType, properties, construction);
        }
        List<? extends TypeMirror> buildThrownTypes = buildThrownTypes(construction, properties);
        if (config.jakartaValidation != JakartaValidationMode.REQUIRED) {
            renderConstructionMethod(
                    out,
                    rawTargetType,
                    targetType,
                    properties,
                    construction,
                    buildThrownTypes,
                    config
            );

            out.append("    ").append(access).append(targetType).append(' ')
                    .append(config.buildMethod).append("()")
                    .append(throwsClause(buildThrownTypes)).append(" {\n")
                    .append("        return construct();\n")
                    .append("    }\n\n");
        }

        if (config.jakartaValidation != JakartaValidationMode.NONE) {
            renderJakartaValidatedBuild(
                    out,
                    rawTargetType,
                    targetType,
                    properties,
                    construction,
                    config
            );
        }

        out.append("}\n");
        return out.toString();
    }

    private void renderDeferredOperationType(StringBuilder out, Property property) {
        String operationType = deferredOperationType(property);
        out.append("    private final class ").append(operationType).append(" {\n")
                .append("        private final int kind;\n");
        if (property.elementCallback != null) {
            out.append("        private final ").append(property.elementType).append(" value;\n")
                    .append("        private final ").append(property.elementCallback.builderType)
                    .append(" builder;\n")
                    .append("        private final java.util.Collection<? extends ")
                    .append(property.elementType).append("> addValues;\n")
                    .append("        private final java.util.Collection<?> removeValues;\n\n")
                    .append("        private ").append(operationType).append("(int kind, ")
                    .append(property.elementType).append(" value, ")
                    .append(property.elementCallback.builderType).append(" builder, ")
                    .append("java.util.Collection<? extends ").append(property.elementType)
                    .append("> addValues, java.util.Collection<?> removeValues) {\n")
                    .append("            this.kind = kind;\n")
                    .append("            this.value = value;\n")
                    .append("            this.builder = builder;\n")
                    .append("            this.addValues = addValues;\n")
                    .append("            this.removeValues = removeValues;\n")
                    .append("        }\n");
        } else {
            out.append("        private final ").append(property.keyType).append(" key;\n")
                    .append("        private final ").append(property.elementType).append(" value;\n")
                    .append("        private final ").append(property.mapValueCallback.builderType)
                    .append(" builder;\n")
                    .append("        private final java.util.Map<? extends ")
                    .append(property.keyType).append(", ? extends ").append(property.elementType)
                    .append("> values;\n")
                    .append("        private final java.util.Collection<?> keys;\n\n")
                    .append("        private ").append(operationType).append("(int kind, ")
                    .append(property.keyType).append(" key, ")
                    .append(property.elementType).append(" value, ")
                    .append(property.mapValueCallback.builderType).append(" builder, ")
                    .append("java.util.Map<? extends ").append(property.keyType)
                    .append(", ? extends ").append(property.elementType)
                    .append("> values, java.util.Collection<?> keys) {\n")
                    .append("            this.kind = kind;\n")
                    .append("            this.key = key;\n")
                    .append("            this.value = value;\n")
                    .append("            this.builder = builder;\n")
                    .append("            this.values = values;\n")
                    .append("            this.keys = keys;\n")
                    .append("        }\n");
        }
        out.append("    }\n\n");
    }

    private void renderRootCallbacks(
            StringBuilder out,
            TypeElement target,
            String builderType,
            String targetType,
            List<Property> properties,
            Construction construction,
            Config config
    ) {
        String access = apiAccess(config);
        String methodTypeParams = staticMethodTypeParameters(target);
        List<? extends TypeMirror> thrownTypes = buildThrownTypes(construction, properties);
        String callbackType = "java.util.function.Function<? super " + builderType
                + ", ? extends " + builderType + ">";
        String newBuilder = config.builderClassName
                + staticBuilderInvocationTypeArguments(target)
                + config.builderMethod + "()";

        if (config.jakartaValidation != JakartaValidationMode.REQUIRED) {
            out.append("    ").append(access).append("static ").append(methodTypeParams)
                    .append(targetType).append(' ').append(config.callbackMethod).append('(')
                    .append(callbackType).append(" configure)")
                    .append(throwsClause(thrownTypes)).append(" {\n")
                    .append("        java.util.Objects.requireNonNull(configure, \"configure\");\n")
                    .append("        ").append(builderType).append(" configured = ")
                    .append("java.util.Objects.requireNonNull(configure.apply(")
                    .append(newBuilder)
                    .append("), \"builder callback returned null\");\n")
                    .append("        return configured.").append(config.buildMethod).append("();\n")
                    .append("    }\n\n");
        }

        if (config.jakartaValidation != JakartaValidationMode.NONE) {
            out.append("    ").append(access).append("static ").append(methodTypeParams)
                    .append(targetType).append(' ').append(config.callbackMethod).append('(')
                    .append(JAKARTA_VALIDATOR).append(" validator, ")
                    .append(callbackType)
                    .append(" configure, java.lang.Class<?>... groups)")
                    .append(throwsClause(thrownTypes)).append(" {\n")
                    .append("        java.util.Objects.requireNonNull(validator, \"validator\");\n")
                    .append("        java.util.Objects.requireNonNull(configure, \"configure\");\n")
                    .append("        java.util.Objects.requireNonNull(groups, \"groups\");\n")
                    .append("        ").append(builderType).append(" configured = ")
                    .append("java.util.Objects.requireNonNull(configure.apply(")
                    .append(newBuilder)
                    .append("), \"builder callback returned null\");\n")
                    .append("        return configured.").append(config.buildMethod)
                    .append("(validator, groups);\n")
                    .append("    }\n\n");
        }

        out.append("    ").append(access).append(builderType).append(' ')
                .append(config.customizeMethod)
                .append("(java.util.function.Consumer<? super ").append(builderType)
                .append("> customizer) {\n")
                .append("        java.util.Objects.requireNonNull(customizer, \"customizer\")")
                .append(".accept(this);\n")
                .append("        return this;\n")
                .append("    }\n\n");
    }

    private void renderValidationExecutableField(
            StringBuilder out,
            Construction construction
    ) {
        String type = construction.kind == ConstructionKind.FACTORY
                ? "java.lang.reflect.Method"
                : "java.lang.reflect.Constructor<?>";
        out.append("    private static final ").append(type).append(' ')
                .append(validationExecutableField(construction)).append(" = ")
                .append("resolveValidationExecutable();\n\n");
    }

    private void renderDeferredPreparationMethods(
            StringBuilder out,
            Property property,
            String rawTargetType,
            Config config
    ) {
        if (config.jakartaValidation != JakartaValidationMode.REQUIRED) {
            renderDeferredPreparationMethod(out, property, rawTargetType, false);
        }
        if (config.jakartaValidation != JakartaValidationMode.NONE) {
            renderDeferredPreparationMethod(out, property, rawTargetType, true);
        }
    }

    private void renderDeferredPreparationMethod(
            StringBuilder out,
            Property property,
            String rawTargetType,
            boolean validated
    ) {
        CallbackTarget callback = property.elementCallback != null
                ? property.elementCallback
                : property.mapValueCallback;
        String parameters = validated
                ? JAKARTA_VALIDATOR + " validator, java.lang.Class<?>[] groups"
                : "";
        out.append("    private ").append(property.type).append(' ')
                .append(deferredPrepareMethod(property)).append('(').append(parameters).append(')')
                .append(throwsClause(callback.thrownTypes)).append(" {\n")
                .append("        ").append(property.type).append(" base = ")
                .append(directRawPropertyValue(property, rawTargetType)).append(";\n")
                .append("        if (this.").append(deferredOperationsField(property))
                .append(" == null || this.").append(deferredOperationsField(property))
                .append(".isEmpty()) {\n")
                .append("            return base;\n")
                .append("        }\n")
                .append("        ").append(property.type).append(" result = base == null\n")
                .append("                ? new ").append(mutableImplementation(property.collectionKind))
                .append("<>()\n")
                .append("                : new ").append(mutableImplementation(property.collectionKind))
                .append("<>(base);\n")
                .append("        for (").append(deferredOperationType(property)).append(" operation : this.")
                .append(deferredOperationsField(property)).append(") {\n")
                .append("            switch (operation.kind) {\n");
        if (property.elementCallback != null) {
            out.append("                case 0 -> result.add(operation.value);\n")
                    .append("                case 1 -> result.add(")
                    .append(callbackBuildExpression(callback, "operation.builder", validated))
                    .append(");\n")
                    .append("                case 2 -> result.addAll(operation.addValues);\n")
                    .append("                case 3 -> result.remove(operation.value);\n")
                    .append("                case 4 -> result.removeAll(operation.removeValues);\n")
                    .append("                case 5 -> result.clear();\n");
        } else {
            out.append("                case 0 -> result.put(operation.key, operation.value);\n")
                    .append("                case 1 -> result.put(operation.key, ")
                    .append(callbackBuildExpression(callback, "operation.builder", validated))
                    .append(");\n")
                    .append("                case 2 -> result.putAll(operation.values);\n")
                    .append("                case 3 -> result.remove(operation.key);\n")
                    .append("                case 4 -> result.keySet().removeAll(operation.keys);\n")
                    .append("                case 5 -> result.clear();\n");
        }
        out.append("                default -> throw new java.lang.IllegalStateException(\"Unknown deferred builder operation: \" + operation.kind);\n")
                .append("            }\n")
                .append("        }\n")
                .append("        return result;\n")
                .append("    }\n\n");
    }

    private String callbackBuildExpression(
            CallbackTarget callback,
            String builderExpression,
            boolean validated
    ) {
        String arguments = validated && callback.validationMode != JakartaValidationMode.NONE
                ? "validator, groups"
                : "";
        return builderExpression + "." + callback.buildMethod + "(" + arguments + ")";
    }

    private void renderRequiredChecksMethod(StringBuilder out, List<Property> properties) {
        out.append("    private void validateRequiredProperties() {\n");
        for (Property property : properties) {
            if (property.required) {
                out.append("        if (!this.").append(setFlag(property)).append(") {\n")
                        .append("            throw new IllegalStateException(\"Required property '")
                        .append(escapeJava(property.name)).append("' was not set\");\n")
                        .append("        }\n");
                if (!property.primitive) {
                    out.append("        if (this.").append(property.name).append(" == null");
                    if (property.valueCallback != null) {
                        out.append(" && this.").append(callbackBuilderField(property))
                                .append(" == null");
                    }
                    if (usesDeferredCollectionCallbacks(property)) {
                        out.append(" && (this.").append(deferredOperationsField(property))
                                .append(" == null || this.").append(deferredOperationsField(property))
                                .append(".isEmpty())");
                    }
                    out.append(") {\n")
                            .append("            throw new IllegalStateException(\"Required property '")
                            .append(escapeJava(property.name)).append("' was null\");\n")
                            .append("        }\n");
                }
            }
        }
        out.append("    }\n\n");
    }

    private void renderValidationExecutableResolver(
            StringBuilder out,
            String rawTargetType,
            List<Property> properties,
            Construction construction
    ) {
        String reflectionType = construction.kind == ConstructionKind.FACTORY
                ? "java.lang.reflect.Method"
                : "java.lang.reflect.Constructor<?>";
        out.append("    private static ").append(reflectionType)
                .append(" resolveValidationExecutable() {\n")
                .append("        try {\n")
                .append("            return ").append(rawTargetType).append(".class.");
        if (construction.kind == ConstructionKind.FACTORY) {
            out.append("getDeclaredMethod(\"")
                    .append(escapeJava(construction.executable.getSimpleName().toString()))
                    .append("\"");
            if (!properties.isEmpty()) {
                out.append(", ");
            }
        } else {
            out.append("getDeclaredConstructor(");
        }
        StringJoiner parameterTypes = new StringJoiner(", ");
        for (Property property : properties) {
            parameterTypes.add(property.erasedType + ".class");
        }
        out.append(parameterTypes).append(");\n")
                .append("        } catch (java.lang.NoSuchMethodException exception) {\n")
                .append("            throw new IllegalStateException(")
                .append("\"Generated builder could not resolve the target ")
                .append(construction.kind == ConstructionKind.FACTORY ? "factory method" : "constructor")
                .append("\", exception);\n")
                .append("        }\n")
                .append("    }\n\n");
    }

    private void renderConstructionMethod(
            StringBuilder out,
            String rawTargetType,
            String targetType,
            List<Property> properties,
            Construction construction,
            List<? extends TypeMirror> thrownTypes,
            Config config
    ) {
        out.append("    private ").append(targetType).append(" construct()")
                .append(throwsClause(thrownTypes)).append(" {\n")
                .append("        validateRequiredProperties();\n");
        renderPreparedValues(out, rawTargetType, properties, config, false);
        out.append("        return ")
                .append(constructionExpression(rawTargetType, targetType, construction, properties))
                .append(";\n")
                .append("    }\n\n");
    }

    private void renderPreparedValues(
            StringBuilder out,
            String rawTargetType,
            List<Property> properties,
            Config config,
            boolean validated
    ) {
        for (Property property : properties) {
            String rawName = preparedRawValue(property);
            out.append("        ").append(property.type).append(' ').append(rawName)
                    .append(" = ").append(rawPropertyValue(property, rawTargetType, validated)).append(";\n")
                    .append("        ").append(property.type).append(' ')
                    .append(preparedValue(property)).append(" = ")
                    .append(finalBuildValue(property, rawName, config)).append(";\n");
        }
    }

    private void renderJakartaValidatedBuild(
            StringBuilder out,
            String rawTargetType,
            String targetType,
            List<Property> properties,
            Construction construction,
            Config config
    ) {
        out.append("    ").append(apiAccess(config)).append(targetType).append(' ')
                .append(config.buildMethod).append('(')
                .append(JAKARTA_VALIDATOR)
                .append(" validator, java.lang.Class<?>... groups)")
                .append(throwsClause(buildThrownTypes(construction, properties))).append(" {\n")
                .append("        java.util.Objects.requireNonNull(validator, \"validator\");\n")
                .append("        java.util.Objects.requireNonNull(groups, \"groups\");\n")
                .append("        validateRequiredProperties();\n");

        renderPreparedValues(out, rawTargetType, properties, config, true);

        if (construction.kind == ConstructionKind.FACTORY) {
            out.append("        ").append(targetType).append(" value = ")
                    .append(constructionExpression(rawTargetType, targetType, construction, properties))
                    .append(";\n")
                    .append("        java.util.Set<? extends ")
                    .append(JAKARTA_CONSTRAINT_VIOLATION)
                    .append("<?>> violations = validator.validate(value, groups);\n")
                    .append("        if (!violations.isEmpty()) {\n")
                    .append("            throw new ")
                    .append(JAKARTA_CONSTRAINT_VIOLATION_EXCEPTION)
                    .append("(violations);\n")
                    .append("        }\n")
                    .append("        return value;\n")
                    .append("    }\n\n");
            return;
        }

        out.append("        java.lang.Object[] executableArguments = new java.lang.Object[]{");
        StringJoiner objectArgs = new StringJoiner(", ");
        for (Property property : properties) {
            objectArgs.add(preparedValue(property));
        }
        out.append(objectArgs).append("};\n")
                .append("        ").append(JAKARTA_EXECUTABLE_VALIDATOR)
                .append(" executableValidator = validator.forExecutables();\n")
                .append("        java.util.Set<? extends ")
                .append(JAKARTA_CONSTRAINT_VIOLATION)
                .append("<?>> parameterViolations = executableValidator")
                .append(".validateConstructorParameters(\n")
                .append("                ").append(validationExecutableField(construction))
                .append(", executableArguments, groups);\n")
                .append("        if (!parameterViolations.isEmpty()) {\n")
                .append("            throw new ")
                .append(JAKARTA_CONSTRAINT_VIOLATION_EXCEPTION)
                .append("(parameterViolations);\n")
                .append("        }\n")
                .append("        ").append(targetType).append(" value = ")
                .append(constructionExpression(rawTargetType, targetType, construction, properties))
                .append(";\n")
                .append("        java.util.Set<")
                .append(JAKARTA_CONSTRAINT_VIOLATION).append("<?>> violations = ")
                .append("new java.util.LinkedHashSet<>();\n")
                .append("        violations.addAll(executableValidator")
                .append(".validateConstructorReturnValue(\n")
                .append("                ").append(validationExecutableField(construction))
                .append(", value, groups));\n")
                .append("        violations.addAll(validator.validate(value, groups));\n")
                .append("        if (!violations.isEmpty()) {\n")
                .append("            throw new ")
                .append(JAKARTA_CONSTRAINT_VIOLATION_EXCEPTION)
                .append("(violations);\n")
                .append("        }\n")
                .append("        return value;\n")
                .append("    }\n\n");
    }

    private String constructionExpression(
            String rawTargetType,
            String targetType,
            Construction construction,
            List<Property> properties
    ) {
        StringJoiner arguments = new StringJoiner(", ");
        for (Property property : properties) {
            arguments.add(preparedValue(property));
        }
        if (construction.kind == ConstructionKind.FACTORY) {
            return "java.util.Objects.requireNonNull("
                    + rawTargetType + "." + construction.executable.getSimpleName()
                    + "(" + arguments + "), \"@BuilderFactory returned null\")";
        }
        return "new " + targetType + "(" + arguments + ")";
    }

    private String preparedRawValue(Property property) {
        return "$jdkBuilder$raw" + property.index;
    }

    private String preparedValue(Property property) {
        return "$jdkBuilder$value" + property.index;
    }

    private void renderSetter(
            StringBuilder out,
            String builderType,
            Property property,
            Config config
    ) {
        out.append("    ").append(apiAccess(config)).append(builderType).append(' ')
                .append(setterName(property, config)).append('(')
                .append(property.type).append(' ').append(property.name).append(") {\n")
                .append("        this.").append(property.name).append(" = ")
                .append(builderStoredValue(property, property.name, config)).append(";\n");
        if (property.valueCallback != null) {
            out.append("        this.").append(callbackBuilderField(property))
                    .append(" = null;\n");
        }
        if (usesDeferredCollectionCallbacks(property)) {
            out.append("        this.").append(deferredOperationsField(property))
                    .append(" = null;\n");
        }
        renderSetFlag(out, property);
        if (needsOwnership(property)) {
            if (config.defensiveCopyCollections) {
                out.append("        this.").append(ownedFlag(property))
                        .append(" = this.").append(property.name).append(" != null;\n");
            } else {
                out.append("        this.").append(ownedFlag(property)).append(" = false;\n");
            }
        }
        out.append("        return this;\n")
                .append("    }\n\n");
    }

    private void renderNestedValueCallback(
            StringBuilder out,
            String builderType,
            Property property,
            Config config
    ) {
        CallbackTarget callback = property.valueCallback;
        String functionType = "java.util.function.Function<? super " + callback.builderType
                + ", ? extends " + callback.builderType + ">";
        out.append("    ").append(apiAccess(config)).append(builderType).append(' ')
                .append(nestedValueCallbackName(property, config)).append('(')
                .append(functionType).append(" configure) {\n")
                .append("        java.util.Objects.requireNonNull(configure, \"configure\");\n")
                .append("        this.").append(callbackBuilderField(property)).append(" = ")
                .append("java.util.Objects.requireNonNull(configure.apply(")
                .append(callback.builderRawType).append('.').append(callback.builderMethod)
                .append("()), \"nested builder callback returned null\");\n")
                .append("        this.").append(property.name).append(" = null;\n");
        renderSetFlag(out, property);
        out.append("        return this;\n")
                .append("    }\n\n");
    }

    private void renderCollectionHelpers(
            StringBuilder out,
            String builderType,
            Property property,
            Config config,
            String rawTargetType
    ) {
        if (usesDeferredCollectionCallbacks(property)) {
            renderDeferredCollectionHelpers(out, builderType, property, config);
            return;
        }
        String singularSuffix = capitalize(property.adderWord);
        String pluralSuffix = capitalize(property.name);
        renderEnsureOwned(
                out,
                property,
                mutableImplementation(property.collectionKind),
                rawTargetType
        );
        String access = apiAccess(config);
        switch (property.collectionKind) {
            case COLLECTION, LIST, SEQUENCED_COLLECTION, SET, SEQUENCED_SET,
                    SORTED_SET, NAVIGABLE_SET -> {
                if (property.generateAdders) {
                    out.append("    ").append(access).append(builderType).append(" add")
                            .append(singularSuffix).append('(').append(property.elementType)
                            .append(" value) {\n")
                            .append("        ensure").append(pluralSuffix).append("Owned();\n")
                            .append("        this.").append(property.name).append(".add(value);\n");
                    renderSetFlag(out, property);
                    out.append("        return this;\n    }\n\n");

                    if (property.elementCallback != null) {
                        CallbackTarget callback = property.elementCallback;
                        out.append("    ").append(access).append(builderType).append(' ')
                                .append(collectionCallbackName(property, config)).append('(')
                                .append("java.util.function.Function<? super ")
                                .append(callback.builderType).append(", ? extends ")
                                .append(callback.builderType).append("> configure)")
                                .append(throwsClause(callback.thrownTypes)).append(" {\n")
                                .append("        java.util.Objects.requireNonNull(configure, \"configure\");\n")
                                .append("        ").append(callback.builderType).append(" configured = ")
                                .append("java.util.Objects.requireNonNull(configure.apply(")
                                .append(callback.builderRawType).append('.')
                                .append(callback.builderMethod)
                                .append("()), \"nested builder callback returned null\");\n")
                                .append("        return add").append(singularSuffix)
                                .append("(configured.").append(callback.buildMethod).append("());\n")
                                .append("    }\n\n");
                    }

                    out.append("    ").append(access).append(builderType).append(" addAll")
                            .append(pluralSuffix).append("(java.util.Collection<? extends ")
                            .append(property.elementType).append("> values) {\n")
                            .append("        java.util.Objects.requireNonNull(values, \"values\");\n")
                            .append("        ensure").append(pluralSuffix).append("Owned();\n")
                            .append("        this.").append(property.name).append(".addAll(values);\n");
                    renderSetFlag(out, property);
                    out.append("        return this;\n    }\n\n");
                }
                if (property.generateRemovers) {
                    out.append("    ").append(access).append(builderType).append(" remove")
                            .append(singularSuffix).append('(').append(property.elementType)
                            .append(" value) {\n")
                            .append("        ensure").append(pluralSuffix).append("Owned();\n")
                            .append("        this.").append(property.name).append(".remove(value);\n");
                    renderSetFlag(out, property);
                    out.append("        return this;\n    }\n\n");

                    out.append("    ").append(access).append(builderType).append(" removeAll")
                            .append(pluralSuffix).append("(java.util.Collection<?> values) {\n")
                            .append("        java.util.Objects.requireNonNull(values, \"values\");\n")
                            .append("        ensure").append(pluralSuffix).append("Owned();\n")
                            .append("        this.").append(property.name).append(".removeAll(values);\n");
                    renderSetFlag(out, property);
                    out.append("        return this;\n    }\n\n");

                    renderClearHelper(out, access, builderType, property, pluralSuffix);
                }
            }
            case MAP, SEQUENCED_MAP, SORTED_MAP, NAVIGABLE_MAP -> {
                if (property.generateAdders) {
                    out.append("    ").append(access).append(builderType).append(" put")
                            .append(singularSuffix).append('(').append(property.keyType)
                            .append(" key, ").append(property.elementType).append(" value) {\n")
                            .append("        ensure").append(pluralSuffix).append("Owned();\n")
                            .append("        this.").append(property.name).append(".put(key, value);\n");
                    renderSetFlag(out, property);
                    out.append("        return this;\n    }\n\n");

                    if (property.mapValueCallback != null) {
                        CallbackTarget callback = property.mapValueCallback;
                        out.append("    ").append(access).append(builderType).append(' ')
                                .append(mapCallbackName(property, config)).append('(').append(property.keyType)
                                .append(" key, java.util.function.Function<? super ")
                                .append(callback.builderType).append(", ? extends ")
                                .append(callback.builderType).append("> configure)")
                                .append(throwsClause(callback.thrownTypes)).append(" {\n")
                                .append("        java.util.Objects.requireNonNull(configure, \"configure\");\n")
                                .append("        ").append(callback.builderType).append(" configured = ")
                                .append("java.util.Objects.requireNonNull(configure.apply(")
                                .append(callback.builderRawType).append('.')
                                .append(callback.builderMethod)
                                .append("()), \"nested builder callback returned null\");\n")
                                .append("        return put").append(singularSuffix)
                                .append("(key, configured.").append(callback.buildMethod).append("());\n")
                                .append("    }\n\n");
                    }

                    out.append("    ").append(access).append(builderType).append(" putAll")
                            .append(pluralSuffix).append("(java.util.Map<? extends ")
                            .append(property.keyType).append(", ? extends ")
                            .append(property.elementType).append("> values) {\n")
                            .append("        java.util.Objects.requireNonNull(values, \"values\");\n")
                            .append("        ensure").append(pluralSuffix).append("Owned();\n")
                            .append("        this.").append(property.name).append(".putAll(values);\n");
                    renderSetFlag(out, property);
                    out.append("        return this;\n    }\n\n");
                }
                if (property.generateRemovers) {
                    out.append("    ").append(access).append(builderType).append(" remove")
                            .append(singularSuffix).append('(').append(property.keyType)
                            .append(" key) {\n")
                            .append("        ensure").append(pluralSuffix).append("Owned();\n")
                            .append("        this.").append(property.name).append(".remove(key);\n");
                    renderSetFlag(out, property);
                    out.append("        return this;\n    }\n\n");

                    out.append("    ").append(access).append(builderType).append(" removeAll")
                            .append(pluralSuffix).append("(java.util.Collection<?> keys) {\n")
                            .append("        java.util.Objects.requireNonNull(keys, \"keys\");\n")
                            .append("        ensure").append(pluralSuffix).append("Owned();\n")
                            .append("        this.").append(property.name)
                            .append(".keySet().removeAll(keys);\n");
                    renderSetFlag(out, property);
                    out.append("        return this;\n    }\n\n");

                    renderClearHelper(out, access, builderType, property, pluralSuffix);
                }
            }
            case NONE, UNSUPPORTED -> {
            }
        }
    }

    private void renderDeferredCollectionHelpers(
            StringBuilder out,
            String builderType,
            Property property,
            Config config
    ) {
        String access = apiAccess(config);
        String singularSuffix = capitalize(property.adderWord);
        String pluralSuffix = capitalize(property.name);
        String operations = deferredOperationsField(property);
        String operationType = deferredOperationType(property);
        String initialize = "        if (this." + operations + " == null) {\n"
                + "            this." + operations + " = new java.util.ArrayList<>();\n"
                + "        }\n";

        if (property.elementCallback != null) {
            if (property.generateAdders) {
                out.append("    ").append(access).append(builderType).append(" add")
                        .append(singularSuffix).append('(').append(property.elementType)
                        .append(" value) {\n")
                        .append(initialize)
                        .append("        this.").append(operations).append(".add(new ")
                        .append(operationType).append("(0, value, null, null, null));\n");
                renderSetFlag(out, property);
                out.append("        return this;\n    }\n\n");

                CallbackTarget callback = property.elementCallback;
                out.append("    ").append(access).append(builderType).append(' ')
                        .append(collectionCallbackName(property, config)).append('(')
                        .append("java.util.function.Function<? super ")
                        .append(callback.builderType).append(", ? extends ")
                        .append(callback.builderType).append("> configure) {\n")
                        .append("        java.util.Objects.requireNonNull(configure, \"configure\");\n")
                        .append("        ").append(callback.builderType).append(" configured = ")
                        .append("java.util.Objects.requireNonNull(configure.apply(")
                        .append(callback.builderRawType).append('.').append(callback.builderMethod)
                        .append("()), \"nested builder callback returned null\");\n")
                        .append(initialize)
                        .append("        this.").append(operations).append(".add(new ")
                        .append(operationType).append("(1, null, configured, null, null));\n");
                renderSetFlag(out, property);
                out.append("        return this;\n    }\n\n");

                out.append("    ").append(access).append(builderType).append(" addAll")
                        .append(pluralSuffix).append("(java.util.Collection<? extends ")
                        .append(property.elementType).append("> values) {\n")
                        .append("        java.util.Objects.requireNonNull(values, \"values\");\n")
                        .append(initialize)
                        .append("        this.").append(operations).append(".add(new ")
                        .append(operationType)
                        .append("(2, null, null, new java.util.ArrayList<>(values), null));\n");
                renderSetFlag(out, property);
                out.append("        return this;\n    }\n\n");
            }
            if (property.generateRemovers) {
                out.append("    ").append(access).append(builderType).append(" remove")
                        .append(singularSuffix).append('(').append(property.elementType)
                        .append(" value) {\n")
                        .append(initialize)
                        .append("        this.").append(operations).append(".add(new ")
                        .append(operationType).append("(3, value, null, null, null));\n");
                renderSetFlag(out, property);
                out.append("        return this;\n    }\n\n");

                out.append("    ").append(access).append(builderType).append(" removeAll")
                        .append(pluralSuffix).append("(java.util.Collection<?> values) {\n")
                        .append("        java.util.Objects.requireNonNull(values, \"values\");\n")
                        .append(initialize)
                        .append("        this.").append(operations).append(".add(new ")
                        .append(operationType)
                        .append("(4, null, null, null, new java.util.ArrayList<>(values)));\n");
                renderSetFlag(out, property);
                out.append("        return this;\n    }\n\n");

                out.append("    ").append(access).append(builderType).append(" clear")
                        .append(pluralSuffix).append("() {\n")
                        .append(initialize)
                        .append("        this.").append(operations).append(".add(new ")
                        .append(operationType).append("(5, null, null, null, null));\n");
                renderSetFlag(out, property);
                out.append("        return this;\n    }\n\n");
            }
            return;
        }

        if (property.generateAdders) {
            out.append("    ").append(access).append(builderType).append(" put")
                    .append(singularSuffix).append('(').append(property.keyType)
                    .append(" key, ").append(property.elementType).append(" value) {\n")
                    .append(initialize)
                    .append("        this.").append(operations).append(".add(new ")
                    .append(operationType).append("(0, key, value, null, null, null));\n");
            renderSetFlag(out, property);
            out.append("        return this;\n    }\n\n");

            CallbackTarget callback = property.mapValueCallback;
            out.append("    ").append(access).append(builderType).append(' ')
                    .append(mapCallbackName(property, config)).append('(')
                    .append(property.keyType).append(" key, ")
                    .append("java.util.function.Function<? super ")
                    .append(callback.builderType).append(", ? extends ")
                    .append(callback.builderType).append("> configure) {\n")
                    .append("        java.util.Objects.requireNonNull(configure, \"configure\");\n")
                    .append("        ").append(callback.builderType).append(" configured = ")
                    .append("java.util.Objects.requireNonNull(configure.apply(")
                    .append(callback.builderRawType).append('.').append(callback.builderMethod)
                    .append("()), \"nested builder callback returned null\");\n")
                    .append(initialize)
                    .append("        this.").append(operations).append(".add(new ")
                    .append(operationType).append("(1, key, null, configured, null, null));\n");
            renderSetFlag(out, property);
            out.append("        return this;\n    }\n\n");

            out.append("    ").append(access).append(builderType).append(" putAll")
                    .append(pluralSuffix).append("(java.util.Map<? extends ")
                    .append(property.keyType).append(", ? extends ")
                    .append(property.elementType).append("> values) {\n")
                    .append("        java.util.Objects.requireNonNull(values, \"values\");\n")
                    .append(initialize)
                    .append("        this.").append(operations).append(".add(new ")
                    .append(operationType)
                    .append("(2, null, null, null, new java.util.LinkedHashMap<>(values), null));\n");
            renderSetFlag(out, property);
            out.append("        return this;\n    }\n\n");
        }
        if (property.generateRemovers) {
            out.append("    ").append(access).append(builderType).append(" remove")
                    .append(singularSuffix).append('(').append(property.keyType)
                    .append(" key) {\n")
                    .append(initialize)
                    .append("        this.").append(operations).append(".add(new ")
                    .append(operationType).append("(3, key, null, null, null, null));\n");
            renderSetFlag(out, property);
            out.append("        return this;\n    }\n\n");

            out.append("    ").append(access).append(builderType).append(" removeAll")
                    .append(pluralSuffix).append("(java.util.Collection<?> keys) {\n")
                    .append("        java.util.Objects.requireNonNull(keys, \"keys\");\n")
                    .append(initialize)
                    .append("        this.").append(operations).append(".add(new ")
                    .append(operationType)
                    .append("(4, null, null, null, null, new java.util.ArrayList<>(keys)));\n");
            renderSetFlag(out, property);
            out.append("        return this;\n    }\n\n");

            out.append("    ").append(access).append(builderType).append(" clear")
                    .append(pluralSuffix).append("() {\n")
                    .append(initialize)
                    .append("        this.").append(operations).append(".add(new ")
                    .append(operationType).append("(5, null, null, null, null, null));\n");
            renderSetFlag(out, property);
            out.append("        return this;\n    }\n\n");
        }
    }

    private void renderClearHelper(
            StringBuilder out,
            String access,
            String builderType,
            Property property,
            String pluralSuffix
    ) {
        out.append("    ").append(access).append(builderType).append(" clear")
                .append(pluralSuffix).append("() {\n")
                .append("        ensure").append(pluralSuffix).append("Owned();\n")
                .append("        this.").append(property.name).append(".clear();\n");
        renderSetFlag(out, property);
        out.append("        return this;\n    }\n\n");
    }

    private void renderEnsureOwned(
            StringBuilder out,
            Property property,
            String implementation,
            String rawTargetType
    ) {
        String suffix = capitalize(property.name);
        out.append("    private void ensure").append(suffix).append("Owned() {\n")
                .append("        if (this.").append(ownedFlag(property)).append(") {\n")
                .append("            return;\n")
                .append("        }\n");
        if (property.defaultProvider != null) {
            out.append("        if (!this.").append(setFlag(property)).append(") {\n")
                    .append("            this.").append(property.name).append(" = ")
                    .append(rawTargetType).append('.').append(property.defaultProvider)
                    .append("();\n")
                    .append("            this.").append(setFlag(property)).append(" = true;\n")
                    .append("        }\n");
        }
        out.append("        this.").append(property.name).append(" = this.")
                .append(property.name).append(" == null\n")
                .append("                ? new ").append(implementation).append("<>()\n")
                .append("                : new ").append(implementation).append("<>(this.")
                .append(property.name).append(");\n")
                .append("        this.").append(ownedFlag(property)).append(" = true;\n")
                .append("    }\n\n");
    }

    private void renderSetFlag(StringBuilder out, Property property) {
        if (tracksAssignment(property)) {
            out.append("        this.").append(setFlag(property)).append(" = true;\n");
        }
    }

    private String builderStoredValue(Property property, String value, Config config) {
        if (property.array && config.defensiveCopyArrays) {
            return value + " == null ? null : " + value + ".clone()";
        }
        if (!config.defensiveCopyCollections
                || property.collectionKind == CollectionKind.NONE
                || property.collectionKind == CollectionKind.UNSUPPORTED) {
            return value;
        }
        return value + " == null ? null : " + mutableCopyExpression(property.collectionKind, value);
    }

    private String rawPropertyValue(
            Property property,
            String rawTargetType,
            boolean validated
    ) {
        if (usesDeferredCollectionCallbacks(property)) {
            String arguments = validated ? "validator, groups" : "";
            return deferredPrepareMethod(property) + "(" + arguments + ")";
        }
        String directValue = directRawPropertyValue(property, rawTargetType);
        if (property.valueCallback == null) {
            return directValue;
        }
        CallbackTarget callback = property.valueCallback;
        String buildArguments = validated
                && callback.validationMode != JakartaValidationMode.NONE
                ? "validator, groups"
                : "";
        return "this." + callbackBuilderField(property) + " == null ? " + directValue
                + " : this." + callbackBuilderField(property) + "." + callback.buildMethod
                + "(" + buildArguments + ")";
    }

    private String directRawPropertyValue(Property property, String rawTargetType) {
        if (property.defaultProvider == null) {
            return "this." + property.name;
        }
        return "this." + setFlag(property) + " ? this." + property.name
                + " : " + rawTargetType + "." + property.defaultProvider + "()";
    }

    private String finalBuildValue(Property property, String value, Config config) {
        if (property.array && config.defensiveCopyArrays) {
            return value + " == null ? null : " + value + ".clone()";
        }
        if (!config.defensiveCopyCollections) {
            return value;
        }
        return switch (property.collectionKind) {
            case COLLECTION, LIST, SEQUENCED_COLLECTION ->
                    value + " == null ? null : java.util.List.copyOf(" + value + ")";
            case SET -> value + " == null ? null : java.util.Set.copyOf(" + value + ")";
            case SEQUENCED_SET -> value + " == null ? null : "
                    + "java.util.Collections.unmodifiableSequencedSet("
                    + "new java.util.LinkedHashSet<>(" + value + "))";
            case SORTED_SET -> value + " == null ? null : "
                    + "java.util.Collections.unmodifiableSortedSet("
                    + "new java.util.TreeSet<>(" + value + "))";
            case NAVIGABLE_SET -> value + " == null ? null : "
                    + "java.util.Collections.unmodifiableNavigableSet("
                    + "new java.util.TreeSet<>(" + value + "))";
            case MAP -> value + " == null ? null : java.util.Map.copyOf(" + value + ")";
            case SEQUENCED_MAP -> value + " == null ? null : "
                    + "java.util.Collections.unmodifiableSequencedMap("
                    + "new java.util.LinkedHashMap<>(" + value + "))";
            case SORTED_MAP -> value + " == null ? null : "
                    + "java.util.Collections.unmodifiableSortedMap("
                    + "new java.util.TreeMap<>(" + value + "))";
            case NAVIGABLE_MAP -> value + " == null ? null : "
                    + "java.util.Collections.unmodifiableNavigableMap("
                    + "new java.util.TreeMap<>(" + value + "))";
            case NONE, UNSUPPORTED -> value;
        };
    }

    private String mutableCopyExpression(CollectionKind kind, String value) {
        return "new " + mutableImplementation(kind) + "<>(" + value + ")";
    }

    private String mutableImplementation(CollectionKind kind) {
        return switch (kind) {
            case COLLECTION, LIST, SEQUENCED_COLLECTION -> "java.util.ArrayList";
            case SET, SEQUENCED_SET -> "java.util.LinkedHashSet";
            case SORTED_SET, NAVIGABLE_SET -> "java.util.TreeSet";
            case MAP, SEQUENCED_MAP -> "java.util.LinkedHashMap";
            case SORTED_MAP, NAVIGABLE_MAP -> "java.util.TreeMap";
            case NONE, UNSUPPORTED -> throw new IllegalArgumentException(
                    "No mutable implementation for " + kind);
        };
    }

    private CollectionKind collectionKind(TypeMirror type) {
        if (!(type instanceof DeclaredType)) {
            return CollectionKind.NONE;
        }
        TypeMirror erased = types.erasure(type);
        Element element = types.asElement(erased);
        if (!(element instanceof TypeElement declared)) {
            return CollectionKind.NONE;
        }
        String qualifiedName = declared.getQualifiedName().toString();
        CollectionKind exact = switch (qualifiedName) {
            case "java.util.Collection" -> CollectionKind.COLLECTION;
            case "java.util.List" -> CollectionKind.LIST;
            case "java.util.SequencedCollection" -> CollectionKind.SEQUENCED_COLLECTION;
            case "java.util.Set" -> CollectionKind.SET;
            case "java.util.SequencedSet" -> CollectionKind.SEQUENCED_SET;
            case "java.util.SortedSet" -> CollectionKind.SORTED_SET;
            case "java.util.NavigableSet" -> CollectionKind.NAVIGABLE_SET;
            case "java.util.Map" -> CollectionKind.MAP;
            case "java.util.SequencedMap" -> CollectionKind.SEQUENCED_MAP;
            case "java.util.SortedMap" -> CollectionKind.SORTED_MAP;
            case "java.util.NavigableMap" -> CollectionKind.NAVIGABLE_MAP;
            default -> null;
        };
        if (exact != null) {
            return exact;
        }

        TypeElement collection = elements.getTypeElement("java.util.Collection");
        TypeElement map = elements.getTypeElement("java.util.Map");
        if ((collection != null && types.isAssignable(erased, types.erasure(collection.asType())))
                || (map != null && types.isAssignable(erased, types.erasure(map.asType())))) {
            return CollectionKind.UNSUPPORTED;
        }
        return CollectionKind.NONE;
    }

    /**
     * Renders a source-level Java type while deliberately discarding every type-use annotation.
     * The target model remains annotated; generated builder fields and methods do not accidentally
     * become a second validation or framework-annotation surface.
     */
    private String sourceType(TypeMirror type) {
        return switch (type.getKind()) {
            case BOOLEAN -> "boolean";
            case BYTE -> "byte";
            case SHORT -> "short";
            case INT -> "int";
            case LONG -> "long";
            case CHAR -> "char";
            case FLOAT -> "float";
            case DOUBLE -> "double";
            case VOID -> "void";
            case ARRAY -> sourceType(((ArrayType) type).getComponentType()) + "[]";
            case DECLARED, ERROR -> sourceDeclaredType((DeclaredType) type);
            case TYPEVAR -> ((TypeVariable) type).asElement().getSimpleName().toString();
            case WILDCARD -> sourceWildcardType((WildcardType) type);
            case INTERSECTION -> ((IntersectionType) type).getBounds().stream()
                    .map(this::sourceType)
                    .collect(java.util.stream.Collectors.joining(" & "));
            case UNION -> ((UnionType) type).getAlternatives().stream()
                    .map(this::sourceType)
                    .collect(java.util.stream.Collectors.joining(" | "));
            case NONE -> "";
            case NULL -> "java.lang.Object";
            default -> throw new IllegalArgumentException(
                    "Unsupported source type for generated builder: " + type.getKind());
        };
    }

    private String sourceDeclaredType(DeclaredType type) {
        if (!(type.asElement() instanceof TypeElement element)) {
            throw new IllegalArgumentException("Cannot resolve declared type: " + type);
        }

        StringBuilder result = new StringBuilder();
        TypeMirror enclosing = type.getEnclosingType();
        if (element.getNestingKind() != NestingKind.TOP_LEVEL
                && !element.getModifiers().contains(Modifier.STATIC)
                && enclosing.getKind() != TypeKind.NONE) {
            result.append(sourceType(enclosing)).append('.').append(element.getSimpleName());
        } else {
            result.append(element.getQualifiedName());
        }

        if (!type.getTypeArguments().isEmpty()) {
            result.append('<');
            StringJoiner arguments = new StringJoiner(", ");
            for (TypeMirror argument : type.getTypeArguments()) {
                arguments.add(sourceType(argument));
            }
            result.append(arguments).append('>');
        }
        return result.toString();
    }

    private String sourceWildcardType(WildcardType type) {
        if (type.getExtendsBound() != null) {
            return "? extends " + sourceType(type.getExtendsBound());
        }
        if (type.getSuperBound() != null) {
            return "? super " + sourceType(type.getSuperBound());
        }
        return "?";
    }

    private String erasedTypeName(TypeMirror type) {
        TypeMirror erased = types.erasure(type);
        return switch (erased.getKind()) {
            case ARRAY -> erasedTypeName(((ArrayType) erased).getComponentType()) + "[]";
            case BOOLEAN -> "boolean";
            case BYTE -> "byte";
            case SHORT -> "short";
            case INT -> "int";
            case LONG -> "long";
            case CHAR -> "char";
            case FLOAT -> "float";
            case DOUBLE -> "double";
            case DECLARED, ERROR -> {
                Element element = types.asElement(erased);
                if (element instanceof TypeElement declared) {
                    yield declared.getQualifiedName().toString();
                }
                throw new IllegalArgumentException("Cannot resolve erased declared type: " + erased);
            }
            default -> throw new IllegalArgumentException(
                    "Unsupported constructor parameter type for reflection: " + type);
        };
    }

    private boolean containsRawDeclaredType(TypeMirror type) {
        return switch (type.getKind()) {
            case ARRAY -> containsRawDeclaredType(((ArrayType) type).getComponentType());
            case DECLARED, ERROR -> {
                DeclaredType declared = (DeclaredType) type;
                boolean raw = declared.asElement() instanceof TypeElement element
                        && !element.getTypeParameters().isEmpty()
                        && declared.getTypeArguments().isEmpty();
                if (raw) {
                    yield true;
                }
                boolean nested = false;
                for (TypeMirror argument : declared.getTypeArguments()) {
                    if (containsRawDeclaredType(argument)) {
                        nested = true;
                        break;
                    }
                }
                yield nested;
            }
            case WILDCARD -> {
                WildcardType wildcard = (WildcardType) type;
                yield (wildcard.getExtendsBound() != null
                        && containsRawDeclaredType(wildcard.getExtendsBound()))
                        || (wildcard.getSuperBound() != null
                        && containsRawDeclaredType(wildcard.getSuperBound()));
            }
            default -> false;
        };
    }

    private boolean supportsAdderMutation(TypeMirror type) {
        if (!(type instanceof DeclaredType declared)) {
            return false;
        }
        return declared.getTypeArguments().stream()
                .noneMatch(argument -> argument.getKind() == TypeKind.WILDCARD);
    }

    private TypeArguments typeArguments(TypeMirror type, CollectionKind kind) {
        if (kind == CollectionKind.NONE
                || kind == CollectionKind.UNSUPPORTED
                || !(type instanceof DeclaredType declared)) {
            return TypeArguments.NONE;
        }
        List<? extends TypeMirror> args = declared.getTypeArguments();
        if (isMapKind(kind)) {
            if (args.size() != 2) {
                return new TypeArguments(
                        "java.lang.Object",
                        "java.lang.Object",
                        "java.lang.Object",
                        "java.lang.Object"
                );
            }
            return new TypeArguments(
                    safeAdderType(args.get(0)),
                    erasedTypeName(args.get(0)),
                    safeAdderType(args.get(1)),
                    erasedTypeName(args.get(1))
            );
        }
        if (args.size() != 1) {
            return new TypeArguments(null, null, "java.lang.Object", "java.lang.Object");
        }
        return new TypeArguments(
                null,
                null,
                safeAdderType(args.get(0)),
                erasedTypeName(args.get(0))
        );
    }

    private boolean isMapKind(CollectionKind kind) {
        return switch (kind) {
            case MAP, SEQUENCED_MAP, SORTED_MAP, NAVIGABLE_MAP -> true;
            case NONE, COLLECTION, LIST, SEQUENCED_COLLECTION, SET, SEQUENCED_SET,
                    SORTED_SET, NAVIGABLE_SET, UNSUPPORTED -> false;
        };
    }

    /** A wildcard is not a legal parameter type in every generated position; use its useful bound. */
    private String safeAdderType(TypeMirror type) {
        if (type.getKind() != TypeKind.WILDCARD) {
            return sourceType(type);
        }
        WildcardType wildcard = (WildcardType) type;
        if (wildcard.getExtendsBound() != null) {
            return sourceType(wildcard.getExtendsBound());
        }
        if (wildcard.getSuperBound() != null) {
            return sourceType(wildcard.getSuperBound());
        }
        return "java.lang.Object";
    }

    private String generatedName(TypeElement type) {
        Config config = readConfig(type);
        return config.builderPackage.isEmpty()
                ? config.builderClassName
                : config.builderPackage + "." + config.builderClassName;
    }

    private String defaultBuilderSimpleName(TypeElement type) {
        String packageName = packageName(type);
        String binaryName = elements.getBinaryName(type).toString();
        String binarySimpleName = packageName.isEmpty()
                ? binaryName
                : binaryName.substring(packageName.length() + 1);
        return binarySimpleName + "Builder";
    }

    private String packageName(TypeElement type) {
        PackageElement pkg = elements.getPackageOf(type);
        return pkg.isUnnamed() ? "" : pkg.getQualifiedName().toString();
    }

    private TypeElement enclosingType(Element element) {
        Element cursor = element.getEnclosingElement();
        while (cursor != null && !(cursor instanceof TypeElement)) {
            cursor = cursor.getEnclosingElement();
        }
        return (TypeElement) cursor;
    }

    private String typeParameterDeclaration(TypeElement type) {
        if (type.getTypeParameters().isEmpty()) {
            return "";
        }
        StringJoiner joiner = new StringJoiner(", ", "<", ">");
        for (TypeParameterElement parameter : type.getTypeParameters()) {
            StringBuilder item = new StringBuilder(parameter.getSimpleName());
            List<String> bounds = parameter.getBounds().stream()
                    .map(this::sourceType)
                    .filter(bound -> !bound.equals("java.lang.Object"))
                    .toList();
            if (!bounds.isEmpty()) {
                item.append(" extends ").append(String.join(" & ", bounds));
            }
            joiner.add(item);
        }
        return joiner.toString();
    }

    private String typeArgumentUse(TypeElement type) {
        if (type.getTypeParameters().isEmpty()) {
            return "";
        }
        StringJoiner joiner = new StringJoiner(", ", "<", ">");
        for (TypeParameterElement parameter : type.getTypeParameters()) {
            joiner.add(parameter.getSimpleName());
        }
        return joiner.toString();
    }

    private String typeArgumentDiamond(TypeElement type) {
        return type.getTypeParameters().isEmpty() ? "" : "<>";
    }


    private String staticBuilderInvocationTypeArguments(TypeElement type) {
        if (type.getTypeParameters().isEmpty()) {
            return ".";
        }
        StringJoiner joiner = new StringJoiner(", ", ".<", ">");
        for (TypeParameterElement parameter : type.getTypeParameters()) {
            joiner.add(parameter.getSimpleName());
        }
        return joiner.toString();
    }

    private String staticMethodTypeParameters(TypeElement type) {
        String declaration = typeParameterDeclaration(type);
        return declaration.isEmpty() ? "" : declaration + " ";
    }

    private String throwsClause(List<? extends TypeMirror> thrownTypes) {
        if (thrownTypes.isEmpty()) {
            return "";
        }
        return " throws " + thrownTypes.stream()
                .map(this::sourceType)
                .collect(java.util.stream.Collectors.joining(", "));
    }

    private boolean isBoolean(TypeMirror type) {
        if (type.getKind() == TypeKind.BOOLEAN) {
            return true;
        }
        TypeElement boxed = elements.getTypeElement("java.lang.Boolean");
        return boxed != null && types.isSameType(types.erasure(type), types.erasure(boxed.asType()));
    }

    private String singularize(String value) {
        if (value.endsWith("ies") && value.length() > 3) {
            return value.substring(0, value.length() - 3) + "y";
        }
        if (value.endsWith("sses") && value.length() > 4) {
            return value.substring(0, value.length() - 2);
        }
        if (value.endsWith("s") && !value.endsWith("ss") && value.length() > 1) {
            return value.substring(0, value.length() - 1);
        }
        return value + "Item";
    }

    private String capitalize(String value) {
        if (value.isEmpty()) {
            return value;
        }
        int first = value.codePointAt(0);
        int upper = Character.toUpperCase(first);
        return new StringBuilder()
                .appendCodePoint(upper)
                .append(value.substring(Character.charCount(first)))
                .toString();
    }

    private String apiAccess(Config config) {
        return config.access == BuilderAccess.PUBLIC ? "public " : "";
    }

    private String setterName(Property property, Config config) {
        return config.setterPrefix.isEmpty()
                ? property.name
                : config.setterPrefix + capitalize(property.name);
    }

    private String nestedValueCallbackName(Property property, Config config) {
        return setterName(property, config) + config.nestedCallbackSuffix;
    }

    private String collectionCallbackName(Property property, Config config) {
        return "add" + capitalize(property.adderWord) + config.nestedCallbackSuffix;
    }

    private String mapCallbackName(Property property, Config config) {
        return "put" + capitalize(property.adderWord) + config.nestedCallbackSuffix;
    }

    private boolean tracksAssignment(Property property) {
        return property.required || property.defaultProvider != null;
    }

    private boolean hasCollectionHelpers(Property property) {
        return property.generateAdders || property.generateRemovers;
    }

    private boolean usesDeferredCollectionCallbacks(Property property) {
        return property.elementCallback != null || property.mapValueCallback != null;
    }

    private boolean needsOwnership(Property property) {
        return hasCollectionHelpers(property) && !usesDeferredCollectionCallbacks(property);
    }

    private String validationExecutableField(Construction construction) {
        return construction.kind == ConstructionKind.FACTORY
                ? "$JDK_BUILDER_VALIDATION_METHOD"
                : "$JDK_BUILDER_VALIDATION_CONSTRUCTOR";
    }

    private String callbackBuilderField(Property property) {
        return property.name + "$builder";
    }

    private String deferredOperationsField(Property property) {
        return "$jdkBuilder$ops" + property.index;
    }

    private String deferredOperationType(Property property) {
        return "$JdkBuilder$Operation" + property.index;
    }

    private String deferredPrepareMethod(Property property) {
        return "$jdkBuilder$prepare" + property.index;
    }

    private List<? extends TypeMirror> copyThrownTypes(List<Property> properties) {
        Map<String, TypeMirror> unique = new LinkedHashMap<>();
        for (Property property : properties) {
            for (TypeMirror thrown : property.accessorThrownTypes) {
                unique.putIfAbsent(sourceType(thrown), thrown);
            }
        }
        return List.copyOf(unique.values());
    }

    private List<? extends TypeMirror> buildThrownTypes(
            Construction construction,
            List<Property> properties
    ) {
        Map<String, TypeMirror> unique = new LinkedHashMap<>();
        for (TypeMirror thrown : construction.thrownTypes()) {
            unique.putIfAbsent(sourceType(thrown), thrown);
        }
        for (Property property : properties) {
            List<CallbackTarget> callbacks = new ArrayList<>();
            if (property.valueCallback != null) {
                callbacks.add(property.valueCallback);
            }
            if (property.elementCallback != null) {
                callbacks.add(property.elementCallback);
            }
            if (property.mapValueCallback != null) {
                callbacks.add(property.mapValueCallback);
            }
            for (CallbackTarget callback : callbacks) {
                for (TypeMirror thrown : callback.thrownTypes) {
                    unique.putIfAbsent(sourceType(thrown), thrown);
                }
            }
        }
        return List.copyOf(unique.values());
    }

    private String setFlag(Property property) {
        return property.name + "$set";
    }

    private String ownedFlag(Property property) {
        return property.name + "$owned";
    }

    private boolean isJavaIdentifier(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        if (!Character.isJavaIdentifierStart(value.codePointAt(0))) {
            return false;
        }
        for (int offset = Character.charCount(value.codePointAt(0)); offset < value.length();) {
            int cp = value.codePointAt(offset);
            if (!Character.isJavaIdentifierPart(cp)) {
                return false;
            }
            offset += Character.charCount(cp);
        }
        return SourceVersion.isIdentifier(value) && !SourceVersion.isKeyword(value);
    }

    private String escapeJava(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private boolean hasAnnotation(Element element, String annotationType) {
        return annotation(element, annotationType).isPresent();
    }

    private java.util.Optional<AnnotationMirror> annotation(Element element, String annotationType) {
        for (AnnotationMirror mirror : element.getAnnotationMirrors()) {
            TypeElement annotationElement = (TypeElement) mirror.getAnnotationType().asElement();
            if (annotationElement.getQualifiedName().contentEquals(annotationType)) {
                return java.util.Optional.of(mirror);
            }
        }
        return java.util.Optional.empty();
    }

    private Map<String, AnnotationValue> valuesWithDefaults(AnnotationMirror mirror) {
        Map<String, AnnotationValue> result = new HashMap<>();
        for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry
                : elements.getElementValuesWithDefaults(mirror).entrySet()) {
            result.put(entry.getKey().getSimpleName().toString(), entry.getValue());
        }
        return result;
    }

    private boolean booleanValue(Map<String, AnnotationValue> values, String name) {
        Object value = Objects.requireNonNull(values.get(name), name).getValue();
        return (Boolean) value;
    }

    private String stringValue(Map<String, AnnotationValue> values, String name) {
        Object value = Objects.requireNonNull(values.get(name), name).getValue();
        return String.valueOf(value);
    }

    private <E extends Enum<E>> E enumValue(
            Map<String, AnnotationValue> values,
            String name,
            Class<E> enumType
    ) {
        Object value = Objects.requireNonNull(values.get(name), name).getValue();
        if (!(value instanceof VariableElement constant)) {
            throw new IllegalArgumentException("Annotation member '" + name + "' is not an enum");
        }
        return Enum.valueOf(enumType, constant.getSimpleName().toString());
    }

    private String stringAnnotationValue(Element element, String annotationType, String member) {
        java.util.Optional<AnnotationMirror> mirror = annotation(element, annotationType);
        if (mirror.isEmpty()) {
            return null;
        }
        AnnotationValue value = valuesWithDefaults(mirror.get()).get(member);
        return value == null ? null : String.valueOf(value.getValue());
    }

    private void error(Element element, String message) {
        messager.printMessage(Diagnostic.Kind.ERROR, message, element);
    }

    private ModelException fail(Element element, String message) {
        return new ModelException(element, message);
    }

    private enum ConstructionKind {
        RECORD_CONSTRUCTOR,
        CONSTRUCTOR,
        FACTORY
    }

    private record Construction(
            ConstructionKind kind,
            ExecutableElement executable
    ) {
        private List<? extends TypeMirror> thrownTypes() {
            return executable == null ? List.of() : executable.getThrownTypes();
        }
    }

    private enum CollectionKind {
        NONE,
        COLLECTION,
        LIST,
        SEQUENCED_COLLECTION,
        SET,
        SEQUENCED_SET,
        SORTED_SET,
        NAVIGABLE_SET,
        MAP,
        SEQUENCED_MAP,
        SORTED_MAP,
        NAVIGABLE_MAP,
        UNSUPPORTED
    }

    private record Config(
            boolean generateFrom,
            boolean collectionAdders,
            boolean collectionRemovers,
            boolean defensiveCopyCollections,
            boolean defensiveCopyArrays,
            JakartaValidationMode jakartaValidation,
            boolean builderCallbacks,
            String callbackMethod,
            String customizeMethod,
            String nestedCallbackSuffix,
            String builderClassName,
            String builderPackage,
            String setterPrefix,
            String builderMethod,
            String buildMethod,
            String fromMethod,
            String toBuilderMethod,
            String copyFromMethod,
            BuilderAccess access
    ) {
    }

    private record TypeArguments(
            String first,
            String firstErased,
            String second,
            String secondErased
    ) {
        private static final TypeArguments NONE = new TypeArguments(null, null, null, null);
    }

    private record Accessor(String name, List<? extends TypeMirror> thrownTypes) {
        private static final Accessor NONE = new Accessor(null, List.of());
    }

    private record Property(
            int index,
            String name,
            TypeMirror mirror,
            String type,
            String erasedType,
            boolean primitive,
            boolean array,
            boolean required,
            String defaultProvider,
            String accessor,
            List<? extends TypeMirror> accessorThrownTypes,
            CollectionKind collectionKind,
            String keyType,
            String keyErasedType,
            String elementType,
            String elementErasedType,
            boolean generateAdders,
            boolean generateRemovers,
            String adderWord,
            CallbackTarget valueCallback,
            CallbackTarget elementCallback,
            CallbackTarget mapValueCallback,
            Element source
    ) {
    }

    private record CallbackTarget(
            String targetType,
            String builderRawType,
            String builderType,
            String builderMethod,
            String buildMethod,
            JakartaValidationMode validationMode,
            List<? extends TypeMirror> thrownTypes
    ) {
    }

    private record GeneratedSymbol(Element element, String description) {
    }

    private record MethodKey(String name, List<String> erasedParameterTypes) {
        private String display() {
            return name + "(" + String.join(", ", erasedParameterTypes) + ")";
        }
    }

    private static final class ModelException extends RuntimeException {
        private static final long serialVersionUID = 1L;
        private final transient Element element;

        private ModelException(Element element, String message) {
            super(message);
            this.element = element;
        }
    }
}
