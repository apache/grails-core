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

import groovy.transform.CompileStatic

import org.gradle.api.GradleException
import org.gradle.api.Task
import org.gradle.api.tasks.application.CreateStartScripts

/**
 * What the start scripts of the Grails command line ({@code grails}, {@code grails.bat}) and of the Grails
 * wrapper ({@code grailsw}, {@code grailsw.bat}) have besides what Gradle generates: the JVM option
 * {@code -XX:TieredStopAtLevel=1} for a command line that runs one command, and a check of the version of the
 * Java they start, see {@link #requireJava(CreateStartScripts)}.
 *
 * <p><strong>Why.</strong> A command of the command line is a short-lived process. Its time is the start of
 * the JVM and the loading of classes, not the running of hot code: the work of a command itself takes
 * 15 to 35 ms of some 450 to 650. With the option the just-in-time compiler stops at C1 and never starts the
 * optimizing compiler (C2), whose compiler threads otherwise compete with the command for a result the
 * process does not live to use. Measured on one machine (macOS, 16 cores, JDK 21, cold JVM, medians):
 * {@code --version} 405 to 340 ms, {@code --help} in a project 533 to 434 ms, {@code create-controller}
 * 566 to 447 ms, {@code create-app} 1524 to 1305 ms, and the CPU time of {@code create-app} 7.7 s to 1.9 s.</p>
 *
 * <p><strong>The cost.</strong> Code that runs long and hot is significantly slower when it is compiled by
 * C1 alone than when C2 compiles it: a loop that C2 would optimize can take twice as long. That is accepted
 * here because a command is short, and the long work it starts (a build, the tests, the application) runs in
 * the JVMs of Gradle and of the application, which are other processes and are not given the option: it is
 * in no environment variable, only on the command line of this one JVM. The one long-lived JVM of the
 * command line itself is the interactive mode, where a command that is repeated was measured slower with
 * the option once the JVM is warm ({@code stats} on 300 source files 44 to 78 ms, {@code create-app} 50 to
 * 59 ms, {@code create-controller} 16 to 22 ms). So the option is given only when the script is started
 * with an argument other than the options that leave the interactive mode to be started, which is a command
 * line that runs one command. The interactive mode is started without an argument, and with
 * {@code --plain-output} alone, as often as it is given: then the JVM compiles as it does by default. With
 * {@code --non-interactive} the command line shows its help and ends, so that is a command like any other.</p>
 *
 * <p><strong>The commands that run for long.</strong> {@code mcp} is a command that does not end: it is the server
 * an agent starts and talks to for as long as the agent runs, and it runs the commands it is sent in its own
 * JVM, as the interactive mode does. So the option is not given either when the command, the first argument
 * that is not an option ({@link #LONG_LIVED_COMMANDS}), is {@code mcp}: {@code mcp}, {@code --plain-output mcp}
 * and {@code mcp --read-only} start a JVM that compiles as it does by default, and {@code compile} one with the
 * option. An option in front of the command ({@code --non-interactive mcp}) does not change that; an option
 * alone is a command line like any other, as said above.</p>
 *
 * <p><strong>How it is overridden.</strong> The option comes first on the command line of the JVM, before
 * what {@code JAVA_OPTS} and {@code GRAILS_OPTS} (for the wrapper {@code GRAILSW_OPTS}) hold, and the JVM
 * takes the last value it is given: {@code GRAILS_OPTS=-XX:TieredStopAtLevel=4} restores the default.</p>
 *
 * <p>The same text, shorter, is written into the scripts beside the option, for whoever reads them.</p>
 */
@CompileStatic
class CommandLineStartScripts {

    /** The JVM option that stops the just-in-time compiler at C1. */
    static final String OPTION = '-XX:TieredStopAtLevel=1'

    private static final String UNIX_DEFAULTS = 'DEFAULT_JVM_OPTS='
    private static final String WINDOWS_DEFAULTS = 'set DEFAULT_JVM_OPTS='
    /**
     * The arguments that, alone, leave the command line to start the interactive mode: the script is started
     * with none of its own then, or with these only.
     */
    static final List<String> INTERACTIVE_OPTIONS = ['--plain-output']

