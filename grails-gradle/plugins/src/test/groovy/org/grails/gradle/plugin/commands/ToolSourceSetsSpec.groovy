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

import java.util.zip.ZipFile

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.TaskOutcome

/**
 * Where the tools of an application are compiled, and against what: in {@code src/cli}, with the
 * classes of the application, both scopes of tools and the classes of {@code src/standalone}; in
 * {@code src/standalone}, with the tool API and {@code grailsCliStandalone} alone, and nothing of the
 * application. The main source set has neither scope on its compile class path, and its jar has no
 * tool. {@code grails-app/commands} is compiled with {@code src/cli}, and is deprecated.
 */
class ToolSourceSetsSpec extends CommandProjectSpecification {

    private static final String PATHS = '''
        tasks.register('paths') {
            def classpaths = [
                    MAIN      : sourceSets.main.compileClasspath,
                    RUNTIME   : configurations.runtimeClasspath,
                    CLI       : sourceSets.cli.compileClasspath,
                    CLI_RUN   : sourceSets.cli.runtimeClasspath,
                    STANDALONE: sourceSets.standalone.compileClasspath,
                    STANDALONE_RUN: sourceSets.standalone.runtimeClasspath,
                    TEST      : sourceSets.test.compileClasspath,
                    TEST_RUN  : sourceSets.test.runtimeClasspath,
            ]
            def root = layout.projectDirectory.asFile
            doLast {
                classpaths.each { String name, FileCollection files ->
                    println name + '=' + files.files.collect { File file ->
                        file.path.startsWith(root.path) && !file.path.contains('maven-repo') ? root.toPath().relativize(file.toPath()).toString().replace(File.separatorChar, '/' as char) : file.name
                    }.sort().join(',')
                }
            }
        }
        dependencies {
            grailsCli 'org.example:only-for-tools:1.0'
            grailsCliStandalone 'org.example:only-for-standalone-tools:1.0'
            // the jars of this build have no Groovy in them; a standalone source in Groovy is compiled with the one of Gradle
            grailsCliStandalone localGroovy()
        }
    '''

    private static final List<String> TOOL_ONLY = ['only-for-tools-1.0.jar', 'only-for-standalone-tools-1.0.jar', 'grails-core-cli-0.0.1-test.jar',
                                                   'grails-console-0.0.1-test.jar']

    def "each source set of tools has its own class path, by name, and the application has neither scope of tools"() {
        given:
        setupTools()

        when:
        Map<String, List<String>> paths = paths(executeTask('paths'))

        then: 'the application is compiled and runs without anything only tools have'
        !paths.MAIN.any { it in TOOL_ONLY }
        !paths.RUNTIME.any { it in TOOL_ONLY }
        !paths.MAIN.any { it.startsWith('build/classes/groovy/cli') || it.startsWith('build/classes/java/standalone') }

        and: 'src/cli has the application, both scopes and the classes of src/standalone'
        paths.CLI.containsAll(TOOL_ONLY + ['build/classes/groovy/main', 'build/resources/main', 'build/classes/java/standalone', 'build/resources/standalone'])
        paths.CLI_RUN.containsAll(TOOL_ONLY + ['build/classes/groovy/main', 'build/classes/groovy/cli', 'build/classes/java/standalone'])

        and: 'src/standalone has the tool API and grailsCliStandalone, and nothing of the application, of src/cli or of grailsCli'
        paths.STANDALONE.containsAll(['only-for-standalone-tools-1.0.jar', 'grails-core-cli-0.0.1-test.jar'])
        !paths.STANDALONE.any { it in ['only-for-tools-1.0.jar', 'grails-console-0.0.1-test.jar'] || it.startsWith('build/') }
        !paths.STANDALONE_RUN.any { it in ['only-for-tools-1.0.jar', 'grails-console-0.0.1-test.jar'] || it.contains('/main') || it.contains('/cli') }

        and: 'the tests have the tools and what they depend on, as they had when tools were of the application'
        paths.TEST.containsAll(TOOL_ONLY + ['build/classes/groovy/main', 'build/classes/groovy/cli', 'build/classes/java/standalone'])
        paths.TEST_RUN.containsAll(TOOL_ONLY + ['build/classes/groovy/cli', 'build/classes/java/standalone', 'build/resources/standalone'])
    }

    def "the tools of src/cli and grails-app/commands are compiled with src/cli, the build says that the folder is deprecated, and the jar of the application has no tool"() {
        given: 'a tool in src/cli that uses a class of the application and one of src/standalone, and one in grails-app/commands'
        setupTools()

        when:
        BuildResult result = executeTask('jar', ['cliClasses'])

        then: 'the tools are compiled by the compilation of src/cli, and not by that of the application'
        result.task(':compileCliGroovy').outcome == TaskOutcome.SUCCESS
        ['NewTool', 'OldCommand'].every { new File(projectDir, "build/classes/groovy/cli/example/${it}.class").isFile() }
        ['NewTool', 'OldCommand'].every { !new File(projectDir, "build/classes/groovy/main/example/${it}.class").exists() }
        new File(projectDir, 'build/classes/java/standalone/example/FilesHelper.class').isFile()
        new File(projectDir, 'build/classes/groovy/standalone/example/CopyTool.class').isFile()
        new File(projectDir, 'build/classes/groovy/main/example/Application.class').isFile()

        and: 'the build says that grails-app/commands is deprecated, and where its classes go'
        result.output.contains('grails-app/commands is deprecated: the classes in it are compiled and run as before, and are no longer packaged with the application. ' +
                'Move them to src/cli/groovy, as they are; a new tool is written there by: grails create-tool')
        result.output.count('grails-app/commands is deprecated') == 1

        and: 'the jar of the application has no tool'
        List<String> packaged = []
        new ZipFile(new File(projectDir, 'build/libs').listFiles().find { it.name.endsWith('.jar') }).withCloseable { ZipFile jar ->
            packaged.addAll(jar.entries().toList()*.name)
        }
        packaged.contains('example/Application.class')
        !packaged.any { it.contains('NewTool') || it.contains('OldCommand') || it.contains('CopyTool') || it.contains('FilesHelper') }
    }

