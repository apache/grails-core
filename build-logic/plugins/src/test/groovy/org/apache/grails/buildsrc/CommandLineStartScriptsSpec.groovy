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
package org.apache.grails.buildsrc

import java.nio.file.Files

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.tasks.application.CreateStartScripts
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.IgnoreIf
import spock.lang.Requires
import spock.lang.Specification
import spock.lang.Unroll

class CommandLineStartScriptsSpec extends Specification {

    static final String DEFAULTS = 'DEFAULT_JVM_OPTS=\'"--enable-native-access=ALL-UNNAMED"\''
    static final String START = 'GRAILS_SHORT_RUN='

    static final String UNIX = '#!/bin/sh\n' +
            '# Add default JVM options here. You can also use JAVA_OPTS and GRAILS_OPTS to pass JVM options to this script.\n' +
            DEFAULTS + '\n' +
            '\n' +
            'exec "$JAVACMD" "$@"\n'

    static final String WINDOWS = '@rem Add default JVM options here.\r\n' +
            'set DEFAULT_JVM_OPTS="--enable-native-access=ALL-UNNAMED"\r\n' +
            '\r\n' +
            '"%JAVA_EXE%" %DEFAULT_JVM_OPTS% %JAVA_OPTS% %GRAILS_OPTS% -classpath "%CLASSPATH%" Main %*\r\n'

    void "the option is put in front of the options a script has by itself, for a start with an argument that is not --plain-output and a command that is not mcp"() {
        when:
        List<String> lines = CommandLineStartScripts.unix(UNIX, 'GRAILS_OPTS').readLines()
        int defaults = lines.indexOf(DEFAULTS)
        int start = lines.indexOf(START)

        then:
        lines[defaults + 1].startsWith('# -XX:TieredStopAtLevel=1 for a command line that runs one command')
        start > defaults
        lines.subList(start, start + 13) == [
                START,
                'for GRAILS_ARGUMENT in "$@" ; do',
                '    case $GRAILS_ARGUMENT in',
                '        --plain-output) ;;',
                '        mcp) GRAILS_SHORT_RUN= ; break ;;',
                '        -*) GRAILS_SHORT_RUN=1 ;;',
                '        *) GRAILS_SHORT_RUN=1 ; break ;;',
                '    esac',
                'done',
                'if [ -n "$GRAILS_SHORT_RUN" ] ; then',
                '    DEFAULT_JVM_OPTS="-XX:TieredStopAtLevel=1 $DEFAULT_JVM_OPTS"',
                'fi',
                'unset GRAILS_ARGUMENT GRAILS_SHORT_RUN',
        ]
        lines.any { String line -> line.contains('SIGNIFICANTLY SLOWER') }
        lines.any { String line -> line.contains('GRAILS_OPTS=-XX:TieredStopAtLevel=4') }
        lines.any { String line -> line.contains('without an argument or with --plain-output alone, and mcp, the server an agent starts') }
        lines.any { String line -> line.contains('unless the command (the first argument that is no option) is mcp') }
        lines.last() == 'exec "$JAVACMD" "$@"'
    }

    @IgnoreIf({ os.windows })
    @Unroll
    void "a script started with #arguments has the option: #given"() {
        given: 'the script, which shows the options instead of starting Java'
        File script = File.createTempFile('grailsstart', '.sh')
        script.text = CommandLineStartScripts.unix(UNIX, 'GRAILS_OPTS').replace('exec "$JAVACMD" "$@"', 'printf "%s\\n" "$DEFAULT_JVM_OPTS"')

        when:
        Process process = new ProcessBuilder(['/bin/sh', script.path] + arguments).redirectErrorStream(true).start()
        String said = process.inputStream.text.trim()

        then:
        process.waitFor() == 0
        said.startsWith('-XX:TieredStopAtLevel=1 ') == given
        said.endsWith('"--enable-native-access=ALL-UNNAMED"')

        cleanup:
        script?.delete()

        where:
        arguments                                       | given
        []                                              | false
        ['--plain-output']                              | false
        ['--plain-output', '--plain-output']            | false
        ['--plain-output', 'compile']                   | true
        ['compile', '--plain-output']                   | true
        ['--non-interactive']                           | true
        ['--non-interactive', 'compile']                | true
        ['*']                                           | true
        ['']                                            | true
        ['two words']                                   | true
        ['mcp']                                         | false
        ['--plain-output', 'mcp']                       | false
        ['mcp', '--read-only']                          | false
        ['--non-interactive', 'mcp']                    | false
        ['-x', '--verbose', '--plain-output', 'mcp']    | false
        ['compile', 'mcp']                              | true
        ['create-app', 'mcp']                           | true
        ['mcp-server']                                  | true
    }

