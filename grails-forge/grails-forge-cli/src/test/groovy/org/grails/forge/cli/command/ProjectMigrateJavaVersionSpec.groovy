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
import org.apache.grails.forge.core.state.ProjectState
import org.grails.forge.cli.CommandSpec
import org.grails.forge.cli.ForgeCli
import spock.lang.Unroll

/**
 * {@code project migrate} and the version of Java of the build: each of the settings a build names it with, in a
 * build file of Groovy and of Kotlin, is read and said with where it is, and a version Grails does not run on is said
 * not to be one.
 */
class ProjectMigrateJavaVersionSpec extends CommandSpec {

    // what an application that Grails 8 created has, which Grails 8 generated for Java 17 when asked
    static final String GRAILS_8_BUILD = '''\
version = "0.1"
group = "com.example"

apply plugin: "war"
apply plugin: "org.apache.grails.gradle.grails-web"
apply plugin: "org.apache.grails.gradle.grails-gsp"

dependencies {
    implementation platform("org.apache.grails:grails-bom:$grailsVersion")
    implementation "org.apache.grails:grails-core"
    implementation "org.apache.grails:grails-data-hibernate5"
}

compileJava.options.release = 17

tasks.withType(Test).configureEach {
    useJUnitPlatform()
}
'''

    static final String KOTLIN_BUILD = '''\
plugins {
    id("org.apache.grails.gradle.grails-web")
    id("org.apache.grails.gradle.grails-gsp")
}

group = "com.example"

dependencies {
    implementation(platform("org.apache.grails:grails-bom:$grailsVersion"))
    implementation("org.apache.grails:grails-data-hibernate5")
}
'''

    PrintStream originalOut
    PrintStream originalErr
    ByteArrayOutputStream out = new ByteArrayOutputStream()
    ByteArrayOutputStream err = new ByteArrayOutputStream()
    File home
    String previousHome

    void setup() {
        originalOut = System.out
        originalErr = System.err
        System.setOut(new PrintStream(out))
        System.setErr(new PrintStream(err))
        home = Files.createTempDirectory('grailsforgehome').toFile()
        previousHome = System.getProperty('user.home')
        System.setProperty('user.home', home.absolutePath)
        new File(dir, 'grails-app/conf').mkdirs()
        new File(dir, 'grails-app/conf/application.yml').text = 'grails:\n    profile: web\n    codegen:\n        defaultPackage: com.example.legacy\n'
        new File(dir, 'settings.gradle').text = "rootProject.name = 'legacy'\n"
    }

    void cleanup() {
        System.setOut(originalOut)
        System.setErr(originalErr)
        System.setProperty('user.home', previousHome)
        home.deleteDir()
    }

    void "the release of the Java compilation that Grails 8 generated, below what Grails runs on, is said with where it is, and is not taken for the record"() {
        given: 'an application as Grails 8 created it for Java 17'
        new File(dir, 'build.gradle').text = GRAILS_8_BUILD
        int line = lineOf(GRAILS_8_BUILD, 'compileJava.options.release')

        when:
        int exit = ForgeCli.execute(beanContext, 'project', 'migrate')
        List<String> said = out.toString().readLines()

        then: 'the record has the version this version generates for, which is said to have been assumed in place of the one the build names'
        exit == 0
        err.toString().isEmpty()
        row(said, 'jdk') == ['21', "(assumed in place of Java 17: compileJava.options.release of build.gradle, line ${line})".toString()]
        ProjectState.read(dir.toPath()).manifest.options.jdk.majorVersion() == 21

        and: 'that the build is for a version of Java Grails does not run on is said, with what to do about it'
        said.contains("| build.gradle names Java 17 (compileJava.options.release, line ${line}), and Grails 9 needs Java 21 or later: ".toString() +
                'change the build to Java 21 or later, or the application does not build with Grails 9')
        said.any { it.startsWith('| Assumed, because nothing in the project says it: servlet (--servlet), reloading (--reloading). Assumed in place of what the project says, ' +
                'which this version does not generate for: jdk (--jdk). ') }

        when: 'the version is given'
        out.reset()
        exit = ForgeCli.execute(beanContext, 'project', 'migrate', '--force', '--jdk', '25')
        said = out.toString().readLines()

        then: 'it is the one of the record, and what the build names is said all the same'
        exit == 0
        row(said, 'jdk') == ['25', '(given with --jdk)']
        said.any { it.startsWith('| build.gradle names Java 17 (compileJava.options.release, ') }
    }

