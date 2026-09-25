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
package org.apache.grails.buildsrc

import groovy.transform.CompileStatic

import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.CopySpec
import org.gradle.api.file.Directory
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.TaskProvider

import static org.apache.grails.buildsrc.GradleUtils.lookupProperty

/**
 * Packages the agent skills of a project into its jar with the SkillsJars layout.
 *
 * <p>Each directory under {@code skills/} is one skill: a {@code SKILL.md} whose frontmatter
 * {@code name} is the directory name, and any files it ships beside it. The skills are packaged at
 * {@code META-INF/skills/<githubSlug>/<skill>/}, where {@code githubSlug} is the same
 * {@code <org>/<repo>} the publish plugin writes into the POM, so SkillsJars tooling extracts the
 * jar the same way it would a SkillsJars catalog jar of that repository.
 */
@CompileStatic
class AgentSkillsPlugin implements Plugin<Project> {

    static final String SKILLS_DIRECTORY = 'skills'
    static final String SKILLS_RESOURCE_ROOT = 'META-INF/skills'
    static final String SKILL_FILE = 'SKILL.md'
    static final String DEFAULT_GITHUB_SLUG = 'apache/grails-core'

    @Override
    void apply(Project project) {
        project.plugins.withId('java') {
            configureSkillResources(project)
        }
    }

    private static void configureSkillResources(Project project) {
        Directory skillsDirectory = project.layout.projectDirectory.dir(SKILLS_DIRECTORY)
        String projectPath = project.path
        // Checked while configuring: with nothing to copy the task below is skipped as NO-SOURCE,
        // and the project would publish an empty jar.
        if (!skillDirectories(skillsDirectory.asFile)) {
            throw new GradleException(
                    "${projectPath} applies the agent skills plugin but has no skills: expected ${SKILLS_DIRECTORY}/<skill>/${SKILL_FILE}"
            )
        }

        TaskProvider<Sync> skillResources = project.tasks.register('agentSkillResources', Sync)
        skillResources.configure { Sync task ->
            task.group = 'build'
            task.description = "Packages the agent skills under ${SKILLS_DIRECTORY}/ into ${SKILLS_RESOURCE_ROOT}/."
            task.from(skillsDirectory) { CopySpec spec ->
                spec.into(project.provider {
                    "${SKILLS_RESOURCE_ROOT}/${lookupProperty(project, 'githubSlug', DEFAULT_GITHUB_SLUG)}" as String
                })
            }
            task.into(project.layout.buildDirectory.dir('generated/agent-skills'))
            task.doFirst {
                validateSkills(projectPath, skillsDirectory.asFile)
            }
        }

        project.extensions.getByType(SourceSetContainer).named(SourceSet.MAIN_SOURCE_SET_NAME) { SourceSet sourceSet ->
            sourceSet.resources.srcDir(skillResources)
        }
    }

    /**
     * A skill agents cannot load must not be published: every directory under {@code skills/} needs a
     * {@code SKILL.md} whose frontmatter names the directory, which is how agents identify it.
     */
    static void validateSkills(String projectPath, File skillsDirectory) {
        skillDirectories(skillsDirectory).each { File skill ->
            File skillFile = new File(skill, SKILL_FILE)
            if (!skillFile.file) {
                throw new GradleException("Agent skill '${skill.name}' of ${projectPath} has no ${SKILL_FILE}")
            }
            String declaredName = frontmatterValue(skillFile, 'name')
            if (declaredName != skill.name) {
                throw new GradleException(
                        "Agent skill '${skill.name}' of ${projectPath} declares the name '${declaredName}' in ${SKILL_FILE}; " +
                                'the frontmatter name must match its directory'
                )
            }
        }
    }

    private static List<File> skillDirectories(File skillsDirectory) {
        (skillsDirectory.listFiles()?.findAll { File file -> file.directory } ?: []) as List<File>
    }

    private static String frontmatterValue(File skillFile, String key) {
        List<String> lines = skillFile.readLines('UTF-8')
        if (!lines || lines.first().trim() != '---') {
            return null
        }
        String prefix = "${key}:"
        for (String line : lines.drop(1)) {
            if (line.trim() == '---') {
                return null
            }
            if (line.startsWith(prefix)) {
                return line.substring(prefix.length()).trim()
            }
        }
        null
    }
}
