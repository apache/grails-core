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
package org.apache.grails.cli

import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermissions
import java.util.concurrent.TimeUnit
import java.util.jar.Attributes
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import java.util.jar.Manifest
import javax.tools.JavaCompiler
import javax.tools.ToolProvider
import spock.lang.IgnoreIf
import spock.lang.Requires
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.TempDir

/**
 * The step from the Grails wrapper of Grails 7 and 8 to the one of this version, on the jar the
 * wrapper downloads: {@code grails-cli-VERSION-all.jar}, as this build makes it. Every wrapper here
 * is started as its scripts start it, in a JVM of its own with {@code grails-wrapper.jar} as the
 * class path and the project as the current directory.
 *
 * <p>The earlier wrapper is a fixture that does what {@code grails.init.GrailsWrapper} of Grails 7
 * and 8 does once it has the jar: it takes {@code update-wrapper} from the front of the command
 * line, loads {@code org.apache.grails.cli.DelegatingShellApplication} from a class loader of the
 * jar alone and calls its {@code main}. The wrapper of this version is the one the jar has with it,
 * and it downloads the jar from a repository that is a directory. The last feature runs a
 * {@code grails-wrapper.jar} of an earlier release when the environment variable
 * {@code GRAILS_OLD_WRAPPER_JAR} names one, and is skipped otherwise: no such jar is kept in the
 * sources.</p>
 */
class WrapperUpgradeSpec extends Specification {

    private static final String BRIDGE = 'org.apache.grails.cli.DelegatingShellApplication'
    private static final List<String> WRAPPER = ['grailsw', 'grailsw.bat', 'grails-wrapper.jar']
    private static final String CLI_JAR_VARIABLE = 'EARLIER_WRAPPER_CLI_JAR'
    private static final String VERSION = '9.0.0'
    // what the command line says once it has replaced the wrapper of an earlier major version, as a step of an upgrade
    private static final String HEALTH = 'grails project health says what else the upgrade notes ask of the project, as far as its files show it.'

    @Shared
    File allJar = new File(System.getProperty('grails.cli.all.jar'))

    @Shared
    @TempDir
    File shared

    @Shared
    File earlierWrapperJar

    @Shared
    File repository

    @TempDir
    File dir

    File project

    void setupSpec() {
        earlierWrapperJar = earlierWrapper(new File(shared, 'earlier'), '8.1.0')
        // the repository the wrapper of this version, and that of an earlier release, download the command line from
        repository = new File(shared, 'repository')
        File versionDirectory = new File(repository, "org/apache/grails/grails-cli/${VERSION}")
        versionDirectory.mkdirs()
        Files.copy(allJar.toPath(), new File(versionDirectory, "grails-cli-${VERSION}-all.jar").toPath())
    }

    void setup() {
        project = new File(dir, 'shop').canonicalFile
        new File(project, 'grails-app/conf').mkdirs()
        new File(project, 'settings.gradle').text = "rootProject.name = 'shop'\n"
        new File(project, 'gradle.properties').text = "grailsVersion=${VERSION}\n"
    }

    void "the class the earlier wrapper starts is in the jar it downloads, and is loaded from that jar alone"() {
        given: 'a class loader of the jar and of nothing else but the JDK, which is less than the wrapper gives it'
        URLClassLoader loader = new URLClassLoader([allJar.toURI().toURL()] as URL[], ClassLoader.getPlatformClassLoader())

        when:
        Class<?> bridge = Class.forName(BRIDGE, true, loader)
        Method main = bridge.getMethod('main', String[])

        then:
        new JarFile(allJar).withCloseable { JarFile jar -> jar.getEntry(BRIDGE.replace('.', '/') + '.class') != null }
        bridge.getClassLoader().is(loader)
        Modifier.isPublic(main.getModifiers()) && Modifier.isStatic(main.getModifiers())
        main.getReturnType() == void

        and: 'the jar names the command line as its start class, which is what the class starts'
        new JarFile(allJar).withCloseable { JarFile jar -> jar.manifest.mainAttributes.getValue('Main-Class') } == 'org.apache.grails.forge.cli.GrailsCli'

        and: 'the wrapper it writes is in the jar too'
        new JarFile(allJar).withCloseable { JarFile jar -> WRAPPER.every { String name -> jar.getEntry("grails-wrapper/${name}") != null } }

        cleanup:
        loader.close()
    }

