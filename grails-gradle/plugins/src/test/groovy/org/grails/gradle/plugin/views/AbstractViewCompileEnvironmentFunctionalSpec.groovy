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
package org.grails.gradle.plugin.views

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome

import org.grails.gradle.plugin.core.GradleSpecification

/**
 * Covers the Grails environment a view compile task compiles for, for each kind of view compiler.
 *
 * <p>The forked compiler reads the application's configuration for one environment, so the
 * environment is part of what the views are. The task states it rather than leaving the compiler
 * to whatever it inherits, compiles from an empty destination so that nothing of an earlier build
 * is left in it, and the build cache keeps views compiled for one environment away from a build for
 * another. The compiler is a stand-in that records what it was started with, and keeps an output
 * it finds, as the real page compiler keeps a page whose class is newer than its source.</p>
 *
 * @since 7.0.18
 */
abstract class AbstractViewCompileEnvironmentFunctionalSpec extends GradleSpecification {

    protected GradleRunner runner
    protected File checkout

    /** The test project under {@code src/test/resources/test-projects} that applies the plugin. */
    abstract String getProjectName()

    /** The task of the plugin that compiles the views. */
    abstract String getTaskName()

    /** Where the task writes, relative to the project. */
    abstract String getDestination()

    /** The class resource of the stand-in compiler, which the project puts on the compiler's classpath. */
    abstract String getStandIn()

    /**
     * Writes a view named {@code name} for the task to compile.
     *
     * @return the view
     */
    abstract File view(File checkout, String name)

    void cleanup() {
        runner?.withEnvironment(null)
    }

    def "views are compiled for development unless the build is told otherwise"() {
        given:
        projectWithViews()

        when:
        compileViews()

        then:
        compiled().grailsEnvironment == 'development'
    }

    def "the environment variable of the build selects the environment"() {
        given:
        projectWithViews()

        when:
        compileViews([GRAILS_ENV: 'production'])

        then:
        compiled().grailsEnvironment == 'production'
    }

    def "the system property given to Gradle outranks the variable, and a short name is expanded"() {
        given:
        projectWithViews()

        when:
        compileViews([GRAILS_ENV: 'development'], ['-Dgrails.env=prod'])

        then: 'the compiler is told, not left to what it inherits'
        compiled().grailsEnvironment == 'production'
        compiled().inheritedEnvironment == 'development'
    }

    def "a blank system property leaves the choice to the variable, as the application does"() {
        given:
        projectWithViews()

        when:
        compileViews([GRAILS_ENV: 'production'], ['-Dgrails.env='])

        then:
        compiled().grailsEnvironment == 'production'
    }

    def "a build can state the environment on the task, by any name Grails accepts for it"() {
        given:
        projectWithViews()
        configureTask("grailsEnvironment = ' Production '")

        when:
        compileViews([GRAILS_ENV: 'development'])

        then:
        compiled().grailsEnvironment == 'production'
    }

    def "a blank environment stated on the task fails the build, before anything is removed"() {
        given:
        projectWithViews()
        compileViews()
        configureTask("grailsEnvironment = ' '")

        when:
        BuildResult result = failToCompileViews([GRAILS_ENV: 'production'])

        then:
        result.output.contains("The grailsEnvironment of :${taskName} is blank")
        compiled().grailsEnvironment == 'development'
    }

    def "a name the application would take for a custom environment is kept as given"() {
        given:
        projectWithViews()
        configureTask("grailsEnvironment = '${stated}'")

        when:
        compileViews()

        then:
        compiled().grailsEnvironment == compiledFor

        where:
        stated        | compiledFor
        'PROD'        | 'PROD'
        'custom'      | 'custom'
        'DEVELOPMENT' | 'development'
    }

    def "the environment is read once, so that what is compiled is what is keyed"() {
        given:
        projectWithViews()
        configureTask("doFirst { grailsEnvironment = 'uat' }")

        when: 'the build runs without the configuration cache, which restores the property as a plain value the guard no longer covers'
        BuildResult result = failToCompileViews([:], ['--no-configuration-cache'])

        then:
        result.output.contains('is final and cannot be changed any further')
    }

    def "an environment a build gave the compiler as a JVM argument is kept, the last where there are several"() {
        given:
        projectWithViews()
        configureTask("compileOptions.forkOptions.jvmArgs = ['-Dgrails.env=dev', '-Dgrails.env=uat']")

        when:
        compileViews([GRAILS_ENV: 'development'], ['-Dgrails.env=prod'])

        then:
        compiled().grailsEnvironment == 'uat'
    }

    def "an environment stated on the task outranks the one its JVM arguments give the compiler"() {
        given:
        projectWithViews()
        configureTask("compileOptions.forkOptions.jvmArgs = ['-Dgrails.env=uat']; grailsEnvironment = 'production'")

        when:
        compileViews()

        then:
        compiled().grailsEnvironment == 'production'
    }

    def "a JVM argument added as a GString, which the fork options' list does not keep out, is read the same"() {
        given: 'added to the list, since Gradle refuses a GString given to the setter'
        projectWithViews()
        configureTask('''compileOptions.forkOptions.jvmArgs = []; compileOptions.forkOptions.jvmArgs.add("-Dgrails.env=${'uat'}")''')

        when:
        compileViews([GRAILS_ENV: 'development'])

        then:
        compiled().grailsEnvironment == 'uat'
    }