    /**
     * The commands that do not end when their work is done, and so are not given the option: the command is the
     * first argument that does not begin with {@code -}, whatever options are in front of it.
     */
    static final List<String> LONG_LIVED_COMMANDS = ['mcp']

    private static final List<String> WHY = [
            OPTION + ' for a command line that runs one command: one that has an argument other than',
            INTERACTIVE_OPTIONS.join(', ') + ', unless the command (the first argument that is no option) is ' + LONG_LIVED_COMMANDS.join(', ') + '.',
            'Why: a command is a short-lived process whose time is the start of the JVM and the loading of',
            'classes. With C1 alone a command is 15-20% faster (create-app 1.5 to 1.3 s) and create-app',
            'takes a quarter of the CPU time, because the optimizing compiler (C2) is never started.',
            'The cost: code that runs long and hot is SIGNIFICANTLY SLOWER without C2. That is accepted',
            'because a command is short, and builds, tests and the application run in the JVMs of Gradle',
            'and of the application, which are not given this option. Interactive mode, which is started',
            'without an argument or with ' + INTERACTIVE_OPTIONS.join(', ') + ' alone, and ' + LONG_LIVED_COMMANDS.join(', ') +
                    ', the server an agent starts,',
            'are the long-lived JVMs of the command line, and are not given it either.',
    ]

    // the line of the script Gradle generates for a POSIX shell that follows the lines that find the java command
    private static final String UNIX_AFTER_JAVA = '# Increase the maximum file descriptors if we can.'

    // the label of the batch file Gradle generates that is reached once java.exe is found
    private static final String WINDOWS_JAVA_FOUND = ':execute'

    private CommandLineStartScripts() {
    }

    /**
     * Has a task write into the scripts it generates a check of the version of the Java they start. The classes the
     * scripts start are compiled for the release {@code javaVersion} of the build, which an earlier Java cannot load:
     * it ends with an {@code UnsupportedClassVersionError} before any class of the command line runs. The scripts
     * name that Java and the version that is needed instead.
     *
     * <p>No JVM is started for the check. The version is read from the {@code release} file of the installation of the
     * Java, which every installation of Java 9 or later has, and most of Java 8: {@code $JAVA_HOME/release}, or the one
     * of the {@code java} on the {@code PATH}. A {@code java} on the {@code PATH} that is a link, and is not in the
     * {@code bin} directory of an installation, is followed to the one it is of, with {@code readlink -f}; that is the
     * one process the check can start, the rest is builtins of the shell. A Java 8 without a {@code release} file, as
     * Corretto 8 or the {@code jre} directory of a JDK 8, is told by its {@code rt.jar}, which later versions do not
     * have. A Java whose installation has neither, as the stub {@code /usr/bin/java} of macOS when {@code JAVA_HOME} is
     * not set, is started as before: finding its installation would take another process on every start.</p>
     *
     * @param task the task that generates the scripts
     */
    static void requireJava(CreateStartScripts task) {
        Integer version = GradleUtils.lookupPropertyByType(task.project, 'javaVersion', Integer)
        if (version == null) {
            throw new GradleException('The start scripts check the version of Java against javaVersion of the build, which is not set')
        }
        int required = version
        task.inputs.property('requiredJavaVersion', required)
        task.doLast { Task generated ->
            CreateStartScripts scripts = (CreateStartScripts) generated
            scripts.unixScript.text = CommandLineStartScripts.unixJavaCheck(scripts.unixScript.text, required)
            scripts.windowsScript.text = CommandLineStartScripts.windowsJavaCheck(scripts.windowsScript.text, required)
        }
    }