    @IgnoreIf({ os.windows })
    void "update-wrapper typed to the earlier wrapper replaces the three files, says so and ends"() {
        given:
        earlierWrapperIn(project)
        Files.setPosixFilePermissions(new File(project, 'grailsw').toPath(), PosixFilePermissions.fromString('rwxr-x---'))

        when:
        Map result = earlier(project, 'update-wrapper')

        then:
        result.status == 0
        result.err.isEmpty()
        result.out.readLines() == ["The Grails wrapper of the project in ${project.path} was that of Grails 8.1.0 and is now that of Grails ${cliVersion()}: " +
                                           'grailsw, grailsw.bat, grails-wrapper.jar were replaced. Commit them.', HEALTH]

        and: 'the files are those the jar has, the script can still be executed by whom it could be, and nothing else is left'
        WRAPPER.every { String name -> new File(project, name).bytes == inJar(name) }
        PosixFilePermissions.toString(Files.getPosixFilePermissions(new File(project, 'grailsw').toPath())) == 'rwxr-x---'
        project.list().sort() == ['gradle.properties', 'grails-app', 'grails-wrapper.jar', 'grailsw', 'grailsw.bat', 'settings.gradle']

        when: 'the wrapper that was written is the one that runs now, and it has nothing to replace'
        Map again = current(project, 'update-wrapper')

        then:
        again.status == 0
        again.out.readLines().last() == "The Grails wrapper of the project in ${project.path} is that of Grails ${cliVersion()}: nothing was changed"
    }

    void "a command typed to the earlier wrapper is run after the wrapper is replaced, with its exit code"() {
        given:
        earlierWrapperIn(project)
        new File(project, 'grails-app/controllers/shop').mkdirs()
        new File(project, 'grails-app/controllers/shop/BookController.groovy').text = 'package shop\n\nclass BookController {\n}\n'

        when:
        Map result = earlier(project, args as String[])

        then:
        result.status == status
        result.out.readLines()[0].startsWith("The Grails wrapper of the project in ${project.path} was that of Grails 8.1.0 and is ${nowOrBeing()} that of Grails ")
        result.out.contains(said) || result.err.contains(said)
        isOfThisVersion(project)

        where:
        args                                  | status | said
        ['stats']                             | 0      | '| Controllers          |     1 |'
        ['update-wrapper', 'stats']           | 0      | '| Controllers          |     1 |'
        ['--plain-output', 'stats']           | 0      | '| Controllers          |     1 |'
        ['no-such-command']                   | 2      | 'There is no command no-such-command'
        ['update-wrapper', 'no-such-command'] | 2      | 'There is no command no-such-command'
    }

    void "without a command the earlier wrapper replaces the files and ends, and does not start the interactive console"() {
        given:
        earlierWrapperIn(project)

        when: 'the input is never closed: a console would wait for it until the test gives up'
        Map result = earlier(project)

        then:
        result.status == 0
        result.out.readLines().size() == 2
        result.out.readLines().last() == HEALTH
        result.out.contains(jarIsInUse() ? 'grailsw, grailsw.bat were replaced, and grails-wrapper.jar is in use by the wrapper that runs this command' : 'grailsw, grailsw.bat, grails-wrapper.jar were replaced')
        isOfThisVersion(project)
    }

    void "outside a project the earlier wrapper replaces nothing and the command line is run: #args"() {
        given: 'a directory that has the files of a wrapper and is no Grails project'
        File outside = new File(dir, 'tools').canonicalFile
        outside.mkdirs()
        earlierWrapperIn(outside)
        Map<String, byte[]> before = WRAPPER.collectEntries { String name -> [(name): new File(outside, name).bytes] }

        when:
        Map result = earlier(outside, args as String[])

        then:
        result.status == 0
        result.out.contains(said)
        !result.out.contains('The Grails wrapper of')
        WRAPPER.every { String name -> new File(outside, name).bytes == before[name] }

        where:
        args                 | said
        []                   | 'Usage: grails '
        ['update-wrapper']   | 'Usage: grails '
        ['--version']        | 'Grails Version: '
    }

