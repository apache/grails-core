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
package org.grails.gradle.plugin.core

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.IgnoreIf

/**
 * A plugin that keeps a Forge feature pack under {@code src/main/forge} can have it tested with
 * applications: {@code forgePackTest} has Forge generate them, in a JVM of its own, and builds each
 * with the Gradle wrapper it was generated with.
 *
 * <p>Forge is built from this plugin and cannot be resolved by its tests, so the test project puts a
 * stand-in on {@code grailsForgePackTooling}: a class that is started and read as Forge is, and writes
 * for each fixture a directory with a script of the name of the wrapper, which says what it was started
 * with and ends as a build does. What Forge generates, and that it can be built, is tested with Forge.</p>
 */
class GrailsPluginForgePackTestSpec extends GradleSpecification {

    def "the feature pack is generated into the applications Forge makes of it, and each is built with its wrapper"() {
        given:
        GradleRunner runner = setupTestResourceProject('plugin-forge-pack')
        File project = runner.projectDir.canonicalFile

        when:
        BuildResult result = executeTask('forgePackTest', ['--info'])

        then:
        assertTaskSuccess('forgePackTest', result)

        and: 'Forge is told the pack and where to generate, and no fixtures'
        result.output.contains("stand-in fixtures of: ${new File(project, 'src/main/forge')}")
        result.output.contains("stand-in fixtures in: ${new File(project, 'build/forge-pack-fixtures')}")
        result.output.contains('stand-in fixtures are the defaults')

        and: 'in a JVM that has the one jar of the configuration on its class path'
        String classPath = result.output.readLines().find { it.contains('stand-in fixtures class path: ') }.split('stand-in fixtures class path: ')[1]
        classPath.endsWith('forge-stub.jar')
        !classPath.contains(File.pathSeparator)

        and: 'each application is built in its directory, with the Gradle user home of this build and the task that packages it'
        ['web', 'rest-api', 'web-plugin', 'plugin', 'rest-api-plugin'].every { String fixture ->
            String log = new File(project, "build/forge-pack-test/${fixture}/build.log").getText('UTF-8')
            wrapperArguments(project, fixture) == 'assemble --console=plain --gradle-user-home <home>' &&
                    log.contains("stand-in wrapper directory: ${new File(project, "build/forge-pack-fixtures/${fixture}")}") &&
                    log.contains('BUILD SUCCESSFUL')
        }

        and: 'with the Java of the toolchain of the project'
        new File(new File(project, 'build/forge-pack-test/web/build.log').getText('UTF-8').readLines()
                .find { it.startsWith('stand-in wrapper JAVA_HOME: ') }.substring('stand-in wrapper JAVA_HOME: '.length())).canonicalFile ==
                new File(System.getProperty('java.home')).canonicalFile

        and: 'what became of them is said, and written down'
        result.output.contains('The feature pack in src/main/forge was generated into 5 applications and built with assemble: web, rest-api, web-plugin, plugin, rest-api-plugin. ' +
                "The logs are in ${new File(project, 'build/forge-pack-test')}")
        new File(project, 'build/forge-pack-test/report.txt').getText('UTF-8') == 'web: built\nrest-api: built\nweb-plugin: built\nplugin: built\nrest-api-plugin: built\n'
    }

    def "the fixtures, the tasks and the arguments the build names are the ones that are generated and run"() {
        given:
        GradleRunner runner = setupTestResourceProject('plugin-forge-pack')
        File project = runner.projectDir.canonicalFile

        when:
        BuildResult result = executeTask('forgePackTest', ['--info', '-Pfixtures=beta:rest-api', '-PfixtureBuild'])

        then: 'Forge is given them, a fixture on a line'
        result.output.contains('stand-in fixture line: alpha web data=mongodb jdk=25 features=one,two')
        result.output.contains('stand-in fixture line: beta rest-api')
        !result.output.contains('stand-in fixtures are the defaults')

        and:
        wrapperArguments(project, 'alpha') == 'build check --console=plain --gradle-user-home <home> --stacktrace'
        new File(project, 'build/forge-pack-test/report.txt').getText('UTF-8') == 'alpha: built\nbeta: built\n'
    }

