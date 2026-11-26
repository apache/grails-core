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

import org.grails.forge.application.ApplicationType
import org.grails.forge.cli.CommandFixture
import org.grails.forge.cli.CommandSpec
import org.grails.forge.cli.ForgeCli
import spock.lang.IgnoreIf
import spock.lang.Shared
import spock.lang.Timeout
import spock.lang.Unroll

/**
 * The tasks a command of a project names by the path of the project run only where Gradle takes the
 * directory of the project for the project of that path. The path is the one the directories give, and
 * a build can give a directory another path, and that path to another directory: Gradle would then run
 * the tasks of another project. Here the settings give the directory {@code app} the path
 * {@code :service}, and the path {@code :app} to the directory {@code legacy-app}; the projects
 * {@code :a} and {@code :b} are in each other's directory; and {@code apps/web/shop} is a project that
 * is included by its directory. Some features move the projects in a callback of the settings instead,
 * which runs after the settings are evaluated. The builds are real Gradle builds, run by a daemon of this
 * specification, by the wrapper as a process and through the Tooling API, with and without the
 * configuration cache.
 */
@IgnoreIf({ os.windows })
@Timeout(600)
class ProjectPathSpec extends CommandSpec implements CommandFixture {

    // the settings of the review that found the check passing a build whose callback moved the projects afterwards
    static final String MOVED_BY_A_CALLBACK = '''\
        include 'app', 'service'

        gradle.settingsEvaluated {
            project(':app').projectDir = file('legacy-app')
            project(':service').projectDir = file('app')
        }
        '''.stripIndent()

    static final String REUSED = 'Reusing configuration cache'

    // The features change the settings between two builds of one daemon and expect the second build to read them
    // again. A daemon that watches the file system knows of a change by the events of the system, and those come late
    // while the system has many of them, as it has while the other specs run: the second build then took the settings
    // for unchanged and reused the entry of the configuration cache the first build stored. Without watching, a
    // build looks at the files it reads itself.
    static final String NOT_WATCHED = 'org.gradle.vfs.watch=false\n'

    @Shared
    IsolatedGradle daemon = new IsolatedGradle()

    void cleanupSpec() {
        daemon.close()
    }

