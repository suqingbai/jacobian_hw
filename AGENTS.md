# Project agent memory

This file is the project's committed home for project-intrinsic agent knowledge: build, test, release, architecture, and sharp-edge notes that should travel with the code.

- Build/lint/test/run commands: see `README.md`. `./gradlew build` must stay green; run `./gradlew spotlessApply` before committing Java changes.
- All dependency and plugin versions live in `gradle/libs.versions.toml`; build scripts reference catalog aliases only. Spring Boot starters are versionless and resolved through the `spring-boot-dependencies` platform.
- Stay on Spring Boot 3.x (not 4.x) and Gradle 8.x: Spring Boot 3.5 docs list Gradle 7.6.4+/8.4+ as supported, not 9.x.
- Checkstyle (`config/checkstyle/checkstyle.xml`) holds static rules only; formatting belongs to google-java-format via Spotless, so do not add formatting rules to Checkstyle.

## Maintaining this file

Keep this file for knowledge useful to almost every future agent session in this project.
Do not repeat what the codebase already shows; point to the authoritative file or command instead.
Prefer rewriting or pruning existing entries over appending new ones.
When updating this file, preserve this bar for all agents and keep entries concise.