    @IgnoreIf({ os.windows })
    void "a variable of the environment that has the name of one of the script's own decides nothing, and is not handed on"() {
        given: 'the script, which shows the options and the environment instead of starting Java'
        File script = File.createTempFile('grailsstart', '.sh')
        script.text = CommandLineStartScripts.unix(UNIX, 'GRAILS_OPTS').replace('exec "$JAVACMD" "$@"',
                'printf "%s\\n" "$DEFAULT_JVM_OPTS"; env | grep "^GRAILS_" || :')

        when: 'started for the interactive mode, by a user who has the variable set'
        ProcessBuilder builder = new ProcessBuilder(['/bin/sh', script.path]).redirectErrorStream(true)
        builder.environment().keySet().removeIf { String name -> name.startsWith('GRAILS_') }
        builder.environment().put('GRAILS_SHORT_RUN', '1')
        builder.environment().put('GRAILS_ARGUMENT', 'compile')
        Process process = builder.start()
        List<String> said = process.inputStream.text.readLines()

        then:
        process.waitFor() == 0
        said == ['"--enable-native-access=ALL-UNNAMED"']

        cleanup:
        script?.delete()
    }

    void "the batch file keeps its line ends, names the variable of its own script, and compares each argument"() {
        when:
        String script = CommandLineStartScripts.windows(WINDOWS, 'GRAILSW_OPTS')

        then:
        !script.replace('\r\n', '').contains('\n')
        script.contains('set GRAILS_SHORT_RUN=\r\n' +
                ':grailsNextArgument\r\n' +
                'set GRAILS_ARGUMENT=%1\r\n' +
                'if not defined GRAILS_ARGUMENT goto grailsArgumentsRead\r\n' +
                'setlocal EnableDelayedExpansion\r\n' +
                'set GRAILS_INTERACTIVE_OPTION=\r\n' +
                'if "!GRAILS_ARGUMENT!"=="--plain-output" set GRAILS_INTERACTIVE_OPTION=1\r\n' +
                'if "!GRAILS_ARGUMENT!"=="mcp" (endlocal & set GRAILS_SHORT_RUN=& goto grailsArgumentsRead)\r\n' +
                'if not defined GRAILS_INTERACTIVE_OPTION if "!GRAILS_ARGUMENT:~0,1!"=="-" (endlocal & set GRAILS_SHORT_RUN=1& shift & goto grailsNextArgument)\r\n' +
                'if not defined GRAILS_INTERACTIVE_OPTION (endlocal & set GRAILS_SHORT_RUN=1& goto grailsArgumentsRead)\r\n' +
                'endlocal\r\n' +
                'shift\r\n' +
                'goto grailsNextArgument\r\n' +
                ':grailsArgumentsRead\r\n' +
                'if defined GRAILS_SHORT_RUN set DEFAULT_JVM_OPTS=-XX:TieredStopAtLevel=1 %DEFAULT_JVM_OPTS%\r\n' +
                'set GRAILS_ARGUMENT=\r\n' +
                'set GRAILS_SHORT_RUN=\r\n')
        script.contains('@rem To override it: set GRAILSW_OPTS=-XX:TieredStopAtLevel=4')
        script.indexOf('set GRAILS_SHORT_RUN=') > script.indexOf('set DEFAULT_JVM_OPTS="--enable-native-access=ALL-UNNAMED"')
        script.indexOf('set GRAILS_SHORT_RUN=') < script.indexOf('"%JAVA_EXE%"')
    }

    void "a script that has no such line, or more than one, is not changed: the build fails"() {
        when:
        CommandLineStartScripts.unix(script, 'GRAILS_OPTS')

        then:
        GradleException e = thrown()
        e.message.contains('The start scripts Gradle generates have changed')

        where:
        script << ['#!/bin/sh\nexec java\n', UNIX + UNIX]
    }

    // the lines of the script Gradle generates that find the java command and start it, which the check goes between
    static final String UNIX_JAVA = '#!/bin/sh\n' +
            'APP_BASE_NAME=${0##*/}\n' +
            'die () {\n' +
            '    echo\n' +
            '    echo "$*"\n' +
            '    echo\n' +
            '    exit 1\n' +
            '} >&2\n' +
            'if [ -n "$JAVA_HOME" ] ; then\n' +
            '    JAVACMD=$JAVA_HOME/bin/java\n' +
            'else\n' +
            '    JAVACMD=java\n' +
            'fi\n' +
            '\n' +
            '# Increase the maximum file descriptors if we can.\n' +
            'exec "$JAVACMD" "$@"\n'

