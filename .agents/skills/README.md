<!--
SPDX-License-Identifier: Apache-2.0

Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements; and to You under the Apache License, Version 2.0.
-->

# Grails Agent Skills

The canonical Grails agent skills live in this directory. Skills that are only useful while working in the Grails framework repository stay here and are not published through SkillsJars.

The repository root `skills/` directory contains normal skill directories for app-facing skills that are useful when building or upgrading end-user Grails applications. Each published directory contains only a `SKILL.md` symbolic link back to the canonical source in `.agents/skills`, so [SkillsJars](https://www.skillsjars.com/) can discover and package the skills without duplicating content. SkillsJars deploys from public GitHub repositories by scanning `skills/**/SKILL.md` and then publishes one Maven Central artifact per discovered skill under `com.skillsjars`.

| Published skill | Source skill | Use |
|-----------------|--------------|-----|
| `grails-developer` | `.agents/skills/grails-developer` | Building current Grails web applications, REST APIs, GORM models, controllers, services, views, plugins, and tests |
| `grails-8-upgrade` | `.agents/skills/grails-8-upgrade` | Upgrading Grails applications from Grails 7.x to Grails 8 |

The repository-specific `hibernate-developer`, `test-fixer`, and `violation-fixer` skills are intentionally not linked from `skills/` because they are for Grails framework development. Contributors already receive them from this repository when working on Grails core.

Run this check before publishing or updating a skill:

```bash
./gradlew verifySkillsJarsSources
```

After changes are merged to the public branch, publish them from SkillsJars by submitting:

| Field | Value |
|-------|-------|
| GitHub Org | `apache` |
| GitHub Repo | `grails-core` |

Expected coordinates use the `apache__grails-core__<skill-name>` artifact pattern, for example `com.skillsjars:apache__grails-core__grails-developer:<date>-<commit>`.