    def "an application that cannot be built fails the build, which names the fixture, the task and the log, and shows its end"() {
        given:
        GradleRunner runner = setupTestResourceProject('plugin-forge-pack')
        File project = runner.projectDir.canonicalFile

        when:
        BuildResult result = runner.withArguments('forgePackTest', '-Pfixtures=broken-beta:rest-api,gamma:web').buildAndFail()

        then:
        result.output.contains('The feature pack in src/main/forge was generated into 3 applications, of which 1 could not be built with assemble:')
        result.output.contains('  - broken-beta: task :compileGroovy failed (Gradle ended with 1)')
        result.output.contains("    the application is in ${new File(project, 'build/forge-pack-fixtures/broken-beta')}")
        result.output.contains("    the whole log is in ${new File(project, 'build/forge-pack-test/broken-beta/build.log')}")

        and: 'the end of the log, in bounds and without the control characters it has'
        result.output.contains('      startup failed:  [31mAudit.groovy: 3: unable to resolve class Missing')
        !result.output.contains('\u001b[31mAudit.groovy')
        result.output.contains('      BUILD FAILED in 1s')
        result.output.contains('      compiler line 39')
        !result.output.contains('      compiler line 12')
        !result.output.contains('      > Task :compileJava')

        and: 'the whole log has all of it'
        new File(project, 'build/forge-pack-test/broken-beta/build.log').getText('UTF-8').contains('compiler line 0')

        and: 'the applications after it were built too'
        new File(project, 'build/forge-pack-test/gamma/build.log').getText('UTF-8').contains('BUILD SUCCESSFUL')
        new File(project, 'build/forge-pack-test/report.txt').getText('UTF-8') ==
                'alpha: built\nbroken-beta: failed: task :compileGroovy failed (Gradle ended with 1)\ngamma: built\n'

        when: 'the build is run again'
        result = runner.withArguments('forgePackTest', '-Pfixtures=broken-beta:rest-api,gamma:web').buildAndFail()

        then: 'it is not taken for one that passed'
        result.output.contains('  - broken-beta: task :compileGroovy failed')
    }

    def "a fixture the generator refuses fails the build with what the generator says, and nothing is built"() {
        given:
        GradleRunner runner = setupTestResourceProject('plugin-forge-pack')

        when:
        BuildResult result = runner.withArguments('forgePackTest', '-Pfixtures=refused-delta:web').buildAndFail()

        then:
        result.output.contains('The feature pack in src/main/forge could not be tested: of 2 fixtures the generator refused 1, and none was built:')
        result.output.contains('  - refused-delta: The feature pack acme-audit is not for an application of the type plugin: its descriptor names [web, rest-api]')
        !result.output.contains('\u001b[31m')
        !new File(runner.projectDir, 'build/forge-pack-test/alpha').exists()
    }

    def "an application that was generated without a wrapper is one that cannot be built"() {
        given:
        GradleRunner runner = setupTestResourceProject('plugin-forge-pack')

        when:
        BuildResult result = runner.withArguments('forgePackTest', '-Pfixtures=bare-one:web').buildAndFail()

        then:
        result.output.contains('  - bare-one: the application was generated without gradlew')
    }

    def "the problems Forge has with a pack fail the build, each on a line"() {
        given:
        GradleRunner runner = setupTestResourceProject('plugin-forge-pack')
        new File(runner.projectDir, 'src/main/forge/feature.yml') << 'colour: red\n'

        when:
        BuildResult result = runner.withArguments('forgePackTest').buildAndFail()

        then:
        result.output.contains('The feature pack in src/main/forge cannot be used:')
        result.output.contains("  - the descriptor: has the key 'colour', which this version of Forge does not read here")
        !new File(runner.projectDir, 'build/forge-pack-fixtures/web').exists()
    }

    def "the applications are generated and built only when that is asked for: #lifecycle does not"() {
        given:
        setupTestResourceProject('plugin-forge-pack')

        when: 'the tasks that would run are listed'
        BuildResult result = executeTask(lifecycle, ['-Ppublished', '--dry-run'])

        then:
        result.output.contains(":${ends} SKIPPED")
        !result.output.contains(':forgePackTest')

        where:
        lifecycle             || ends
        'check'               || 'check'
        'build'               || 'build'
        'assemble'            || 'assemble'
        'publish'             || 'publish'
        'publishToMavenLocal' || 'publishToMavenLocal'
    }

    def "a plugin without a feature pack has none to test, and resolves no Forge"() {
        given: 'a build that would take Forge from a repository, and has none'
        GradleRunner runner = setupTestResourceProject('plugin-forge-pack')
        new File(runner.projectDir, 'src/main/forge').deleteDir()

        when:
        BuildResult result = executeTask('forgePackTest', ['-PforgeOfGrails'])

        then:
        result.tasks.find { it.path == ':forgePackTest' }.outcome == TaskOutcome.SKIPPED
        !new File(runner.projectDir, 'build/forge-pack-fixtures').exists()
    }

