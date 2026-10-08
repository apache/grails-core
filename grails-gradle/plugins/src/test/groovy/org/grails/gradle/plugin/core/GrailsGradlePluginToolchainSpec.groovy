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

/**
 * Tests that {@link GrailsGradlePlugin} propagates the project's Java toolchain
 * to JavaExec tasks via {@code javaLauncher.convention()}.
 *
 * <p>Without the fix, JavaExec tasks spawned by Grails (dbm-* migration
 * commands, console, shell, application context commands) use the JDK
 * running Gradle instead of the project's configured toolchain.</p>
 *
 * @since 7.0.8
 * @see GrailsGradlePlugin#configureToolchainForForkTasks
 */
class GrailsGradlePluginToolchainSpec extends GradleSpecification {

    // ----------------------------------------------------------------
    // Toolchain propagation
    // ----------------------------------------------------------------

    def "JavaExec tasks inherit project toolchain"() {
        given:
        setupTestResourceProject('toolchain-javaexec')

        when:
        def result = executeTask('checkToolchain')

        then:
        result.output.contains("TOOLCHAIN_VERSION=${CURRENT_JDK}")
    }

    def "Test tasks inherit project toolchain"() {
        given:
        setupTestResourceProject('toolchain-test')

        when:
        def result = executeTask('checkTestToolchain')

        then:
        result.output.contains("TEST_TOOLCHAIN_VERSION=${CURRENT_JDK}")
    }

    def "ApplicationContextCommandTask inherits toolchain via grails-web plugin"() {
        given:
        setupTestResourceProject('toolchain-command')

        when:
        def result = executeTask('checkCommandToolchain')

        then:
        result.output.contains("CMD_TOOLCHAIN_VERSION=${CURRENT_JDK}")
    }

    def "convention allows individual task override via set()"() {
        given:
        setupTestResourceProject('toolchain-override')

        when:
        def result = executeTask('checkOverride')

        then:
        result.output.contains("OVERRIDE_VERSION=${CURRENT_JDK}")
    }

    // ----------------------------------------------------------------
    // Backwards compatibility (no toolchain configured)
    // ----------------------------------------------------------------

    def "JavaExec tasks work without errors when no toolchain configured"() {
        given:
        setupTestResourceProject('no-toolchain-javaexec')

        when:
        def result = executeTask('checkToolchain')

        then:
        result.output.contains('HAS_LAUNCHER=')
    }

    def "GrailsWebGradlePlugin works without errors when no toolchain configured"() {
        given:
        setupTestResourceProject('no-toolchain-web')

        when:
        def result = executeTask('checkNoError')

        then:
        result.output.contains('WEB_PLUGIN_OK=true')
    }

    // ----------------------------------------------------------------
    // Fork settings preservation
    // ----------------------------------------------------------------

    def "configureForkSettings applies system properties and default heap sizes"() {
        given:
        setupTestResourceProject('fork-settings-defaults')

        when:
        def result = executeTask('inspectSysProps')

        then:
        result.output.contains('HAS_ENV=true')
        result.output.contains('MIN_HEAP=768m')
        result.output.contains('MAX_HEAP=768m')
    }

    def "BootRun tasks enable Spring Boot console colors"() {
        given:
        setupTestResourceProject('bootrun-console-ansi')

        when:
        def result = executeTask('inspectBootRunSysProps')

        then:
        result.output.contains('CONSOLE_AVAILABLE=true')
    }

    def "BootRun hands startup progress settings given as Gradle properties to the application"() {
        given:
        setupTestResourceProject('bootrun-startup-progress')

        when:
        def result = executeTask('inspectBootRunStartupProgress',
                ['-Pgrails.startup.progress.openBrowser', '-Pgrails.startup.progress.browserCommand=firefox,--new-window'])

        then: 'each reaches the application under its own name, a bare one as true'
        result.output.contains('STARTUP_PROGRESS_ARG -Dgrails.startup.progress.openBrowser=true')
        result.output.contains('STARTUP_PROGRESS_ARG -Dgrails.startup.progress.browserCommand=firefox,--new-window')
    }

    def "BootRun hands no startup progress settings to the application when none are given"() {
        given:
        setupTestResourceProject('bootrun-startup-progress')

        when:
        def result = executeTask('inspectBootRunStartupProgress')

        then:
        !result.output.contains('STARTUP_PROGRESS_ARG')
    }