    void "in a project that still names an earlier version nothing is replaced, and the command line says which it works with"() {
        given:
        new File(project, 'gradle.properties').text = 'grailsVersion=8.1.0\n'
        earlierWrapperIn(project)
        Map<String, byte[]> before = WRAPPER.collectEntries { String name -> [(name): new File(project, name).bytes] }

        when: 'the earlier wrapper was given the command line of this version all the same, as PREFERRED_GRAILS_VERSION cannot do in a project but a wrapper home can'
        Map result = earlier(project, 'stats')

        then:
        result.status == 1
        result.err.contains('This project is on Grails 8.1.0 (gradle.properties), and the command line of Grails 9 works with projects of Grails 9 and later.')
        !result.out.contains('The Grails wrapper of')
        WRAPPER.every { String name -> new File(project, name).bytes == before[name] }
    }

    @IgnoreIf({ os.windows || System.getProperty('user.name') == 'root' })
    void "a file that cannot be replaced is named with what to do by hand, and the command is run all the same"() {
        given: 'a script that is a link out of the project, and a batch file that is read-only'
        earlierWrapperIn(project)
        File elsewhere = new File(dir, 'elsewhere/grailsw')
        elsewhere.parentFile.mkdirs()
        elsewhere.text = '#!/bin/sh\n# the script of another directory\n'
        new File(project, 'grailsw').delete()
        Files.createSymbolicLink(new File(project, 'grailsw').toPath(), elsewhere.toPath())
        File batch = new File(project, 'grailsw.bat')
        byte[] batchBefore = batch.bytes
        batch.setWritable(false)

        when:
        Map result = earlier(project, 'stats')

        then: 'the jar is replaced, the command is run and its exit code is the one of the run'
        result.status == 0
        new File(project, 'grails-wrapper.jar').bytes == inJar('grails-wrapper.jar')
        result.out.contains('is now that of Grails ')
        result.out.contains('grails-wrapper.jar was replaced. Commit it.')
        result.out.contains('| Totals ')

        and: 'the link is the link it was, what it names is not written to, and the read-only file is as it was'
        Files.isSymbolicLink(new File(project, 'grailsw').toPath())
        elsewhere.text == '#!/bin/sh\n# the script of another directory\n'
        batch.bytes == batchBefore

        and:
        result.err.contains('grailsw was not replaced: it is a symbolic link, and a link is not followed. Replace it by hand in ')
        result.err.contains('grailsw.bat was not replaced: it is read-only. Replace it by hand in ')
        result.err.contains("-bin.zip of the release")

        when: 'update-wrapper, typed to the wrapper whose jar was written, ends with the exit code of what could not be done'
        Map alone = current(project, 'update-wrapper')

        then:
        alone.status == 1
        alone.err.contains('grailsw was not replaced: it is a symbolic link')
    }

    @IgnoreIf({ os.windows })
    void "update-wrapper typed to the wrapper of this version downloads the command line again and replaces what differs"() {
        given: 'the wrapper of this version, with a script that is not the one of the version the project names'
        WRAPPER.each { String name -> new File(project, name).bytes = inJar(name) }
        new File(project, 'grailsw').text = '#!/bin/sh\n# the script of another release\n'
        new File(project, 'grailsw').setExecutable(true)
        File home = new File(dir, 'home')

        when:
        Map result = current(project, home, 'update-wrapper')

        then:
        result.status == 0
        result.err.isEmpty()
        result.out.contains("Updated wrapper to version: ${VERSION}")
        new File(home, "wrapper/${VERSION}/grails-cli-${VERSION}-all.jar").length() == allJar.length()
        result.out.readLines().last() == "The Grails wrapper of the project in ${project.path} is now that of Grails ${cliVersion()}: grailsw was replaced. Commit it."
        new File(project, 'grailsw').bytes == inJar('grailsw')
        new File(project, 'grailsw').canExecute()

        when: 'a command line that follows update-wrapper is not run: it is one command'
        Map more = current(project, home, 'update-wrapper', 'stats')

        then:
        more.status == 2
        more.err.contains('update-wrapper takes no argument stats')
        !more.out.contains('| Totals ')
    }

