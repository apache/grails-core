---
name: grails-8-upgrade
description: Superseded by the grails-upgrade skill. Use grails-upgrade for upgrading Grails applications from Grails 7.x to Grails 8 and between Grails 8 releases.
license: Apache-2.0
---

<!--
SPDX-License-Identifier: Apache-2.0

Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements; and to You under the Apache License, Version 2.0.
-->

## This Skill Has Moved

The guidance for upgrading a Grails application to Grails 8 is now in the `grails-upgrade` skill, published as `org.apache.grails.skills:grails-upgrade`. It holds everything this skill held, and the changes of each Grails 8 release after 8.0.0.

- Load the `grails-upgrade` skill and follow it. Do not continue with this one.
- If `grails-upgrade` is not available, add it to the application's build beside this skill, with the version that the build declares for `grails-8-upgrade`, and extract it with `./gradlew extractSkillsJars`:

```groovy
dependencies {
    skill 'org.apache.grails.skills:grails-upgrade:8.0.1'
}
```

`grails-upgrade` is published from Grails 8.0.1 on, so any version of this skill has a `grails-upgrade` of the same version. Leave the version out only when the build declares `grails-8-upgrade` without one, which means the Grails 8 BOM manages both.

`org.apache.grails.skills:grails-8-upgrade` stays published for the Grails 8 releases so that builds which declare it keep resolving. Grails 9 does not publish it. Replace the dependency with `grails-upgrade`.
