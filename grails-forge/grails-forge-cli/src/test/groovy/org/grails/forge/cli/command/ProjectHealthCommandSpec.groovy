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
package org.grails.forge.cli.command

import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.jar.Attributes
import java.util.jar.JarOutputStream
import java.util.jar.Manifest
import groovy.json.JsonSlurper
import org.apache.grails.forge.cli.health.HealthContext
import org.apache.grails.forge.core.state.ProjectState
import org.grails.forge.cli.CommandSpec
import org.grails.forge.cli.ForgeCli
import org.grails.forge.util.VersionInfo
import spock.lang.Shared
import spock.lang.Unroll

/**
 * {@code project health}: what the files of a project show of its state, read as text, for each
 * check and each of its findings, through the command as it is typed: a project that {@code create-app}
 * has just generated, changed by each row in one way, an application the Grails shell created, the
 * JSON document, the exit codes with and without {@code --strict}, and the places it runs or not.
 */
class ProjectHealthCommandSpec extends CommandSpec {

    /** The checks, in the order they run. */
    static final List<String> CHECKS = ['grails-version', 'gradle-wrapper', 'java-target', 'sdkmanrc', 'removed-dependency', 'deprecated-dependency', 'record',
                                        'grails-wrapper', 'git-files', 'command-catalog', 'leftover', 'agent-skills']

    /**
     * What a project that create-app has just generated is advised, and nothing else: the build has not run, so the commands of the application
     * are not listed yet. Its Gradle wrapper names the SHA-256 of its distribution, so it is not advised to.
     */
    static final List<String> ADVICE_OF_A_NEW_PROJECT = ['command-catalog.missing']

    /** Every finding of every check, by its id: what the reference page of the command lists, and what a tool goes by. */
    static final List<String> IDS = [
            'grails-version.earlier', 'grails-version.newer', 'grails-version.release',
            'gradle-wrapper.missing', 'gradle-wrapper.version', 'gradle-wrapper.older', 'gradle-wrapper.checksum', 'gradle-wrapper.checksum-differs',
            'java-target.release', 'java-target.record',
            'sdkmanrc.java', 'sdkmanrc.grails', 'sdkmanrc.gradle', 'sdkmanrc.groovy',
            'removed-dependency.profile', 'removed-dependency.shell', 'removed-dependency.profile-plugin', 'removed-dependency.jline',
            'deprecated-dependency.gpars', 'deprecated-dependency.gpars-and-groovy', 'deprecated-dependency.rxjava',
            'record.missing', 'record.unreadable', 'record.baseline', 'record.earlier-generator', 'record.version-drift', 'record.interrupted',
            'grails-wrapper.version', 'grails-wrapper.outdated', 'grails-wrapper.leftover',
            'git-files.record-ignored', 'git-files.state-ignored', 'git-files.wrapper-line-endings',
            'command-catalog.missing', 'command-catalog.refused', 'command-catalog.stale',
            'leftover.settings-groovy', 'leftover.earlier-record', 'leftover.earlier-record-differs', 'leftover.profile-config', 'leftover.preferred-shell',
            'agent-skills.upgrade-skill', 'agent-skills.not-extracted'].asImmutable()

    static final String TOOLCHAIN_25 = 'java {\n    toolchain {\n        languageVersion = JavaLanguageVersion.of(25)\n    }\n}\n'
    static final String TOOLCHAIN_21 = 'java {\n    toolchain {\n        languageVersion = JavaLanguageVersion.of(21)\n    }\n}\n'

    /** The Gradle create-app generates applications with, the SHA-256 Gradle publishes for its binary distribution, and the earliest Gradle of Grails. */
    static final String GRADLE = VersionInfo.gradleVersion
    static final String GRADLE_SHA256 = VersionInfo.gradleDistributionSha256Sum
    static final String GRADLE_MINIMUM = VersionInfo.minimumGradleVersion
    static final String WRAPPER_PROPERTIES = 'gradle/wrapper/gradle-wrapper.properties'

    /** What moves the wrapper to that Gradle with its SHA-256: the wrapper of the project, and an installed Gradle where no wrapper runs. */
    static final String WRAPPER_TO_GENERATED = "./gradlew wrapper --gradle-version ${GRADLE} --gradle-distribution-sha256-sum ${GRADLE_SHA256}".toString()
    static final String INSTALLED_TO_GENERATED = "gradle wrapper --gradle-version ${GRADLE} --gradle-distribution-sha256-sum ${GRADLE_SHA256}".toString()

    @Shared
    File template

    PrintStream originalOut
    PrintStream originalErr
    ByteArrayOutputStream out = new ByteArrayOutputStream()
    ByteArrayOutputStream err = new ByteArrayOutputStream()
    File home
    String previousHome

    void setup() {
        originalOut = System.out
        originalErr = System.err
        System.setOut(new PrintStream(out, true))
        System.setErr(new PrintStream(err, true))
        // ~/.grails/settings.groovy is looked for there, and a change that was interrupted
        home = Files.createTempDirectory('grailshealthhome').toFile()
        previousHome = System.getProperty('user.home')
        System.setProperty('user.home', home.absolutePath)
        HealthContext.setEnvironment([:])
        if (template == null) {
            template = Files.createTempDirectory('grailshealthtemplate').toFile()
            assert create(template, 'create-app', 'com.example.shop') == 0
            out.reset()
            err.reset()
        }
    }

    void cleanup() {
        System.setOut(originalOut)
        System.setErr(originalErr)
        System.setProperty('user.home', previousHome)
        HealthContext.setEnvironment(null)
        home.deleteDir()
    }

    void cleanupSpec() {
        template?.deleteDir()
    }

    @Unroll
    void "a project #command has just generated has no error and no warning, and every check runs"() {
        given:
        assert create(dir, command, 'com.example.shop') == 0

        when:
        int exit = run(new File(dir, 'shop'), 'project', 'health', '--json', '--strict')
        Map document = parsed()

        then: 'not even with --strict does it fail'
        exit == 0
        document.passed == true
        document.counts.error == 0
        document.counts.warning == 0
        document.counts.skipped == 0

        and: 'the advice it has is what a project that was never built has, and nothing else'
        ids(document, 'advice') == ADVICE_OF_A_NEW_PROJECT

        and: 'each check reports, and those that found nothing say so: every other check, once, as the tour of the guide shows'
        (document.findings as List<Map>)*.check.unique() as Set == CHECKS as Set
        (document.findings as List<Map>).findAll { it.level == 'ok' }.every { it.id == "${it.check}.ok" && it.fix == null }
        document.counts.ok == CHECKS.size() - ADVICE_OF_A_NEW_PROJECT.size()
        document.counts.advice == ADVICE_OF_A_NEW_PROJECT.size()

        where:
        command << ['create-app', 'create-restapi', 'create-plugin', 'create-web-plugin', 'create-rest-api-plugin']
    }

