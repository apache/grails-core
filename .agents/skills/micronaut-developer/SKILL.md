<!--
SPDX-License-Identifier: Apache-2.0

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
-->
---
name: micronaut-developer
description: Guide for working in grails-forge (grails-forge-core, grails-forge-api, grails-forge-cli, grails-forge-web-netty) — a Micronaut application, not a Grails one. Covers Micronaut DI/bean patterns, HTTP controllers, MicronautTest+Spock, Picocli CLI commands, Rocker templating, and the Feature extension-point system. Use this instead of grails-developer/hibernate-developer when changing code under grails-forge/.
license: Apache-2.0
compatibility: opencode, claude, grok, gemini, copilot, cursor, windsurf
metadata:
  audience: maintainers
  frameworks: micronaut
---

## What I Do

- Provide repository-specific guidance for `grails-forge/` — the project generator behind start.grails.org (the Grails equivalent of Spring Initializr).
- Cover Micronaut idioms actually used in this codebase: DI (`@Singleton`, `@Inject`), HTTP (`@Controller`, `@Get`, `@Post`), testing (`@MicronautTest` + Spock), and bean indexing for plugin-style extension points (`@Indexed`).
- Cover the Picocli CLI layer in `grails-forge-cli` and the Rocker templating engine used for both code generation output and API responses.
- Correct root `AGENTS.md` rules that do not apply here — see "Where Root AGENTS.md Rules Don't Apply" below.

## When to Use Me

Activate this skill instead of `grails-developer`/`hibernate-developer` when working on:

