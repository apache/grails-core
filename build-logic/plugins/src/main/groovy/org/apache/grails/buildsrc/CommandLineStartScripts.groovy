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
 * {@code -XX:TieredStopAtLevel=1} for a command line that runs one command.
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

    private static final List<String> WHY = [
            OPTION + ' for a command line that runs one command: one that has an argument other than',
            INTERACTIVE_OPTIONS.join(', ') + '.',
            'Why: a command is a short-lived process whose time is the start of the JVM and the loading of',
            'classes. With C1 alone a command is 15-20% faster (create-app 1.5 to 1.3 s) and create-app',
            'takes a quarter of the CPU time, because the optimizing compiler (C2) is never started.',
            'The cost: code that runs long and hot is SIGNIFICANTLY SLOWER without C2. That is accepted',
            'because a command is short, and builds, tests and the application run in the JVMs of Gradle',
            'and of the application, which are not given this option. Interactive mode, which is started',
            'without an argument or with ' + INTERACTIVE_OPTIONS.join(', ') + ' alone, and is the one long-lived JVM',
            'of the command line, is not given it either.',
    ]

    private CommandLineStartScripts() {
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
        task.doLast { Task generated ->
            CreateStartScripts scripts = (CreateStartScripts) generated
            scripts.unixScript.text = CommandLineStartScripts.unix(scripts.unixScript.text, optionsVariable)
            scripts.windowsScript.text = CommandLineStartScripts.windows(scripts.windowsScript.text, optionsVariable)
        }
    }

    /**
     * @param script the script Gradle generates for a POSIX shell
     * @return the script with the option, for a start with an argument other than {@link #INTERACTIVE_OPTIONS}
     */
    static String unix(String script, String optionsVariable) {
        String line = lineOf(script, UNIX_DEFAULTS, '\n')
        // the word of a case is neither split nor expanded as a path, so an argument is compared as it is
        String added = WHY.collect { String each -> "# ${each}\n" }.join('') +
                "# To override it: ${optionsVariable}=-XX:TieredStopAtLevel=4 (what ${optionsVariable} and JAVA_OPTS hold comes later\n" +
                '# on the command line of the JVM, and the JVM takes the last value it is given).\n' +
                'for GRAILS_ARGUMENT in "$@" ; do\n' +
                '    case $GRAILS_ARGUMENT in\n' +
                "        ${INTERACTIVE_OPTIONS.join('|')}) ;;\n" +
                "        *) DEFAULT_JVM_OPTS=\"${OPTION} \$DEFAULT_JVM_OPTS\" ; break ;;\n" +
                '    esac\n' +
                'done\n'
        return script.replace(line, line + '\n' + added)
    }

    /**
     * @param script the batch file Gradle generates
     * @return the batch file with the option, for a start with an argument other than {@link #INTERACTIVE_OPTIONS}
     */
    static String windows(String script, String optionsVariable) {
        String end = script.contains('\r\n') ? '\r\n' : '\n'
        String line = lineOf(script, WINDOWS_DEFAULTS, end)
        // Each argument is read into a variable while delayed expansion is off, so that an exclamation mark in it
        // stays one, and compared while it is on: the value of !variable! is not read again by the command
        // interpreter, so a quote, an equals sign or an ampersand in it does not break the comparison. What Java
        // is given is %*, which shift does not change.
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

    // the one line of a script that sets the JVM options the script has by itself, without its end
    private static String lineOf(String script, String start, String end) {
        List<String> lines = script.split(end).findAll { String each -> each.startsWith(start) }.toList()
        if (lines.size() != 1) {
            throw new GradleException("The start scripts Gradle generates have changed: ${lines.size()} lines start with ${start}, " +
                    "and ${OPTION} is added after the one that does")
        }
        return lines[0]
    }
}
