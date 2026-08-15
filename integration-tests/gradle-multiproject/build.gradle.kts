import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.compile.JavaCompile

plugins {
    base
}

val processorVersion = "1.5.0-SNAPSHOT"
val validationVersion = "3.1.1"
val hibernateValidatorVersion = "9.1.3.Final"
val expresslyVersion = "6.0.0"
val localRepository = providers.gradleProperty("jdkBuilderRepo")
    .orElse(layout.projectDirectory.dir("../../build/external-m2").asFile.absolutePath)

allprojects {
    repositories {
        maven {
            url = uri(localRepository.get())
        }
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "java")

    extensions.configure<JavaPluginExtension> {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    }

    dependencies {
        add("compileOnly", "io.github.jdkbuilder:jdk-builder-processor:$processorVersion")
        add("annotationProcessor", "io.github.jdkbuilder:jdk-builder-processor:$processorVersion")
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.compilerArgs.addAll(listOf("-Xlint:all,-processing", "-Werror", "-parameters"))
    }
}

project(":model") {
    extensions.configure<SourceSetContainer> {
        named("main") {
            java.setSrcDirs(listOf(rootProject.file("../maven-reactor/model/src/main/java")))
        }
    }
    dependencies {
        add("compileOnly", "jakarta.validation:jakarta.validation-api:$validationVersion")
    }
}

project(":app") {
    extensions.configure<SourceSetContainer> {
        named("main") {
            java.setSrcDirs(listOf(rootProject.file("../maven-reactor/app/src/main/java")))
        }
    }
    val mainSourceSet = extensions.getByType<SourceSetContainer>().named("main")
    dependencies {
        add("implementation", project(":model"))
        add("implementation", "org.hibernate.validator:hibernate-validator:$hibernateValidatorVersion")
        add("runtimeOnly", "org.glassfish.expressly:expressly:$expresslyVersion")
    }

    val realValidation by tasks.registering(JavaExec::class) {
        dependsOn(tasks.named("classes"))
        classpath = mainSourceSet.get().runtimeClasspath
        mainClass.set("integration.app.RealValidationMain")
    }

    tasks.named("check") {
        dependsOn(realValidation)
    }
}