    @Requires({ env.GRAILS_OLD_WRAPPER_JAR })
    @IgnoreIf({ os.windows })
    void "a grails-wrapper.jar of an earlier release replaces itself, and the wrapper it wrote then runs the command"() {
        given: 'the jar of an earlier release, as an application has it committed'
        File released = new File(System.getenv('GRAILS_OLD_WRAPPER_JAR'))
        Files.copy(released.toPath(), new File(project, 'grails-wrapper.jar').toPath())
        new File(project, 'grailsw').text = '#!/bin/sh\n# the script of an earlier release\n'
        new File(project, 'grailsw.bat').text = '@rem the script of an earlier release\r\n'
        File home = new File(dir, 'home')

        when:
        Map result = current(project, home, 'update-wrapper')

        then:
        result.status == 0
        result.out.contains("Updated wrapper to version: ${VERSION}")
        result.out.readLines().last() ==~ /The Grails wrapper of the project in .* was that of Grails [78]\..* and is now that of Grails .*: grailsw, grailsw\.bat, grails-wrapper\.jar were replaced\. Commit them\./
        WRAPPER.every { String name -> new File(project, name).bytes == inJar(name) }

        when:
        Map stats = current(project, home, 'stats')

        then:
        stats.status == 0
        stats.out.contains('| Totals ')
        !stats.out.contains('The Grails wrapper of')
    }

    @Requires({ os.windows })
    void "on Windows the earlier wrapper, started by its batch file, is replaced in two runs without a step by hand, and each run ends with the exit code of its command"() {
        given: 'the wrapper of Grails 8, with the batch file the release has'
        earlierWrapperIn(project)
        new File(project, 'grailsw.bat').setText(earlierBatchFile(), 'ISO-8859-1')
        byte[] earlierBatch = new File(project, 'grailsw.bat').bytes

        when: 'a command that ends with an exit code of its own: the command interpreter reads it from the batch file that was put under it'
        Map first = batch(project, [(CLI_JAR_VARIABLE): allJar.path], 'no-such-command')

        then:
        assert first.status == 2: said(first, project)
        assert first.out.contains('is being replaced by that of Grails ') &&
                first.out.contains('grailsw was replaced, and grailsw.bat and grails-wrapper.jar are in use by the wrapper that runs this command'): said(first, project)
        first.err.contains('There is no command no-such-command')

        and: 'the new files are beside the two in use, and the batch file is the one that stands in until the next run'
        assert new File(project, 'grailsw.bat.new').exists() && new File(project, 'grails-wrapper.jar.new').exists(): said(first, project)
        new File(project, 'grailsw.bat.new').bytes == inJar('grailsw.bat')
        new File(project, 'grails-wrapper.jar.new').bytes == inJar('grails-wrapper.jar')
        new File(project, 'grails-wrapper.jar').bytes == earlierWrapperJar.bytes
        new File(project, 'grailsw').bytes == inJar('grailsw')
        new File(project, 'grailsw.bat').bytes != earlierBatch
        new File(project, 'grailsw.bat').bytes != inJar('grailsw.bat')

        when: 'the wrapper is run once more'
        Map second = batch(project, [GRAILS_REPO_URL: repository.path, GRAILS_WRAPPER_HOME: new File(dir, 'home').path], 'stats')

        then: 'it is the wrapper of this version in all its files, which runs the command and replaces nothing'
        assert second.status == 0: said(second, project)
        assert second.out.contains('| Totals '): said(second, project)
        !second.out.contains('The Grails wrapper of')
        assert WRAPPER.every { String name -> new File(project, name).bytes == inJar(name) }: said(second, project)
        assert !project.list().any { String name -> name.endsWith('.new') || name.endsWith('.tmp') }: said(second, project)

        when:
        Map third = batch(project, [GRAILS_REPO_URL: repository.path, GRAILS_WRAPPER_HOME: new File(dir, 'home').path], 'update-wrapper')

        then:
        assert third.status == 0: said(third, project)
        assert third.out.readLines().last() == "The Grails wrapper of the project in ${project.path} is that of Grails ${cliVersion()}: nothing was changed": said(third, project)
    }

    @Requires({ os.windows })
    void "on Windows a jar that was written beside the one in use is moved over it by the batch file of this version before Java is started"() {
        given: 'what update-wrapper leaves when the wrapper was not started by a batch file: the scripts of this version, and the new jar beside the earlier one'
        earlierWrapperIn(project)
        new File(project, 'grailsw.bat').bytes = inJar('grailsw.bat')
        new File(project, 'grails-wrapper.jar.new').bytes = inJar('grails-wrapper.jar')

        when:
        Map result = batch(project, [GRAILS_REPO_URL: repository.path, GRAILS_WRAPPER_HOME: new File(dir, 'home').path, (CLI_JAR_VARIABLE): allJar.path], 'stats')

        then: 'the wrapper that ran is the one of this version: the earlier one would have said that it replaces the wrapper'
        assert result.status == 0: said(result, project)
        assert result.out.contains('| Totals ') && !result.out.contains('The Grails wrapper of'): said(result, project)
        assert new File(project, 'grails-wrapper.jar').bytes == inJar('grails-wrapper.jar') && !new File(project, 'grails-wrapper.jar.new').exists(): said(result, project)
    }