    @Unroll
    void "#id is #level: #what"() {
        given:
        File project = copyOfTemplate()
        // what a row does is done in this feature, with its outputs
        Closure changed = (Closure) change.clone()
        changed.delegate = this
        changed.resolveStrategy = Closure.DELEGATE_FIRST
        changed.call(project, home)
        out.reset()
        err.reset()
        assert id in IDS || id.endsWith('.ok') || id.endsWith('.skipped')

        when:
        int exit = run(project, 'project', 'health', '--json')
        Map document = parsed()
        Map finding = (document.findings as List<Map>).find { it.id == id }

        then: 'the finding is there, at its level, with what to do'
        finding != null
        finding.level == level
        finding.check == id.substring(0, id.indexOf('.'))
        finding.message
        (finding.fix != null) == (level in ['error', 'warning', 'advice'])
        finding.origin == 'built-in'

        and: 'the command ends with 1 only for an error'
        exit == (document.counts.error > 0 ? 1 : 0)
        document.passed == (exit == 0)

        when: 'with --strict'
        out.reset()
        exit = run(project, 'project', 'health', '--json', '--strict')
        document = parsed()

        then: 'a warning fails it as well, and advice never does'
        exit == (document.counts.error + document.counts.warning > 0 ? 1 : 0)

        when: 'the same in words, with -v for what was found to be in order'
        out.reset()
        err.reset()
        run(project, 'project', 'health', '-v')
        List<String> lines = out.toString().readLines()
        String label = [error: 'Error', warning: 'Warning', advice: 'Advice', skipped: 'Skipped', ok: 'OK'][level]

        then: 'one line for it, with its level, its id and what to do'
        lines.any { String line -> line.startsWith("| ${label.padRight(7)} ${id} ") && (level in ['ok', 'skipped'] || line.contains('. Fix: ')) }

        where:
        id                                     | level     | what                                                   | change
        'grails-version.earlier'               | 'warning' | 'the project is on Grails 8'                           | { File p, File h -> grailsVersion(p, '8.0.1') }
        'grails-version.newer'                 | 'warning' | 'the project is on a later major version'              | { File p, File h -> grailsVersion(p, '10.0.0') }
        'grails-version.release'               | 'advice'  | 'the project is on another release of Grails 9'        | { File p, File h -> grailsVersion(p, '9.0.99') }
        'grails-version.skipped'               | 'skipped' | 'gradle.properties names no grailsVersion'             | { File p, File h -> without(p, 'gradle.properties', ~/grailsVersion=.*\n/) }
        'gradle-wrapper.missing'               | 'error'   | 'there is no Gradle wrapper'                           | { File p, File h -> new File(p, 'gradlew').delete(); new File(p, 'gradlew.bat').delete() }
        'gradle-wrapper.missing'               | 'error'   | 'the wrapper names no Gradle'                          | { File p, File h -> new File(p, 'gradle/wrapper/gradle-wrapper.properties').delete() }
        'gradle-wrapper.version'               | 'error'   | 'the wrapper is of Gradle 8'                           | { File p, File h -> distribution(p, 'https\\://services.gradle.org/distributions/gradle-8.14.3-bin.zip') }
        'gradle-wrapper.older'                 | 'advice'  | 'the wrapper is older than create-app generates'       | { File p, File h -> distribution(p, "https\\://services.gradle.org/distributions/gradle-${VersionInfo.minimumGradleVersion}-bin.zip") }
        'gradle-wrapper.checksum'              | 'advice'  | 'the wrapper names no SHA-256 of its distribution'     | { File p, File h -> without(p, 'gradle/wrapper/gradle-wrapper.properties', ~/distributionSha256Sum=.*\n/) }
        'gradle-wrapper.checksum-differs'      | 'warning' | 'the wrapper names another SHA-256 for its distribution' | { File p, File h -> checksum(p, '0' * 64) }
        'gradle-wrapper.checksum-differs'      | 'warning' | 'the SHA-256 is in capitals, which the wrapper refuses' | { File p, File h -> checksum(p, VersionInfo.gradleDistributionSha256Sum.toUpperCase()) }
        'gradle-wrapper.ok'                    | 'ok'      | 'the wrapper is the one create-app generates'          | { File p, File h -> }
        'gradle-wrapper.ok'                    | 'ok'      | 'a distribution of a mirror is not compared'           | { File p, File h -> distribution(p, "https\\://repo.example.com/gradle/gradle-${VersionInfo.gradleVersion}-bin.zip"); checksum(p, '0' * 64) }
        'gradle-wrapper.ok'                    | 'ok'      | 'the complete distribution has a checksum of its own'  | { File p, File h -> distribution(p, "https\\://services.gradle.org/distributions/gradle-${VersionInfo.gradleVersion}-all.zip"); checksum(p, '0' * 64) }
        'gradle-wrapper.skipped'               | 'skipped' | 'the wrapper names a distribution of its own'          | { File p, File h -> distribution(p, 'https\\://repo.example.com/dists/gradle.zip') }
        'java-target.release'                  | 'warning' | 'the build compiles for Java 17'                       | { File p, File h -> replace(p, 'build.gradle', ~/compileJava\.options\.release = \d+/, 'compileJava.options.release = 17') }
        'java-target.release'                  | 'warning' | 'the toolchain is of Java 17'                           | { File p, File h -> append(p, 'build.gradle', 'java {\n    toolchain {\n        languageVersion = JavaLanguageVersion.of(17)\n    }\n}\n') }
        'java-target.release'                  | 'warning' | 'the build is for Java 1.8'                            | { File p, File h -> append(p, 'build.gradle', 'java {\n    sourceCompatibility = JavaVersion.VERSION_1_8\n}\n') }
        'java-target.release'                  | 'warning' | 'the release is set in a block of the options'         | { File p, File h -> append(p, 'build.gradle', 'tasks.withType(JavaCompile).configureEach {\n    options.with {\n        release = 17\n    }\n}\n') }
        'java-target.release'                  | 'warning' | 'a build in Kotlin has a toolchain of Java 17'         | { File p, File h -> kotlinBuild(p); append(p, 'build.gradle.kts', 'kotlin {\n    jvmToolchain(17)\n}\n') }
        'java-target.ok'                       | 'ok'      | 'the build compiles for Java 21'                       | { File p, File h -> }
        'java-target.ok'                       | 'ok'      | 'what is set for other projects is not counted'        | { File p, File h -> append(p, 'build.gradle', 'subprojects {\n    tasks.withType(JavaCompile) {\n        options.release = 17\n    }\n}\n') }
        'java-target.ok'                       | 'ok'      | 'a setting that is commented out is not counted'       | { File p, File h -> append(p, 'build.gradle', '// compileJava.options.release = 17\n/* sourceCompatibility = 1.8 */\n') }
        'java-target.skipped'                  | 'skipped' | 'the build names no Java and applies another file'     | { File p, File h -> replace(p, 'build.gradle', ~/compileJava\.options\.release = \d+/, "apply from: 'gradle/java.gradle'") }
        'java-target.record'                   | 'advice'  | 'the record migrate wrote is for another Java'         | { File p, File h -> migrated(p); replace(p, 'build.gradle', ~/compileJava\.options\.release = \d+/, 'compileJava.options.release = 25') }
        'sdkmanrc.java'                        | 'warning' | '.sdkmanrc selects Java 17'                            | { File p, File h -> new File(p, '.sdkmanrc').text = 'java=17.0.12-tem\n' }
        'sdkmanrc.grails'                      | 'warning' | '.sdkmanrc selects another Grails'                     | { File p, File h -> new File(p, '.sdkmanrc').text = '# the versions\ngrails=8.0.1\n' }
        'sdkmanrc.gradle'                      | 'advice'  | '.sdkmanrc selects Gradle 8'                           | { File p, File h -> new File(p, '.sdkmanrc').text = 'gradle=8.14.3\n' }
        'sdkmanrc.groovy'                      | 'advice'  | '.sdkmanrc selects Groovy 4'                           | { File p, File h -> new File(p, '.sdkmanrc').text = 'groovy=4.0.27\n' }
        'sdkmanrc.ok'                          | 'ok'      | '.sdkmanrc selects what the project is built with'     | { File p, File h -> new File(p, '.sdkmanrc').text = "java=21.0.12-librca\ngrails=${VersionInfo.grailsVersion}\n" }
        'removed-dependency.profile'           | 'error'   | 'the build declares a profile'                         | { File p, File h -> dependency(p, 'profile "org.apache.grails.profiles:web"') }
        'removed-dependency.shell'             | 'error'   | 'the build declares the Grails shell'                  | { File p, File h -> dependency(p, 'implementation "org.apache.grails:grails-shell-cli"') }
        'removed-dependency.shell'             | 'error'   | 'the build declares a profile as a dependency'         | { File p, File h -> dependency(p, 'implementation "org.apache.grails.profiles:web"') }
        'removed-dependency.profile-plugin'    | 'error'   | 'the build applies the profile plugin'                 | { File p, File h -> append(p, 'build.gradle', 'apply plugin: "org.apache.grails.gradle.grails-profile"\n') }
        'removed-dependency.jline'             | 'error'   | 'the build declares the JNA terminal of JLine'         | { File p, File h -> dependency(p, 'runtimeOnly "org.jline:jline-terminal-jna"') }
        'removed-dependency.skipped'           | 'skipped' | 'the build names a dependency of a version catalog'    | { File p, File h -> dependency(p, 'implementation libs.acme.audit') }
        'deprecated-dependency.gpars'          | 'warning' | 'the build declares the GPars promises'                | { File p, File h -> dependency(p, 'runtimeOnly "org.apache.grails.async:grails-async-gpars"') }
        'deprecated-dependency.gpars-and-groovy' | 'error' | 'the build declares both event buses'                  | { File p, File h -> dependency(p, 'runtimeOnly "org.apache.grails.events:grails-events-gpars"\n    runtimeOnly "org.apache.grails.events:grails-events-groovy-concurrent"') }
        'deprecated-dependency.rxjava'         | 'warning' | 'the build declares RxJava 1'                          | { File p, File h -> dependency(p, 'implementation "io.reactivex:rxjava"') }
        'record.missing'                       | 'warning' | 'the project has no record'                            | { File p, File h -> new File(p, '.grails').deleteDir() }
        'record.unreadable'                    | 'warning' | 'the record was changed by hand'                       | { File p, File h -> append(p, '.grails/project.yml', 'by: hand\n') }
        'record.baseline'                      | 'warning' | 'a file of the baseline was changed'                   | { File p, File h -> append(p, '.grails/baseline/files/build.gradle', '// changed\n') }
        'record.earlier-generator'             | 'warning' | 'an earlier version wrote the record'                  | { File p, File h -> writtenBy(p, '9.0.0-M1', 'a' * 64) }
        'record.version-drift'                 | 'warning' | 'the project was moved to another release by hand'     | { File p, File h -> grailsVersion(p, '9.0.99') }
        'record.interrupted'                   | 'warning' | 'a change did not come to its end'                     | { File p, File h -> new File(p, '.grails/transactions/t0123456789abcdef01234567').mkdirs() }
        'record.ok'                            | 'ok'      | 'the project has the record grails-forge-cli.yml'      | { File p, File h -> new File(p, '.grails').deleteDir(); new File(p, 'grails-forge-cli.yml').text = 'applicationType: web\ndefaultPackage: com.example\n' }
        'grails-wrapper.version'               | 'warning' | 'the wrapper is that of Grails 8'                      | { File p, File h -> wrapperJar(p, '8.0.1') }
        'grails-wrapper.outdated'              | 'advice'  | 'a file of the wrapper is not the one of this version' | { File p, File h -> append(p, 'grailsw', '# changed\n') }
        'grails-wrapper.leftover'              | 'warning' | 'an update of the wrapper left a file beside it'       | { File p, File h -> new File(p, 'grails-wrapper.jar.new').bytes = new File(p, 'grails-wrapper.jar').bytes }
        'grails-wrapper.skipped'               | 'skipped' | 'the jar of the wrapper names no version'              | { File p, File h -> wrapperJar(p, null) }
        'git-files.record-ignored'             | 'warning' | '.gitignore leaves out .grails'                        | { File p, File h -> append(p, '.gitignore', '.grails/\n') }
        'git-files.state-ignored'              | 'advice'  | 'no .gitignore leaves out what is of one machine'      | { File p, File h -> without(p, '.gitignore', ~/\.grails\/(transactions\/|history)\n/); without(p, '.grails/.gitignore', ~/\/(transactions\/|history)\n/) }
        'git-files.wrapper-line-endings'       | 'advice'  | 'there is no .gitattributes'                           | { File p, File h -> new File(p, '.gitattributes').delete() }
        'git-files.ok'                         | 'ok'      | '.grails/.gitignore leaves out what is of one machine' | { File p, File h -> without(p, '.gitignore', ~/\.grails\/(transactions\/|history)\n/) }
        'git-files.ok'                         | 'ok'      | '.gitignore does, and the record of a snapshot not'    | { File p, File h -> without(p, '.grails/.gitignore', ~/\/(transactions\/|history)\n/) }
        'command-catalog.missing'              | 'advice'  | 'the build has not listed the commands'                | { File p, File h -> }
        'command-catalog.refused'              | 'advice'  | 'the list is no file'                                  | { File p, File h -> new File(p, '.grails/cache/command-catalog.json').mkdirs() }
        'command-catalog.stale'                | 'advice'  | 'the build file was changed after the list'            | { File p, File h -> catalog(p); new File(p, 'build.gradle').setLastModified(System.currentTimeMillis() + 60_000) }
        'command-catalog.ok'                   | 'ok'      | 'the build listed the commands'                        | { File p, File h -> catalog(p) }
        'leftover.settings-groovy'             | 'warning' | '~/.grails/settings.groovy is there'                   | { File p, File h -> new File(h, '.grails').mkdirs(); new File(h, '.grails/settings.groovy').text = 'grails { }\n' }
        'leftover.earlier-record'              | 'advice'  | 'grails-forge-cli.yml is beside .grails'               | { File p, File h -> new File(p, 'grails-forge-cli.yml').text = 'applicationType: web\n' }
        'leftover.earlier-record-differs'      | 'warning' | 'grails-forge-cli.yml names another package'           | { File p, File h -> new File(p, 'grails-forge-cli.yml').text = 'applicationType: web\ndefaultPackage: org.other\n' }
        'leftover.profile-config'              | 'advice'  | 'the configuration names a profile'                    | { File p, File h -> append(p, 'grails-app/conf/application.yml', '---\ngrails:\n    profile: web\n') }
        'leftover.preferred-shell'             | 'advice'  | 'GRAILS_PREFERRED_SHELL is set'                        | { File p, File h -> HealthContext.setEnvironment([GRAILS_PREFERRED_SHELL: 'forge']) }
        'agent-skills.upgrade-skill'           | 'advice'  | 'a project of Grails 8 has no upgrade skill'           | { File p, File h -> grailsVersion(p, '8.0.1') }
        'agent-skills.not-extracted'           | 'advice'  | 'the upgrade skill is declared and not extracted'      | { File p, File h -> grailsVersion(p, '8.0.1'); skills(p) }
        'agent-skills.upgrade-skill'           | 'advice'  | 'the plugin is in a comment only'                      | { File p, File h -> grailsVersion(p, '8.0.1'); skills(p); replace(p, 'build.gradle', ~/    id 'com\.skillsjars/, "    // id 'com.skillsjars") }
        'agent-skills.ok'                      | 'ok'      | 'the upgrade skill is extracted'                       | { File p, File h -> grailsVersion(p, '8.0.1'); skills(p); extracted(p, '.agents/skills') }
        'agent-skills.ok'                      | 'ok'      | 'the skill is extracted to the outputDir of the build' | { File p, File h -> grailsVersion(p, '8.0.1'); skills(p); append(p, 'build.gradle', "skillsjars {\n    outputDir = layout.projectDirectory.dir('.ai/skills')\n}\n"); extracted(p, '.ai/skills') }
        'agent-skills.ok'                      | 'ok'      | 'no upgrade is under way'                              | { File p, File h -> }
    }

    @Unroll
    void "#id names the whole command that moves the wrapper to the Gradle create-app generates with, with its SHA-256, or where the SHA-256 is: #what"() {
        given:
        File project = copyOfTemplate()
        Closure changed = (Closure) change.clone()
        changed.delegate = this
        changed.resolveStrategy = Closure.DELEGATE_FIRST
        changed.call(project)
        out.reset()
        err.reset()

        when:
        run(project, 'project', 'health', '--json')
        Map finding = (parsed().findings as List<Map>).find { it.id == id }

        then: 'the fix, and the command of the document, which is the one the fix names'
        finding.fix == fix.toString()
        finding.command == command

        when: 'in words'
        out.reset()
        run(project, 'project', 'health')

        then:
        out.toString().readLines().any { String line -> line.contains(" ${id} ") && line.endsWith(". Fix: ${fix}") }

        where:
        id                                | what                                                         | change                                                                                                                        | fix                                                                                                                                                                                                                                         | command
        'gradle-wrapper.missing'          | 'there is no Gradle wrapper, so an installed Gradle writes it' | { File p -> new File(p, 'gradlew').delete(); new File(p, 'gradlew.bat').delete() }                                          | "Commit the Gradle wrapper (gradlew, gradlew.bat and gradle/wrapper): ${INSTALLED_TO_GENERATED}, run with an installed Gradle ${GRADLE_MINIMUM} or later, writes it"                                                                        | INSTALLED_TO_GENERATED
        'gradle-wrapper.missing'          | 'there are no properties, and ./gradlew runs nothing'        | { File p -> new File(p, WRAPPER_PROPERTIES).delete() }                                                                        | "Write the wrapper again with an installed Gradle ${GRADLE_MINIMUM} or later: ${INSTALLED_TO_GENERATED}; ./gradlew runs no Gradle without a distributionUrl in ${WRAPPER_PROPERTIES}"                                                      | INSTALLED_TO_GENERATED
        'gradle-wrapper.missing'          | 'the properties name no distribution, and ./gradlew runs nothing' | { File p -> without(p, WRAPPER_PROPERTIES, ~/distributionUrl=.*\n/) }                                                    | "Write the wrapper again with an installed Gradle ${GRADLE_MINIMUM} or later: ${INSTALLED_TO_GENERATED}; ./gradlew runs no Gradle without a distributionUrl in ${WRAPPER_PROPERTIES}"                                                      | INSTALLED_TO_GENERATED
        'gradle-wrapper.version'          | 'the wrapper is of Gradle 8, and keeps the SHA-256 of another' | { File p -> distribution(p, 'https\\://services.gradle.org/distributions/gradle-8.14.3-bin.zip') }                          | "Set the distributionUrl of ${WRAPPER_PROPERTIES} to https://services.gradle.org/distributions/gradle-${GRADLE}-bin.zip and its distributionSha256Sum to ${GRADLE_SHA256}, as ${WRAPPER_TO_GENERATED} does, and commit the wrapper" | WRAPPER_TO_GENERATED
        'gradle-wrapper.older'            | 'the wrapper is older than create-app generates'             | { File p -> distribution(p, "https\\://services.gradle.org/distributions/gradle-${GRADLE_MINIMUM}-bin.zip") }               | "${WRAPPER_TO_GENERATED}, and commit the wrapper"                                                                                                                                                                                           | WRAPPER_TO_GENERATED
        'gradle-wrapper.checksum'         | 'the distribution create-app generates with has no SHA-256'  | { File p -> without(p, WRAPPER_PROPERTIES, ~/distributionSha256Sum=.*\n/) }                                                   | "Set distributionSha256Sum to ${GRADLE_SHA256} in ${WRAPPER_PROPERTIES}, the SHA-256 that gradle.org publishes for Gradle ${GRADLE}, as ${WRAPPER_TO_GENERATED} does"                                                                   | WRAPPER_TO_GENERATED
        'gradle-wrapper.checksum'         | 'an earlier Gradle has none, whose SHA-256 the command line does not know' | { File p -> distribution(p, "https\\://services.gradle.org/distributions/gradle-${GRADLE_MINIMUM}-bin.zip"); without(p, WRAPPER_PROPERTIES, ~/distributionSha256Sum=.*\n/) } | "Add to ${WRAPPER_PROPERTIES} the distributionSha256Sum of gradle-${GRADLE_MINIMUM}-bin.zip, the SHA-256 that gradle.org publishes for it (https://gradle.org/release-checksums/), as ./gradlew wrapper --gradle-version ${GRADLE_MINIMUM} --gradle-distribution-sha256-sum SHA256 writes it" | null
        'gradle-wrapper.checksum'         | 'the complete distribution has none, and keeps its type'    | { File p -> distribution(p, "https\\://services.gradle.org/distributions/gradle-${GRADLE}-all.zip"); without(p, WRAPPER_PROPERTIES, ~/distributionSha256Sum=.*\n/) } | "Add to ${WRAPPER_PROPERTIES} the distributionSha256Sum of gradle-${GRADLE}-all.zip, the SHA-256 that gradle.org publishes for it (https://gradle.org/release-checksums/), as ./gradlew wrapper --gradle-version ${GRADLE} --distribution-type all --gradle-distribution-sha256-sum SHA256 writes it" | null
        'gradle-wrapper.checksum'         | 'a mirror has none, which the wrapper task would not keep'   | { File p -> distribution(p, "https\\://repo.example.com/gradle/gradle-${GRADLE}-bin.zip"); without(p, WRAPPER_PROPERTIES, ~/distributionSha256Sum=.*\n/) } | "Add to ${WRAPPER_PROPERTIES} the distributionSha256Sum of gradle-${GRADLE}-bin.zip, the SHA-256 that gradle.org publishes for it (https://gradle.org/release-checksums/)" | null
        'gradle-wrapper.checksum-differs' | 'the wrapper refuses to download Gradle, so it is written by hand' | { File p -> checksum(p, '0' * 64) }                                                                                     | "Set distributionSha256Sum to ${GRADLE_SHA256} in ${WRAPPER_PROPERTIES}, the SHA-256 that gradle.org publishes for Gradle ${GRADLE}"                                                                                                       | null
    }

    void "java-target names a version of Java below the earliest with its setting and line, as project migrate names it"() {
        given: 'a build that Grails 8 generated for Java 17, with another setting below it'
        File project = copyOfTemplate()
        replace(project, 'build.gradle', ~/compileJava\.options\.release = \d+/, 'compileJava.options.release = 17')
        append(project, 'build.gradle', 'java {\n    targetCompatibility = JavaVersion.VERSION_11\n}\n')
        String build = new File(project, 'build.gradle').text
        int release = lineOf(build, 'compileJava.options.release')
        int target = lineOf(build, 'targetCompatibility')
        int major = HealthContext.minimumJava
        String grails = "Grails ${VersionInfo.grailsVersion.tokenize('.').first()}"

        when:
        run(project, 'project', 'health', '--json')
        Map finding = (parsed().findings as List<Map>).find { it.id == 'java-target.release' }

        then: 'each setting, with its line, in the order of the build file'
        finding.message == "build.gradle names Java 17 (compileJava.options.release, line ${release}) and Java 11 (targetCompatibility, line ${target}), and ${grails} needs Java ${major} or later: ".toString() +
                'a class of the project that uses one of Grails does not compile'
        finding.fix == "In build.gradle, set compileJava.options.release (line ${release}) and targetCompatibility (line ${target}) to Java ${major} or later".toString()
        finding.files == ['build.gradle']

        when: 'project migrate reads the build alike'
        new File(project, '.grails').deleteDir()
        out.reset()
        run(project, 'project', 'migrate', '--dry-run')

        then:
        out.toString().readLines().contains("| build.gradle names Java 17 (compileJava.options.release, line ${release}) and Java 11 (targetCompatibility, line ${target}), and ${grails} ".toString() +
                "needs Java ${major} or later: change the build to Java ${major} or later, or the application does not build with ${grails}")
    }

    @Unroll
    void "java-target goes by the setting the classes are compiled for, the release before targetCompatibility, sourceCompatibility and the toolchain: #what"() {
        given: 'the record project migrate writes for the generated build, which is for Java 21'
        File project = copyOfTemplate()
        migrated(project)
        replace(project, 'build.gradle', ~/compileJava\.options\.release = \d+\n/, declared)
        String build = new File(project, 'build.gradle').text
        List<Integer> lines = markers.collect { String marker -> lineOf(build, marker) }

        when:
        run(project, 'project', 'health', '--json')
        List<Map> findings = (parsed().findings as List<Map>).findAll { it.check == 'java-target' }

        then:
        findings*.id == [id]
        findings.first().message == message.call(lines)

        where:
        what                                     | declared                                                                     | markers                                                     | id                   | message
        'the release, and a later toolchain'     | "${TOOLCHAIN_25}compileJava.options.release = 21\n"                          | ['compileJava.options.release', 'JavaLanguageVersion']      | 'java-target.ok'     | { List<Integer> l -> "build.gradle compiles for Java 21 (compileJava.options.release, line ${l[0]}); it also names Java 25 (the Java toolchain, line ${l[1]})".toString() }
        'targetCompatibility, and a toolchain'   | "${TOOLCHAIN_25}java {\n    targetCompatibility = JavaVersion.VERSION_21\n}\n" | ['targetCompatibility', 'JavaLanguageVersion']              | 'java-target.ok'     | { List<Integer> l -> "build.gradle compiles for Java 21 (targetCompatibility, line ${l[0]}); it also names Java 25 (the Java toolchain, line ${l[1]})".toString() }
        'the toolchain alone'                    | TOOLCHAIN_25                                                                 | ['JavaLanguageVersion']                                     | 'java-target.record' | { List<Integer> l -> "The record says that the application is for Java 21 (.grails/project.yml), and build.gradle compiles for Java 25 (the Java toolchain, line ${l[0]})".toString() }
        'a later release, and the toolchain'     | "compileJava.options.release = 25\n${TOOLCHAIN_21}"                          | ['compileJava.options.release']                             | 'java-target.record' | { List<Integer> l -> "The record says that the application is for Java 21 (.grails/project.yml), and build.gradle compiles for Java 25 (compileJava.options.release, line ${l[0]})".toString() }
        'sourceCompatibility before the toolchain' | "${TOOLCHAIN_21}sourceCompatibility = '25'\n"                              | ['sourceCompatibility']                                     | 'java-target.record' | { List<Integer> l -> "The record says that the application is for Java 21 (.grails/project.yml), and build.gradle compiles for Java 25 (sourceCompatibility, line ${l[0]})".toString() }
    }

    void "git-files.state-ignored asks for the lines in the record's own .gitignore, and in the one of the project where there is no record"() {
        given: 'a record that an earlier snapshot wrote, whose .gitignore does not leave out what is of one machine, and a .gitignore of the project that does not either'
        File project = copyOfTemplate()
        without(project, '.gitignore', ~/\.grails\/(transactions\/|history)\n/)
        without(project, '.grails/.gitignore', ~/\/(transactions\/|history)\n/)

        when:
        run(project, 'project', 'health', '--json')
        Map finding = (parsed().findings as List<Map>).find { it.id == 'git-files.state-ignored' }

        then: 'the lines go with the record, whatever the .gitignore of the project says'
        finding.message == 'No .gitignore of the project leaves out .grails/transactions and .grails/history, which are of one machine: what a change that did not end ' +
                'leaves, and the history of the interactive console'
        finding.fix == 'Add /transactions/ and /history to .grails/.gitignore, as create-app and project migrate write it'
        finding.files == ['.grails/.gitignore']
        finding.upgradeNote.endsWith('#upgradingRecordIgnoresMachineState')

        when: 'the project has no record'
        new File(project, '.grails').deleteDir()
        out.reset()
        run(project, 'project', 'health', '--json')
        finding = (parsed().findings as List<Map>).find { it.id == 'git-files.state-ignored' }

        then: 'they go into the .gitignore of the project; project migrate writes the record with its own'
        finding.fix == 'Add .grails/transactions/ and .grails/history to the .gitignore of the project, as create-app writes them'
        finding.files == ['.gitignore']

        when: 'project migrate writes the record'
        out.reset()
        run(project, 'project', 'migrate')
        out.reset()
        run(project, 'project', 'health', '--json')

        then: 'its .gitignore leaves them out'
        (parsed().findings as List<Map>).find { it.check == 'git-files' }.id == 'git-files.ok'
    }

    void "the JSON document has every member in one order, and each finding has an id of its check, its level, the fix and the upgrade note"() {
        given:
        File project = copyOfTemplate()
        dependency(project, 'profile "org.apache.grails.profiles:web"')

        when:
        int exit = run(project, 'project', 'health', '--json')
        String written = out.toString()
        Map document = parsed()

        then: 'one line, and nothing else on either output'
        exit == 1
        written.readLines().size() == 1
        err.toString().isEmpty()

        and:
        document.keySet() as List == ['format', 'command', 'projectDirectory', 'outcome', 'reason', 'message', 'projectGrailsVersion', 'commandLineVersion', 'online',
                                      'strict', 'passed', 'counts', 'findings']
        document.format == 1
        document.command == 'project health'
        document.projectDirectory == project.canonicalPath
        document.outcome == 'checked'
        document.reason == null
        document.message == null
        document.projectGrailsVersion == VersionInfo.grailsVersion
        document.commandLineVersion == VersionInfo.grailsVersion
        document.online == false
        document.strict == false
        document.passed == false
        document.counts.keySet() as List == ['error', 'warning', 'advice', 'ok', 'skipped']
        document.counts.values().sum() == document.findings.size()

        and: 'the findings in the order of their levels'
        List<Map> findings = document.findings as List<Map>
        findings.every { it.keySet() as List == ['id', 'check', 'level', 'message', 'fix', 'command', 'files', 'upgradeNote', 'origin'] }
        findings*.level == findings*.level.sort(false) { ['error', 'warning', 'advice', 'skipped', 'ok'].indexOf(it) }
        findings.every { it.id ==~ /[a-z]+(-[a-z]+)*\.[a-z]+(-[a-z]+)*/ && it.id.startsWith(it.check + '.') }

        and: 'the profile, with the line that names the note, and the command where there is one'
        Map profile = findings.find { it.id == 'removed-dependency.profile' }
        profile.level == 'error'
        profile.files == ['build.gradle']
        profile.upgradeNote == "https://grails.apache.org/docs/${VersionInfo.documentationVersion}/guide/upgrading.html#upgradingShellNotPublished".toString()
        findings.find { it.id == 'command-catalog.missing' }.command == 'grails-forge-cli gradle grailsCommandCatalog'
    }

    void "the findings are said in words, by their levels, with a line that counts them, and -v lists what was found to be in order"() {
        given:
        File project = copyOfTemplate()
        dependency(project, 'implementation "io.reactivex:rxjava"')

        when:
        int exit = run(project, 'project', 'health')
        List<String> lines = out.toString().readLines()

        then:
        exit == 0
        err.toString().isEmpty()
        lines.first() == "| Project shop on Grails ${VersionInfo.grailsVersion} (gradle.properties), checked by the command line of Grails ${VersionInfo.grailsVersion}, offline".toString()
        lines[1].startsWith('| Warning deprecated-dependency.rxjava ')
        lines[1].contains('build.gradle declares io.reactivex:rxjava, of RxJava 1 or 2, which is deprecated for removal. Fix: Declare io.reactivex.rxjava3:rxjava in its place')
        lines.subList(2, lines.size() - 1).every { it.startsWith('| Advice ') }
        lines.last() ==~ /\| \d+ checks OK \(-v lists them\)\. 0 errors, 1 warning, \d+ advice, 0 skipped/
        !lines.any { it.startsWith('| OK ') }

        when:
        out.reset()
        run(project, 'project', 'health', '-v')
        lines = out.toString().readLines()

        then: 'the checks that found nothing come last'
        lines.findAll { it.startsWith('| OK ') }.size() == (lines.last() =~ /\| (\d+) checks OK/)[0][1].toInteger()
        lines.findIndexOf { it.startsWith('| OK ') } > lines.findLastIndexOf { it.startsWith('| Advice ') }
        lines.any { it.startsWith('| OK      grails-version.ok ') }
    }

    void "an error ends the command with 1 and names its id on the standard error; a warning does with --strict, and advice never"() {
        given:
        File project = copyOfTemplate()

        when: 'only advice'
        int exit = run(project, 'project', 'health', '--strict')

        then:
        exit == 0
        err.toString().isEmpty()

        when: 'a warning'
        dependency(project, 'implementation "io.reactivex:rxjava"')
        err.reset()
        exit = run(project, 'project', 'health')

        then:
        exit == 0
        err.toString().isEmpty()

        when:
        exit = run(project, 'project', 'health', '--strict')

        then:
        exit == 1
        err.toString().readLines() == ['| Error project health --strict found 1 warning: deprecated-dependency.rxjava']

        when: 'an error'
        err.reset()
        dependency(project, 'runtimeOnly "org.jline:jline-terminal-jansi"')
        exit = run(project, 'project', 'health')

        then:
        exit == 1
        err.toString().readLines() == ['| Error project health found 1 error: removed-dependency.jline']

        when:
        err.reset()
        exit = run(project, 'project', 'health', '--strict')

        then:
        exit == 1
        err.toString().readLines() == ['| Error project health --strict found 1 error and 1 warning: removed-dependency.jline, deprecated-dependency.rxjava']
    }

    void "outside a project the command says that it is a command of a project, and ends with 1, in words and in the document"() {
        when:
        int exit = run(dir, 'project', 'health')

        then:
        exit == 1
        err.toString().readLines() == ["| Error project health is a command of a Grails project; there is none in ${dir.canonicalPath} or above it".toString()]
        out.toString().isEmpty()

        when:
        err.reset()
        exit = run(dir, 'project', 'health', '--json')
        Map document = parsed()

        then:
        exit == 1
        err.toString().isEmpty()
        document.outcome == 'refused'
        document.reason == 'no-project'
        document.message == "project health is a command of a Grails project; there is none in ${dir.canonicalPath} or above it".toString()
        document.projectDirectory == null
        document.passed == false
        document.counts == null
        document.findings == null
    }

    @Unroll
    void "an application the Grails shell created, on Grails #version, is checked for what Grails 9 asks of it, as no other command of a project is"() {
        given:
        File project = new File(dir, 'legacy')
        shellProject(project, version)

        when:
        int exit = run(project, 'project', 'health', '--json')
        Map document = parsed()

        then: 'it runs, and finds what is left to do'
        exit == 1
        document.outcome == 'checked'
        document.projectGrailsVersion == version
        ids(document, 'warning').containsAll(['grails-version.earlier', 'record.missing'])
        ids(document, 'error').contains('removed-dependency.profile')
        ids(document, 'advice').containsAll(['leftover.profile-config', 'agent-skills.upgrade-skill'])
        (document.findings as List<Map>).find { it.id == 'agent-skills.upgrade-skill' }.fix.contains("skill 'org.apache.grails.skills:grails-upgrade:${VersionInfo.grailsVersion}'")

        when: 'another command of a project is refused there, and names this one'
        out.reset()
        err.reset()
        exit = run(project, 'create-controller', 'Book')

        then:
        exit == 1
        err.toString().contains("This project is on Grails ${version} (gradle.properties)")
        err.toString().contains('project migrate, which writes the record the commands of a project read, and project health, which says what the upgrade notes ' +
                'still ask of the project, are the commands of a project that run here')

        when: 'project migrate writes the record, and points at this command'
        out.reset()
        err.reset()
        exit = run(project, 'project', 'migrate')

        then:
        exit == 0
        out.toString().readLines().last() == '| grails-forge-cli project health says what else the upgrade notes ask of the project, as far as its files show it'

        when:
        out.reset()
        run(project, 'project', 'health', '--json')
        document = parsed()

        then: 'the record is there now'
        (document.findings as List<Map>).find { it.check == 'record' }.level == 'ok'

        where:
        version << ['7.0.4', '8.0.1']
    }

    void "project migrate points at this command when it moves the record of an earlier version"() {
        given:
        File project = copyOfTemplate()
        writtenBy(project, '9.0.0-M1', 'a' * 64)

        when:
        int exit = run(project, 'project', 'migrate')
        List<String> lines = out.toString().readLines()

        then:
        exit == 0
        lines.any { it.startsWith('| The record was moved to this version') }
        lines.last() == '| grails-forge-cli project health says what else the upgrade notes ask of the project, as far as its files show it'

        when:
        out.reset()
        run(project, 'project', 'health', '--json')

        then:
        (parsed().findings as List<Map>).find { it.check == 'record' }.id == 'record.ok'
    }

    void "update-wrapper points at this command when it replaces the wrapper of an earlier major version, and not when it replaces one of this"() {
        given:
        File project = copyOfTemplate()
        wrapperJar(project, '8.0.1')

        when:
        int exit = run(project, 'update-wrapper')
        List<String> lines = out.toString().readLines()

        then:
        exit == 0
        lines.first().contains('was that of Grails 8.0.1 and is now that of Grails')
        lines.last() == 'grails-forge-cli project health says what else the upgrade notes ask of the project, as far as its files show it.'

        when: 'the wrapper is that of this major version, and another build of it'
        out.reset()
        append(project, 'grailsw', '# changed\n')
        exit = run(project, 'update-wrapper')
        lines = out.toString().readLines()

        then:
        exit == 0
        lines.size() == 1
        !lines.first().contains('project health')

        when:
        out.reset()
        run(project, 'project', 'health', '--json')

        then:
        (parsed().findings as List<Map>).find { it.check == 'grails-wrapper' }.id == 'grails-wrapper.ok'
    }

    void "the command is listed, described for a tool and completed with the other commands of the project"() {
        when:
        int exit = ForgeCli.execute(beanContext, 'help')

        then:
        exit == 0
        out.toString().contains('*  project health')

        when:
        out.reset()
        exit = ForgeCli.execute(beanContext, 'help', '--json')
        Map project = ((List<Map>) ((Map) new JsonSlurper().parseText(out.toString())).commands).find { it.name == 'project' }
        Map health = (project.commands as List<Map>).find { it.name == 'health' }

        then:
        exit == 0
        health.project == true
        health.origin == 'built-in'
        (health.options as List<Map>)*.names.containsAll([['--json'], ['--strict']])
        health.relations == []

        when:
        out.reset()
        exit = ForgeCli.execute(beanContext, 'help', 'project', 'health')

        then:
        exit == 0
        out.toString().startsWith('Usage: grails-forge-cli project health ')
        out.toString().contains('--strict')

        when:
        err.reset()
        exit = ForgeCli.execute(beanContext, 'project')

        then:
        exit == 2
        err.toString().contains('project health says what the files of the project show of its state')
    }

    void "the reference page of the command lists every id, which is what a tool goes by"() {
        given: 'the page in the guide of this checkout: the specs run in grails-forge/grails-forge-cli'
        String text = new File(previousUsrDir, '../../grails-doc/src/en/ref/Command Line/project-health.adoc').canonicalFile.text

        expect:
        IDS.findAll { String id -> !text.contains("`${id}`") } == []
        CHECKS.findAll { String check -> !text.contains("`${check}`") } == []
    }

    // a create command, in a directory of its own: what it says is not what is looked at
    private int create(File directory, String... arguments) {
        try {
            return run(directory, arguments)
        } finally {
            out.reset()
            err.reset()
        }
    }

    private int run(File directory, String... arguments) {
        String current = System.getProperty('user.dir')
        System.setProperty('user.dir', directory.absolutePath)
        try {
            return ForgeCli.execute(beanContext, arguments)
        } finally {
            System.setProperty('user.dir', current)
        }
    }

    private Map parsed() {
        return (Map) new JsonSlurper().parseText(out.toString())
    }

    private static List<String> ids(Map document, String level) {
        return (document.findings as List<Map>).findAll { it.level == level }*.id as List<String>
    }

    private File copyOfTemplate() {
        Path from = new File(template, 'shop').toPath()
        Path to = new File(dir, 'shop').toPath()
        Files.walk(from).withCloseable { stream ->
            stream.each { Path each ->
                Path target = to.resolve(from.relativize(each).toString())
                if (Files.isDirectory(each, LinkOption.NOFOLLOW_LINKS)) {
                    Files.createDirectories(target)
                } else {
                    Files.copy(each, target, StandardCopyOption.COPY_ATTRIBUTES)
                }
            }
        }
        return to.toFile()
    }

    // an application as the Grails shell created it, on an earlier version of Grails
    private static void shellProject(File project, String version) {
        new File(project, 'grails-app/conf').mkdirs()
        new File(project, 'grails-app/init/com/example/legacy').mkdirs()
        new File(project, 'grails-app/conf/application.yml').text = ProjectMigrateCommandSpec.SHELL_CONFIGURATION
        new File(project, 'grails-app/init/com/example/legacy/Application.groovy').text = 'package com.example.legacy\n\nclass Application {\n}\n'
        new File(project, 'build.gradle').text = ProjectMigrateCommandSpec.SHELL_BUILD
        new File(project, 'settings.gradle').text = "rootProject.name = 'legacy'\n"
        new File(project, 'gradle.properties').text = "grailsVersion=${version}\n"
    }

    static void grailsVersion(File project, String version) {
        replace(project, 'gradle.properties', ~/grailsVersion=.*/, "grailsVersion=${version}")
    }

    static void distribution(File project, String url) {
        replace(project, 'gradle/wrapper/gradle-wrapper.properties', ~/distributionUrl=.*/, "distributionUrl=${url}")
    }

    // the line of the build file that has the marker first, from 1
    static int lineOf(String build, String marker) {
        int index = build.readLines().findIndexOf { String line -> line.contains(marker) }
        assert index >= 0
        return index + 1
    }

    // the build file as a build file of Kotlin, which is read where there is no build.gradle
    static void kotlinBuild(File project) {
        assert new File(project, 'build.gradle').renameTo(new File(project, 'build.gradle.kts'))
    }

    static void checksum(File project, String sha256) {
        replace(project, 'gradle/wrapper/gradle-wrapper.properties', ~/distributionSha256Sum=.*/, "distributionSha256Sum=${sha256}")
    }

    static void dependency(File project, String line) {
        append(project, 'build.gradle', "dependencies {\n    ${line}\n}\n")
    }

    static void replace(File project, String path, java.util.regex.Pattern pattern, String with) {
        File file = new File(project, path)
        String text = file.text
        assert text =~ pattern
        file.text = text.replaceAll(pattern, java.util.regex.Matcher.quoteReplacement(with))
    }

    static void without(File project, String path, java.util.regex.Pattern pattern) {
        File file = new File(project, path)
        file.text = file.text.replaceAll(pattern, '')
    }

    static void append(File project, String path, String text) {
        File file = new File(project, path)
        file.text = file.text + text
    }

    // the record written by project migrate, for an application whose record says what it is taken to be
    void migrated(File project) {
        new File(project, '.grails').deleteDir()
        out.reset()
        assert run(project, 'project', 'migrate') == 0
        out.reset()
        err.reset()
    }

    static void catalog(File project) {
        File file = new File(project, '.grails/cache/command-catalog.json')
        file.parentFile.mkdirs()
        file.text = '{"format":1,"complete":true,"diagnostics":[],"commands":[]}'
        file.setLastModified(System.currentTimeMillis() + 30_000)
    }

    static void skills(File project) {
        replace(project, 'build.gradle', ~/plugins \{/, "plugins {\n    id 'com.skillsjars.gradle-plugin' version '0.2.0'")
        dependency(project, "skill 'org.apache.grails.skills:grails-upgrade:${VersionInfo.grailsVersion}'")
    }

    static void extracted(File project, String directory) {
        File skill = new File(project, "${directory}/skillsjars__apache__grails-core__grails-upgrade/SKILL.md")
        skill.parentFile.mkdirs()
        skill.text = '---\nname: grails-upgrade\n---\n'
    }

    // a grails-wrapper.jar whose manifest says the version, or none
    static void wrapperJar(File project, String version) {
        Manifest manifest = new Manifest()
        manifest.mainAttributes.put(Attributes.Name.MANIFEST_VERSION, '1.0')
        if (version != null) {
            manifest.mainAttributes.put(Attributes.Name.IMPLEMENTATION_VERSION, version)
        }
        new File(project, 'grails-wrapper.jar').withOutputStream { OutputStream stream ->
            new JarOutputStream(stream, manifest).close()
        }
    }

    // the record as an earlier version of the command line would have written it, sealed again
    static void writtenBy(File project, String version, String generatorSha256) {
        File manifest = new File(project, '.grails/project.yml')
        File lock = new File(project, '.grails/forge.lock.yml')
        File index = new File(project, '.grails/baseline/index.yml')
        manifest.text = manifest.text.replace("grailsVersion: ${VersionInfo.grailsVersion}\n", "grailsVersion: ${version}\n")
        String body = lock.text.replace("  version: ${VersionInfo.version}\n  sha256: ${VersionInfo.generatorSha256}\n", "  version: ${version}\n  sha256: ${generatorSha256}\n")
        body = body.replace("\ngrailsVersion: ${VersionInfo.grailsVersion}\n", "\ngrailsVersion: ${version}\n")
        body = body.replaceFirst('manifest:\n  sha256: [0-9a-f]{64}', "manifest:\n  sha256: ${sha(manifest.bytes)}")
                .replaceFirst('baselineIndex:\n  sha256: [0-9a-f]{64}', "baselineIndex:\n  sha256: ${sha(index.bytes)}")
        body = body.substring(0, body.lastIndexOf('seal:\n'))
        lock.text = body + "seal:\n  sha256: ${sha(body.bytes)}\n"
        assert ProjectState.read(project.toPath()).lock.generatorVersion == version
    }

    private static String sha(byte[] bytes) {
        return MessageDigest.getInstance('SHA-256').digest(bytes).encodeHex().toString()
    }
}