    /**
     * @param script the script Gradle generates for a POSIX shell
     * @param version the lowest version of Java the classes it starts run on
     * @return the script with a check, after the java command is found, that the Java is not of an earlier version
     */
    static String unixJavaCheck(String script, int version) {
        String line = lineOf(script, UNIX_AFTER_JAVA, '\n', 'the check of the version of Java is added before the one that does')
        // Builtins of the shell only, but for a java on the PATH that is a link and is not in the bin directory of its
        // installation, which takes one readlink (or, where readlink has no -f, one ls for each link)
        List<String> added = [
                "# The classes this script starts are compiled for Java ${version}, which an earlier Java cannot load. Such a Java is".toString(),
                '# named here, from the release file of its installation, instead of failing with UnsupportedClassVersionError.',
                '# No JVM is started for the check; a Java whose version it cannot read is started as before.',
                'if [ -n "$JAVA_HOME" ] ; then',
                '    GRAILS_JAVA=$JAVA_HOME',
                '    GRAILS_JAVA_FOUND="JAVA_HOME ($JAVA_HOME)"',
                'else',
                '    # the java command on the PATH, as the shell finds it',
                '    GRAILS_JAVA=',
                '    GRAILS_JAVA_PATH=$PATH:',
                '    while [ -n "$GRAILS_JAVA_PATH" ] ; do',
                '        GRAILS_JAVA_LINK=${GRAILS_JAVA_PATH%%:*}',
                '        GRAILS_JAVA_LINK=${GRAILS_JAVA_LINK:-.}/java',
                '        GRAILS_JAVA_PATH=${GRAILS_JAVA_PATH#*:}',
                '        if [ -f "$GRAILS_JAVA_LINK" ] && [ -x "$GRAILS_JAVA_LINK" ] ; then',
                '            GRAILS_JAVA=$GRAILS_JAVA_LINK',
                '            break',
                '        fi',
                '    done',
                '    GRAILS_JAVA_FOUND="the java command on the PATH ($GRAILS_JAVA)"',
                '    # a link to the java of an installation, as /usr/bin/java on Linux, unless it is in the bin directory of one',
                '    if [ -h "$GRAILS_JAVA" ] && [ ! -f "${GRAILS_JAVA%/*}/../release" ] ; then',
                '        GRAILS_JAVA_LINK=$( readlink -f "$GRAILS_JAVA" 2>/dev/null ) && [ -n "$GRAILS_JAVA_LINK" ] && GRAILS_JAVA=$GRAILS_JAVA_LINK',
                '        while [ -h "$GRAILS_JAVA" ] ; do',
                '            GRAILS_JAVA_LINK=$( ls -ld "$GRAILS_JAVA" )',
                '            GRAILS_JAVA_LINK=${GRAILS_JAVA_LINK#*\' -> \'}',
                '            case $GRAILS_JAVA_LINK in',
                '              /*) GRAILS_JAVA=$GRAILS_JAVA_LINK ;;',
                '              *)  GRAILS_JAVA=${GRAILS_JAVA%/*}/$GRAILS_JAVA_LINK ;;',
                '            esac',
                '        done',
                '    fi',
                '    GRAILS_JAVA=${GRAILS_JAVA:+${GRAILS_JAVA%/*}/..}',
                'fi',
                'GRAILS_JAVA_VERSION=',
                'if [ -n "$GRAILS_JAVA" ] && [ -f "$GRAILS_JAVA/release" ] ; then',
                '    while IFS= read -r GRAILS_JAVA_LINE || [ -n "$GRAILS_JAVA_LINE" ] ; do',
                '        case $GRAILS_JAVA_LINE in',
                '          JAVA_VERSION=*) GRAILS_JAVA_VERSION=${GRAILS_JAVA_LINE#JAVA_VERSION=} ;;',
                '        esac',
                '    done < "$GRAILS_JAVA/release"',
                'fi',
                'GRAILS_JAVA_VERSION=${GRAILS_JAVA_VERSION#\\"}',
                'GRAILS_JAVA_VERSION=${GRAILS_JAVA_VERSION%\\"}',
                '# a Java 8 that has no release file, as Corretto 8 or the jre directory of a JDK 8, has the rt.jar that later',
                '# versions do not have',
                'if [ -n "$GRAILS_JAVA" ] && [ -z "$GRAILS_JAVA_VERSION" ] && { [ -f "$GRAILS_JAVA/lib/rt.jar" ] || [ -f "$GRAILS_JAVA/jre/lib/rt.jar" ] ; } ; then',
                '    GRAILS_JAVA_VERSION=\'8 or earlier\'',
                'fi',
                '# 1.8.0_402 is Java 8, 17.0.2 Java 17, 26-ea Java 26',
                'GRAILS_JAVA_MAJOR=${GRAILS_JAVA_VERSION#1.}',
                'GRAILS_JAVA_MAJOR=${GRAILS_JAVA_MAJOR%%[!0-9]*}',
                "if [ -n \"\$GRAILS_JAVA_MAJOR\" ] && [ \"\$GRAILS_JAVA_MAJOR\" -lt ${version} ] ; then".toString(),
                "    die \"ERROR: \$APP_BASE_NAME needs Java ${version} or later, and \$GRAILS_JAVA_FOUND is Java \$GRAILS_JAVA_VERSION.".toString(),
                '',
                'Please set the JAVA_HOME variable in your environment to the location of',
                "a Java ${version} or later installation.\"".toString(),
                'fi',
                'unset GRAILS_JAVA GRAILS_JAVA_FOUND GRAILS_JAVA_PATH GRAILS_JAVA_LINK GRAILS_JAVA_LINE GRAILS_JAVA_VERSION GRAILS_JAVA_MAJOR',
                '',
        ]
        return script.replace(line, added.join('\n') + '\n' + line)
    }