    def "a pack that was tested and is as it was is not tested again, and one that has changed is"() {
        given:
        GradleRunner runner = setupTestResourceProject('plugin-forge-pack')
        executeTask('forgePackTest')

        when:
        BuildResult result = executeTask('forgePackTest')

        then: 'with the configuration of the first build'
        result.output.contains('Reusing configuration cache')
        result.tasks.find { it.path == ':forgePackTest' }.outcome == TaskOutcome.UP_TO_DATE

        when: 'a file of the pack that Gradle does not look at is changed'
        new File(runner.projectDir, 'src/main/forge/templates/.gitignore').text = 'build\n'
        result = executeTask('forgePackTest')

        then:
        result.output.contains('Reusing configuration cache')
        result.tasks.find { it.path == ':forgePackTest' }.outcome == TaskOutcome.SUCCESS

        when: 'the tasks to run in the applications are others'
        result = executeTask('forgePackTest', ['-PfixtureBuild'])

        then:
        result.tasks.find { it.path == ':forgePackTest' }.outcome == TaskOutcome.SUCCESS
        new File(runner.projectDir, 'build/forge-pack-test/web/build.log').getText('UTF-8').contains('stand-in wrapper arguments: build check ')

        when: 'a log is taken away'
        new File(runner.projectDir, 'build/forge-pack-test/web/build.log').delete()
        result = executeTask('forgePackTest', ['-PfixtureBuild'])

        then:
        result.tasks.find { it.path == ':forgePackTest' }.outcome == TaskOutcome.SUCCESS
    }

    def "no file of the pack is read while the build is configured for the test: #change is seen with the configuration of the build before"() {
        given:
        GradleRunner runner = setupTestResourceProject('plugin-forge-pack')
        executeTask('forgePackTest')
        edit.call(new File(runner.projectDir, 'src/main/forge'))

        when:
        BuildResult result = passes ? executeTask('forgePackTest') : runner.withArguments('forgePackTest').buildAndFail()

        then: 'the pack is an input of the task, which runs again, and not of the configuration'
        result.output.contains('Reusing configuration cache')
        !result.output.contains('configuration cache cannot be reused')
        result.tasks.find { it.path == ':forgePackTest' }.outcome == (passes ? TaskOutcome.SUCCESS : TaskOutcome.FAILED)
        said == null || result.output.contains(said)

        where:
        change                              | edit                                                                       | passes || said
        'a template that is edited'         | { File pack -> new File(pack, 'templates/Audit.groovy.gsp') << '// more' } | true   || null
        'a file that is added'              | { File pack -> new File(pack, 'templates/conf/more.txt').text = 'more' }   | true   || null
        'a file Gradle does not look at'    | { File pack -> new File(pack, 'templates/.gitignore').text = 'build' }     | true   || null
        'a descriptor that is spoiled'      | { File pack -> new File(pack, 'feature.yml') << 'colour: red\n' }          | false  || "  - the descriptor: has the key 'colour', which this version of Forge does not read here"
        'a descriptor that is taken away'   | { File pack -> new File(pack, 'feature.yml').delete() }                    | false  || '  - There is no feature.yml in the directory, or it is not a file'
    }

    def "the applications are built offline when the #where says so"() {
        given:
        GradleRunner runner = setupTestResourceProject('plugin-forge-pack')

        when:
        executeTask('forgePackTest', [argument])

        then:
        wrapperArguments(runner.projectDir.canonicalFile, 'web') == 'assemble --console=plain --gradle-user-home <home> --offline'

        when: 'and are not when it does not'
        executeTask('forgePackTest')

        then:
        !new File(runner.projectDir, 'build/forge-pack-test/web/build.log').getText('UTF-8').contains('--offline')

        where:
        where                                  | argument
        'build'                                | '-PfixtureOffline'
        'command line'                         | '--offline-fixtures'
        'build of the plugin, which is itself' | '--offline'
    }

