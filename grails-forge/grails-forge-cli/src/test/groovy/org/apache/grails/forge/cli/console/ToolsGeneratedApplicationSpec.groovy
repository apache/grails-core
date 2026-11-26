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
package org.apache.grails.forge.cli.console

import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipFile
import groovy.json.JsonSlurper
import org.grails.forge.application.ApplicationType
import org.grails.forge.cli.CommandFixture
import org.grails.forge.cli.CommandSpec
import org.grails.forge.cli.ForgeCli
import spock.lang.IgnoreIf
import spock.lang.Requires
import spock.lang.Shared
import spock.lang.Timeout

/**
 * The tools of a generated application, one in each folder, with the Gradle plugin, the compiler and the
 * classes that run tools as they are published: a tool of {@code src/cli} runs in the application, one
 * of {@code src/cli} that does not start the application is run by the build without it, one of the
 * deprecated {@code grails-app/commands} runs as it did after a warning, and a tool of
 * {@code src/standalone} is started by the command line as a process of its own, for which Gradle is not
 * run while nothing it is made from has changed, also while the application does not compile. None of
 * them, and nothing only they need, is in what the application is packaged as. The Gradle wrapper of
 * the application writes down every time it is started.
 *
 * <p>This needs the Gradle plugin of the checkout to have the folders of tools, and the REST API
 * application the companion {@code grails-scaffolding-cli-standalone} of the framework; the
 * specification runs where they are.</p>
 */
@IgnoreIf({ os.windows })
@Requires({ ToolsOfTheBuild.hasToolFolders() })
@Timeout(1200)
class ToolsGeneratedApplicationSpec extends CommandSpec implements CommandFixture {

    /** The scaffolding plugin and the fields plugin it depends on, with what follows the name of each in a dependency report */
    private static final List<String> OF_THE_PLUGIN = ['grails-scaffolding:', 'grails-fields:']

    /** GSP, which the scaffolding plugin depends on too, with what follows its name in a dependency report */
    private static final String GSP = 'grails-gsp:'

    /** The class paths of a REST API application that GSP is not on: the tests have it from grails-testing-support-web */
    private static final List<String> WITHOUT_GSP = ['runtimeClasspath', 'grailsCliClasspath', 'grailsCliStandaloneClasspath']

    // the Gradle daemon of the builds of the application is one of this specification, which ends it
    @Shared
    IsolatedGradle gradle = new IsolatedGradle()

    PrintStream originalOut
    PrintStream originalErr
    ByteArrayOutputStream out = new ByteArrayOutputStream()
    ByteArrayOutputStream err = new ByteArrayOutputStream()

    void setup() {
        originalOut = System.out
        originalErr = System.err
        System.setOut(new PrintStream(out, true))
        System.setErr(new PrintStream(err, true))
    }

    void cleanup() {
        System.setOut(originalOut)
        System.setErr(originalErr)
        ConsoleProject.setSafeDirectories(null)
    }

    void cleanupSpec() {
        gradle.close()
    }

