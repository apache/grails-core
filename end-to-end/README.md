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

# End-to-end tests

Tests that exercise Grails from the outside, where doing so needs something the core build
cannot provide — a different JDK, a different Grails major, or a real published artifact.

This is its own Gradle build, so the core build never reaches these projects and `./gradlew build`
at the repository root is unaffected by anything here.

It resolves Grails from the artifacts the core build **publishes**, not by project substitution.
That is what makes these tests end-to-end: they consume grails-core the way an application does,
through real poms and Gradle module metadata, including the CLI companion artifacts. The repository
is `<repository root>/build/local-maven` — the same one `grails-forge` points its generated
applications at via `GRAILS_REPO_URL`.

## Projects

| Project | What it is |
|---|---|
| `legacy-g7-command-plugin` | A **standalone build**, not part of this one. Compiles against published Grails 7 / Groovy 4 to produce a genuine precompiled `grails.dev.commands.ApplicationCommand` binary. |
| `legacy-commands-plugin` | A Grails 8 plugin whose legacy commands are recompiled under Groovy 5. |
| `legacy-commands` | A Grails 8 application that consumes both and runs their commands through the registry. |
| `spring-dependency-management` | A Grails 8 application that manages its versions with the legacy `io.spring.dependency-management` plugin instead of the Grails Gradle plugin's native `platform(grails-bom)`, as an upgraded Grails 7 application does. |
| `taglib-index-incremental` | Builds a Grails 8 application **twice, without a clean**, to prove a renamed or deleted tag library cannot survive in the published tag library index. Incremental behaviour is the whole point, so it cannot be expressed by a project the core build builds once for itself. |

`legacy-g7-command-plugin` is deliberately excluded from `settings.gradle`. An included build would
substitute `org.apache.grails:grails-core` for this repository's Groovy 5 project, which is exactly
the substitution the fixture exists to avoid — it must be compiled by a real Grails 7 toolchain for
its trait-woven bytecode to prove anything.

`spring-dependency-management` is here because Spring DM imports a BOM as a **Maven** BOM, resolving
it in its own detached configuration. That bypasses any project substitution, so the import can only
ever be satisfied by a published `org.apache.grails:grails-bom:<projectVersion>` — which is precisely
what this build already provides. In the core build it had to be excluded whenever nothing had been
published yet (a reproducible release build, or a fresh release branch whose version has never been
published), and otherwise silently fell back to whatever the Apache snapshot repository happened to
hold rather than the working tree.

## JDKs

The Grails 7 half declares the JDK it needs in a `.sdkmanrc`, rather than a Gradle toolchain, so that
neither the core build nor a contributor's default environment inherits a second JDK requirement:

| Where | JDK | Why |
|---|---|---|
| `legacy-g7-command-plugin/.sdkmanrc` | 17, Gradle 8.14.5 | What Grails 7 pins, so the fixture is built the way a Grails 7 plugin actually was. `gradle-bootstrap` generates this wrapper from that file via its `legacyG7Wrapper` task, rather than copying the shared one. |
| the repository's root `.sdkmanrc` | 21 | The Grails 8 baseline. This build consumes artifacts from the core build, so it runs on whatever the core build runs on — it deliberately does not re-pin that. |

## Running locally

Three steps, in order. Each fails with an actionable message if a prior one was skipped.

Publish Grails to the repository this build resolves from. Both builds publish into the same
directory and both are needed — the BOM constrains `org.apache.grails.gradle` artifacts too:

```shell
(cd grails-gradle && ./gradlew publishAllPublicationsToTestCaseMavenRepoRepository)
./gradlew publishAllPublicationsToTestCaseMavenRepoRepository
```

Build the Grails 7 fixture — it is consumed as a prebuilt jar:

```shell
cd end-to-end/legacy-g7-command-plugin
sdk env
./gradlew jar
```

Run the suite, on the root JDK:

```shell
sdk env            # from the repository root
cd end-to-end
./gradlew check
```

Re-run the publish whenever you change something in the core build that these tests exercise;
nothing here can detect that for you, because the whole point is that the build boundary is real.
For the same reason a re-run against freshly published artifacts often comes back `UP-TO-DATE` —
nothing Gradle can see about these projects changed. `DO_NOT_CACHE_TESTS` (honoured here exactly as
in the core build, see `DEVELOPMENT.md`) forces the test tasks to run anyway:

```shell
DO_NOT_CACHE_TESTS=1 ./gradlew check
```

CI does the same three steps, reading both JDK majors out of the `.sdkmanrc` files. Only the major
is honoured there: `sdk env` gives a developer the exact Liberica patch, but these are test builds
outside the reproducible-build surface, so CI deliberately takes the runner's current release of
each major. See `.github/workflows/end-to-end.yml`.

## Style and analysis checks

These projects are deliberately outside the violation gate — no `grails-code-style` or
`grails-code-analysis` plugin is applied here, matching every other test-example application in the
repository (none of the `grails-test-examples/*` projects apply them either, and the root
`aggregateViolations` reports only collect from projects that do). They are fixtures: the
application and plugin sources exist to exercise the command registry, and the Grails 7 fixture
must stay compilable by a real Grails 7 toolchain, which the current convention plugins do not
target.

## Why not `includeBuild('..')`

Composite substitution would win over the local repository and put us back to resolving projects
instead of artifacts, which is the thing these tests exist not to do. It also cannot express the CLI
companions: `grails-core-cli` is a secondary capability of `:grails-core` rather than a project, so
substituting it hits a capability self-conflict.

Published metadata has that solved already — the companion is a first-class module with its own
publication, and `CliPublishingSupport` rewrites capability requests out of what gets published so
that external consumers resolve the plain coordinate. Resolving from the repository gets that for
free; `settings.gradle` uses `exclusiveContent` so every `org.apache.grails` artifact must come from
the local build and a remote snapshot cannot quietly satisfy the request instead.