    static final String WINDOWS_JAVA = '@if "%DEBUG%"=="" @echo off\r\n' +
            'setlocal EnableExtensions\r\n' +
            'set APP_BASE_NAME=%~n0\r\n' +
            'if defined JAVA_HOME goto findJavaFromJavaHome\r\n' +
            'set JAVA_EXE=java.exe\r\n' +
            'goto execute\r\n' +
            ':findJavaFromJavaHome\r\n' +
            'set JAVA_HOME=%JAVA_HOME:"=%\r\n' +
            'set JAVA_EXE=%JAVA_HOME%/bin/java.exe\r\n' +
            'if exist "%JAVA_EXE%" goto execute\r\n' +
            '"%COMSPEC%" /c exit 1\r\n' +
            'goto exitWithErrorLevel\r\n' +
            '\r\n' +
            ':execute\r\n' +
            '@rem Setup the command line\r\n' +
            'echo started %JAVA_EXE%\r\n' +
            '"%COMSPEC%" /c exit 0\r\n' +
            'goto exitWithErrorLevel\r\n' +
            '\r\n' +
            ':exitWithErrorLevel\r\n' +
            '"%COMSPEC%" /c exit %ERRORLEVEL%\r\n'

    @IgnoreIf({ os.windows })
    @Unroll
    void "a script started with a Java whose release file says #release is #what"() {
        given: 'a Java that says it was started'
        File home = File.createTempDir('grailsjava', '')
        javaIn(home)
        if (release != null) {
            new File(home, 'release').text = "IMPLEMENTOR=\"Someone\"\n${release}\nOS_NAME=\"Linux\"\n"
        }

        when:
        Map started = startUnix(['--version'], [JAVA_HOME: home.path])

        then:
        started.exit == (refused ? 1 : 0)
        (started.said as String).contains("started --version") == !refused
        !refused || (started.said as String).readLines() == ['', "ERROR: grails needs Java 21 or later, and JAVA_HOME (${home.path}) is Java ${version}.".toString(), '',
                                                              'Please set the JAVA_HOME variable in your environment to the location of',
                                                              'a Java 21 or later installation.', '']

        cleanup:
        home?.deleteDir()

        where:
        release                                       | refused | version      | what
        'JAVA_VERSION="17.0.2"'                       | true    | '17.0.2'     | 'refused'
        'JAVA_VERSION="11"'                           | true    | '11'         | 'refused'
        'JAVA_VERSION="1.8.0_402"'                    | true    | '1.8.0_402'  | 'refused'
        'JAVA_VERSION="20.0.2"'                       | true    | '20.0.2'     | 'refused'
        'JAVA_VERSION="21.0.12"'                      | false   | null         | 'started'
        'JAVA_VERSION="21"'                           | false   | null         | 'started'
        'JAVA_VERSION="26-ea"'                        | false   | null         | 'started'
        'JAVA_VERSION="100.1"'                        | false   | null         | 'started'
        'JAVA_RUNTIME_VERSION="17.0.2+8"'             | false   | null         | 'started: it names no JAVA_VERSION'
        'JAVA_VERSION="unknown"'                      | false   | null         | 'started: it is no version'
        null                                          | false   | null         | 'started: there is no release file'
    }

    @IgnoreIf({ os.windows })
    @Unroll
    void "a JDK 8 without a release file is told by its #jar"() {
        given:
        File jdk = File.createTempDir('grailsjava', '')
        javaIn(jdk)
        new File(jdk, jar).parentFile.mkdirs()
        new File(jdk, jar).text = ''

        when:
        Map started = startUnix([], [JAVA_HOME: jdk.path])

        then:
        started.exit == 1
        (started.said as String).contains("ERROR: grails needs Java 21 or later, and JAVA_HOME (${jdk.path}) is Java 8 or earlier.")

        cleanup:
        jdk?.deleteDir()

        where:
        jar << ['jre/lib/rt.jar', 'lib/rt.jar']
    }

    @IgnoreIf({ os.windows })
    void "the jre directory of a JDK 8, which has no release file, is told by its rt.jar"() {
        given:
        File jdk = File.createTempDir('grailsjava', '')
        File jre = new File(jdk, 'jre')
        javaIn(jre)
        new File(jre, 'lib').mkdirs()
        new File(jre, 'lib/rt.jar').text = ''
        new File(jdk, 'release').text = 'JAVA_VERSION="1.8.0_402"\n'

        when:
        Map started = startUnix([], [JAVA_HOME: jre.path])

        then:
        started.exit == 1
        (started.said as String).contains("ERROR: grails needs Java 21 or later, and JAVA_HOME (${jre.path}) is Java 8 or earlier.")

        cleanup:
        jdk?.deleteDir()
    }