    /**
     * @param script the batch file Gradle generates
     * @param version the lowest version of Java the classes it starts run on
     * @return the batch file with a check, once java.exe is found, that the Java is not of an earlier version
     */
    static String windowsJavaCheck(String script, int version) {
        String end = script.contains('\r\n') ? '\r\n' : '\n'
        String line = lineOf(script, WINDOWS_JAVA_FOUND, end, 'the check of the version of Java is added after the one that does')
        if (line != WINDOWS_JAVA_FOUND) {
            throw new GradleException("The start scripts Gradle generates have changed: the label ${WINDOWS_JAVA_FOUND} has more on its line, " +
                    'and the check of the version of Java is added after it')
        }
        // What is read is held by a variable of FOR, whose value the command interpreter does not read again, until only
        // the major version is left. JAVA_HOME was read by the lines above, without its quotes.
        List<String> added = [
                "@rem The classes this script starts are compiled for Java ${version}, which an earlier Java cannot load. Such a Java is".toString(),
                '@rem named here, from the release file of its installation, instead of failing with UnsupportedClassVersionError.',
                '@rem No JVM is started for the check; a Java whose version it cannot read is started as before.',
                'set GRAILS_JAVA=',
                'set GRAILS_JAVA_FOUND=',
                'if defined JAVA_HOME set "GRAILS_JAVA=%JAVA_HOME%"',
                'if defined JAVA_HOME set "GRAILS_JAVA_FOUND=JAVA_HOME (%JAVA_HOME%)"',
                'if not defined JAVA_HOME for %%i in (java.exe) do if not "%%~$PATH:i"=="" set "GRAILS_JAVA=%%~dp$PATH:i.."',
                'if not defined JAVA_HOME for %%i in (java.exe) do if not "%%~$PATH:i"=="" set "GRAILS_JAVA_FOUND=the java command on the PATH (%%~$PATH:i)"',
                'set GRAILS_JAVA_VERSION=',
                'if defined GRAILS_JAVA if exist "%GRAILS_JAVA%\\release" for /f "usebackq tokens=1,* delims==" %%a in ("%GRAILS_JAVA%\\release") do ' +
                        'if "%%a"=="JAVA_VERSION" set "GRAILS_JAVA_VERSION=%%~b"',
                '@rem a Java 8 that has no release file, as Corretto 8 or the jre directory of a JDK 8, has the rt.jar that later',
                '@rem versions do not have',
                'if not defined GRAILS_JAVA_VERSION if defined GRAILS_JAVA if exist "%GRAILS_JAVA%\\lib\\rt.jar" set "GRAILS_JAVA_VERSION=8 or earlier"',
                'if not defined GRAILS_JAVA_VERSION if defined GRAILS_JAVA if exist "%GRAILS_JAVA%\\jre\\lib\\rt.jar" set "GRAILS_JAVA_VERSION=8 or earlier"',
                '@rem 1.8.0_402 is Java 8, 17.0.2 Java 17, 26-ea Java 26',
                'set GRAILS_JAVA_MAJOR=',
                'if defined GRAILS_JAVA_VERSION for /f "tokens=1,2 delims=.-+_ " %%a in ("%GRAILS_JAVA_VERSION%") do ' +
                        'if "%%a"=="1" (set "GRAILS_JAVA_MAJOR=%%b") else set "GRAILS_JAVA_MAJOR=%%a"',
                "if defined GRAILS_JAVA_MAJOR if %GRAILS_JAVA_MAJOR% LSS ${version} goto grailsJavaTooOld".toString(),
                'goto grailsJavaChecked',
                '',
                ':grailsJavaTooOld',
                '1>&2 echo.',
                "1>&2 echo ERROR: %APP_BASE_NAME% needs Java ${version} or later, and %GRAILS_JAVA_FOUND% is Java %GRAILS_JAVA_VERSION%.".toString(),
                '1>&2 echo.',
                '1>&2 echo Please set the JAVA_HOME variable in your environment to the location of',
                "1>&2 echo a Java ${version} or later installation.".toString(),
                '',
                '"%COMSPEC%" /c exit 1',
                'goto exitWithErrorLevel',
                '',
                ':grailsJavaChecked',
                'set GRAILS_JAVA=',
                'set GRAILS_JAVA_FOUND=',
                'set GRAILS_JAVA_VERSION=',
                'set GRAILS_JAVA_MAJOR=',
        ]
        return script.replace(line + end, line + end + added.collect { String each -> each + end }.join(''))
    }