    def "custom heap sizes are not overridden by fork settings"() {
        given:
        setupTestResourceProject('fork-settings-custom')

        when:
        def result = executeTask('inspectHeap')

        then:
        result.output.contains('MIN_HEAP=512m')
        result.output.contains('MAX_HEAP=2g')
    }

    def "bootRun supplies the default run-app PID file under the project build directory"() {
        given:
        def runner = setupTestResourceProject('boot-run-pid')

        when:
        def result = executeTask('inspectBootRunPid')

        then:
        pidFileFromOutput(result.output)?.canonicalFile ==
                new File(runner.projectDir, "build${File.separator}run-app.pid").canonicalFile
    }

    def "bootRun supplies the default run-app PID file for a grails-web application"() {
        given:
        def runner = setupTestResourceProject('boot-run-pid-web')

        when:
        def result = executeTask('inspectBootRunPid')

        then:
        pidFileFromOutput(result.output)?.canonicalFile ==
                new File(runner.projectDir, "build${File.separator}run-app.pid").canonicalFile
    }

    def "bootRun ignores a CLI supplied run-app PID file and uses the hard-coded location"() {
        given:
        def runner = setupTestResourceProject('boot-run-pid')
        File cliPidFile = new File('from-cli.pid').absoluteFile
        String pidFileProperty = "-Dgrails.cli.pid.file=${cliPidFile.absolutePath}".toString()

        when:
        def result = executeTask('inspectBootRunPid', [pidFileProperty])

        then:
        pidFileFromOutput(result.output)?.canonicalFile ==
                new File(runner.projectDir, "build${File.separator}run-app.pid").canonicalFile
        !result.output.contains(cliPidFile.absolutePath)
    }

    def "bootRun tells development reloading where the build compiles its classes and puts its resources"() {
        given:
        setupTestResourceProject('bootrun-classes-dir')

        when:
        def result = executeTask('inspectBootRunClassesDir')

        then:
        result.output.contains('CLASSES_DIR=build/classes/groovy/main')
        result.output.contains('RESOURCES_DIR=build/resources/main')
        result.output.readLines().contains('TARGET_DIR=build')
    }

    def "bootRun with a build directory of its own points reloading at that directory's classes and resources"() {
        given: 'a second instance of one checkout, built where the first one does not run from'
        setupTestResourceProject('bootrun-classes-dir')

        when:
        def result = executeTask('inspectBootRunClassesDir', ['-PownBuildDir=build-parent/build-8070'])

        then: 'relative to the project, as GrailsApp.recompile joins it to the application directory, with / on every platform'
        result.output.contains('CLASSES_DIR=build-parent/build-8070/classes/groovy/main')
        result.output.contains('RESOURCES_DIR=build-parent/build-8070/resources/main')

        and: 'the build directory itself, where development keeps its restart marker, as a path and not just its name'
        result.output.readLines().contains('TARGET_DIR=build-parent/build-8070')
    }

    def "bootRun keeps a grails.project.class.dir that the build sets on the task itself"() {
        given:
        setupTestResourceProject('bootrun-classes-dir')

        when:
        def result = executeTask('inspectBootRunClassesDir', ['-PownClassDir=user/chosen/dir'])

        then:
        result.output.contains('CLASSES_DIR=user/chosen/dir')

        and: 'the resources directory, which the build did not set, is still passed'
        result.output.contains('RESOURCES_DIR=build/resources/main')
    }

    def "bootRun with a build directory outside the project passes a path that climbs out of it"() {
        given:
        setupTestResourceProject('bootrun-classes-dir')

        when:
        def result = executeTask('inspectBootRunClassesDir', ['-PownBuildDir=../outside-build'])

        then:
        result.output.contains('CLASSES_DIR=../outside-build/classes/groovy/main')
        result.output.contains('RESOURCES_DIR=../outside-build/resources/main')
        result.output.readLines().contains('TARGET_DIR=../outside-build')
    }

    private static File pidFileFromOutput(String output) {
        String line = output.readLines().find { it.startsWith('PID_FILE=') }
        line ? new File(line.substring('PID_FILE='.length())) : null
    }
}