    def "the build of an application that takes longer than it is given is stopped, and is a fixture that could not be built"() {
        given:
        GradleRunner runner = setupTestResourceProject('plugin-forge-pack')
        File project = runner.projectDir.canonicalFile

        when: 'the time given is one in which the fixture beside it is built on a busy machine too'
        long started = System.currentTimeMillis()
        BuildResult result = runner.withArguments('forgePackTest', '-Pfixtures=slow-beta:web', '-PfixtureTimeout=30').buildAndFail()
        long took = System.currentTimeMillis() - started

        then: 'the fixtures beside it are built'
        result.output.contains('was generated into 2 applications, of which 1 could not be built with assemble:')
        result.output.contains('  - slow-beta: the build was not done after 30 seconds and was stopped, with everything it had started')
        result.output.contains('      stand-in wrapper does not end')
        new File(project, 'build/forge-pack-test/report.txt').getText('UTF-8') ==
                'alpha: built\nslow-beta: failed: the build was not done after 30 seconds and was stopped, with everything it had started\n'

        and: 'and the task did not wait for what the wrapper had started, which runs for ten minutes'
        took < 300_000
    }

    // the gradlew.bat of the stand-in leaves nothing behind
    @IgnoreIf({ os.windows })
    def "a process the build of an application leaves behind, which has its output, does not hold the task"() {
        given:
        GradleRunner runner = setupTestResourceProject('plugin-forge-pack')
        File project = runner.projectDir.canonicalFile

        when:
        BuildResult result = executeTask('forgePackTest', ['-Pfixtures=lingering-beta:web'])
        Long left = new File(project, 'build/forge-pack-fixtures/lingering-beta/lingering.pid').text.trim() as Long

        then: 'the task has ended, and that process, which runs for ten minutes, is still there'
        assertTaskSuccess('forgePackTest', result)
        ProcessHandle.of(left).map { ProcessHandle handle -> handle.alive }.orElse(false)

        cleanup:
        if (left != null) {
            ProcessHandle.of(left).ifPresent { ProcessHandle handle -> handle.destroyForcibly() }
        }
    }

    def "a repository of the build is taken for what it is and not for its name, and one that cannot be given to an application is left out and said"() {
        given:
        GradleRunner runner = setupTestResourceProject('plugin-forge-pack')
        File project = runner.projectDir.canonicalFile

        when:
        BuildResult result = executeTask('forgePackTest', ['--info', '-PoddRepositories'])

        then: 'the two with the names of Maven Central and of the local repository keep their addresses'
        new File(project, 'build/forge-pack-fixtures/web/repositories.txt').getText('UTF-8') ==
                "https://mirror.example.org/maven;${new File(project, 'maven-repo').path};mavenCentral();mavenLocal()"

        and: 'the one with a password is said by its host, and the password is nowhere'
        result.output.contains('The repository of the build at secret.example.org has a user or a password in its address, and the ' +
                'applications of forgePackTest do not get it')
        !result.output.contains('hunter2')
        !result.output.contains('alice')
        holding(project, 'hunter2') == []

        and: 'the one that is not https is said by its address'
        result.output.contains('The repository of the build at http://plain.example.org/maven is not one a generated application can be given')

        when: 'the build is run again, with the configuration of the first'
        new File(project, 'src/main/forge/templates/.gitignore').text = 'build\n'
        result = executeTask('forgePackTest', ['--info', '-PoddRepositories'])

        then: 'it is said again'
        result.output.contains('Reusing configuration cache')
        result.output.contains('The repository of the build at secret.example.org has a user or a password')
    }

    def "a repository with a password that the build names for the applications is given to them, and is not shown or kept by the task"() {
        given:
        GradleRunner runner = setupTestResourceProject('plugin-forge-pack')
        File project = runner.projectDir.canonicalFile

        when:
        BuildResult result = executeTask('forgePackTest', ['--info', '-PfixtureSecretRepository'])

        then: 'Forge writes it into the application, as it does for a create command'
        new File(project, 'build/forge-pack-fixtures/web/repositories.txt').getText('UTF-8') == 'https://alice:hunter2@secret.example.org/maven'
        result.output.contains('stand-in fixtures repositories: https://secret.example.org/maven')

        and: 'and nothing else has it: not the output, not the report, not what Gradle keeps of the inputs of the task'
        !result.output.contains('hunter2')
        holding(project, 'hunter2') == ['build/forge-pack-fixtures/plugin/repositories.txt', 'build/forge-pack-fixtures/rest-api-plugin/repositories.txt',
                                        'build/forge-pack-fixtures/rest-api/repositories.txt',
                                        'build/forge-pack-fixtures/web-plugin/repositories.txt', 'build/forge-pack-fixtures/web/repositories.txt']

        when: 'other repositories'
        result = executeTask('forgePackTest', ['-PfixtureRepositories'])

        then: 'are another test'
        result.tasks.find { it.path == ':forgePackTest' }.outcome == TaskOutcome.SUCCESS
    }

