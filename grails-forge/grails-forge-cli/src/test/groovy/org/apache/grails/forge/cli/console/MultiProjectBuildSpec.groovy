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
import groovy.json.JsonSlurper
import java.util.function.Predicate
import org.grails.forge.application.ApplicationType
import org.grails.forge.cli.CommandFixture
import org.grails.forge.cli.CommandSpec
import org.grails.forge.cli.ForgeCli
import spock.lang.IgnoreIf
import spock.lang.Shared
import spock.lang.Unroll

/**
 * The commands of a project in a Gradle build of several projects. The build here has the wrapper of a
 * generated application at its root and needs nothing of the framework: two projects that are Grails
 * projects by their {@code grails-app} directory, one of them two directories down, a library that is
 * none, and a build that is included as a build. Every project has tasks of the names the commands run,
 * which say which task of which project ran, and the root says what Gradle was asked for and where.
 */
@IgnoreIf({ os.windows })
class MultiProjectBuildSpec extends CommandSpec implements CommandFixture {

    static final String ROOT = '''\
        // said by the build Gradle was started for, not by one it includes
        if (gradle.parent == null) {
            def start = gradle.startParameter
            println 'requested=' + start.taskNames.collect { '<' + it + '>' }.join('')
            println 'in=' + rootDir.toPath().relativize(start.currentDir.toPath()).join('/')
        }
        '''.stripIndent()

    static final String PROJECT = '''\
        import org.gradle.api.tasks.options.Option

        abstract class Recorded extends DefaultTask {

            @Input @Optional @Option(option = 'tests', description = 'The tests to run')
            List<String> tests = []

            @Input @Option(option = 'rerun-tests', description = 'Run the tests again')
            boolean rerunTests = false

            @Input @Optional @Option(option = 'args-file', description = 'The arguments of a command')
            String argsFile

            @TaskAction
            void run() {
                println "ran ${path}" + (argsFile ? " with ${new File(project.projectDir, argsFile).text}" : '')
            }
        }

        configurations.create('runtimeClasspath')
        ['test', 'cleanTest', 'testApp', 'classes', 'clean', 'assemble', 'bootRun', 'console', 'shell', 'runTool', 'runScriptCommand'].each { String name ->
            tasks.register(name, Recorded)
        }
        '''.stripIndent()

    // One Gradle daemon, of this specification alone, runs the builds of its features: they have nothing to
    // compile, and without it every one of them would wait for a JVM of Gradle to start. A feature that looks
    // at the process a build runs in asks for a build without a daemon.
    @Shared
    IsolatedGradle daemon = new IsolatedGradle()

    void cleanupSpec() {
        daemon.close()
    }

    PrintStream originalOut
    PrintStream originalErr
    ByteArrayOutputStream out = new ByteArrayOutputStream()
    File root
    Long started

    void setup() {
        originalOut = System.out
        originalErr = System.err
        System.setOut(new PrintStream(out, true))
        System.setErr(new PrintStream(out, true))
        root = dir.canonicalFile
        generateProject(ApplicationType.WEB)
        root.eachFile { File file ->
            if (!(file.name in ['gradlew', 'gradlew.bat', 'gradle'])) {
                file.directory ? file.deleteDir() : file.delete()
            }
        }
        new File(root, 'settings.gradle').text = "rootProject.name = 'acme'\ninclude 'apps:admin', 'apps:web:shop', 'libs:core'\nincludeBuild 'tools/site'\n" +
                daemon.recordingOf(true)
        // the daemon of the specification, which ends with it: no daemon is left behind by a case
        new File(root, 'gradle.properties').text = daemon.propertiesOf(true)
        new File(root, 'build.gradle').text = ROOT
        ['apps/admin', 'apps/web/shop', 'apps/extra', 'tools/site'].each { String project -> grailsProject(project) }
        new File(root, 'libs/core/src/main/groovy').mkdirs()
        new File(root, 'libs/core/build.gradle').text = PROJECT
        new File(root, 'tools/site/settings.gradle').text = "rootProject.name = 'site'\n" + daemon.recordingOf(true)
        // a build of its own where Gradle is started in it, which is then run by the daemon of the specification too
        new File(root, 'tools/site/gradle.properties').text = daemon.propertiesOf(true)
        String option = 'import org.gradle.api.tasks.options.Option\n'
        new File(root, 'tools/site/build.gradle').text = option + ROOT + PROJECT.replace(option, '')
    }