    def "a destination that is not a directory of the task's own within the build directory is refused rather than emptied"() {
        given:
        projectWithViews()
        File kept = new File(checkout, 'elsewhere/kept.txt')
        kept.parentFile.mkdirs()
        kept.text = 'not the task\'s to remove'
        configureTask("destinationDirectory = ${destination}")

        when:
        BuildResult result = failToCompileViews()

        then:
        result.output.contains("The destination of :${taskName}, ")
        result.output.contains(refusedAs)
        kept.exists()

        where:
        destination                                        | refusedAs
        "layout.projectDirectory.dir('elsewhere')"         | 'is not a directory within the build directory'
        'layout.buildDirectory'                            | 'is not a directory within the build directory'
        "layout.buildDirectory.dir('classes/groovy/main')" | 'is on the classpath the views are compiled against'
    }

    def "a JVM argument that names the property without a value does not reach the compiler either"() {
        given:
        projectWithViews()
        configureTask("compileOptions.forkOptions.jvmArgs = ['-Dgrails.env']")

        when: 'the build is told production, and the compiler would fall back to development if the argument blanked the property'
        compileViews([GRAILS_ENV: 'development'], ['-Dgrails.env=prod'])

        then:
        compiled().grailsEnvironment == 'production'
    }

    def "an environment change compiles every view again, from an empty destination"() {
        given:
        projectWithViews()
        cached()

        when: 'the environment changes between two builds with no clean in between'
        compileViews([GRAILS_ENV: 'development'], ['--build-cache'])
        BuildResult production = compileViews([GRAILS_ENV: 'production'], ['--build-cache'])

        then: 'the compiler finds nothing of the earlier build to keep'
        production.task(":${taskName}").outcome == TaskOutcome.SUCCESS
        lastRun().keptEarlier == 'false'
        compiled().grailsEnvironment == 'production'

        when: 'a clean build for production is restored from the cache'
        executeTask('clean')
        BuildResult restored = compileViews([GRAILS_ENV: 'production'], ['--build-cache'])

        then: 'it gets what production compiled, not what was left of development'
        restored.task(":${taskName}").outcome == TaskOutcome.FROM_CACHE
        compiled().grailsEnvironment == 'production'
    }

    def "a change to the views alone compiles from an empty destination as well"() {
        given:
        projectWithViews()
        File other = view(checkout, 'other')
        compileViews()

        when: 'a view is edited'
        other << '\n'
        BuildResult edited = compileViews()

        then:
        edited.task(":${taskName}").outcome == TaskOutcome.SUCCESS
        lastRun().keptEarlier == 'false'

        when: 'a view is removed'
        other.delete()
        BuildResult removed = compileViews()

        then:
        removed.task(":${taskName}").outcome == TaskOutcome.SUCCESS
        lastRun().keptEarlier == 'false'
    }

    def "views compiled for one environment are not restored into a build for another"() {
        given:
        projectWithViews()
        cached()

        when:
        BuildResult development = compileViews([GRAILS_ENV: 'development'], ['--build-cache'])
        executeTask('clean')
        BuildResult production = compileViews([GRAILS_ENV: 'production'], ['--build-cache'])
        executeTask('clean')
        BuildResult developmentAgain = compileViews([GRAILS_ENV: 'development'], ['--build-cache'])

        then: 'each environment compiles its own views'
        development.task(":${taskName}").outcome == TaskOutcome.SUCCESS
        production.task(":${taskName}").outcome == TaskOutcome.SUCCESS

        and: 'and gets them back'
        developmentAgain.task(":${taskName}").outcome == TaskOutcome.FROM_CACHE
        compiled().grailsEnvironment == 'development'
    }

    protected void projectWithViews() {
        runner = setupTestResourceProject(projectName)
        checkout = runner.projectDir
        // the stand-in, under the real compiler's name, and the recorder it delegates to
        [standIn, 'org/grails/gradle/plugin/views/RecordingForkedCompiler.class'].each { String resource ->
            File compiler = new File(checkout, "compiler/${resource}")
            compiler.parentFile.mkdirs()
            compiler.bytes = getClass().classLoader.getResource(resource).bytes
        }
        view(checkout, 'index')
    }

    /** Configures the compile task in the build script, with {@code configuration} as its body. */
    protected void configureTask(String configuration) {
        new File(checkout, 'build.gradle') << "\ntasks.named('${taskName}') { ${configuration} }\n"
    }

    /** Keeps the project's build cache in a directory of its own, so that only this test's builds fill it. */
    protected void cached() {
        File cache = new File(checkout.parentFile, "${projectName}-build-cache")
        new File(checkout, 'settings.gradle') << """
            buildCache { local { directory = file('${cache.absolutePath.replace('\\', '/')}') } }
        """
    }

    /** Compiles the views in a build whose environment carries {@code variables} and no {@code GRAILS_ENV} otherwise. */
    protected BuildResult compileViews(Map<String, String> variables = [:], List<String> arguments = []) {
        environment(variables)
        executeTask(taskName, arguments)
    }

    protected BuildResult failToCompileViews(Map<String, String> variables = [:], List<String> arguments = []) {
        environment(variables)
        runner.withArguments([taskName, '--stacktrace'] + arguments).forwardOutput().buildAndFail()
    }

    protected void environment(Map<String, String> variables) {
        Map<String, String> environment = new HashMap<>(System.getenv())
        environment.remove('GRAILS_ENV')
        environment.putAll(variables)
        runner.withEnvironment(environment)
    }

    /** What the compiler recorded when it wrote the output: the environment it was told, and the one it inherited. */
    protected Map<String, String> compiled() {
        new File(checkout, "${destination}/compiler.txt").readLines().collectEntries { String line ->
            line.split('=', 2) as List
        }
    }

    /** What the compiler found on its last run: the environment it was told, and whether an earlier output was there to keep. */
    protected Map<String, String> lastRun() {
        new File(checkout, "${destination}/runs.txt").readLines().last().split(' ').collectEntries { String pair ->
            pair.split('=', 2) as List
        }
    }
}