    /**
     * Has a task write the option into the scripts it generates, after it has generated them.
     *
     * @param task the task that generates the scripts
     * @param optionsVariable the environment variable of the scripts that takes JVM options, named in their comment
     */
    static void compileForShortRuns(CreateStartScripts task, String optionsVariable) {
        task.inputs.property('shortRunOption', OPTION)
        task.inputs.property('shortRunComment', WHY)
        task.inputs.property('interactiveOptions', INTERACTIVE_OPTIONS)
        task.inputs.property('longLivedCommands', LONG_LIVED_COMMANDS)
        // named in what is written into both scripts
        task.inputs.property('shortRunOptionsVariable', optionsVariable)
        task.doLast { Task generated ->
            CreateStartScripts scripts = (CreateStartScripts) generated
            scripts.unixScript.text = CommandLineStartScripts.unix(scripts.unixScript.text, optionsVariable)
            scripts.windowsScript.text = CommandLineStartScripts.windows(scripts.windowsScript.text, optionsVariable)
        }
    }

    /**
     * @param script the script Gradle generates for a POSIX shell
     * @return the script with the option, for a start with an argument other than {@link #INTERACTIVE_OPTIONS}
     * whose command is not one of {@link #LONG_LIVED_COMMANDS}
     */
    static String unix(String script, String optionsVariable) {
        String line = lineOf(script, UNIX_DEFAULTS, '\n', "${OPTION} is added after the one that does")
        // The word of a case is neither split nor expanded as a path, so an argument is compared as it is. The
        // arguments are read up to the command: an option in front of it gives the option, unless the command
        // that follows is a long-lived one.
        String added = WHY.collect { String each -> "# ${each}\n" }.join('') +
                "# To override it: ${optionsVariable}=-XX:TieredStopAtLevel=4 (what ${optionsVariable} and JAVA_OPTS hold comes later\n" +
                '# on the command line of the JVM, and the JVM takes the last value it is given).\n' +
                'GRAILS_SHORT_RUN=\n' +
                'for GRAILS_ARGUMENT in "$@" ; do\n' +
                '    case $GRAILS_ARGUMENT in\n' +
                "        ${INTERACTIVE_OPTIONS.join('|')}) ;;\n" +
                "        ${LONG_LIVED_COMMANDS.join('|')}) GRAILS_SHORT_RUN= ; break ;;\n" +
                '        -*) GRAILS_SHORT_RUN=1 ;;\n' +
                '        *) GRAILS_SHORT_RUN=1 ; break ;;\n' +
                '    esac\n' +
                'done\n' +
                'if [ -n "$GRAILS_SHORT_RUN" ] ; then\n' +
                "    DEFAULT_JVM_OPTS=\"${OPTION} \$DEFAULT_JVM_OPTS\"\n" +
                'fi\n' +
                'unset GRAILS_ARGUMENT GRAILS_SHORT_RUN\n'
        return script.replace(line, line + '\n' + added)
    }

