/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.apache.grails.forge.cli.health

import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import groovy.transform.CompileStatic

/**
 * {@code agent-skills}: whether the {@code grails-upgrade} agent skill, which turns the upgrade notes
 * into a checklist for an AI coding agent, is there for the agents of a project that is being
 * upgraded: declared in the build for the SkillsJars Gradle plugin, and extracted into a directory
 * agents read skills from. It is looked for only where an upgrade is under way, in a project on an
 * earlier major version of Grails or one with an error or a warning of an upgrade note; anywhere else
 * every new application would be told of it.
 *
 * @since 9.0
 */
@CompileStatic
final class AgentSkillsCheck extends HealthCheck {

    static final String SKILL = 'org.apache.grails.skills:grails-upgrade'
    // the directory the SkillsJars Gradle plugin extracts the skill to, below its output directory
    private static final String EXTRACTED = 'skillsjars__apache__grails-core__grails-upgrade/SKILL.md'
    // where agents read skills from, and where the guide has the plugin write them
    private static final List<String> SKILL_DIRECTORIES = ['.agents/skills', '.claude/skills'].asImmutable()
    private static final String EXTRACT = './gradlew extractSkillsJars'

    @Override
    String getName() {
        return 'agent-skills'
    }

    @Override
    List<HealthFinding> check(HealthContext context) {
        boolean upgrading = context.onEarlierMajor || context.found.any { HealthFinding each ->
            (each.level == HealthLevel.ERROR || each.level == HealthLevel.WARNING) && each.upgradeNote != null
        }
        if (!upgrading) {
            return [ok('No upgrade is under way, so the agent skill of the upgrade is not looked for')]
        }
        BuildFacts build = context.build
        List<String> directories = new ArrayList<String>(SKILL_DIRECTORIES)
        if (build.skillsOutputDirectory != null && !directories.contains(build.skillsOutputDirectory)) {
            directories.add(0, build.skillsOutputDirectory)
        }
        String extracted = directories.find { String each -> isExtracted(context.directory, each) }
        if (extracted != null) {
            return [ok("The agent skill of the upgrade is extracted for the agents of the project (${extracted})", ["${extracted}/${EXTRACTED}".toString()])]
        }
        if (build.skillsJarsPlugin && SKILL in context.evidence.dependencies) {
            return [found('not-extracted', HealthLevel.ADVICE, "${build.file} declares the agent skill of the upgrade (${SKILL}), and it is not extracted: " +
                    'agents read it from a directory of the project', "${EXTRACT} writes it, as it does in every fresh clone, where extracted skills are not committed",
                    EXTRACT, [build.file], null)]
        }
        String version = context.onEarlierMajor ? ":${context.commandLineVersion}" : ''
        return [found('upgrade-skill', HealthLevel.ADVICE, 'The agent skill of the upgrade, which turns the upgrade notes into a checklist for an AI coding agent, ' +
                'is not there for the agents of the project', "Add id '${BuildFacts.SKILLSJARS_PLUGIN}' version '0.2.0' to the plugins of ${build.file}, " +
                "skill '${SKILL}${version}' to its dependencies, and skillsjars { outputDir = layout.projectDirectory.dir('.agents/skills') }, and run ${EXTRACT}",
                null, [build.file], null)]
    }

    private static boolean isExtracted(Path project, String directory) {
        try {
            Path skill = project.resolve(directory).normalize().resolve(EXTRACTED)
            return skill.startsWith(project) && Files.isRegularFile(skill, LinkOption.NOFOLLOW_LINKS)
        } catch (RuntimeException ignored) {
            // a directory the build names that is no path here
            return false
        }
    }
}