    def "the applications resolve from #what"() {
        given:
        GradleRunner runner = setupTestResourceProject('plugin-forge-pack')
        File project = runner.projectDir.canonicalFile

        when: 'the build itself has others in its environment'
        BuildResult result
        try {
            result = runner.withEnvironment(System.getenv() + [GRAILS_REPO_URL: 'https://environment.example.org/maven'])
                    .withArguments(['forgePackTest', '--info', '--stacktrace'] + arguments).build()
        } finally {
            runner.withEnvironment(null)
        }

        then:
        result.output.contains("stand-in fixtures repositories: ${repositories.call(project)}")
        !result.output.contains('stand-in fixtures repositories: https://environment.example.org/maven')

        where:
        what                                                 | arguments                                     || repositories
        'the repositories every application has, by default' | []                                            || { File root -> 'null' }
        'the repositories of the build script of the plugin' | ['-PwithRepository']                          || { File root -> new File(root, 'maven-repo').path }
        'the repositories the build names for them'          | ['-PwithRepository', '-PfixtureRepositories'] || { File root -> 'https://repo.example.org/maven;mavenCentral()' }
        'the local Maven repository first, when asked'       | ['-PwithRepository', '-PfixtureMavenLocal']   || { File root -> 'mavenLocal();' + new File(root, 'maven-repo').path }
    }

    def "a pack is tested with the Forge of the Grails version of the project, unless the build says otherwise"() {
        given:
        GradleRunner runner = setupTestResourceProject('plugin-forge-pack')

        when: 'this build has no repository to take it from'
        BuildResult result = runner.withArguments('forgePackTest', '-PforgeOfGrails').buildAndFail()

        then: 'the build says what was looked for, what for, and how to name another'
        result.output.contains("Execution failed for task ':forgePackTest'")
        result.output.contains('The feature pack in src/main/forge could not be tested: Forge, which tests it, was not found in the repositories of the build')
        result.output.contains("  - org.apache.grails.forge:grails-forge-core:${PROJECT_VERSION}")
        result.output.contains('A pack is tested with the Forge of the Grails version of the project. Add a repository that has it, or name the Forge ' +
                "to test with: dependencies { grailsForgePackTooling 'org.apache.grails.forge:grails-forge-core:<version>' }")

        when: 'the build is run again'
        result = runner.withArguments('forgePackTest', '-PforgeOfGrails').buildAndFail()

        then: 'it looks again, since a repository can have by now what it had not'
        !result.output.contains('Reusing configuration cache')
    }

    def "Forge is not run with a part of its class path"() {
        given:
        GradleRunner runner = setupTestResourceProject('plugin-forge-pack')

        when: 'a module of the tooling has its descriptor in the repository and no jar'
        BuildResult result = runner.withArguments('forgePackTest', '--info', '-PwithRepository', '-PtoolingExtra=org.example:toollib:1.0.0').buildAndFail()

        then:
        result.output.contains('The feature pack in src/main/forge could not be tested: toollib-1.0.0.jar of org.example:toollib:1.0.0 could not be ' +
                'fetched from the repositories of the build, which have its module. Forge is not run with a part of what it needs')
        !result.output.contains('stand-in fixtures of')
    }

    def "a Forge that #failure fails the build and is not taken for one that generated the applications"() {
        given:
        GradleRunner runner = setupTestResourceProject('plugin-forge-pack')
        new File(runner.projectDir, 'src/main/forge/feature.yml') << "stand-in: ${how}\n"

        when:
        BuildResult result = runner.withArguments('forgePackTest').buildAndFail()

        then:
        result.output.contains("The feature pack in src/main/forge could not be tested (Forge ended with ${exit})")
        result.output.contains(said)

        and: 'and nothing was built'
        !new File(runner.projectDir, 'build/forge-pack-test/web').exists()

        where:
        failure                                        | how                   || exit | said
        'says it could not generate'                   | 'fixtures-crash'      || 2    | '  The fixtures of the feature pack could not be generated: stand-in'
        'is ended before it is done'                   | 'fixtures-ended'      || 0    | 'without saying that it had got to its end: a template may have ended the JVM'
        'names other fixtures than it counts'          | 'fixtures-miscounted' || 0    | "so it cannot be told what was generated: 'generated web'"
        'has something else written beside its lines'  | 'fixtures-noisy'      || 0    | "so it cannot be told what was generated: 'written by a template'"
    }