    /**
     * @param script the batch file Gradle generates
     * @return the batch file with the option, for a start with an argument other than {@link #INTERACTIVE_OPTIONS}
     * whose command is not one of {@link #LONG_LIVED_COMMANDS}
     */
    static String windows(String script, String optionsVariable) {
        String end = script.contains('\r\n') ? '\r\n' : '\n'
        String line = lineOf(script, WINDOWS_DEFAULTS, end, "${OPTION} is added after the one that does")
        // Each argument is read into a variable while delayed expansion is off, so that an exclamation mark in it
        // stays one, and compared while it is on: the value of !variable! is not read again by the command
        // interpreter, so a quote, an equals sign or an ampersand in it does not break the comparison. What Java
        // is given is %*, which shift does not change. The arguments are read up to the command: an option in
        // front of it gives the option and the next argument is read, unless the command that follows is a
        // long-lived one.
        String added = WHY.collect { String each -> "@rem ${each}${end}" }.join('') +
                "@rem To override it: set ${optionsVariable}=-XX:TieredStopAtLevel=4 (what ${optionsVariable} and JAVA_OPTS hold comes${end}" +
                "@rem later on the command line of the JVM, and the JVM takes the last value it is given).${end}" +
                "set GRAILS_SHORT_RUN=${end}" +
                ":grailsNextArgument${end}" +
                "set GRAILS_ARGUMENT=%1${end}" +
                "if not defined GRAILS_ARGUMENT goto grailsArgumentsRead${end}" +
                "setlocal EnableDelayedExpansion${end}" +
                "set GRAILS_INTERACTIVE_OPTION=${end}" +
                INTERACTIVE_OPTIONS.collect { String option -> "if \"!GRAILS_ARGUMENT!\"==\"${option}\" set GRAILS_INTERACTIVE_OPTION=1${end}" }.join('') +
                LONG_LIVED_COMMANDS.collect { String command ->
                    "if \"!GRAILS_ARGUMENT!\"==\"${command}\" (endlocal & set GRAILS_SHORT_RUN=& goto grailsArgumentsRead)${end}"
                }.join('') +
                "if not defined GRAILS_INTERACTIVE_OPTION if \"!GRAILS_ARGUMENT:~0,1!\"==\"-\" (endlocal & set GRAILS_SHORT_RUN=1& shift & goto grailsNextArgument)${end}" +
                "if not defined GRAILS_INTERACTIVE_OPTION (endlocal & set GRAILS_SHORT_RUN=1& goto grailsArgumentsRead)${end}" +
                "endlocal${end}" +
                "shift${end}" +
                "goto grailsNextArgument${end}" +
                ":grailsArgumentsRead${end}" +
                "if defined GRAILS_SHORT_RUN set DEFAULT_JVM_OPTS=${OPTION} %DEFAULT_JVM_OPTS%${end}" +
                "set GRAILS_ARGUMENT=${end}" +
                "set GRAILS_SHORT_RUN=${end}"
        return script.replace(line, line + end + added)
    }

    // the one line of a script that starts as given, without its end
    private static String lineOf(String script, String start, String end, String added) {
        List<String> lines = script.split(end).findAll { String each -> each.startsWith(start) }.toList()
        if (lines.size() != 1) {
            throw new GradleException("The start scripts Gradle generates have changed: ${lines.size()} lines start with ${start}, and ${added}")
        }
        return lines[0]
    }
}