    void cleanup() {
        System.setOut(originalOut)
        System.setErr(originalErr)
        ConsoleProject.setOwnedByUser(null)
        ConsoleProject.setSafeDirectories(null)
        // a build that a feature started and did not see end is ended here
        if (started != null) {
            ProcessHandle.of(started).ifPresent { ProcessHandle handle ->
                handle.descendants().forEach { ProcessHandle each -> each.destroyForcibly() }
                handle.destroyForcibly()
            }
        }
    }

    @Unroll
    void "#line in #directory asks the Gradle of the build for #requested"() {
        when:
        int exit = run(directory, line as String[])

        then:
        exit == 0
        lineOf('requested=') == requested
        lineOf('in=') == project
        ran() == tasks
        errors() == []

        where:
        line                                   | directory                                  | project         | requested                                                      | tasks
        ['compile']                            | 'apps/web/shop'                            | 'apps/web/shop' | '<:apps:web:shop:classes>'                                     | [':apps:web:shop:classes']
        ['compile']                            | 'apps/web/shop/grails-app/controllers'     | 'apps/web/shop' | '<:apps:web:shop:classes>'                                     | [':apps:web:shop:classes']
        ['compile']                            | 'apps/admin'                               | 'apps/admin'    | '<:apps:admin:classes>'                                        | [':apps:admin:classes']
        ['test-app', '--clean', 'BookSpec']    | 'apps/admin/src'                           | 'apps/admin'    | '<:apps:admin:testApp><--tests><BookSpec><--rerun-tests>'      | [':apps:admin:testApp']
        ['war', '--clean']                     | 'apps/admin'                               | 'apps/admin'    | '<:apps:admin:clean><:apps:admin:assemble>'                    | [':apps:admin:clean', ':apps:admin:assemble']
        ['run-app']                            | 'apps/web/shop'                            | 'apps/web/shop' | '<:apps:web:shop:bootRun>'                                     | [':apps:web:shop:bootRun']
        ['run-tool', 'say', 'a b']             | 'apps/admin/grails-app'                    | 'apps/admin'    | '<:apps:admin:runTool><--args-file=.grails/run/command-args-0.json>' | [':apps:admin:runTool with ["say","a b"]']
        ['gradle', 'classes']                  | 'apps/admin'                               | 'apps/admin'    | '<classes>'                                                    | [':apps:admin:classes']
        ['gradle', 'classes', '-q']            | 'libs/core'                                | 'libs/core'     | '<classes>'                                                    | [':libs:core:classes']
        ['gradle', ':apps:admin:classes']      | 'libs/core'                                | 'libs/core'     | '<:apps:admin:classes>'                                        | [':apps:admin:classes']
    }

    void "gradle in the root of the build is Gradle there, for every project of it"() {
        when:
        int exit = run('', 'gradle', 'classes')

        then:
        exit == 0
        lineOf('requested=') == '<classes>'
        lineOf('in=') == ''
        ran() as Set<String> == [':apps:admin:classes', ':apps:web:shop:classes', ':libs:core:classes'] as Set<String>
    }

    void "a build that is included as a build is the build of its project, run with the wrapper of the directory above"() {
        when:
        int exit = run('tools/site/grails-app', 'compile')

        then: 'the task is that of the root: the project is the root of its own build'
        exit == 0
        lineOf('requested=') == '<:classes>'
        lineOf('in=') == ''
        ran() == [':classes']
    }

    void "a project that is the root of a build which includes a plugin beside its sources is run alone, and not the plugin with it"() {
        given: 'an application with settings of its own that include a plugin, which has the tasks the application has'
        new File(root, 'tools/site/settings.gradle').text = "rootProject.name = 'site'\ninclude 'inline'\n"
        new File(root, 'tools/site/inline/src/main/groovy').mkdirs()
        new File(root, 'tools/site/inline/build.gradle').text = PROJECT

        when: 'a command of the application'
        int exit = run('tools/site/grails-app', 'compile')

        then: 'Gradle is asked for the task of the root, which a task that is named without a path is not'
        exit == 0
        lineOf('requested=') == '<:classes>'
        ran() == [':classes']

        when: 'a command of the application and its plugins'
        out.reset()
        exit = run('tools/site', 'run-tool', 'say')

        then:
        exit == 0
        lineOf('requested=').startsWith('<:runTool>')
        ran().size() == 1
        ran()[0].startsWith(':runTool ')

        when: 'Gradle itself is asked, as it is typed'
        out.reset()
        exit = run('tools/site', 'gradle', 'classes')

        then: 'for every project, as Gradle does'
        exit == 0
        lineOf('requested=') == '<classes>'
        ran() as Set<String> == [':classes', ':inline:classes'] as Set<String>
    }