    @Unroll
    void "#setting in a build file of #language is read, and is said with where it is"() {
        given:
        String build = (language == 'Kotlin' ? KOTLIN_BUILD : GRAILS_8_BUILD.replace('compileJava.options.release = 17\n', '')) + '\n' + declared
        new File(dir, language == 'Kotlin' ? 'build.gradle.kts' : 'build.gradle').text = build
        String file = language == 'Kotlin' ? 'build.gradle.kts' : 'build.gradle'

        when:
        int exit = ForgeCli.execute(beanContext, 'project', 'migrate', '--dry-run')
        List<String> said = out.toString().readLines()

        then:
        exit == 0
        err.toString().isEmpty()
        row(said, 'jdk') == ['25', "(${setting} of ${file}, line ${lineOf(build, marker)})".toString()]
        !said.any { it.contains('needs Java 21') || it.contains('does not generate for') }

        where:
        language | setting                       | marker                            | declared
        'Groovy' | 'compileJava.options.release' | 'compileJava.options.release'     | 'compileJava.options.release = 25\n'
        'Groovy' | 'options.release'             | 'options.release = 25'            | 'tasks.withType(JavaCompile).configureEach {\n    options.release = 25\n}\n'
        'Groovy' | 'options.release'             | 'options.release.set(25)'         | 'compileJava {\n    options.release.set(25)\n}\n'
        'Groovy' | 'options.release'             | 'options.release = 25'            | 'tasks.named(\'compileJava\', JavaCompile) {\n    it.options.release = 25\n}\n'
        'Groovy' | 'release'                     | 'release = 25'                    | 'tasks.withType(JavaCompile).configureEach {\n    options.with {\n        release = 25\n    }\n}\n'
        'Groovy' | 'the Java toolchain'          | 'JavaLanguageVersion'             | 'java {\n    toolchain {\n        languageVersion = JavaLanguageVersion.of(25)\n    }\n}\n'
        'Groovy' | 'the Java toolchain'          | 'JavaLanguageVersion'             | 'java {\n    toolchain {\n        languageVersion = JavaLanguageVersion .of ( 25 )\n    }\n}\n'
        'Groovy' | 'targetCompatibility'         | 'targetCompatibility'             | 'java {\n    sourceCompatibility = JavaVersion.VERSION_25\n    targetCompatibility = JavaVersion.VERSION_25\n}\n'
        'Groovy' | 'sourceCompatibility'         | 'sourceCompatibility'             | "sourceCompatibility = '25'\n"
        'Groovy' | 'java.sourceCompatibility'    | 'sourceCompatibility'             | 'java.sourceCompatibility = JavaVersion.toVersion(25)\n'
        'Kotlin' | 'options.release'             | 'options.release'                 | 'tasks.withType<JavaCompile> {\n    options.release.set(25)\n}\n'
        'Kotlin' | 'options.release'             | 'options.release'                 | 'tasks.compileJava {\n    options.release = 25\n}\n'
        'Kotlin' | 'release'                     | 'release.set(25)'                 | 'tasks.withType<JavaCompile>().configureEach {\n    options.apply {\n        release.set(25)\n    }\n}\n'
        'Kotlin' | 'the Java toolchain'          | 'JavaLanguageVersion'             | 'java {\n    toolchain {\n        languageVersion.set(JavaLanguageVersion.of(25))\n    }\n}\n'
        'Kotlin' | 'jvmToolchain'                | 'jvmToolchain'                    | 'kotlin {\n    jvmToolchain(25)\n}\n'
        'Kotlin' | 'java.targetCompatibility'    | 'targetCompatibility'             | 'java.targetCompatibility = JavaVersion.VERSION_25\n'
    }

