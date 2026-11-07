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

# Agent Guide for grails-gradle

> **IMPORTANT**: `grails-gradle` holds the Grails Gradle plugins that applications apply. It has its own
> `settings.gradle` and its own CI jobs, but it shares the root's dependency management: `build.gradle` applies the
> root's `dependencies.gradle`, so versions are managed there. It is build tooling, not Grails application code, so
> two of the root rules do not apply here (see below). Root [`AGENTS.md`](../AGENTS.md) still governs PR, branch and
> review conventions and repository-wide policy.

## Quick Reference

Run from inside `grails-gradle/`:

```bash
./gradlew build --continue
./gradlew build -PskipTests -PskipCodeStyle
./gradlew validateDependencyVersions --continue
```

## Rules That Differ From Root

1. **Use plain `@CompileStatic`, not `@GrailsCompileStatic`.** The plugins are Gradle code, not Grails artefacts; the only `@GrailsCompileStatic` mentions in this build are documentation of what a plugin configures for an application.
2. **`javax.inject.Inject` and `javax.xml.*` are correct here.** They are JSR-330 and JDK APIs, not Jakarta EE packages. The `jakarta.*` references in this build configure a downstream application's dependencies.
3. **Versions are not pinned here.** `gradle.properties` holds daemon and cache settings only; `build.gradle` applies the root's `dependencies.gradle`. Bump versions in the root file.
4. **Specs that need a real build run it under TestKit.** Those extend `GradleSpecification` (`plugins/src/test/groovy/org/grails/gradle/plugin/core/GradleSpecification.groovy`, 23 specs, all in `plugins`) and run a generated project; the rest, in `plugins`, `model` and `common`, are ordinary Spock unit specs.

## Project Structure

| Directory | Gradle project | Role |
|---|---|---|
| `bom/` | `:grails-gradle-bom` | A `java-platform` constraining the sibling projects |
| `common/` | `:grails-gradle-common` | Shared utilities |
| `model/` | `:grails-gradle-model` | The subset of Grails runtime classes the plugins need at build time, before the framework is on the classpath |
| `plugins/` | `:grails-gradle-plugins` | The `Plugin<Project>` implementations |
| `tasks/` | `:grails-gradle-tasks` | The task classes |
| `buildSrc/` | none (its own `settings.gradle`) | The build conventions of this build; applies `org.apache.grails.buildsrc.properties` and the root's `dependencies.gradle`. The one part of this build that is not an included project |

## CI

`.github/workflows/gradle.yml` is where this build is built and tested, so look there first when a plugin change
breaks. Its `buildGradle` job runs `./gradlew build` in `grails-gradle` on JDK 21 and 25, with `--rerun-tasks` when the
CI policy enables tests and `-PskipTests` otherwise; `validateDependencies` runs `validateDependencyVersions` and
`validateBomProperties` has a step for it too, both in `grails-gradle`; `publishGradle` publishes it. `end-to-end.yml`
publishes it to a local repository that its tests resolve from. The build-health, code-style, code-analysis, coverage and release workflows under
`.github/workflows` each have a job with `working-directory: grails-gradle` as well, so this build is checked on its own
on every push and pull request.