    @IgnoreIf({ os.windows })
    @Unroll
    void "without JAVA_HOME, the java command on the PATH is followed through its links to the release file of its installation, #how"() {
        given: 'a java on the PATH that is a link to a link of the java of an installation, as /usr/bin/java is on Linux'
        File root = File.createTempDir('grailsjava', '')
        File home = new File(root, 'jvm/java-17')
        File java = javaIn(home)
        new File(home, 'release').text = 'JAVA_VERSION="17.0.2"\n'
        File alternatives = new File(root, 'alternatives')
        alternatives.mkdirs()
        Files.createSymbolicLink(new File(alternatives, 'java').toPath(), java.toPath())
        File bin = new File(root, 'bin')
        bin.mkdirs()
        Files.createSymbolicLink(new File(bin, 'java').toPath(), bin.toPath().relativize(new File(alternatives, 'java').toPath()))
        // a readlink that has no -f, as that of macOS before 12.3
        File tools = new File(root, 'tools')
        tools.mkdirs()
        if (!readlinkF) {
            new File(tools, 'readlink').text = '#!/bin/sh\necho "readlink: illegal option -- f" >&2\nexit 1\n'
            new File(tools, 'readlink').setExecutable(true)
        }
        String path = "${bin.path}:${tools.path}:/usr/bin:/bin".toString()

        when:
        Map started = startUnix(['compile'], [PATH: path])

        then:
        started.exit == 1
        (started.said as String).contains("ERROR: grails needs Java 21 or later, and the java command on the PATH (${bin.path}/java) is Java 17.0.2.")

        when: 'the installation is of Java 21'
        new File(home, 'release').text = 'JAVA_VERSION="21.0.12"\n'
        started = startUnix(['compile'], [PATH: path])

        then:
        started.exit == 0
        (started.said as String).contains('started compile')

        cleanup:
        root?.deleteDir()

        where:
        readlinkF << [true, false]
        how = readlinkF ? 'with readlink -f' : 'with ls where readlink has no -f'
    }

    @IgnoreIf({ os.windows })
    void "without JAVA_HOME, a java on the PATH in the bin directory of an installation, also through a link to the directory, is read there"() {
        given: 'an installation reached through a link to its directory, as the current Java of SDKMAN'
        File root = File.createTempDir('grailsjava', '')
        File home = new File(root, 'candidates/17.0.2')
        javaIn(home)
        new File(home, 'release').text = 'JAVA_VERSION="17.0.2"\n'
        Files.createSymbolicLink(new File(root, 'candidates/current').toPath(), new File(root, 'candidates/17.0.2').toPath())
        File bin = new File(root, 'candidates/current/bin')

        when:
        Map started = startUnix([], [PATH: "${bin.path}:/usr/bin:/bin".toString()])

        then:
        started.exit == 1
        (started.said as String).contains("ERROR: grails needs Java 21 or later, and the java command on the PATH (${bin.path}/java) is Java 17.0.2.")

        cleanup:
        root?.deleteDir()
    }

    void "the check of the version of Java is between the lines that find Java and those that start it, and names the version of the build"() {
        when:
        List<String> unix = CommandLineStartScripts.unixJavaCheck(UNIX_JAVA, 25).readLines()
        String windows = CommandLineStartScripts.windowsJavaCheck(WINDOWS_JAVA, 25)
        List<String> batch = windows.split('\r\n').toList()

        then:
        unix.indexOf('if [ -n "$JAVA_HOME" ] ; then') < unix.indexOf('if [ -n "$GRAILS_JAVA_MAJOR" ] && [ "$GRAILS_JAVA_MAJOR" -lt 25 ] ; then')
        unix.indexOf('if [ -n "$GRAILS_JAVA_MAJOR" ] && [ "$GRAILS_JAVA_MAJOR" -lt 25 ] ; then') < unix.indexOf('# Increase the maximum file descriptors if we can.')
        unix.any { String line -> line.contains('needs Java 25 or later') }

        and: 'the batch file keeps its line ends, and checks once java.exe is found, before the command line is set up'
        !windows.replace('\r\n', '').contains('\n')
        batch.indexOf(':execute') < batch.indexOf('if defined GRAILS_JAVA_MAJOR if %GRAILS_JAVA_MAJOR% LSS 25 goto grailsJavaTooOld')
        batch.indexOf(':grailsJavaChecked') < batch.indexOf('@rem Setup the command line')
        batch.any { String line -> line.contains('needs Java 25 or later') }
        // nothing reads the numbered arguments, which the lines before may have shifted
        !batch.any { String line -> line ==~ /.*%[1-9].*/ }
    }