    @Unroll
    void "#what below what Grails runs on is said: #setting"() {
        given:
        String build = GRAILS_8_BUILD.replace('compileJava.options.release = 17\n', declared)
        new File(dir, 'build.gradle').text = build

        when:
        int exit = ForgeCli.execute(beanContext, 'project', 'migrate', '--dry-run')
        List<String> said = out.toString().readLines()

        then:
        exit == 0
        row(said, 'jdk') == ['21', "(assumed in place of Java ${version}: ${setting} of build.gradle, line ${lineOf(build, marker)})".toString()]
        said.contains("| build.gradle names Java ${version} (${setting}, line ${lineOf(build, marker)}), and Grails 9 needs Java 21 or later: ".toString() +
                'change the build to Java 21 or later, or the application does not build with Grails 9')

        where:
        what            | setting               | version | marker                | declared
        'a toolchain'   | 'the Java toolchain'  | 17      | 'JavaLanguageVersion' | 'java {\n    toolchain {\n        languageVersion = JavaLanguageVersion.of(17)\n    }\n}\n'
        'Java 8'        | 'sourceCompatibility' | 8       | 'sourceCompatibility' | "sourceCompatibility = '1.8'\n"
        'Java 8'        | 'targetCompatibility' | 8       | 'targetCompatibility' | 'targetCompatibility = JavaVersion.VERSION_1_8\n'
        'a release'     | 'options.release'     | 11      | 'options.release'     | 'tasks.withType(JavaCompile) {\n    options.release = 11\n}\n'
    }

    void "of several settings, the release is the version of the record, and what the others name otherwise is said"() {
        given: 'a build that compiles with Java 25 for Java 21'
        String build = GRAILS_8_BUILD.replace('compileJava.options.release = 17\n',
                'java {\n    toolchain {\n        languageVersion = JavaLanguageVersion.of(25)\n    }\n}\n\ncompileJava.options.release = 21\n')
        new File(dir, 'build.gradle').text = build

        when:
        int exit = ForgeCli.execute(beanContext, 'project', 'migrate', '--dry-run')
        List<String> said = out.toString().readLines()

        then:
        exit == 0
        row(said, 'jdk') == ['21', "(compileJava.options.release of build.gradle, line ${lineOf(build, 'compileJava.options.release')})".toString()]
        said.contains("| build.gradle also names Java 25 (the Java toolchain, line ${lineOf(build, 'JavaLanguageVersion')}); the record takes Java 21, ".toString() +
                'of compileJava.options.release, which is what the classes are compiled for')
    }

    void "a release that is of something else than a compilation is not read as one"() {
        given:
        String build = GRAILS_8_BUILD.replace('compileJava.options.release = 17\n', 'ext.release = 17\nversion.release = 11\ndef releaseNumber = 8\n')
        new File(dir, 'build.gradle').text = build

        when:
        int exit = ForgeCli.execute(beanContext, 'project', 'migrate', '--dry-run')
        List<String> said = out.toString().readLines()

        then:
        exit == 0
        row(said, 'jdk') == ['21', '(assumed)']
        !said.any { it.contains('needs Java 21') }
    }

    void "a setting that is commented out, or that is for other projects, is not read"() {
        given:
        String build = GRAILS_8_BUILD.replace('compileJava.options.release = 17\n',
                '// compileJava.options.release = 17\n/* java {\n    toolchain {\n        languageVersion = JavaLanguageVersion.of(17)\n    }\n} */\n' +
                        'subprojects {\n    tasks.withType(JavaCompile) {\n        options.release = 17\n    }\n}\n')
        new File(dir, 'build.gradle').text = build

        when:
        int exit = ForgeCli.execute(beanContext, 'project', 'migrate', '--dry-run')
        List<String> said = out.toString().readLines()

        then:
        exit == 0
        row(said, 'jdk') == ['21', '(assumed)']
        !said.any { it.contains('Java 17') }
    }

    private static int lineOf(String build, String marker) {
        int index = build.readLines().findIndexOf { String line -> line.contains(marker) }
        assert index >= 0
        return index + 1
    }

    // the value of a row of what the command says, and what it says of where the value is from
    private static List<String> row(List<String> said, String what) {
        String line = said.find { String each -> each.startsWith("    ${what} ") }
        assert line != null: "no row ${what} in ${said}"
        String rest = line.substring(4 + what.length()).trim()
        int open = rest.indexOf(' (')
        return [rest.substring(0, open).trim(), rest.substring(open + 1).trim()]
    }
}
