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
 * The file the build writes for the commands that run as a process of their own, without Gradle:
 * {@code .grails/cache/standalone-commands.json}, with the class path the command line starts each
 * of them with. It is written by every build that compiles the project, names paths only for
 * commands the catalog lists with the target {@code standalone}, and says when the command line has
 * to leave a command to the build.
 */
class StandaloneCommandsFileSpec extends CommandProjectSpecification {

    private static final String CATALOG = '.grails/cache/command-catalog.json'
    private static final String FILE = '.grails/cache/standalone-commands.json'

    // what the compiler does with a registration written by hand: it copies it into the classes directory
    private static final String REGISTRATION_COPIED = '''
        tasks.named('compileGroovy') {
            def classes = destinationDirectory
            def registration = layout.projectDirectory.file('src/main/resources/META-INF/grails-cli.factories')
            doLast {
                def merged = new File(classes.get().asFile, 'META-INF/grails-cli.factories')
                merged.parentFile.mkdirs()
                merged.text = registration.asFile.text
            }
        }
    '''

    def "the class path of a standalone command of the application and of a companion is written down, and of no other command"() {
        given:
        setupKinds()

        when: 'a build that compiles the project'
        BuildResult result = executeTask('classes')
        Map file = file()

        then: 'the tasks ran after it, and the catalog says which commands run how'
        result.task(':grailsCommandCatalog').outcome == TaskOutcome.SUCCESS
        result.task(':grailsStandaloneCommands').outcome == TaskOutcome.SUCCESS
        catalog().commands.collectEntries { [(it.name): it.target] } ==
                ['build-tool': 'gradle', 'config-report': 'application', 'local': 'standalone', 'other': 'application', 'tool': 'standalone']

        and: 'the file lists the standalone commands alone, each with the class path it runs with'
        file.format == 1
        file.commands == [local: 'application', tool: 'dependencies']
        !file.containsKey('throughGradle')
        file.java instanceof Integer && file.java >= 17

        and: 'the output of the project, for the command of the application'
        file.application.every { String path -> new File(path).absolute }
        canonical(file.application).containsAll([new File(projectDir, 'build/classes/groovy/main').canonicalPath, new File(projectDir, 'build/resources/main').canonicalPath])

        and: 'and no directory that is not there, as the one of the classes of Java in a project without Java sources'
        file.application.every { String path -> new File(path).exists() }
        !canonical(file.application).contains(new File(projectDir, 'build/classes/java/main').canonicalPath)

        and: 'what the project depends on and its command line tier, without the output of the project'
        file.dependencies.every { String path -> new File(path).absolute && new File(path).isFile() }
        file.dependencies.collect { String path -> new File(path).name }.containsAll(
                ['tool-cli-1.0.jar', 'grails-core-cli-0.0.1-test.jar', 'grails-console-0.0.1-test.jar'])
        !file.dependencies.any { String path -> path in file.application }

        and: 'the directories of the sources of the project'
        canonical(file.sources).containsAll([new File(projectDir, 'src/main/groovy').canonicalPath, new File(projectDir, 'src/main/resources').canonicalPath])

        and: 'the catalog still has no path of this machine, and the file is ASCII on one line'
        !catalogText().contains(projectDir.name)
        fileText().bytes.every { byte b -> b == 0x0a || (b >= 0x20 && b <= 0x7e) }
        fileText().count('\n') == 1
    }

    def "a project without a standalone command has a file without a path"() {
        given:
        setupCommandProject()

        when:
        BuildResult result = executeTask('classes')

        then:
        result.task(':grailsStandaloneCommands').outcome == TaskOutcome.SUCCESS
        file().findAll { it.key != 'java' } == [format: 1, commands: [:], application: [], dependencies: [], sources: []]
        !fileText().contains(projectDir.name)
    }

    def "the file is not written again when nothing changed, and is when a source of the project or the catalog is newer than it"() {
        given:
        setupKinds()
        executeTask('grailsStandaloneCommands')
        // the tasks of the commands are registered from the jars of grailsCliClasspath while the build is
        // configured, which the first build resolved: the second stores what the third reuses
        executeTask('grailsStandaloneCommands')
        File written = new File(projectDir, FILE)
        String first = written.text

        when:
        BuildResult again = executeTask('grailsStandaloneCommands')

        then:
        again.output.contains('Configuration cache entry reused')
        again.task(':grailsStandaloneCommands').outcome == TaskOutcome.UP_TO_DATE

        when: 'a source of the project is newer than the file, and says the same'
        File source = new File(projectDir, 'src/main/groovy/example/Application.groovy')
        source.setLastModified(written.lastModified() + 1)
        BuildResult touched = executeTask('grailsStandaloneCommands')

        then: 'the file is written again, with what it had, and is no longer older than the source'
        touched.task(':grailsStandaloneCommands').outcome == TaskOutcome.SUCCESS
        written.text == first
        written.lastModified() >= source.lastModified()

        when: 'the build file is newer than the catalog, which is written again, and the file after it'
        File catalog = new File(projectDir, CATALOG)
        new File(projectDir, 'build.gradle').setLastModified(Math.max(catalog.lastModified(), written.lastModified()) + 1)
        BuildResult rebuilt = executeTask('grailsStandaloneCommands')

        then:
        rebuilt.task(':grailsCommandCatalog').outcome == TaskOutcome.SUCCESS
        rebuilt.task(':grailsStandaloneCommands').outcome == TaskOutcome.SUCCESS
        written.text == first
        written.lastModified() >= catalog.lastModified()
        written.lastModified() >= new File(projectDir, 'build.gradle').lastModified()

        when: 'and nothing after that'
        BuildResult settled = executeTask('grailsStandaloneCommands')

        then:
        settled.task(':grailsStandaloneCommands').outcome == TaskOutcome.UP_TO_DATE
    }

