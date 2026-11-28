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
package org.grails.gradle.plugin.commands

import groovy.json.JsonOutput
import groovy.json.JsonSlurper

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.TaskOutcome

/**
 * The record the build writes for the tools that run as a process of their own, without Gradle:
 * {@code .grails/cache/standalone-tools.json}, with the class path the command line starts each of them
 * with. It is made from {@code src/standalone} and {@code grailsCliStandalone} alone: nothing of the
 * application, of {@code src/cli} or of {@code grailsCli} is in it, and writing it does not compile the
 * application, so it is written while the application does not compile.
 */
class StandaloneToolsRecordSpec extends CommandProjectSpecification {

    private static final String CATALOG = '.grails/cache/tool-catalog.json'
    private static final String FILE = '.grails/cache/standalone-tools.json'

    def "the record lists the standalone tools of src/standalone and of the -cli-standalone companions, with the class path of src/standalone alone"() {
        given:
        setupKinds()

        when: 'a build that compiles the project'
        BuildResult result = executeTask('classes')
        Map file = file()

        then: 'the tasks ran after it, and the catalog says which tools run how'
        result.task(':grailsToolCatalog').outcome == TaskOutcome.SUCCESS
        result.task(':grailsStandaloneTools').outcome == TaskOutcome.SUCCESS
        catalog().tools.collectEntries { [(it.name): it.target] } ==
                ['build-tool': 'application', 'config-report': 'application', 'copy': 'standalone', 'local': 'standalone', 'tool': 'standalone']

        and: 'the record lists the standalone tools of src/standalone and of grailsCliStandalone, and not the one of a -cli companion'
        file.format == 1
        file.keySet() == ['format', 'tools', 'classpath', 'sources', 'java'] as Set
        file.tools == ['copy', 'local']
        file.java instanceof Integer && file.java >= 17

        and: 'the output of src/standalone first, for the tool of the project, and no directory that is not there'
        file.classpath.every { String path -> new File(path).absolute && new File(path).exists() }
        canonical(file.classpath).take(1) == [new File(projectDir, 'build/resources/standalone').canonicalPath]

        and: 'then what src/standalone depends on, which is grailsCliStandalone: nothing of the application, of src/cli or of grailsCli'
        file.classpath.drop(1).collect { String path -> new File(path).name }.sort() == ['copy-cli-standalone-1.0.jar', 'grails-core-cli-0.0.1-test.jar']
        !canonical(file.classpath).any { String path ->
            path.contains("${File.separator}main") || path.contains("${File.separator}cli${File.separator}") || path.contains('grails-console') ||
                    path.contains('tool-cli-1.0') || path.contains('runtime-only')
        }

        and: 'the directories of the sources of src/standalone, and of no other source set'
        canonical(file.sources) == ['groovy', 'java', 'resources'].collect { new File(projectDir, "src/standalone/${it}").canonicalPath }

        and: 'the catalog still has no path of this machine, and the file is ASCII on one line'
        !catalogText().contains(projectDir.name)
        fileText().bytes.every { byte b -> b == 0x0a || (b >= 0x20 && b <= 0x7e) }
        fileText().count('\n') == 1
    }

    def "the record is written while the application does not compile, and compiles nothing but src/standalone"() {
        given: 'an application whose main source does not compile'
        setupKinds()
        projectFile('src/main/groovy/example/Broken.groovy', 'package example\nclass Broken {\n')
        projectFile('src/cli/groovy/example/AlsoBroken.groovy', 'package example\nclass AlsoBroken {\n')

        when:
        BuildResult result = executeTask('grailsStandaloneTools')

        then:
        result.task(':grailsStandaloneTools').outcome == TaskOutcome.SUCCESS
        result.task(':compileGroovy') == null
        result.task(':compileCliGroovy') == null
        result.task(':grailsToolCatalog') == null
        result.task(':processStandaloneResources').outcome == TaskOutcome.SUCCESS
        file().tools == ['copy', 'local']
    }

    def "a project without a standalone tool has a file without a path"() {
        given:
        setupCommandProject()

        when:
        BuildResult result = executeTask('classes')

        then:
        result.task(':grailsStandaloneTools').outcome == TaskOutcome.SUCCESS
        file().findAll { it.key != 'java' } == [format: 1, tools: [], classpath: [], sources: []]
        !fileText().contains(projectDir.name)
    }