    def "what is not a Forge that generates fixtures is said to be none"() {
        given:
        GradleRunner runner = setupTestResourceProject('plugin-forge-pack')

        when:
        BuildResult result = runner.withArguments('forgePackTest', '-PnotForge').buildAndFail()

        then:
        result.output.contains('The feature pack in src/main/forge could not be tested (Forge ended with 1). What the grailsForgePackTooling ' +
                'configuration resolved has no org.apache.grails.forge.core.pack.PackFixtures: it is a Forge older than this version of the ' +
                'Grails Gradle plugin expects')
    }

    def "a fixture Forge could not read from its line fails the build before anything is started: #problem"() {
        given:
        GradleRunner runner = setupTestResourceProject('plugin-forge-pack')

        when:
        BuildResult result = runner.withArguments(['forgePackTest', '--info'] + arguments).buildAndFail()

        then:
        result.output.contains(message)
        !result.output.contains('stand-in fixtures of')

        where:
        problem                         | arguments                            || message
        'a name that is not one'        | ['-Pfixtures=Not_A_Name:web']        || "The name of a fixture of forgePackTest is lower case letters, digits and single hyphens, starts with a letter and has at most 60 characters: 'Not_A_Name' is not"
        'a type with a space'           | ['-Pfixtures=beta:web plugin']       || 'The fixture beta of forgePackTest has a value for applicationType that is empty or has a space, a comma, an equals sign or a control character'
        'an option there is not'        | ['-PfixtureOption=database']         || "The fixture odd of forgePackTest has the option 'database': a fixture has data, servlet, reloading, jdk"
        'an option with a space'        | ['-PfixtureOption=data']             || 'The fixture odd of forgePackTest has a value for data that is empty or has a space, a comma, an equals sign or a control character'
    }

    @IgnoreIf({ os.windows })
    def "what Gradle says went wrong is what is shown of a log that says it, and not the stack trace after it"() {
        given:
        GradleRunner runner = setupTestResourceProject('plugin-forge-pack')

        when:
        BuildResult result = runner.withArguments('forgePackTest', '-Pfixtures=failing-beta:web').buildAndFail()
        List<String> shown = result.output.readLines().findAll { it.contains('      ') && !it.contains(' is in ') }.collect { it.trim() }

        then:
        result.output.contains('  - failing-beta: task :generateI18nDescriptor failed (Gradle ended with 1)')
        shown == ['* What went wrong:', "Execution failed for task ':generateI18nDescriptor'.",
                  '> The plugin ships message bundles outside its own namespace']
    }

    @IgnoreIf({ os.windows })
    def "the end of a long log is what is shown, and every line of it in bounds"() {
        given:
        GradleRunner runner = setupTestResourceProject('plugin-forge-pack')

        when:
        BuildResult result = runner.withArguments('forgePackTest', '-Pfixtures=broken-beta:web').buildAndFail()
        List<String> shown = result.output.readLines().findAll { it.contains('      ') && !it.contains(' is in ') }.collect { it.trim() }

        then:
        shown.size() == 30
        shown.first() == 'compiler line 13'
        shown.last() == 'BUILD FAILED in 1s'
        shown.count { it == '0' * 300 + '...' } == 1
    }

    // the files of the project, but the build script, that have the text in them, whatever else they hold
    private static List<String> holding(File project, String text) {
        List<String> files = []
        project.eachFileRecurse(groovy.io.FileType.FILES) { File file ->
            if (file.name != 'build.gradle' && new String(file.bytes, 'ISO-8859-1').contains(text)) {
                files << project.toPath().relativize(file.toPath()).join('/')
            }
        }
        return files.sort()
    }

    // what the wrapper of a fixture was started with, the Gradle user home of this build said as <home>
    private static String wrapperArguments(File project, String fixture) {
        String arguments = new File(project, "build/forge-pack-test/${fixture}/build.log").getText('UTF-8').readLines()
                .find { it.startsWith('stand-in wrapper arguments: ') }.substring('stand-in wrapper arguments: '.length())
        String home = (arguments =~ /--gradle-user-home (\S+)/)[0][1]
        assert new File(home).canonicalFile == new File(project.parentFile, '.gradle').canonicalFile
        return arguments.replace(home, '<home>')
    }
}