    PrintStream originalOut
    PrintStream originalErr
    ByteArrayOutputStream out = new ByteArrayOutputStream()
    File root

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
        new File(root, 'settings.gradle').text = '''\
            rootProject.name = 'acme'
            include 'service', 'app', 'a', 'b', 'apps:web:shop'
            project(':service').projectDir = file('app')
            project(':app').projectDir = file('legacy-app')
            project(':a').projectDir = file('b')
            project(':b').projectDir = file('a')
            '''.stripIndent() + daemon.recordingOf(true)
        new File(root, 'gradle.properties').text = daemon.propertiesOf(true) + NOT_WATCHED
        new File(root, 'build.gradle').text = MultiProjectBuildSpec.ROOT
        ['app', 'legacy-app', 'a', 'b', 'apps/web/shop'].each { String project -> grailsProject(new File(root, project)) }
    }

    void cleanup() {
        System.setOut(originalOut)
        System.setErr(originalErr)
        GradleBuild.setRunner(null)
        ToolingApiRunner.closeConnections()
    }

    @Unroll
    void "#line in #directory, whose directories give the path of another project of the build, runs no task of any project"() {
        when:
        int exit = run(directory, line as String[])

        then: 'Gradle is asked for the tasks of the path the directories give, and runs none'
        exit == 1
        ran() == []
        said().contains("The Grails command line ran Gradle for the project in ${new File(root, directory).path} and named its tasks by the path its directories give, ${named}, " +
                "but in this build the project in that directory is ${actual}${elsewhere == null ? " (and the build has no project ${named})" : " (and ${named} is the project in ${new File(root, elsewhere).path})"}. " +
                'Nothing was run, so that no task of another project runs in its place. ' +
                "Run the task of this project by its path yourself, as in: grails gradle ${actual}:${task}")

        where:
        line                      | directory    | named          | actual     | task         | elsewhere
        ['test-app']              | 'app'        | ':app'         | ':service' | 'testApp'    | 'legacy-app'
        ['compile']               | 'app'        | ':app'         | ':service' | 'classes'    | 'legacy-app'
        ['run-app']               | 'app'        | ':app'         | ':service' | 'bootRun'    | 'legacy-app'
        ['run-tool', 'say']       | 'app'        | ':app'         | ':service' | 'runTool'    | 'legacy-app'
        ['compile']               | 'legacy-app' | ':legacy-app'  | ':app'     | 'classes'    | null
        ['compile']               | 'a'          | ':a'           | ':b'       | 'classes'    | 'b'
        ['compile']               | 'b'          | ':b'           | ':a'       | 'classes'    | 'a'
    }

    void "a project that is included by its directory runs its own tasks"() {
        when:
        int exit = run('apps/web/shop/grails-app', 'compile')

        then:
        exit == 0
        lineOf('requested=') == '<:apps:web:shop:classes>'
        ran() == [':apps:web:shop:classes']
    }

    void "through the Tooling API, as in the interactive console, the tasks of another project are not run either"() {
        given:
        GradleBuild.setRunner(GradleBuild.TOOLING_API)

        when:
        int exit = run('app', 'compile')

        then:
        exit == 1
        ran() == []
        said().contains("but in this build the project in that directory is :service (and :app is the project in ${new File(root, 'legacy-app').path}). Nothing was run")

        when: 'a project whose path is the one its directories give'
        out.reset()
        exit = run('apps/web/shop', 'compile')

        then:
        exit == 0
        ran() == [':apps:web:shop:classes']
    }

    @Unroll
    void "a callback of the settings that moves the projects after they are evaluated runs no task of another project: #runner, #cache"() {
        given: 'settings whose callback runs after the one an init script registers when the settings are evaluated'
        settings(MOVED_BY_A_CALLBACK)
        configurationCache(cached)
        GradleBuild.setRunner(runner)

        when:
        int exit = run('app', 'compile')

        then: 'what is compared is where the projects are once the callback has moved them'
        exit == 1
        ran() == []
        said().contains(movedAway())

        when: 'the same command again, which reuses an entry of the configuration cache where the first one stored one that can be'
        out.reset()
        exit = run('app', 'compile')

        then:
        exit == 1
        ran() == []
        said().contains(movedAway())
        !said().contains(REUSED)

        where:
        runner                  | cached | cache
        GradleBuild.PROCESS     | false  | 'without the configuration cache'
        GradleBuild.PROCESS     | true   | 'with the configuration cache'
        GradleBuild.TOOLING_API | false  | 'without the configuration cache'
        GradleBuild.TOOLING_API | true   | 'with the configuration cache'
    }

    @Unroll
    void "an entry of the configuration cache a build of the project stored is not reused once a callback of the settings moves the projects: #runner"() {
        given: 'settings that give the directory app the path its directories give'
        settings("include 'app', 'service'\nproject(':service').projectDir = file('legacy-app')\n")
        configurationCache(true)
        GradleBuild.setRunner(runner)

        when: 'an entry is stored, and reused'
        int stored = run('app', 'compile')
        String first = said()
        out.reset()
        int reused = run('app', 'compile')

        then: 'the check does not keep the entry from being reused'
        stored == 0 && reused == 0
        first.contains('Configuration cache entry stored')
        said().contains(REUSED)
        ran() == [':app:classes']

        when: 'a callback of the settings moves the projects'
        settings(MOVED_BY_A_CALLBACK)
        out.reset()
        int moved = run('app', 'compile')

        then: 'the entry is not reused for it, and the check runs no task of another project'
        moved == 1
        ran() == []
        !said().contains(REUSED)
        said().contains(movedAway())

        when: 'and again'
        out.reset()
        moved = run('app', 'compile')

        then:
        moved == 1
        ran() == []
        !said().contains(REUSED)
        said().contains(movedAway())

        where:
        runner << [GradleBuild.PROCESS, GradleBuild.TOOLING_API]
    }

    @Unroll
    void "#line gives Gradle what follows the second -- as Gradle reads it on a command line of its own, after the check of the path: #runner"() {
        given: 'a task of the project of the name the review gave'
        new File(root, 'apps/web/shop/build.gradle') << "tasks.register('helpful', Recorded)\n"
        GradleBuild.setRunner(runner)

        when:
        int exit = run('apps/web/shop', line as String[])

        then: 'the tasks after the second -- are run, with the task of the command, and the path was checked'
        exit == 0
        ran() == [':apps:web:shop:classes', ':apps:web:shop:helpful']

        when: 'in a directory whose path is that of another project'
        out.reset()
        exit = run('app', line as String[])

        then:
        exit == 1
        ran() == []
        said().contains("but in this build the project in that directory is :service (and :app is the project in ${new File(root, 'legacy-app').path}). Nothing was run")

        where:
        runner                  | line
        GradleBuild.PROCESS     | ['compile', '--', '--', 'helpful']
        GradleBuild.PROCESS     | ['compile', '--', '--offline', '--', 'helpful']
        GradleBuild.TOOLING_API | ['compile', '--', '--', 'helpful']
        GradleBuild.TOOLING_API | ['compile', '--', '--offline', '--', 'helpful']
    }

    void "run-app --detach starts no build for another project"() {
        when:
        int exit = run('app', 'run-app', '--detach', '--ready-timeout', '600')

        then:
        exit == 1
        ran() == []
        said().contains('but in this build the project in that directory is :service')
        !new File(root, 'app/.grails/run/app.json').exists()
    }

    void "a directory the settings include under no path runs nothing, and the command says how it is included"() {
        given:
        grailsProject(new File(root, 'apps/extra'))

        when:
        int exit = run('apps/extra', 'compile')

        then: 'Gradle refuses a directory that is in no project of the build itself, once every callback of the settings has run and before the check is asked'
        exit == 1
        ran() == []
        said().contains("Project directory '${new File(root, 'apps/extra').path}' is not part of the build defined by settings file '${new File(root, 'settings.gradle').path}'")
        said().contains("If Gradle said that no project of the build is in the directory, the settings of that build do not include ${new File(root, 'apps/extra').path} by its directory: " +
                "the line for that is include 'apps:extra'")
    }

    void "a project that is a build of its own whose settings put the root project in another directory runs nothing"() {
        given: 'a build in tools/site whose root project is in tools/site/elsewhere, and a project of the same tasks there'
        File site = new File(root, 'tools/site')
        grailsProject(site)
        grailsProject(new File(site, 'elsewhere'))
        new File(site, 'settings.gradle').text = "rootProject.name = 'site'\nrootProject.projectDir = file('elsewhere')\n" + daemon.recordingOf(true)
        new File(site, 'gradle.properties').text = daemon.propertiesOf(true)

        when:
        int exit = run('tools/site', 'compile')

        then: 'the task of the root is that of another directory, and is not run'
        exit == 1
        ran() == []
        said().contains("The Grails command line ran Gradle for the project in ${site.path} and named its tasks as those of the root project of the build, which its settings make it, " +
                "but no project of this build is in that directory (and the root project is in ${new File(site, 'elsewhere').path}). Nothing was run")
    }

    private static void grailsProject(File project) {
        new File(project, 'grails-app/controllers').mkdirs()
        new File(project, 'build.gradle').text = MultiProjectBuildSpec.PROJECT
    }

    // the settings of the build in place of those of the setup, with its name and the line of the daemon
    private void settings(String included) {
        new File(root, 'settings.gradle').text = "rootProject.name = 'acme'\n" + included + daemon.recordingOf(true)
    }

    private void configurationCache(boolean on) {
        new File(root, 'gradle.properties').text = daemon.propertiesOf(true) + NOT_WATCHED + (on ? 'org.gradle.configuration-cache=true\n' : '')
    }

    // what the check says in app, whose path :app the callback gives legacy-app
    private String movedAway() {
        return "The Grails command line ran Gradle for the project in ${new File(root, 'app').path} and named its tasks by the path its directories give, :app, " +
                "but in this build the project in that directory is :service (and :app is the project in ${new File(root, 'legacy-app').path}). " +
                'Nothing was run, so that no task of another project runs in its place. Run the task of this project by its path yourself, as in: grails gradle :service:classes'
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

    private String said() {
        return out.toString().replaceAll(/\s+/, ' ')
    }
}