    @Unroll
    void "settings that are a link to a file make the project the root of its build as a file does: #settings"() {
        given: 'an application whose settings are a link, as Gradle follows it, and include a plugin that has the tasks the application has'
        File site = new File(root, 'tools/site')
        assert new File(site, 'settings.gradle').delete()
        new File(site, 'conf').mkdirs()
        new File(site, "conf/${settings}").text = text
        Files.createSymbolicLink(new File(site, settings).toPath(), Path.of("conf/${settings}"))
        new File(site, 'inline/src/main/groovy').mkdirs()
        new File(site, 'inline/build.gradle').text = PROJECT

        when: 'a command of the application'
        int exit = run('tools/site/grails-app', 'compile')

        then: 'Gradle is asked for the task of the root, and the plugin is not compiled with it'
        exit == 0
        lineOf('requested=') == '<:classes>'
        ran() == [':classes']

        and: 'Gradle is given what makes sure that the root is this project'
        new File(site, '.grails/run/project-path.gradle').isFile()

        when: 'Gradle itself is asked, as it is typed'
        out.reset()
        exit = run('tools/site', 'gradle', 'classes')

        then: 'for every project, as Gradle does'
        exit == 0
        ran() as Set<String> == [':classes', ':inline:classes'] as Set<String>

        where:
        settings              | text
        'settings.gradle'     | "rootProject.name = 'site'\ninclude 'inline'\n"
        'settings.gradle.kts' | 'rootProject.name = "site"\ninclude("inline")\n'
    }

    void "settings that are a link to nothing are no settings, as for Gradle: the project is a project of the build above"() {
        given:
        Files.createSymbolicLink(new File(root, 'apps/admin/settings.gradle').toPath(), Path.of('conf/nothing.gradle'))

        when:
        int exit = run('apps/admin', 'compile')

        then:
        exit == 0
        lineOf('requested=') == '<:apps:admin:classes>'
        ran() == [':apps:admin:classes']
    }

    void "the wrapper of the build is the one that is run, also where the project has one of its own"() {
        given:
        File own = new File(root, 'apps/admin/gradlew')
        own.text = '#!/bin/sh\necho ran the wrapper of the project\nexit 7\n'
        own.setExecutable(true)

        when:
        int exit = run('apps/admin', 'compile')

        then:
        exit == 0
        ran() == [':apps:admin:classes']
        !out.toString().contains('ran the wrapper of the project')
    }

    void "a project that has the only wrapper is built with it, as a project of the build above"() {
        given:
        ['gradlew', 'gradlew.bat', 'gradle'].each { String name ->
            assert new File(root, name).renameTo(new File(root, "apps/admin/${name}"))
        }

        when:
        int exit = run('apps/admin', 'compile')

        then:
        exit == 0
        lineOf('requested=') == '<:apps:admin:classes>'
        ran() == [':apps:admin:classes']
    }

    @Unroll
    void "#line in #place names the Grails projects of the build and runs nothing"() {
        when:
        int exit = run(directory, line as String[])

        then:
        exit == 1
        !out.toString().contains('requested=')
        errors().size() == 1
        errors()[0].contains("${line.take(2).join(' ') - ' Book' - ' grails-quartz'} is a command of a Grails project; ${new File(root, directory).path} ${what.replace('ROOT', root.path)}. " +
                'The Grails projects of the build: apps/admin, apps/extra, apps/web/shop, tools/site. Run the command in the directory of the one that is meant' + how)

        where:
        line                             | directory   | place                  | what                                                            | how
        ['compile']                      | ''          | 'the root'             | 'is the root of a Gradle build and is no Grails project itself' | ', as in: cd apps/admin'
        ['run-app']                      | ''          | 'the root'             | 'is the root of a Gradle build and is no Grails project itself' | ', as in: cd apps/admin'
        ['stats']                        | ''          | 'the root'             | 'is the root of a Gradle build and is no Grails project itself' | ', as in: cd apps/admin'
        ['create-controller', 'Book']    | ''          | 'the root'             | 'is the root of a Gradle build and is no Grails project itself' | ', as in: cd apps/admin'
        ['feature', 'add', 'grails-quartz'] | ''       | 'the root'             | 'is the root of a Gradle build and is no Grails project itself' | ', as in: cd apps/admin'
        ['compile']                      | 'libs/core' | 'a library of it'      | 'is in the Gradle build of ROOT and in no Grails project' | ''
        ['compile']                      | 'apps'      | 'a directory of it'    | 'is in the Gradle build of ROOT and in no Grails project' | ''
    }