    void "a tool of each folder runs as its folder and its type say, and a standalone tool runs without Gradle, also while the application does not compile"() {
        given: 'a generated application with a tool in each folder, which was never built'
        application(ApplicationType.WEB)
        write('src/cli/groovy/example/grails/InsideTool.groovy', '''\
            package example.grails

            import org.apache.grails.core.cli.ApplicationTool
            import org.apache.grails.core.cli.ToolSupport

            class InsideTool implements ApplicationTool, ToolSupport {

                boolean handle() {
                    println "KIND application context=${applicationContext != null} args=${args}"
                    true
                }
            }
            ''')
        write('src/cli/groovy/example/grails/BuiltTool.groovy', '''\
            package example.grails

            import org.apache.grails.core.cli.ApplicationTool
            import org.apache.grails.core.cli.ToolSupport

            class BuiltTool implements ApplicationTool, ToolSupport {

                boolean startsApplication = false

                boolean handle() {
                    println "KIND built context=${applicationContext != null} args=${args}"
                    true
                }
            }
            ''')
        write('grails-app/commands/example/grails/LegacyCommand.groovy', '''\
            package example.grails

            import org.apache.grails.core.cli.GrailsApplicationCommand

            class LegacyCommand implements GrailsApplicationCommand {

                boolean handle() {
                    println "KIND legacy context=${applicationContext != null} args=${args}"
                    true
                }
            }
            ''')
        File plain = write('src/standalone/groovy/example/grails/PlainTool.groovy', plainTool('first'))

        when: 'the standalone tool is run by its name'
        int exit = ForgeCli.execute(beanContext, 'plain', 'one', '--flag=x')

        then: 'the build listed the tools, which compiles the project and writes down the class path, and the tool ran as a process'
        assert exit == 0 : err.toString() + out.toString()
        err.toString().readLines()[0] == '| Listing the tools of the application and its plugins, of which plain may be one, with the Gradle task grailsToolCatalog'
        err.toString().readLines().contains('| Running the tool plain of the application, as a process of its own, without Gradle')
        out.toString().readLines().contains("KIND standalone first args=[one] flag=x base=${dir.canonicalPath}".toString())
        runs() == 1
        standaloneFile().tools.contains('plain')

        when: 'it is run again, and nothing has changed'
        out.reset()
        err.reset()
        long before = System.nanoTime()
        exit = ForgeCli.execute(beanContext, 'plain', 'two')
        long standaloneMillis = (System.nanoTime() - before).intdiv(1_000_000)

        then: 'Gradle is not started at all, and the application is not either'
        assert exit == 0 : err.toString() + out.toString()
        runs() == 1
        err.toString().readLines() == ['| Running the tool plain of the application, as a process of its own, without Gradle']
        out.toString().readLines().contains("KIND standalone first args=[two] flag=null base=${dir.canonicalPath}".toString())
        !out.toString().contains('Grails application running')
        !(out.toString() + err.toString()).contains('Tomcat')

        when: 'a source of the application does not compile, and a tool of src/cli was changed'
        write('src/main/groovy/example/grails/Broken.groovy', 'package example.grails\n\nclass Broken {\n')
        new File(dir, 'src/cli/groovy/example/grails/InsideTool.groovy') << '// changed\n'
        out.reset()
        err.reset()
        exit = ForgeCli.execute(beanContext, 'plain', 'three')

        then: 'the standalone tool runs all the same, without Gradle'
        assert exit == 0 : err.toString() + out.toString()
        runs() == 1
        out.toString().readLines().contains("KIND standalone first args=[three] flag=null base=${dir.canonicalPath}".toString())

        when: 'its own source is changed while the application still does not compile'
        plain.text = plainTool('second')
        out.reset()
        err.reset()
        exit = ForgeCli.execute(beanContext, 'plain', 'four')

        then: 'the build runs once, for the standalone tools alone, and the new tool runs: no class is run that is older than its source'
        assert exit == 0 : err.toString() + out.toString()
        runs() == 2
        err.toString().readLines()[0].startsWith('| Building the standalone tools of the project before plain is run, with the Gradle task grailsStandaloneTools: ')
        out.toString().readLines().contains("KIND standalone second args=[four] flag=null base=${dir.canonicalPath}".toString())

        when: 'the application compiles again'
        new File(dir, 'src/main/groovy/example/grails/Broken.groovy').delete()
        out.reset()
        err.reset()
        before = System.nanoTime()
        exit = ForgeCli.execute(beanContext, 'built', 'one')
        long gradleMillis = (System.nanoTime() - before).intdiv(1_000_000)

        then: 'the tool that does not start the application is run by the build without it'
        assert exit == 0 : err.toString() + out.toString()
        runs() == 3
        err.toString().readLines().contains('| Running the tool built of the application, with the Gradle task runTool')
        out.toString().readLines().contains('KIND built context=false args=[one]')

        when: 'the tool of the application is run by its name'
        out.reset()
        err.reset()
        exit = ForgeCli.execute(beanContext, 'inside', 'one')

        then: 'the build runs it in the application'
        assert exit == 0 : err.toString() + out.toString()
        runs() == 4
        out.toString().readLines().contains('KIND application context=true args=[one]')

        when: 'the tool of grails-app/commands is run by its name'
        out.reset()
        err.reset()
        exit = ForgeCli.execute(beanContext, 'legacy', 'one')

        then: 'it runs in the application as before, after a warning for its folder and one for its type'
        assert exit == 0 : err.toString() + out.toString()
        runs() == 5
        err.toString().readLines().containsAll(['| Warning legacy is in grails-app/commands, which is deprecated: move it to src/cli/groovy',
                                                '| Warning legacy implements ApplicationCommand, which is deprecated: implement ApplicationTool'])
        out.toString().readLines().contains('KIND legacy context=true args=[one]')

        when: 'the standalone tool is run with run-tool'
        out.reset()
        err.reset()
        exit = ForgeCli.execute(beanContext, 'run-tool', 'plain', 'five')

        then: 'the build runs it, without the application'
        assert exit == 0 : err.toString() + out.toString()
        runs() == 6
        err.toString().readLines().contains('| Running the tool plain of the application, with the Gradle task runTool')
        out.toString().readLines().contains("KIND standalone second args=[five] flag=null base=${dir.canonicalPath}".toString())

        when: 'a standalone tool of a plugin is run for a domain class the build has not compiled'
        write('grails-app/domain/example/grails/Book.groovy', 'package example.grails\n\nclass Book {\n\n    String title\n}\n')
        out.reset()
        err.reset()
        exit = ForgeCli.execute(beanContext, 'generate-controller', 'example.grails.Book')

        then: 'it is the one of the companion -cli-standalone, which the plugin advertises, and Gradle is not run for it'
        assert exit == 0 : err.toString() + out.toString()
        runs() == 6
        standaloneFile().tools.contains('generate-controller')
        err.toString().readLines()[0].startsWith('| Running the tool generate-controller of org.apache.grails:grails-scaffolding-cli-standalone:')
        err.toString().readLines()[0].endsWith(', as a process of its own, without Gradle')
        new File(dir, 'grails-app/controllers/example/grails/BookController.groovy').isFile()

        when: 'a tool asks how the tools are run'
        out.reset()
        err.reset()
        ForgeCli.execute(beanContext, 'help', '--json')
        Map described = (Map) new JsonSlurper().parseText(out.toString().readLines().last())

        then:
        ((List<Map>) described.commands).findAll { it.origin == 'application' }.collectEntries { [(it.name): [it.target, it.startsApplication, it.deprecated]] } == [
                built : ['application', false, null],
                inside: ['application', true, null],
                legacy: ['application', true, ['type', 'folder']],
                plain : ['standalone', false, null]]
        ((List<Map>) described.commands).find { it.name == 'generate-controller' }.target == 'standalone'
        runs() == 6

        when: 'the application is packaged'
        out.reset()
        err.reset()
        exit = ForgeCli.execute(beanContext, 'gradle', 'bootJar')
        List<String> entries = []
        new ZipFile(new File(dir, 'build/libs').listFiles().find { it.name.endsWith('.jar') && !it.name.endsWith('-plain.jar') }).withCloseable { ZipFile jar ->
            entries.addAll(jar.entries().toList()*.name)
        }

        then: 'it has none of the tools, and none of what only tools need'
        assert exit == 0 : err.toString() + out.toString()
        !entries.any { String entry -> ['InsideTool', 'BuiltTool', 'PlainTool', 'LegacyCommand'].any { entry.endsWith("/${it}.class") } }
        !entries.any { String entry -> entry.contains('META-INF/grails-tools/') }
        !entries.any { String entry -> ['/grails-core-cli-', '/grails-console-', '/grails-scaffolding-cli'].any { entry.contains(it) } }

        cleanup: 'how long the same work took each way, for whoever reads the report of the specification'
        originalOut?.println("TIMING standalone tool, no Gradle: ${standaloneMillis} ms; tool the build runs, running daemon: ${gradleMillis} ms")
    }

