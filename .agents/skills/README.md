<!--
SPDX-License-Identifier: Apache-2.0

Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements; and to You under the Apache License, Version 2.0.
-->

# Grails Agent Skills

Agents working in this repository find the Grails agent skills in this directory. Skills that are only useful while working in the Grails framework repository live here and are not published.

App-facing skills, useful when building or upgrading Grails applications, are also published as jars with every Grails release. Their canonical source lives in the Gradle project that publishes each one, under `grails-skills/<project>/skills/<skill>/`. The `.agents/skills/<skill>/SKILL.md` entries for them are symbolic links to that source, so agents such as OpenCode still find them where they look by default, and `.claude/skills` links to those in turn. Edit the skill in its project; everything that reads it sees the same file.

| Published skill | Gradle project | Maven coordinates | Use |
|-----------------|----------------|-------------------|-----|
| `grails-developer` | `:grails-skills-developer` (`grails-skills/developer/skills/grails-developer`) | `org.apache.grails.skills:grails-developer` | Building current Grails web applications, REST APIs, GORM models, controllers, services, views, plugins, and tests |
| `grails-8-upgrade` | `:grails-skills-upgrade-guide-8` (`grails-skills/upgrade-guide-8/skills/grails-8-upgrade`) | `org.apache.grails.skills:grails-8-upgrade` | Upgrading Grails applications from Grails 7.x to Grails 8 |

The jars use the [SkillsJars](https://www.skillsjars.com/) layout, holding the skill directory at `META-INF/skills/apache/grails-core/<skill>/`, so SkillsJars tooling extracts them the same way it would a SkillsJars catalog jar of this repository. They are released, signed, and voted on with the rest of Grails, and the Grails BOM manages their versions, so an application declares them without a version. Applications extract them with the SkillsJars Gradle plugin, as the Agent Skills section of the user guide describes.

The `gradle-developer`, `groovy-developer`, `hibernate-developer`, `java-developer`, `mono-repo-integration`, `test-fixer`, and `violation-fixer` skills are intentionally not published because they are for Grails framework development. Contributors already receive them from this repository when working on Grails core.

To publish another skill:

1. Create a Gradle project under `grails-skills/<project>/` whose `build.gradle` matches the existing ones: it applies `org.apache.grails.buildsrc.agent-skills` and sets `pomArtifactId` to the skill's name, plus `pomTitle` and `pomDescription`.
2. Put the skill in `grails-skills/<project>/skills/<skill>/`: a `SKILL.md` whose frontmatter `name` is `<skill>`, and any files it ships beside it. The plugin packages that directory at `META-INF/skills/apache/grails-core/<skill>/` and fails the build if the name and directory differ.
3. Link `.agents/skills/<skill>/SKILL.md` to it, from a real `.agents/skills/<skill>/` directory, so agents working in this repository find it:
   ```bash
   mkdir .agents/skills/<skill>
   ln -s ../../../grails-skills/<project>/skills/<skill>/SKILL.md .agents/skills/<skill>/SKILL.md
   ```
4. Register the project in `settings.gradle` and in the `publishedProjects` list of `gradle/publish-root-config.gradle`.
5. Add its coordinates to `.github/dependency-graph/external-references.yml` and to the table in `grails-doc/src/en/guide/gettingStarted/agentSkills.adoc`, and declare it in the example builds `end-to-end/agent-skills` and `end-to-end/agent-skills-plain-build`, adding its name to `PUBLISHED_SKILLS` in their spec.

Inspect a skill jar locally with:

```bash
./gradlew :grails-skills-developer:jar
unzip -l grails-skills/developer/build/libs/grails-skills-developer-*.jar
```

`end-to-end/agent-skills` is an example application that gets the published skills the way the user guide shows, and `end-to-end/agent-skills-plain-build` does the same from a build without the Grails Gradle plugin; see `end-to-end/README.md` for how to run them.