    @Requires({ os.windows })
    @Unroll
    void "a batch file started with a Java whose release file says #release is #what"() {
        given:
        File home = File.createTempDir('grailsjava', '')
        new File(home, 'bin').mkdirs()
        new File(home, 'bin/java.exe').text = ''
        if (release != null) {
            new File(home, 'release').text = "IMPLEMENTOR=\"Someone\"\r\n${release}\r\n"
        }
        File script = new File(home, 'grails.bat')
        script.text = CommandLineStartScripts.windowsJavaCheck(WINDOWS_JAVA, 21)

        when:
        ProcessBuilder builder = new ProcessBuilder(['cmd.exe', '/c', script.path]).redirectErrorStream(true)
        builder.environment().put('JAVA_HOME', home.path)
        Process process = builder.start()
        String said = process.inputStream.text
        int exit = process.waitFor()

        then:
        exit == (refused ? 1 : 0)
        said.contains('started ') == !refused
        !refused || said.contains("ERROR: grails needs Java 21 or later, and JAVA_HOME (${home.path}) is Java ${version}.")

        cleanup:
        home?.deleteDir()

        where:
        release                     | refused | version     | what
        'JAVA_VERSION="17.0.2"'     | true    | '17.0.2'    | 'refused'
        'JAVA_VERSION="1.8.0_402"'  | true    | '1.8.0_402' | 'refused'
        'JAVA_VERSION="21.0.12"'    | false   | null        | 'started'
        'JAVA_VERSION="26-ea"'      | false   | null        | 'started'
        null                        | false   | null        | 'started: there is no release file'
    }

    @Requires({ os.windows })
    void "a batch file started with a JDK 8 without a release file tells it by its rt.jar"() {
        given:
        File home = File.createTempDir('grailsjava', '')
        new File(home, 'bin').mkdirs()
        new File(home, 'bin/java.exe').text = ''
        new File(home, 'jre/lib').mkdirs()
        new File(home, 'jre/lib/rt.jar').text = ''
        File script = new File(home, 'grails.bat')
        script.text = CommandLineStartScripts.windowsJavaCheck(WINDOWS_JAVA, 21)

        when:
        ProcessBuilder builder = new ProcessBuilder(['cmd.exe', '/c', script.path]).redirectErrorStream(true)
        builder.environment().put('JAVA_HOME', home.path)
        Process process = builder.start()
        String said = process.inputStream.text

        then:
        process.waitFor() == 1
        said.contains("ERROR: grails needs Java 21 or later, and JAVA_HOME (${home.path}) is Java 8 or earlier.")

        cleanup:
        home?.deleteDir()
    }

    // a java that says what it was started with, in the bin directory of an installation
    private static File javaIn(File home) {
        File java = new File(home, 'bin/java')
        java.parentFile.mkdirs()
        java.text = '#!/bin/sh\necho started "$@"\n'
        java.setExecutable(true)
        return java
    }

    // the script with the check, named as the command line is, started with the environment it is given
    private static Map startUnix(List<String> arguments, Map<String, String> environment) {
        File directory = File.createTempDir('grailsstart', '')
        File script = new File(directory, 'grails')
        script.text = CommandLineStartScripts.unixJavaCheck(UNIX_JAVA, 21)
        script.setExecutable(true)
        try {
            ProcessBuilder builder = new ProcessBuilder(['/bin/sh', script.path] + arguments).redirectErrorStream(true)
            builder.environment().remove('JAVA_HOME')
            builder.environment().putAll(environment)
            Process process = builder.start()
            String said = process.inputStream.text
            return [exit: process.waitFor(), said: said]
        } finally {
            directory.deleteDir()
        }
    }

    void "the variable the scripts name is an input of the task, so that another one writes them again"() {
        given:
        Project project = ProjectBuilder.builder().build()
        CreateStartScripts task = project.tasks.register('startScripts', CreateStartScripts).get()

        when:
        CommandLineStartScripts.compileForShortRuns(task, 'GRAILSW_OPTS')

        then:
        task.inputs.properties.shortRunOptionsVariable == 'GRAILSW_OPTS'
        task.inputs.properties.shortRunOption == CommandLineStartScripts.OPTION
    }
}