    def "the executable jar of the application has no tool and no library that only tools have"() {
        given:
        setupTools('''
            tasks.named('bootJar') {
                includeTools = false
            }
        ''')

        when:
        executeTask('bootJar')
        File jar = new File(projectDir, 'build/libs').listFiles().find { it.name.endsWith('.jar') && !it.name.endsWith('-plain.jar') }
        List<String> packaged = []
        new ZipFile(jar).withCloseable { ZipFile zip ->
            packaged.addAll(zip.entries().toList()*.name)
        }

        then:
        packaged.contains('BOOT-INF/classes/example/Application.class')
        !packaged.any { it.contains('NewTool') || it.contains('OldCommand') || it.contains('CopyTool') || it.contains('FilesHelper') }
        !packaged.any { String entry -> TOOL_ONLY.any { String library -> entry.endsWith("/${library}") } }
    }

    def "an application without grails-app/commands is told nothing, a class of the application cannot use a tool, and a standalone tool cannot use the application"() {
        given:
        setupCommandProject()
        projectFile('src/cli/groovy/example/NewTool.groovy', 'package example\n\nclass NewTool {\n}\n')
        projectFile('src/main/groovy/example/UsesATool.groovy', 'package example\n\nclass UsesATool {\n\n    NewTool tool\n}\n')

        when:
        BuildResult result = runner.withArguments('compileGroovy', '--stacktrace').forwardOutput().buildAndFail()

        then: 'the application is compiled without its tools'
        result.task(':compileGroovy').outcome == TaskOutcome.FAILED
        result.output.contains('unable to resolve class NewTool')

        when:
        new File(projectDir, 'src/main/groovy/example/UsesATool.groovy').delete()
        projectFile('src/standalone/java/example/UsesTheApplication.java', 'package example;\n\npublic class UsesTheApplication {\n\n    Application application;\n}\n')
        result = runner.withArguments('compileStandaloneJava', '--stacktrace').forwardOutput().buildAndFail()

        then: 'a standalone tool is compiled without the application'
        result.task(':compileStandaloneJava').outcome == TaskOutcome.FAILED
        result.output.contains('cannot find symbol')

        when:
        new File(projectDir, 'src/standalone/java/example/UsesTheApplication.java').delete()
        result = executeTask('cliClasses')

        then:
        result.task(':compileCliGroovy').outcome == TaskOutcome.SUCCESS
        !result.output.contains('grails-app/commands is deprecated')
    }

    def "the source sets of the tools are there in every project, empty, with the folders the design names"() {
        given:
        setupCommandProject('''
            tasks.register('folders') {
                def folders = ['cli', 'standalone'].collectEntries { String name ->
                    [(name): sourceSets.getByName(name).allSource.srcDirs.collect { layout.projectDirectory.asFile.toPath().relativize(it.toPath()).toString().replace(File.separatorChar, '/' as char) }.sort()]
                }
                doLast {
                    folders.each { String name, List<String> directories -> println "${name.toUpperCase()}=${directories.join(',')}" }
                }
            }
        ''')

        when:
        BuildResult result = executeTask('folders')

        then:
        result.output.contains('CLI=grails-app/commands,src/cli/groovy,src/cli/java,src/cli/resources')
        result.output.contains('STANDALONE=src/standalone/groovy,src/standalone/java,src/standalone/resources')
    }

    private void setupTools(String more = '') {
        setupCommandProject(PATHS + more)
        publish('org.example', 'only-for-tools', '1.0', [:])
        publish('org.example', 'only-for-standalone-tools', '1.0', [:])
        projectFile('src/standalone/java/example/FilesHelper.java', 'package example;\n\npublic class FilesHelper {\n}\n')
        projectFile('src/standalone/groovy/example/CopyTool.groovy', 'package example\n\nclass CopyTool {\n\n    FilesHelper helper\n}\n')
        projectFile('src/standalone/resources/templates/copy.txt', 'a template')
        projectFile('src/cli/groovy/example/NewTool.groovy', 'package example\n\nclass NewTool {\n\n    Application application\n    FilesHelper helper\n}\n')
        projectFile('grails-app/commands/example/OldCommand.groovy', 'package example\n\nclass OldCommand {\n\n    NewTool other\n    Application application\n}\n')
    }

    private static Map<String, List<String>> paths(BuildResult result) {
        ['MAIN', 'RUNTIME', 'CLI', 'CLI_RUN', 'STANDALONE', 'STANDALONE_RUN', 'TEST', 'TEST_RUN'].collectEntries { String name ->
            String line = result.output.readLines().find { it.startsWith("${name}=") }
            [(name): line.substring(name.length() + 1).split(',').toList()]
        }
    }
}
