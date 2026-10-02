plugins {
    java
    checkstyle
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spotless)
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