    def "the file is written again when a source of src/standalone or a build file is newer than it, and not when a source of the application or of src/cli is"() {
        given: 'a source in each source set; the standalone one is compiled with the Groovy of Gradle, as no jar of this build has Groovy in it'
        setupKinds('''
            dependencies {
                grailsCliStandalone localGroovy()
            }
        ''')
        projectFile('src/main/groovy/example/Other.groovy', 'package example\nclass Other { }\n')
        projectFile('src/cli/groovy/example/Helper.groovy', 'package example\nclass Helper { }\n')
        projectFile('src/standalone/groovy/example/Shared.groovy', 'package example\nclass Shared { }\n')
        executeTask('grailsStandaloneTools')
        // the tasks of the tools are registered from the jars of grailsCliClasspath while the build is
        // configured, which the first build resolved: the second stores what the third reuses
        executeTask('grailsStandaloneTools')
        File written = new File(projectDir, FILE)
        String first = written.text

        when:
        BuildResult again = executeTask('grailsStandaloneTools')

        then:
        again.output.contains('Configuration cache entry reused')
        again.task(':grailsStandaloneTools').outcome == TaskOutcome.UP_TO_DATE

        when: 'a source of the application and one of src/cli are newer than the file'
        for (String path : ['src/main/groovy/example/Other.groovy', 'src/cli/groovy/example/Helper.groovy']) {
            new File(projectDir, path).setLastModified(written.lastModified() + 1)
        }
        BuildResult unrelated = executeTask('grailsStandaloneTools')

        then: 'the file is not written again'
        unrelated.task(':grailsStandaloneTools').outcome == TaskOutcome.UP_TO_DATE

        when: 'a source of src/standalone is newer than the file, and says the same'
        File source = new File(projectDir, 'src/standalone/groovy/example/Shared.groovy')
        source.setLastModified(written.lastModified() + 1)
        BuildResult touched = executeTask('grailsStandaloneTools')

        then: 'the file is written again, with what it had, and is no longer older than the source'
        touched.task(':grailsStandaloneTools').outcome == TaskOutcome.SUCCESS
        written.text == first
        written.lastModified() >= source.lastModified()

        when: 'the build file is newer than the file'
        File build = new File(projectDir, 'build.gradle')
        build.setLastModified(written.lastModified() + 1)
        BuildResult rebuilt = executeTask('grailsStandaloneTools')

        then:
        rebuilt.task(':grailsStandaloneTools').outcome == TaskOutcome.SUCCESS
        written.text == first
        written.lastModified() >= build.lastModified()

        when: 'and nothing after that'
        BuildResult settled = executeTask('grailsStandaloneTools')

        then:
        settled.task(':grailsStandaloneTools').outcome == TaskOutcome.UP_TO_DATE
    }

    def "a class path with the output of another project of the build is not written down, and the file says that the build runs the tools"() {
        given:
        setupCommandProject('''
            dependencies {
                grailsCliStandalone project(':companion')
            }
        ''')
        projectFile("companion/src/main/resources/${CLI_FACTORIES}", registrations('sibling.SiblingTool'))
        projectFile("companion/src/main/resources/${descriptorPath('sibling.SiblingTool')}", described('sibling.SiblingTool', 'sibling', 'standalone'))

        when:
        executeTask('classes')
        Map file = file()

        then:
        catalog().tools.find { it.name == 'sibling' }.target == 'standalone'
        file.tools == ['sibling']
        file.classpath == []
        file.sources == []
        file.throughGradle == 'their class path has the output of another project of the build (:companion), which only the build keeps current'
        !fileText().contains(projectDir.name)
    }

    def "a class path that does not resolve completely is not written down, and the build that compiles the project does not fail"() {
        given: 'a library that the standalone tools need at run time only, and that is not in the repository'
        setupCommandProject('''
            dependencies {
                grailsCliStandalone 'org.example:copy-cli-standalone:1.0'
                standaloneRuntimeOnly 'org.example:missing:1.0'
            }
        ''')
        publishStandaloneCompanion()

        when:
        BuildResult result = executeTask('classes')
        Map file = file()

        then:
        result.task(':grailsStandaloneTools').outcome == TaskOutcome.SUCCESS
        file.tools == ['copy']
        file.classpath == []
        file.throughGradle == 'standaloneRuntimeClasspath cannot be resolved completely, so their class path is not known'
    }