    @Requires({ os.windows })
    void "on Windows the batch file of this version is replaced while the command interpreter reads it, and the run ends with its exit code"() {
        given: 'the wrapper of this version, with a batch file of another release of it: every line is somewhere else'
        WRAPPER.each { String name -> new File(project, name).bytes = inJar(name) }
        String other = new String(inJar('grailsw.bat'), 'ISO-8859-1').replace('@rem  grailsw startup script for Windows', '@rem  grailsw startup script for Windows, of another release ' + ('x' * 700))
        assert other != new String(inJar('grailsw.bat'), 'ISO-8859-1')
        new File(project, 'grailsw.bat').setText(other, 'ISO-8859-1')
        Map<String, String> environment = [GRAILS_REPO_URL: repository.path, GRAILS_WRAPPER_HOME: new File(dir, 'home').path]

        when:
        Map result = batch(project, environment, 'update-wrapper')

        then:
        assert result.status == 0: said(result, project)
        assert result.out.readLines().last() == "The Grails wrapper of the project in ${project.path} is now that of Grails ${cliVersion()}: grailsw.bat was replaced. Commit it.": said(result, project)
        assert result.err.isEmpty(): said(result, project)
        new File(project, 'grailsw.bat').bytes == inJar('grailsw.bat')
        !project.list().any { String name -> name.endsWith('.new') }

        when: 'a command that fails, typed to a batch file that is replaced under it again'
        new File(project, 'grailsw.bat').setText(other, 'ISO-8859-1')
        Map failing = batch(project, environment, 'update-wrapper', 'stats')

        then: 'the exit code is that of the command'
        assert failing.status == 2: said(failing, project)
        failing.err.contains('update-wrapper takes no argument stats')
    }

    // on Windows the JVM of the wrapper has its jar open, and the system replaces no file that is: the new one is beside it
    private static boolean jarIsInUse() {
        return File.separatorChar == ('\\' as char)
    }

    private static String nowOrBeing() {
        return jarIsInUse() ? 'being replaced by' : 'now'
    }

    // the wrapper is that of this version, where the jar is in use with the new one beside it for the next run
    private boolean isOfThisVersion(File directory) {
        return WRAPPER.every { String name ->
            File beside = new File(directory, name + '.new')
            assert beside.exists() == (jarIsInUse() && name == 'grails-wrapper.jar'): "${beside} in ${directory.list().sort()}"
            (beside.exists() ? beside : new File(directory, name)).bytes == inJar(name)
        }
    }

    // what a run through a batch file said and left, for the message of an assertion
    private static String said(Map result, File directory) {
        return "exit code ${result.status}\n--- output\n${result.out}\n--- errors\n${result.err}\n--- ${directory}\n${directory.list().sort().join('\n')}\n" +
                "--- grailsw.bat\n${new File(directory, 'grailsw.bat').exists() ? new File(directory, 'grailsw.bat').getText('ISO-8859-1') : 'is not there'}"
    }

    // runs grailsw.bat of the directory with the command interpreter, as a user who types grailsw does
    private Map batch(File directory, Map<String, String> environment, String... args) {
        File userHome = new File(dir, 'user-home')
        userHome.mkdirs()
        List<String> command = [System.getenv('ComSpec') ?: 'cmd.exe', '/d', '/c', 'grailsw.bat']
        command.addAll(args)
        File out = File.createTempFile('out', '.txt', dir)
        File err = File.createTempFile('err', '.txt', dir)
        ProcessBuilder builder = new ProcessBuilder(command).directory(directory).redirectOutput(out).redirectError(err)
        ['GRAILS_REPO_URL', 'GRAILS_WRAPPER_HOME', 'PREFERRED_GRAILS_VERSION', 'GRAILS_WRAPPER_ALLOWED_TYPES', 'GRAILS_SAFE_DIRECTORY', 'JAVA_TOOL_OPTIONS', 'GRAILSW_OPTS', 'DEBUG'].each { String name ->
            builder.environment().remove(name)
        }
        builder.environment().putAll(environment)
        builder.environment().put('JAVA_HOME', System.getProperty('java.home'))
        // no wrapper of these features asks the network for anything: one that did would be refused at once
        builder.environment().put('JAVA_OPTS', "\"-Duser.home=${userHome.path}\" -Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=9 -Dhttp.proxyHost=127.0.0.1 -Dhttp.proxyPort=9".toString())
        Process process = builder.start()
        try {
            assert process.waitFor(180, TimeUnit.SECONDS): "the wrapper did not end:\n${out.text}\n${err.text}"
            return [status: process.exitValue(), out: out.text, err: err.text]
        } finally {
            process.descendants().forEach { ProcessHandle each -> each.destroyForcibly() }
            process.destroyForcibly()
        }
    }