    @Requires({ ToolsOfTheBuild.hasStandaloneScaffolding() })
    void "the scaffolding tools of a REST API application, which has them without the plugin, run as processes without Gradle, and nothing of the plugin is on a class path"() {
        given: 'a REST API application with the scaffolding feature, which was never built, and a domain class'
        application(ApplicationType.REST_API, 'scaffolding')
        write('grails-app/domain/example/grails/Bird.groovy', 'package example.grails\n\nclass Bird {\n\n    String name\n}\n')

        expect: 'the standalone tools of the plugin, in one line, and not the plugin'
        new File(dir, 'build.gradle').text.contains('    grailsCliStandalone "org.apache.grails:grails-scaffolding-cli-standalone"\n')
        !new File(dir, 'build.gradle').text.contains('"org.apache.grails:grails-scaffolding"')
        !new File(dir, 'build.gradle').text.contains('grails-scaffolding-cli"')

        when: 'generate-all is run by its name'
        int exit = ForgeCli.execute(beanContext, 'generate-all', 'example.grails.Bird')

        then: 'the build listed the tools once, and the tool ran as a process, with the templates of a REST API application'
        assert exit == 0 : err.toString() + out.toString()
        runs() == 1
        standaloneFile().tools.contains('generate-all')
        err.toString().readLines().any { String line ->
            line.startsWith('| Running the tool generate-all of org.apache.grails:grails-scaffolding-cli-standalone:') && line.endsWith(', as a process of its own, without Gradle')
        }
        new File(dir, 'grails-app/controllers/example/grails/BirdController.groovy').text.contains("static responseFormats = ['json']")
        ['index.gson', 'show.gson', '_bird.gson'].every { String view -> new File(dir, "grails-app/views/bird/${view}").isFile() }
        !new File(dir, 'grails-app/views/bird/index.gsp').exists()

        when: 'the views are generated again, in an application whose new classes the build has not compiled'
        new File(dir, 'grails-app/views/bird/show.gson').delete()
        out.reset()
        err.reset()
        exit = ForgeCli.execute(beanContext, 'generate-views', 'example.grails.Bird', '--force')

        then: 'Gradle is not started'
        assert exit == 0 : err.toString() + out.toString()
        runs() == 1
        new File(dir, 'grails-app/views/bird/show.gson').isFile()
        !new File(dir, 'grails-app/views/bird/show.gsp').exists()

        when: 'the class paths of the application, of its tests, of the tools the build runs and of the standalone tools'
        Map<String, String> reports = ['runtimeClasspath', 'testRuntimeClasspath', 'integrationTestRuntimeClasspath', 'grailsCliClasspath',
                                       'grailsCliStandaloneClasspath'].collectEntries { String configuration ->
            out.reset()
            assert ForgeCli.execute(beanContext, 'gradle', 'dependencies', '--configuration', configuration) == 0
            [(configuration): out.toString()]
        }

        then: 'none has the scaffolding plugin or the fields plugin; the tools have the companion of the standalone tools'
        reports.findAll { String configuration, String report -> OF_THE_PLUGIN.any { report.contains("org.apache.grails:${it}") } }.keySet() == [] as Set
        reports.grailsCliClasspath.contains('org.apache.grails:grails-scaffolding-cli-standalone')
        reports.grailsCliStandaloneClasspath.contains('org.apache.grails:grails-scaffolding-cli-standalone')

        and: 'neither the application nor its tools have GSP, which the tests have from the testing support of Grails, and not from the plugin'
        WITHOUT_GSP.findAll { String configuration -> reports[configuration].contains("org.apache.grails:${GSP}") } == []
    }

