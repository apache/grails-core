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

import java.util.zip.ZipFile

import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import spock.lang.Specification
import spock.lang.TempDir

class AgentSkillsPluginSpec extends Specification {

    @TempDir
    File projectDir

    def setup() {
        new File(projectDir, 'settings.gradle').text = "rootProject.name = 'skills-fixture'"
        writeBuild('')
    }

    void 'each skill and the files beside it are packaged under the SkillsJars path of the repository'() {
        given:
        writeSkill('first-skill')
        writeSkill('second-skill')
        writeFile('skills/second-skill/references/detail.md', 'detail')

        when:
        build('jar')

        then:
        skillEntries() == [
                'META-INF/skills/apache/grails-core/first-skill/SKILL.md',
                'META-INF/skills/apache/grails-core/second-skill/SKILL.md',
                'META-INF/skills/apache/grails-core/second-skill/references/detail.md',
        ]

        and: 'unchanged'
        jarText('META-INF/skills/apache/grails-core/first-skill/SKILL.md') ==
                new File(projectDir, 'skills/first-skill/SKILL.md').text
    }

    void 'the skills are packaged under the githubSlug the project sets'() {
        given: 'the slug set after the plugin is applied, as a build script sets it'
        writeBuild("ext.githubSlug = 'example/other-repo'")
        writeSkill('first-skill')

        when:
        build('jar')

        then:
        skillEntries() == ['META-INF/skills/example/other-repo/first-skill/SKILL.md']
    }

    void 'a skill removed from the project is gone from the next jar'() {
        given:
        writeSkill('first-skill')
        writeSkill('second-skill')
        build('jar')

        when:
        new File(projectDir, 'skills/second-skill').deleteDir()
        build('jar')

        then:
        skillEntries() == ['META-INF/skills/apache/grails-core/first-skill/SKILL.md']
    }

    void 'a skill whose frontmatter name does not match its directory fails the build'() {
        given:
        writeSkill('first-skill', 'another-name')

        when:
        BuildResult result = buildAndFail('jar')

        then:
        result.output.contains("Agent skill 'first-skill' of : declares the name 'another-name' in SKILL.md")
    }

    void 'a skill without frontmatter fails the build'() {
        given:
        writeFile('skills/first-skill/SKILL.md', '# No frontmatter\n')

        when:
        BuildResult result = buildAndFail('jar')

        then:
        result.output.contains("Agent skill 'first-skill' of : declares the name 'null' in SKILL.md")
    }

    void 'a skill directory without a SKILL.md fails the build'() {
        given:
        writeFile('skills/first-skill/README.md', 'not a skill')

        when:
        BuildResult result = buildAndFail('jar')

        then:
        result.output.contains("Agent skill 'first-skill' of : has no SKILL.md")
    }

    void 'a project with no skills fails the build rather than publish an empty jar'() {
        when:
        BuildResult result = buildAndFail('jar')

        then:
        result.output.contains(': applies the agent skills plugin but has no skills: expected skills/<skill>/SKILL.md')
    }

    void 'nothing is registered for a project without the java plugin'() {
        given:
        Project project = ProjectBuilder.builder().withProjectDir(projectDir).build()

        when:
        project.pluginManager.apply(AgentSkillsPlugin)

        then:
        project.tasks.findByName('agentSkillResources') == null
    }

    private void writeBuild(String extra) {
        new File(projectDir, 'build.gradle').text = """
            plugins {
                id 'java'
                id 'org.apache.grails.buildsrc.agent-skills'
            }

            ${extra}
        """
    }

    private void writeSkill(String directory, String name = directory) {
        writeFile("skills/${directory}/SKILL.md", """---
name: ${name}
description: A skill for the fixture
---

Instructions for ${name}.
""")
    }

    private void writeFile(String path, String text) {
        File file = new File(projectDir, path)
        file.parentFile.mkdirs()
        file.text = text
    }

    private List<String> skillEntries() {
        new ZipFile(jarFile()).withCloseable { ZipFile zip ->
            zip.entries().collect { it.name }.findAll { it.startsWith('META-INF/skills/') && !it.endsWith('/') }.sort()
        }
    }

    private String jarText(String entry) {
        new ZipFile(jarFile()).withCloseable { ZipFile zip ->
            zip.getInputStream(zip.getEntry(entry)).getText('UTF-8')
        }
    }

    private File jarFile() {
        new File(projectDir, 'build/libs/skills-fixture.jar')
    }

    private BuildResult build(String task) {
        runner(task).build()
    }

    private BuildResult buildAndFail(String task) {
        runner(task).buildAndFail()
    }

    private GradleRunner runner(String task) {
        GradleRunner.create()
                .withProjectDir(projectDir)
                .withArguments(task, '--stacktrace')
                .withPluginClasspath()
    }
}
