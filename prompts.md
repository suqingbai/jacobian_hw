## Tooling
I use an open source agent distro - firstmate(https://github.com/kunchenguid/firstmate) - to manage my terminal based multi-agent development workflow. Besides coding, this tool also automate linting, documentation, validation and PR creation. It supports agentic development natively, so it also generates and maintains AGENTS.md.

## Prompt 1 — Build Spring Boot service skeleton with Gradle
**Asked:** "jacobian-hw project is a SprinBoot app provide REST endpoint for order intake
and query APIs. First step, create a starter application with Java 21, latest
stable springBoot 3.x release, and gradle.Manage dependency with version
catalog, manage java version using java toolChain. Add lint to the build steps."

**Got:** https://github.com/suqingbai/jacobian_hw/pull/1 

**Changed:** None

**Why:** 

## Prompt 2 — Add GitHub Action for CI
**Asked:** "add github action for CI, use direct PR mode"

**Got:** https://github.com/suqingbai/jacobian_hw/pull/2

**Changed:** None

**Why:** 

## Prompt 3 — Add persistent layer support: postgreSQL db, JPA, and flyWay
**Asked:** "pull in persistant layer support: postgreSQL db, JPA, and flyWay. For spring
dev profile, running postgreSQL(latest stable dev version) container locally,
bootstrap db container using spring support for docker to avoid seperate
startup. Test spring to db connection using "select * from dual" or something
similar."

**Got:** https://github.com/suqingbai/jacobian_hw/pull/3

**Changed:** None

**Why:** 

**Research:** 
1. confirmed upgrading spring boot managed flyway version to newer is logical
2. confirmed postgreSql test container lifecycle, one container per ./gradlew test run