    // a generated application of this specification, with the wrapper writing down each time it is started
    private void application(ApplicationType type, String... features) {
        generateProject(type, features)
        new File(dir, 'settings.gradle') << '\n' + gradle.recording
        new File(dir, 'gradle.properties') << gradle.ofAGeneratedApplication
        File wrapper = new File(dir, 'gradlew')
        Files.move(wrapper.toPath(), new File(dir, 'gradlew.real').toPath())
        wrapper.text = '#!/bin/sh\nhere="$(dirname "$0")"\necho "$*" >> "$here/gradle-runs.txt"\nexec "$here/gradlew.real" "$@"\n'
        wrapper.setExecutable(true, false)
        // the framework of this build is in a repository that is a directory, whose jars a build uses where they are
        safeRepository()
    }

    private File write(String path, String text) {
        File file = new File(dir, path)
        file.parentFile.mkdirs()
        file.text = text.stripIndent()
        return file
    }

    private static String plainTool(String version) {
        return """\
            package example.grails

            import org.apache.grails.core.cli.StandaloneTool
            import org.apache.grails.core.cli.ToolSupport

            class PlainTool implements StandaloneTool, ToolSupport {

                boolean handle() {
                    println "KIND standalone ${version} args=\${args} flag=\${executionContext.commandLine.optionValue('flag')} base=\${executionContext.baseDir.canonicalPath}"
                    !args.contains('fail')
                }
            }
            """.stripIndent()
    }

    // how often the Gradle wrapper of the application was started
    private int runs() {
        File file = new File(dir, 'gradle-runs.txt')
        return file.isFile() ? file.readLines().size() : 0
    }

    private Map standaloneFile() {
        return (Map) new JsonSlurper().parse(new File(dir, StandaloneToolsFile.LOCATION))
    }

    // names the repository of the framework as a directory whose files are used whoever owns them, where it is one
    private static void safeRepository() {
        String repository = System.getenv('GRAILS_REPO_URL')
        if (repository == null || repository.trim().isEmpty()) {
            return
        }
        try {
            Path directory = repository.startsWith('file:') ? Path.of(URI.create(repository)) : Path.of(repository)
            if (directory.isAbsolute() && Files.isDirectory(directory)) {
                ConsoleProject.setSafeDirectories(directory.toRealPath().toString())
            }
        } catch (RuntimeException ignored) {
            // a repository that is no directory has its files in the Gradle user home
        }
    }
}