- Anything under `grails-forge/**`.
- A new or modified `Feature` implementation (the generator's extension-point system).
- HTTP endpoints in `grails-forge-api`.
- CLI commands in `grails-forge-cli`.
- Rocker templates (`.rocker.raw`, `.rocker.html`) used for generated-project scaffolding or API rendering.

## Module Context

`grails-forge` is a **Micronaut application that generates Grails applications** — it is not itself a Grails app, and none of the GORM/artefact-handler/Hibernate content in root `AGENTS.md` applies to it. It's a multi-module Gradle build (own `settings.gradle`) with these modules:

| Subproject | Role |
|---|---|
| `grails-forge-core` | Generation logic: the `Feature` system, templating, dependency/config assembly |
| `grails-forge-api` | HTTP API (Micronaut `@Controller`s) serving generation requests — backs start.grails.org |
| `grails-forge-web-netty` | Micronaut/Netty deployment of the API, shipped to Google Cloud Run |
| `grails-forge-cli` | Picocli command-line client hitting the same generation logic |
| `grails-forge-analytics-postgres` | Separate Postgres-backed analytics service (its own `Application`, controllers, repositories) |
| `test-core` | End-to-end specs that actually generate a project and verify it builds (e.g. `CreateAppSpec`, `CreateRestApiSpec`) |
| `grails-cli`, `grails-cli-shadow` | Legacy shell-packaging plumbing (shadow-jar distribution config) — not part of the generator logic, no Micronaut patterns here |

There's also a separate React UI in `apache/grails-forge-ui` (a different repo) that consumes the API — not part of this codebase.

## Key Patterns

### Dependency Injection

Standard Micronaut DI, constructor or field injection, `jakarta.inject.*` (not `javax.inject.*` — the one root `AGENTS.md` rule that *does* transfer here).

```java
@Singleton
public class MongoSync extends MongoFeature {
    public MongoSync(TestContainers testContainers) {
        super(testContainers);
    }
    ...
}
```

### The Feature System (core domain concept)

Every installable option in a generated project — a database driver, a test framework, a cloud integration — is a `@Singleton` implementing `org.grails.forge.feature.Feature` (or a category base class like `MongoFeature`), auto-discovered via Micronaut's compile-time `@Indexed(Feature.class)` bean indexing (no classpath scanning, no manual registry to update):

```java
public interface Feature extends Named, Ordered, Described {
    @NonNull String getName();       // unique feature id
    default boolean isPreview() { return false; }
    default boolean isCommunity() { return false; }
    // ... getTitle(), getDescription(), apply(GeneratorContext), etc.
}
```

`apply(GeneratorContext)` is where a feature mutates the generated project's config and dependency list:

```java
@Override
public void apply(GeneratorContext generatorContext) {
    Map<String, Object> config = generatorContext.getConfiguration();
    config.put("grails.mongodb.url", "mongodb://${MONGO_HOST:localhost}:${MONGO_PORT:27017}/foo");
    generatorContext.addDependency(Dependency.builder()
            .groupId("org.mongodb")
            .artifactId("mongodb-driver-sync")
            .implementation());
}
```

New features go in `grails-forge-core/src/main/java/org/grails/forge/feature/<category>/`, grouped by `Category`.

### HTTP Controllers

Standard Micronaut HTTP, in `grails-forge-api`:

```java
@Controller
public class ApplicationController {
    @Get("/versions")
    @Produces(MediaType.APPLICATION_JSON)
    public HttpResponse<?> versions(...) { ... }
}
```

### Testing: MicronautTest + Spock

Not `HibernateGormDatastoreSpec` or any grails-core testing-support class — full HTTP round-trip tests via an injected client:

```groovy
@MicronautTest
class ApplicationControllerSpec extends Specification {
    @Inject
    @Client("/")
    HttpClient client

    void "test versions"() {
        given:
        def response = client.toBlocking().retrieve(HttpRequest.GET('/versions'), Map)
        expect:
        response.containsKey("versions")
    }
}
```

### CLI Commands (Picocli, DI-wired)

`grails-forge-cli` commands are Picocli `@CommandLine.Command` classes, DI-constructed:

```java
@CommandLine.Command(name = CreateServiceCommand.NAME, description = "Creates a Service Class")
public class CreateServiceCommand extends CodeGenCommand {
    public static final String NAME = "create-service";

    @CommandLine.Parameters(paramLabel = "SERVICE-NAME", description = "...")
    String serviceName;

    @Inject
    public CreateServiceCommand(@Parameter CodeGenConfig config) {
        super(config);
    }
    ...
}
```

### Templating: Rocker

Code generation output (and some API rendering) uses Rocker templates (`.rocker.raw` / `.rocker.html`), compiled to Java classes and rendered via `RockerTemplate`/`RockerWritable`. Templates live alongside the feature or command that uses them (e.g. `feature/build/gitignore.rocker.raw`, `template/api/grailsForgeApi.rocker.raw`).

## Where Root AGENTS.md Rules Don't Apply

Verified against actual `grails-forge` source before writing this — don't take these on faith either, re-check if the codebase changes:

- **Rule "Use `@GrailsCompileStatic`, not `@CompileStatic`" does not apply and is actively wrong here.** `grails-forge` has zero Grails artefacts and zero `@GrailsCompileStatic` usages; it correctly uses plain `@CompileStatic` (8 files). Don't "fix" this.
- **`GrailsWebRequest.lookup()`, GORM, artefact handlers**: none of these concepts exist in this codebase (0 occurrences). Ignore the root file's Artefact Types table, Key Modules table, and Test Isolation section when working here.
- **Code style tooling differs**: `grails-forge` uses Spotless (`spotlessJavaMisc`) and `checkstyleNohttp`, not the root project's CodeNarc/PMD/SpotBugs/`aggregateViolations` stack. Don't run `./gradlew clean aggregateViolations` expecting it to cover this subproject — it doesn't. Run `grails-forge`'s own style tasks from within `grails-forge/`.
- **Rule "jakarta.* not javax.*" does apply** — `grails-forge` follows it (108 files use `jakarta.*`), and Micronaut itself is jakarta-based, so this is one root rule that transfers cleanly.
- **Dependency/BOM management differs**: `grails-forge` has its own `settings.gradle`/`build.gradle` and its own version properties (`micronautVersion`, `picocliVersion`, etc.), independent of `dependencies.gradle`/`grails-bom`. The root `validateDependencyVersions` BOM rules don't govern this subproject.

## Build & Test

Run from inside `grails-forge/`, not the repo root:

```bash
cd grails-forge
./gradlew build
./gradlew :grails-forge-api:test
./gradlew :grails-forge-cli:test
```

## Pitfalls to Avoid

- Do not apply GORM/Hibernate/artefact-handler mental models here — this is a Micronaut HTTP service and CLI, not a Grails application.
- Do not add a new `Feature` without checking `@Indexed(Feature.class)` picks it up automatically via `@Singleton` — no manual registry file to update.
- Do not use `javax.inject.*` — this codebase is jakarta-based like the rest of the repo.
- Do not assume root `AGENTS.md`'s violation-fixer workflow covers this subproject's style checks.

## Source of Truth

This skill is the repository guidance for `grails-forge` work. When the module's conventions change, update this skill directly so agents load current rules from `.agents/skills/micronaut-developer/SKILL.md`.
