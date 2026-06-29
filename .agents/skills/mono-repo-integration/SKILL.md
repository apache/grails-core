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
name: mono-repo-integration
description: Step-by-step process for merging a previously-standalone Grails plugin repository (e.g. grails-spring-security, grails-redis) into the grails-core monorepo as one or more Gradle subprojects, wiring it into the shared build, publishing, docs, and CI the same way the existing modules are.
license: Apache-2.0
compatibility: opencode, claude, grok, gemini, copilot, cursor, windsurf
metadata:
  audience: maintainers
  frameworks: grails
  versions: 7
---

## What I Do

- Merge a standalone Grails plugin repo (its source was copied into a top-level folder such as `grails-<name>/`) into the grails-core monorepo build.
- Strip the imported repo's standalone build/release/CI infrastructure and rewire its modules onto the monorepo's **shared** Gradle config, publishing, docs guide, and CI.

This skill is the generalization of the **Spring Security merge** (git commits prefixed `Spring Security Merge - ...`, starting at `6d06f6c84f` / `fd1939a7e2`). When in doubt, read those commits — they are the canonical worked example:

```bash
git log --oneline --grep "Spring Security Merge"
git show <sha>            # inspect any individual step
```

## Guiding Principles (NON-NEGOTIABLE)

1. **No custom/duplicated gradle files.** The imported repo ships its own `gradle/*.gradle` (test-config, publish-config, java-config, docs-config, reproducible-config, rat-root-config, examples-config, etc.). **Delete them all.** Every module must `apply from:` the monorepo's existing root `gradle/*.gradle` files instead.
2. **No hard-coded dependency versions.** The monorepo uses the Grails BOM (`grails-bom`). Drop version numbers from the imported `build.gradle` files and from `gradle.properties`; rely on `platform("org.apache.grails:grails-bom:$grailsVersion")`. Only add a version to `dependencies.gradle` (and reference it) if the dependency is genuinely not already managed by a BOM. Check first: `grep -i '<artifact>' dependencies.gradle grails-bom/*/build/*-constraints.adoc`.
3. **Publish the same way.** Add each published module to `publishedProjects` in `gradle/publish-root-config.gradle`, and have each module `apply from: '<root>/gradle/publish-config.gradle'` (the monorepo's, not the imported one). Do not keep a per-repo publish-config.
4. **Integrate authors into the publish plugin.** Merge the imported repo's developer list into `build-logic/plugins/src/main/groovy/org/apache/grails/buildsrc/PublishPlugin.groovy`, keeping each list alphabetized by handle. Two rules:
   - **Dedupe against BOTH the `contributor(...)` and `emeritus(...)` lists** — and match on the person, not just the handle (e.g. `christianoestreich` may already be present as `ctoestreich`; `burtbeckwith`/`puneetbehl` may already be `emeritus`). Never add someone who is already in either list under any handle.
   - **Classify by recency, defaulting to `emeritus`.** Check each author's most recent commit (`git log --all --author="<name>" --format=%ad --date=short -1`). If they have not contributed recently, add them as `emeritus(...)`, not `contributor(...)`. Most authors from a long-dormant imported plugin will be emeritus. Only use `contributor(...)` for genuinely active contributors.
5. **Functional/example apps live under `grails-test-examples/`.** Move the imported repo's `examples/*` and `*-test-app` projects out of the plugin folder into `grails-test-examples/<name>/...` and wire them through `gradle/functional-test-config.gradle`.
6. **Docs go into the guide.** Migrate documentation into `grails-doc/src/en/guide/...` as AsciiDoc. There is no standalone docs subproject. Markdown READMEs must be converted to `.adoc`.
7. **Drop historical release history and author/changelog sections from docs.** The monorepo guide does not store per-plugin release history, "previous work", or author lists. Remove `history.adoc`, `authors.adoc`, `previouswork.adoc`, changelog tables, and README "release history" sections.
8. **Remove hard-coded URLs to the old project docs.** Replace absolute links to the legacy standalone documentation site with guide-relative cross-references or BOM/attribute-driven links (see `grails-doc/build.gradle` attribute map).
9. Follow `CLAUDE.md` rules throughout: `jakarta.*` not `javax.*`, Apache license header on every new file, 4-space indent, no wildcard imports, tests via public APIs.
10. **Inter-module dependency syntax differs by project kind.** The plugin/library modules themselves depend on sibling monorepo modules via **`project(':grails-...')`**. The `grails-test-examples/` apps depend on those same modules via **Maven coordinates** (`'org.apache.grails:grails-...'`), consuming them as published artifacts. Do not mix these up — convert the imported `project(...)` references in example/test apps to coordinates during Phase 4.

## Phased Process

Mirror the Spring Security commit sequence. Make one focused commit per phase, messaged `<Name> Merge - <step>`.

### Phase 0 — Import the repository (preserving history)
Bring the standalone repo in under a top-level `grails-<name>/` prefix using a subtree-style merge, so the imported commit history is preserved (joined via `-s ours`) while the working tree is populated from `read-tree`. Add the source repo as a remote first (`git remote add grails-<name> <url> && git fetch grails-<name>`), then:

```bash
# <ref> is the imported repo's release branch, e.g. grails-redis/5.0.x
git merge -s ours --no-commit --allow-unrelated-histories grails-<name>/<ref>
git read-tree --prefix=grails-<name>/ -u grails-<name>/<ref>
git commit -m "Initial import of Grails <Name> Repository"
```

This produces the single `Initial import of Grails <Name> Repository` commit (the starting point the rest of this skill restructures). Example actually used for redis:

```bash
git merge -s ours --no-commit --allow-unrelated-histories grails-redis/5.0.x
git read-tree --prefix=grails-redis/ -u grails-redis/5.0.x
git commit -m "Initial import of Grails Redis Repository"
```

### Phase 0.5 — Survey
- `git log --oneline | grep -i "Initial import"` to find the import commit.
- Map the imported tree: `find grails-<name> -type d`. Identify: plugin module(s), example/test apps, docs (adoc or README), the developer list (`gradle/publish-config.gradle` → `it.developers`), and all standalone infra.
- List what the monorepo already provides so you reuse it: `ls gradle/`, `publishedProjects` in `gradle/publish-root-config.gradle`, the `contributor(...)` block in `PublishPlugin.groovy`, the guide layout under `grails-doc/src/en/guide/`, and the CI test-filter flags in `DEVELOPMENT.md`.

### Phase 1 — Initial Moves (examine infra, then port-or-delete + restructure)
**Do not blindly delete.** Most of the imported repo's standalone infrastructure is removed because the monorepo already provides it — but several files carry repo-specific customizations that must be *carried over* into the monorepo's equivalents first. Examine each, decide port-or-delete, then delete the standalone copy. `git diff` the imported file against the monorepo's equivalent to see exactly what is custom.

**Examine and port (customizations must survive):**
- **`NOTICE` / `LICENSE`** — first determine whether they are *standard* (boilerplate Apache header/notice) or *customized*. If standard, just delete them: the monorepo's shared gradle plugins (applied to every module) generate/import the generic `LICENSE`/`NOTICE` automatically, so no carry-over is needed. Only when they are customized (bundled third-party components, extra attribution clauses) do you diff against the monorepo's top-level `NOTICE`/`LICENSE` and `licenses/` and merge the repo-specific additions in before deleting the imported copies.
- **`.gitignore`** — may contain custom excludes (generated dirs, plugin-specific artifacts). Fold any non-duplicate entries into the monorepo's root `.gitignore` before deleting.
- **RAT config** (the repo's `gradle/rat-*.gradle`) — almost always lists files that must be excluded for `rat` license validation to pass (templates, generated files, third-party-licensed assets shipped with the plugin). **Port every still-relevant exclude** into the root `gradle/rat-root-config.gradle` (paths rewritten to the new `grails-<name>/...` location). Missing these causes `./gradlew rat` to fail later. (Cross-reference Phase 2.)
- **`gradle.properties`** — versions should generally match the monorepo, but watch for third-party libraries pinned here that *should* be BOM-managed: those must be imported into the BOM (`dependencies.gradle` / `grails-bom`) rather than carried as loose properties. Carry over only genuinely non-BOM-managed props (Phase 2).
- **`.github/workflows/`** — generally removable (the monorepo has its own CI), but read them first: check for extra validations, required **service containers** (e.g. a `redis`/`postgres` service the functional tests need), or dependency setup steps. Anything load-bearing must be reproduced in the monorepo workflow + the new dedicated test job you add in Phase 2.
- **`buildSrc/`** — usually removable, but inspect for custom tasks/plugins/conventions the build actually depends on; integrate any such behavior into `build-logic/` or the root gradle config before deleting.

**Examine briefly, then typically delete:**
- **`etc/`** — typically build-verification/release scripts (reproducible-build checks, artifact verification). Usually safe to drop, but do a short scan to confirm nothing the monorepo lacks is referenced by the build.
- **`.asf.yaml`, `.sdkmanrc`, `CODE_OF_CONDUCT.md`, `HEADER`, `ISSUE_TEMPLATE.md`** — standalone-repo metadata superseded by the monorepo's; delete.

**Delete outright (always superseded by the monorepo):**
- `gradlew`, `gradlew.bat`, `gradle/wrapper/`, `gradle-bootstrap/`
- repo-root `settings.gradle`, root `build.gradle`
- the repo's own `gradle/*.gradle` convention files (test-config, publish-config, java-config, docs-config, reproducible-config, examples-config, and the now-ported rat config)
- `README.md` — its content is migrated to the guide in Phase 3, then deleted.

**Test-skip property note:** the monorepo's `grails-core` CI workflows pass a skip flag (e.g. `-Pskip<Name>`/`-Pskip<Name>Tests`) to exclude this plugin's functional tests from the default runs, and a **separate dedicated workflow** runs them (with any required service containers). Note here what the imported CI needed; wire the flag + dedicated job in Phase 2.

**Restructure** directories to the monorepo convention. **Initial Moves is pure deletion + relocation — do NOT rewrite file contents here.** Keeping moves and content edits in separate commits means git records relocations as renames, so every later phase's content change diffs cleanly against the moved file instead of appearing as a delete+add. Concretely, the Initial Moves commit:
- **Collapses a single-plugin repo's source up to the repo root** — move `grails-<name>/plugin/{grails-app,src,build.gradle}` to `grails-<name>/` so the plugin project's dir is simply `grails-<name>/` (no `projectDir` mapping needed, since the dir name matches the project name). Multi-module repos (like spring-security) keep nested `plugin`/`docs` folders.
- **Relocates example/functional apps to `grails-test-examples/<name>/...`** (e.g. `grails-<name>/examples/<app>` → `grails-test-examples/<name>/<app>`), moved verbatim. If the repo has only a **single** functional app, flatten it directly into `grails-test-examples/<name>/` (drop the redundant per-app subfolder) and name the project `grails-test-examples-<name>`.
Each deployable module gets a clean folder; nested project dirs are mapped explicitly via `projectDir` in `settings.gradle`, so directory names can differ from project names. The content edits to these moved files (build-script rewrites, dependency-by-coordinate conversion, applying root gradle config) happen in the *later* phases and will show as clean diffs.

### Phase 2 — Integrate the build
Edit, in this order:
- **`settings.gradle`** (root): add each module to the `include(...)` list with a `grails-<name>-...` project name, then set `project(':grails-<name>-...').projectDir = new File(settingsDir, 'grails-<name>/<path>')`. Add functional/example apps as `grails-test-examples-<name>-...` mapped into `grails-test-examples/<name>/...`.
- **Each module `build.gradle`**: keep the `plugins { ... }` block and dependencies, but (a) strip versions in favor of the BOM, (b) replace the `apply { from ... }` block to point at the **root** `gradle/*.gradle` files. **Declare all Gradle plugins in the `plugins { }` block** (the composite build resolves the project's `org.apache.grails.*` convention plugins there) rather than the legacy `apply plugin: '...'` form — match the modern test projects (e.g. `grails-test-examples/scaffolding`). Note: `apply from: '<script>.gradle'` for applying gradle *script* snippets is a separate, still-standard mechanism — only plugin *application* moves into `plugins { }`. (`test-config.gradle`, `publish-config.gradle`, `docs-config.gradle`, `java-config.gradle`, `reproducible-config.gradle`, etc.). Delete the module's reference to any deleted per-repo gradle file. For dependencies on **other monorepo modules**, use **`project(':grails-...')` syntax** (not Maven coordinates) — these are the published Gradle projects building alongside this one (see Principle 10).
- **`dependencies.gradle`**: add only the genuinely-unmanaged dependency coordinates + versions (alphabetical, in both `bomDependencyVersions` and `bomDependencies`).
- **`gradle.properties`**: add only non-BOM-managed version props; match the existing `<name>Version` naming style.
- **`gradle/publish-root-config.gradle`**: add every published module to `publishedProjects` (alphabetical).
- **`build-logic/plugins/.../PublishPlugin.groovy`**: merge in the imported developers (alphabetical, deduped against both lists). Per Principle 4, check each author's commit recency and add inactive ones as `emeritus('<id>', '<name>', project)` rather than `contributor(...)`.
- **Test filter flags** — add `only<Name>Tests` / `skip<Name>Tests` consistently across: `gradle/test-config.gradle`, `gradle/functional-test-config.gradle`, `gradle/grails-data-tck-config.gradle`, `build-logic/docs-core/build.gradle`, and document both in `DEVELOPMENT.md`. (Spring Security added `onlySpringSecurityTests`/`skipSpringSecurityTests`.) Only introduce a dedicated `<name>-test-config.gradle` if the plugin truly needs bespoke test wiring (Spring Security did for Geb/integration); prefer reusing the shared one.
- **`gradle/rat-root-config.gradle`**: add RAT excludes for template files, generated files, and third-party-licensed assets the module ships.
- **`.github/workflows/gradle.yml`**: add a CI job (or extend an existing matrix) that runs the new `only<Name>Tests` slice. **For tests that need an external service (DB, cache, broker), prefer Testcontainers driven by a Spock `IGlobalExtension` over a GitHub Actions `services:` container** — this is the repo's established convention (see the mongodb example apps' `SpringBootStart*Extension`/`*ContainerVersion` pattern; the monorepo has *no* `services:` blocks). Check the BOM first — the container module is often already managed (e.g. `com.redis:testcontainers-redis`, `org.testcontainers:*`). The extension starts the container, reads the image version from a `-P<name>ContainerVersion` system property (wired through the test-config), and injects host/port into the spec (the imported tests usually already read host/port from env/config). Run the CI job with `-Ponly<Name>Tests -P<name>ContainerVersion=<v>` across a version matrix. Also pass `-Pskip<Name>Tests` on the plain `./gradlew build`(-with-tests) jobs so the service-dependent tests don't run where no container is provisioned.

### Phase 3 — Migrate documentation into the guide
- **A guide section is mandatory — always add one.** If the imported repo had no dedicated documentation (no `docs/`, no `.adoc` guide), the docs are typically living in the project's `README.md`. In that case **convert the `README.md` into an appropriate guide section** rather than skipping documentation. Strip README-only boilerplate (build/CI badges, "Building"/"Publishing to mavenLocal" dev instructions, release history) and keep the user-facing usage content.
- Create `grails-doc/src/en/guide/<area>/<name>/...adoc`. For plugins with a security flavor this is `grails-doc/src/en/guide/security/securityPlugins/...`; otherwise pick the matching guide area (or a new top-level area for the plugin). Convert Markdown READMEs to AsciiDoc.
- Wire the new pages into the guide TOC (`grails-doc/src/en/guide/index.adoc` and the relevant section `.adoc`).
- If the docs pull in source snippets or example output, register the source dirs in `grails-doc/build.gradle` (`sourcedir`/`functionalSourceDir`-style attribute map) and point them at the relocated `grails-test-examples/<name>/...` apps.
- **Drop release history / authors** (Principle 7) and **remove hard-coded legacy-docs URLs** (Principle 8). Where a link to the old independently-published docs must remain (historical versions), route it through a single attribute in `grails-doc/build.gradle` rather than hard-coding it per page.
- **Preserve "helpful" links** — READMEs commonly end with a list of useful external references (upstream project docs, command references, related libraries, blog posts/presentations). Don't discard these: add a dedicated section to the plugin's guide documentation (e.g. a "Reference" / "Useful Links" / "Further Reading" page or trailing section) to hold them, rather than dropping them with the README.
- Use BOM syntax in install/usage snippets (no hard-coded plugin/dependency versions) (Principle 2).

### Phase 4 — Wire up the relocated functional/example apps
The apps were physically moved to `grails-test-examples/<name>/...` back in Initial Moves; this phase is the content edits on them:
- Update each app's `build.gradle` to depend on the plugin **by Maven coordinates** (e.g. `implementation 'org.apache.grails:grails-<name>'`), **not** `project(':grails-<name>')` — `grails-test-examples` apps consume modules as published artifacts, the opposite convention from the plugin modules themselves (see Principle 10). Use the BOM, and `apply from:` the root `gradle/functional-test-config.gradle` / `gradle/test-config.gradle` (or the dedicated `<name>-test-config.gradle`).
- Confirm their `settings.gradle` mappings (Phase 2) and that `functional-test-config.gradle` recognizes the `grails-test-examples-<name>-*` prefix for the test-filter flags.

### Phase 5 — Cleanup & verify
- Remove now-empty imported folders.
- Match property/naming style to existing conventions (e.g. `xxxVersion`).
- Build and test:
  ```bash
  ./gradlew build -PskipTests                       # compiles + wires
  ./gradlew :grails-<name>:test
  ./gradlew check -Ponly<Name>Tests --continue      # the new test slice
  ./gradlew codeStyle
  ./gradlew rat                                      # license headers / excludes
  ./gradlew :grails-doc:publishGuide -x aggregateGroovydoc   # docs build
  ```

## Verification Checklist

- [ ] No `grails-<name>/gradle/*.gradle`, `buildSrc/`, `gradlew*`, `.github/`, `.asf.yaml`, `gradle-bootstrap/`, repo-root `settings.gradle`/`build.gradle`/`gradle.properties` remain.
- [ ] Every module applies the **root** gradle config files; none reference deleted per-repo files.
- [ ] No hard-coded dependency versions; BOM resolves them. New unmanaged versions only in `dependencies.gradle`/`gradle.properties`.
- [ ] All published modules in `publishedProjects`; authors merged into `PublishPlugin.groovy`.
- [ ] `only<Name>Tests`/`skip<Name>Tests` flags exist in all test-config files + `DEVELOPMENT.md`; CI job added.
- [ ] Example/functional apps live under `grails-test-examples/<name>/` and depend on the in-repo project.
- [ ] Docs live in `grails-doc/src/en/guide/...`, wired into the TOC, no release history/authors, no hard-coded legacy URLs, BOM-style install snippets.
- [ ] `./gradlew build -PskipTests`, `codeStyle`, `rat`, and the doc build all pass.