    /**
     * The batch file of the wrapper of Grails 7 and 8 in what matters here, as the start script template of
     * Gradle 8 made it: the line that starts Java is a command of its own, and the command interpreter reads
     * what follows it from the file once Java has ended.
     */
    private static String earlierBatchFile() {
        List<String> lines = ['@rem'] + (1..16).collect { int number -> "@rem line ${number} of the licence header the release has, which is as long as this".toString() } +
                ['',
                 '@if "%DEBUG%"=="" @echo off',
                 '@rem Set local scope for the variables with windows NT shell',
                 'if "%OS%"=="Windows_NT" setlocal',
                 '',
                 'set DIRNAME=%~dp0',
                 'if "%DIRNAME%"=="" set DIRNAME=.',
                 'set APP_HOME=%DIRNAME%',
                 'for %%i in ("%APP_HOME%") do set APP_HOME=%%~fi',
                 'set DEFAULT_JVM_OPTS=',
                 '',
                 'set JAVA_HOME=%JAVA_HOME:"=%',
                 'set JAVA_EXE=%JAVA_HOME%/bin/java.exe',
                 '',
                 ':execute',
                 'set CLASSPATH=%APP_HOME%\\grails-wrapper.jar',
                 '',
                 '@rem Execute grailsw',
                 '"%JAVA_EXE%" %DEFAULT_JVM_OPTS% %JAVA_OPTS% %GRAILSW_OPTS%  -classpath "%CLASSPATH%" grails.init.Start %*',
                 '',
                 ':end',
                 '@rem End local scope for the variables with windows NT shell',
                 'if %ERRORLEVEL% equ 0 goto mainEnd',
                 '',
                 ':fail',
                 'rem Set variable GRAILSW_EXIT_CONSOLE if you need the _script_ return code instead of',
                 'rem the _cmd.exe /c_ return code!',
                 'set EXIT_CODE=%ERRORLEVEL%',
                 'if %EXIT_CODE% equ 0 set EXIT_CODE=1',
                 'if not ""=="%GRAILSW_EXIT_CONSOLE%" exit %EXIT_CODE%',
                 'exit /b %EXIT_CODE%',
                 '',
                 ':mainEnd',
                 'if "%OS%"=="Windows_NT" endlocal',
                 '',
                 ':omega',
                 '']
        return lines.join('\r\n')
    }

    // the version the command line says it is of, which is that of this build and not the one the repository of the test names
    private String cliVersion() {
        return new JarFile(allJar).withCloseable { JarFile jar -> jar.manifest.mainAttributes.getValue('Implementation-Version') }
    }

    private byte[] inJar(String name) {
        return new JarFile(allJar).withCloseable { JarFile jar -> jar.getInputStream(jar.getEntry("grails-wrapper/${name}")).bytes }
    }

    private void earlierWrapperIn(File directory) {
        Files.copy(earlierWrapperJar.toPath(), new File(directory, 'grails-wrapper.jar').toPath(), StandardCopyOption.REPLACE_EXISTING)
        new File(directory, 'grailsw').text = '#!/bin/sh\n# the script of Grails 8\n'
        new File(directory, 'grailsw').setExecutable(true)
        new File(directory, 'grailsw.bat').text = '@rem the script of Grails 8\r\n'
    }

    // runs the fixture of the earlier wrapper, which has the jar of the command line named to it
    private Map earlier(File directory, String... args) {
        assert new File(directory, 'grails-wrapper.jar').bytes == earlierWrapperJar.bytes
        return start(directory, [(CLI_JAR_VARIABLE): allJar.path], args)
    }

    private Map current(File directory, String... args) {
        return current(directory, new File(dir, 'home'), args)
    }