    void "a project the settings do not include fails as Gradle fails it, and the command says which project it asked for"() {
        when:
        int exit = run('apps/extra', 'compile')

        then:
        exit == 1
        ran() == []
        said().contains('| Error Gradle did not succeed: it ended with exit code 1')
        said().contains("| Note The tasks were asked of the project :apps:extra of the Gradle build in ${root.path}, which is the path the directories give.")
        said().contains("the settings of that build do not include ${new File(root, 'apps/extra').path} by its directory: the line for that is include 'apps:extra'")
    }

    void "a build above that is not the user's is not run, unless its directory is named as safe"() {
        given:
        ConsoleProject.setOwnedByUser({ Path path -> path != root.toPath() } as Predicate<Path>)

        when:
        int exit = run('apps/admin', 'compile')

        then:
        exit == 1
        !out.toString().contains('requested=')
        said().contains("The Gradle wrapper ${new File(root, 'gradlew').path} is not run for the project in ${new File(root, 'apps/admin').path}: ${root.path} is owned by")
        said().contains("name ${root.path} in the environment variable GRAILS_SAFE_DIRECTORY to run it all the same")

        when: 'gradle itself, from a directory that is in no Grails project'
        out.reset()
        exit = run('libs/core', 'gradle', 'classes')

        then:
        exit == 1
        !out.toString().contains('requested=')
        said().contains('GRAILS_SAFE_DIRECTORY')

        when:
        out.reset()
        ConsoleProject.setSafeDirectories(root.path)
        exit = run('apps/admin', 'compile')

        then:
        exit == 0
        ran() == [':apps:admin:classes']
    }

    void "run-app --detach and stop-app are of the project they are given in, and of no other project of the build"() {
        given: 'a project whose bootRun answers on the port it is given, in a build without a daemon: what answers ends with the Gradle that is stopped'
        new File(root, 'gradle.properties').text = daemon.propertiesOf(false)
        new File(root, 'apps/admin/build.gradle').text = LifecycleCommandsSpec.BUILD
        int port = new ServerSocket(0, 1, InetAddress.getLoopbackAddress()).withCloseable { ServerSocket socket -> socket.localPort }

        when:
        int exit = run('apps/admin/grails-app', 'run-app', '--detach', '--port', port as String, '--ready-timeout', '600')
        File record = new File(root, 'apps/admin/.grails/run/app.json')
        started = (new JsonSlurper().parse(record) as Map).pid as long

        then: 'the record is in the project, not where the command was given or at the root of the build'
        exit == 0
        errors() == []
        ApplicationPort.isOpen(port)
        !new File(root, '.grails').exists()
        !new File(root, 'apps/admin/grails-app/.grails').exists()

        when: 'stop-app in another project of the build, which has the same wrapper'
        out.reset()
        int other = run('apps/web/shop', 'stop-app')

        then:
        other == 1
        said().contains('No application that run-app started is running for this project')
        ApplicationPort.isOpen(port)

        when: 'in the project: the process is believed to be the wrapper of the build running the task of this project'
        out.reset()
        int stopped = run('apps/admin/src', 'stop-app')

        then:
        stopped == 0
        errors() == []
        !ApplicationPort.isOpen(port)
        !record.exists()
        !ProcessHandle.of(started).map { ProcessHandle handle -> handle.alive }.orElse(false)
    }

    private void grailsProject(String path) {
        File project = new File(root, path)
        new File(project, 'grails-app/controllers').mkdirs()
        new File(project, 'src').mkdirs()
        // where run-tool keeps the arguments of a tool
        new File(project, '.grails').mkdirs()
        new File(project, 'build.gradle').text = PROJECT
    }

    private int run(String directory, String... arguments) {
        System.setProperty('user.dir', new File(root, directory).absolutePath)
        return ForgeCli.execute(beanContext, arguments)
    }

    private String lineOf(String start) {
        List<String> lines = out.toString().readLines().findAll { String line -> line.startsWith(start) }
        assert lines.size() == 1
        return lines[0].substring(start.length())
    }

    private List<String> ran() {
        return out.toString().readLines().findAll { String line -> line.startsWith('ran :') }.collect { String line -> line.substring('ran '.length()) }
    }

    private List<String> errors() {
        return out.toString().readLines().findAll { String line -> line.contains('| Error') }
    }

    private String said() {
        return out.toString().replaceAll(/\s+/, ' ')
    }
}
