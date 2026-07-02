<!--
SPDX-License-Identifier: Apache-2.0

Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements; and to You under the Apache License, Version 2.0.
-->

# Grails Agent Skills

The canonical Grails agent skills live in this directory. Skills that are only useful while working in the Grails framework repository stay here and are not published.

App-facing skills that are useful when building or upgrading end-user Grails applications are published to Maven Central as individual artifacts. Each published skill has its own Gradle project under `grails-skills/`. The project packages the skill's `SKILL.md` into a jar via a symlink back to the canonical source in this directory, so there is no content duplication.

| Published skill | Gradle project | Maven coordinates | Source skill | Use |
|-----------------|----------------|-------------------|--------------|-----|
| `grails-developer` | `grails-skills/developer` | `org.apache.grails.skills:grails-developer` | `.agents/skills/grails-developer` | Building current Grails web applications, REST APIs, GORM models, controllers, services, views, plugins, and tests |
| `grails-8-upgrade` | `grails-skills/upgrade-guide-8` | `org.apache.grails.skills:grails-8-upgrade` | `.agents/skills/grails-8-upgrade` | Upgrading Grails applications from Grails 7.x to Grails 8 |

The repository-specific `hibernate-developer`, `test-fixer`, `violation-fixer`, `groovy-developer`, and `java-developer` skills are intentionally not published because they are for Grails framework development. Contributors already receive them from this repository when working on Grails core.

Each `grails-skills/<name>/src/main/resources/SKILL.md` is a symbolic link to the canonical source under `.agents/skills/<name>/SKILL.md`. Edit the canonical source; the published jar always packages the current content.

To add a new published skill:

1. Add the canonical `SKILL.md` under `.agents/skills/<name>/`.
2. Create a Gradle project under `grails-skills/<project>/` with a `build.gradle` that applies `java` and `org.apache.grails.buildsrc.publish`, sets `group = 'org.apache.grails.skills'`, and declares `pomArtifactId`, `pomTitle`, and `pomDescription`.
3. Symlink `grails-skills/<project>/src/main/resources/SKILL.md` to the canonical source.
4. Register the project in `settings.gradle` and add it to the `publishedProjects` list in `gradle/publish-root-config.gradle`.

Build a skill jar locally with:

```bash
./gradlew :grails-skills-developer:build
./gradlew :grails-skills-developer:publishToMavenLocal
```