    // runs the grails-wrapper.jar of the directory as it is, which downloads the command line from the repository
    private Map current(File directory, File home, String... args) {
        home.mkdirs()
        return start(directory, [GRAILS_REPO_URL: repository.path, GRAILS_WRAPPER_HOME: home.path], args)
    }

    private Map start(File directory, Map<String, String> environment, String... args) {
        File userHome = new File(dir, 'user-home')
        userHome.mkdirs()
        // no wrapper of these features asks the network for anything: one that did would be refused at once
        List<String> command = [new File(System.getProperty('java.home'), 'bin/java').path, "-Duser.home=${userHome.path}".toString(),
                                '-Dhttps.proxyHost=127.0.0.1', '-Dhttps.proxyPort=9', '-Dhttp.proxyHost=127.0.0.1', '-Dhttp.proxyPort=9',
                                '-cp', new File(directory, 'grails-wrapper.jar').path, 'grails.init.Start']
        command.addAll(args)
        File out = File.createTempFile('out', '.txt', dir)
        File err = File.createTempFile('err', '.txt', dir)
        ProcessBuilder builder = new ProcessBuilder(command).directory(directory).redirectOutput(out).redirectError(err)
        ['GRAILS_REPO_URL', 'GRAILS_WRAPPER_HOME', 'PREFERRED_GRAILS_VERSION', 'GRAILS_WRAPPER_ALLOWED_TYPES', 'GRAILS_SAFE_DIRECTORY', 'JAVA_TOOL_OPTIONS'].each { String name ->
            builder.environment().remove(name)
        }
        builder.environment().putAll(environment)
        // the input stays open, as that of a terminal does
        Process process = builder.start()
        try {
            assert process.waitFor(180, TimeUnit.SECONDS): "the wrapper did not end:\n${out.text}\n${err.text}"
            return [status: process.exitValue(), out: out.text, err: err.text]
        } finally {
            process.destroyForcibly()
        }
    }

    /**
     * A {@code grails-wrapper.jar} that starts the command line as the wrapper of Grails 7 and 8 does: its class
     * {@code grails.init.Start} is the start sequence of {@code grails.init.GrailsWrapper.execute} of those versions,
     * without the download, for which the jar is named in the environment.
     */
    private static File earlierWrapper(File directory, String version) {
        File source = new File(directory, 'sources/grails/init/Start.java')
        source.parentFile.mkdirs()
        source.text = """\
            package grails.init;

            import java.io.File;
            import java.lang.reflect.Method;
            import java.net.URL;
            import java.net.URLClassLoader;
            import java.util.Arrays;

            public class Start {

                public static void main(String[] args) {
                    try {
                        boolean forceUpdate = (args.length > 0 && args[0].trim().equals("update-wrapper"));
                        String[] adjustedArgs = args;
                        if (forceUpdate) {
                            adjustedArgs = Arrays.copyOfRange(args, 1, args.length);
                        }
                        File executedJarFile = new File(System.getenv("${CLI_JAR_VARIABLE}"));
                        URLClassLoader child = new URLClassLoader(new URL[]{executedJarFile.toURI().toURL()});
                        Class<?> classToLoad = Class.forName("${BRIDGE}", true, child);
                        Method main = classToLoad.getMethod("main", String[].class);
                        main.invoke(null, (Object) adjustedArgs);
                    } catch (Exception e) {
                        System.out.println("Unable to execute Grails Wrapper: " + e.getMessage());
                        e.printStackTrace();
                        System.exit(1);
                    }
                }
            }
            """.stripIndent()
        File classes = new File(directory, 'classes')
        classes.mkdirs()
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler()
        assert compiler.run(null, null, null, '-d', classes.path, source.path) == 0

        Manifest manifest = new Manifest()
        manifest.mainAttributes.put(Attributes.Name.MANIFEST_VERSION, '1.0')
        manifest.mainAttributes.put(Attributes.Name.MAIN_CLASS, 'grails.init.Start')
        manifest.mainAttributes.put(Attributes.Name.IMPLEMENTATION_VERSION, version)
        File jar = new File(directory, 'grails-wrapper.jar')
        jar.withOutputStream { OutputStream stream ->
            JarOutputStream written = new JarOutputStream(stream, manifest)
            written.putNextEntry(new JarEntry('grails/init/Start.class'))
            written << new File(classes, 'grails/init/Start.class').bytes
            written.closeEntry()
            written.close()
        }
        return jar
    }
}
