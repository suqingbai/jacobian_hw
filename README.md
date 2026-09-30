# Order Intake Service

Multi-tenant order intake service for the take-home described in
[SeniorPlatformTakehome.md](SeniorPlatformTakehome.md). This is currently the starter skeleton:
a Spring Boot app with the actuator health endpoint, lint, and tests wired up. The order
intake and query APIs are not implemented yet.

## Stack

- Java 21 (Gradle toolchain)
- Spring Boot 3.5 (web, actuator, validation)
- Gradle 8 with the Kotlin DSL; all versions live in `gradle/libs.versions.toml`
- Lint: Spotless + google-java-format (formatting) and Checkstyle (static rules,
  `config/checkstyle/checkstyle.xml`)

## Development

Requires a JDK 21 on the machine (or let Gradle toolchain resolution find one). Use the wrapper:

```sh
./gradlew build            # compile, lint, and test (fails on any lint violation)
./gradlew check            # lint + tests only
./gradlew spotlessApply    # auto-fix formatting
./gradlew test             # tests only
./gradlew bootRun          # run on http://localhost:8080
```

Health check once the app is running:

```sh
curl http://localhost:8080/actuator/health
# {"status":"UP"}
```
