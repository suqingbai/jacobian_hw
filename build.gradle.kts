plugins {
    java
    checkstyle
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spotless)
    alias(libs.plugins.jib)
}

group = "com.jacobian"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

val mockitoAgent: Configuration = configurations.create("mockitoAgent")

repositories {
    mavenCentral()
}

dependencies {
    implementation(platform(libs.spring.boot.dependencies))
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.flyway.core)
    implementation(libs.flyway.database.postgresql)
    implementation(libs.postgresql)
    implementation(libs.springdoc.openapi.starter.webmvc.ui)

    developmentOnly(platform(libs.spring.boot.dependencies))
    developmentOnly(libs.spring.boot.docker.compose)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.boot.testcontainers)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.testcontainers.postgresql)
    testRuntimeOnly(libs.junit.platform.launcher)

    mockitoAgent(platform(libs.spring.boot.dependencies))
    mockitoAgent(libs.mockito.core) { isTransitive = false }
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    // Load Mockito's inline mock maker as an agent up front instead of self-attaching at runtime.
    // The agent extends the bootstrap classpath, which is incompatible with class data sharing,
    // so CDS is disabled to keep the test JVM from warning about it.
    jvmArgs("-javaagent:${mockitoAgent.asPath}", "-Xshare:off")
    // Log each test outcome so CI output shows which tests ran, passed, or were skipped.
    testLogging {
        events("passed", "skipped", "failed")
    }
}

spotless {
    java {
        googleJavaFormat(libs.versions.google.java.format.get())
        formatAnnotations()
    }
}

checkstyle {
    toolVersion = libs.versions.checkstyle.get()
    maxWarnings = 0
}

// Container image: ./gradlew jibDockerBuild builds to the local Docker daemon, ./gradlew jib
// pushes to the registry. CI only runs jibBuildTar.
jib {
    // Same classpath as bootJar: runtimeClasspath also carries developmentOnly dependencies
    // (Docker Compose support), which must stay out of the image.
    configurationName = "productionRuntimeClasspath"
    from {
        // Distroless Java 21 (Debian 13), nonroot variant: JRE only, no shell or package manager,
        // and a built-in nonroot user. Pinned to the multi-arch index digest for reproducible
        // builds; bump the tag and digest together.
        image =
            "gcr.io/distroless/java21-debian13:nonroot" +
            "@sha256:0a1f5a75661918de9c0813f287f651c3bf2d6dd752eada5f084eb0c1f14ced9e"
    }
    to {
        // Docker Hub repository, tagged with the project version and latest. Override with
        // -Pimage=registry/name:tag, which gets only that tag.
        val imageOverride = providers.gradleProperty("image").orNull
        image = imageOverride ?: "docker.io/suebai/order-intake:${project.version}"
        if (imageOverride == null) {
            tags = setOf("latest")
        }
    }
    container {
        // The distroless nonroot user (uid/gid 65532), numeric so runAsNonRoot checks can verify it.
        user = "65532:65532"
        ports = listOf("8080")
        jvmFlags = listOf("-XX:MaxRAMPercentage=75", "-XX:+ExitOnOutOfMemoryError")
        labels.putAll(
            mapOf(
                "org.opencontainers.image.title" to rootProject.name,
                "org.opencontainers.image.version" to project.version.toString(),
                "org.opencontainers.image.source" to "https://github.com/suqingbai/jacobian_hw",
            ),
        )
    }
}
