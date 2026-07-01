<!--
SPDX-License-Identifier: Apache-2.0

Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements; and to You under the Apache License, Version 2.0.
-->

# Grails Agent Skills

The canonical Grails agent skills live in this directory.

The repository root `skills` path is intentionally a symbolic link to this directory so [SkillsJars](https://www.skillsjars.com/) can discover the skills without maintaining duplicate files. SkillsJars deploys from public GitHub repositories by scanning `skills/**/SKILL.md` and then publishes one Maven Central artifact per skill under `com.skillsjars`.

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