    def "a runtime class path of the application that does not resolve does not keep the standalone tools from their class path"() {
        given:
        setupCommandProject('''
            dependencies {
                grailsCliStandalone 'org.example:copy-cli-standalone:1.0'
                runtimeOnly 'org.example:missing:1.0'
            }
        ''')
        publishStandaloneCompanion()

        when:
        executeTask('grailsStandaloneTools')
        Map file = file()

        then:
        file.tools == ['copy']
        file.throughGradle == null
        file.classpath.collect { String path -> new File(path).name }.sort() == ['copy-cli-standalone-1.0.jar', 'grails-core-cli-0.0.1-test.jar']
    }

    def "a project that has a task of the name keeps it, and is told that the file is not written"() {
        given: 'a project whose task is there before the plugin is'
        setupCommandProject()
        projectFile('companion/build.gradle', '''
            plugins {
                id 'java-library'
                id 'org.apache.grails.gradle.grails-cli' apply false
            }
            tasks.register('grailsStandaloneTools') {
                doLast { println 'THE TASK OF THE BUILD' }
            }
            apply plugin: 'org.apache.grails.gradle.grails-cli'
        ''')

        when:
        BuildResult result = executeTask(':companion:grailsStandaloneTools')

        then:
        result.output.contains('Project :companion already has a task named grailsStandaloneTools, so the Grails Gradle plugin does not register ' +
                'its own and the class path of the tools that run without Gradle is not written down. Give the task another name.')
        result.output.contains('THE TASK OF THE BUILD')
        !new File(projectDir, "companion/${FILE}").exists()
    }

    // an application with a standalone tool in src/standalone, a -cli-standalone companion with a standalone tool,
    // and a -cli companion with a tool of each kind
    private void setupKinds(String more = '') {
        setupCommandProject('''
            dependencies {
                grailsCli 'org.example:tool-cli:1.0'
                grailsCliStandalone 'org.example:copy-cli-standalone:1.0'
                runtimeOnly 'org.example:runtime-only:1.0'
            }
        ''' + more)
        publish('org.example', 'tool-cli', '1.0', [
                (CLI_FACTORIES): registrations('com.example.ToolTool', 'com.example.BuildToolTool'),
                (descriptorPath('com.example.ToolTool')): described('com.example.ToolTool', 'tool', 'standalone'),
                (descriptorPath('com.example.BuildToolTool')): described('com.example.BuildToolTool', 'build-tool', 'application', false),
        ])
        publish('org.example', 'runtime-only', '1.0', [:])
        publishStandaloneCompanion()
        projectFile("src/standalone/resources/${CLI_FACTORIES}", registrations('example.LocalTool'))
        projectFile("src/standalone/resources/${descriptorPath('example.LocalTool')}", described('example.LocalTool', 'local', 'standalone'))
    }

    private void publishStandaloneCompanion() {
        publish('org.example', 'copy-cli-standalone', '1.0', [
                (CLI_FACTORIES): registrations('com.example.CopyTool'),
                (descriptorPath('com.example.CopyTool')): described('com.example.CopyTool', 'copy', 'standalone'),
        ])
    }

    private static List<String> canonical(List<String> paths) {
        paths.collect { String path -> new File(path).canonicalPath }
    }

    private static String described(String className, String name, String target, boolean startsApplication = true) {
        JsonOutput.toJson([format: 1, class: className, name: name, target: target] + (target == 'application' ? [startsApplication: startsApplication] : [:]))
    }

    private String catalogText() {
        new File(projectDir, CATALOG).getText('US-ASCII')
    }

    private Map catalog() {
        (Map) new JsonSlurper().parseText(catalogText())
    }

    private String fileText() {
        new File(projectDir, FILE).getText('US-ASCII')
    }

    private Map file() {
        (Map) new JsonSlurper().parseText(fileText())
    }
}