    def "a class path with the output of another project of the build is not written down, and the file says that the build runs the commands"() {
        given:
        setupCommandProject('''
            dependencies {
                grailsCli project(':companion')
            }
        ''')
        projectFile("companion/src/main/resources/${CLI_FACTORIES}", registrations('sibling.SiblingCommand'))
        projectFile("companion/src/main/resources/${descriptorPath('sibling.SiblingCommand')}", described('sibling.SiblingCommand', 'sibling', 'standalone'))

        when:
        executeTask('classes')
        Map file = file()

        then:
        catalog().commands.find { it.name == 'sibling' }.target == 'standalone'
        file.commands == [sibling: 'dependencies']
        file.application == []
        file.dependencies == []
        file.sources == []
        file.throughGradle == 'their class path has the output of another project of the build (:companion), which only the build keeps current'
        !fileText().contains(projectDir.name)
    }

    def "a class path that does not resolve completely is not written down, and the build that compiles the project does not fail"() {
        given:
        setupCommandProject('''
            dependencies {
                grailsCli 'org.example:tool-cli:1.0'
                runtimeOnly 'org.example:missing:1.0'
            }
        ''')
        publishTool()

        when:
        BuildResult result = executeTask('classes')
        Map file = file()

        then:
        result.task(':grailsStandaloneCommands').outcome == TaskOutcome.SUCCESS
        file.commands == [tool: 'dependencies']
        file.dependencies == []
        file.throughGradle == 'runtimeClasspath cannot be resolved completely, so their class path is not known'
    }

    def "a project that has a task of the name keeps it, and is told that the file is not written"() {
        given: 'a project whose task is there before the plugin is'
        setupCommandProject()
        projectFile('companion/build.gradle', '''
            plugins {
                id 'java-library'
                id 'org.apache.grails.gradle.grails-cli' apply false
            }
            tasks.register('grailsStandaloneCommands') {
                doLast { println 'THE TASK OF THE BUILD' }
            }
            apply plugin: 'org.apache.grails.gradle.grails-cli'
        ''')

        when:
        BuildResult result = executeTask(':companion:grailsStandaloneCommands')

        then:
        result.output.contains('Project :companion already has a task named grailsStandaloneCommands, so the Grails Gradle plugin does not register ' +
                'its own and the class path of the commands that run without Gradle is not written down. Give the task another name.')
        result.output.contains('THE TASK OF THE BUILD')
        !new File(projectDir, "companion/${FILE}").exists()
    }

    // an application with a standalone command, and a companion with a command of each kind
    private void setupKinds() {
        setupCommandProject(REGISTRATION_COPIED + '''
            dependencies {
                grailsCli 'org.example:tool-cli:1.0'
            }
        ''')
        publishTool()
        projectFile("src/main/resources/${CLI_FACTORIES}", registrations('example.LocalCommand'))
        projectFile("src/main/resources/${descriptorPath('example.LocalCommand')}", described('example.LocalCommand', 'local', 'standalone'))
    }

    private void publishTool() {
        publish('org.example', 'tool-cli', '1.0', [
                (CLI_FACTORIES): registrations('com.example.ToolCommand', 'com.example.BuildToolCommand', 'com.example.OtherCommand'),
                (descriptorPath('com.example.ToolCommand')): described('com.example.ToolCommand', 'tool', 'standalone'),
                (descriptorPath('com.example.BuildToolCommand')): described('com.example.BuildToolCommand', 'build-tool', 'gradle'),
                (descriptorPath('com.example.OtherCommand')): described('com.example.OtherCommand', 'other', 'application'),
        ])
    }

    private static List<String> canonical(List<String> paths) {
        paths.collect { String path -> new File(path).canonicalPath }
    }

    private static String described(String className, String name, String target) {
        JsonOutput.toJson([format: 1, class: className, name: name, target: target])
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
