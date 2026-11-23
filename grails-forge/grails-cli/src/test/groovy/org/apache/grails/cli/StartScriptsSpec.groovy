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

import java.nio.file.Files
import java.util.regex.Matcher
import org.apache.grails.forge.cli.console.PlainOutput
import spock.lang.IgnoreIf
import spock.lang.Specification
import spock.lang.Unroll

/**
 * The JVM options of the start scripts of the {@code grails} command line: {@code -XX:TieredStopAtLevel=1} for a start with a
 * command, first on the command line of the JVM so that what a user sets comes after it, and in nothing
 * the JVM hands on to what it starts. The scripts are the ones the build generates.
 */
class StartScriptsSpec extends Specification {

    static final String OPTION = '-XX:TieredStopAtLevel=1'

    // the option of the command line that shows the help instead of the interactive console, and ends
    static final String NON_INTERACTIVE = '--non-interactive'

    File scripts = new File(System.getProperty('grails.cli.scripts'))
    File dir = Files.createTempDirectory('grailsscripts').toFile().canonicalFile

    void cleanup() {
        dir.deleteDir()
    }

    @IgnoreIf({ os.windows })
    @Unroll
    void "started with #arguments the JVM is #given the option, before the options of the user, and its environment does not have it"() {
        when:
        Map started = start(arguments, [JAVA_OPTS: '-Xmx300m', GRAILS_OPTS: '-XX:TieredStopAtLevel=4 -Dmine=1'])
        List<String> jvm = started.arguments as List<String>

        then:
        jvm.contains(OPTION) == expected
        !expected || jvm.indexOf(OPTION) == 0
        !expected || jvm.indexOf(OPTION) < jvm.indexOf('-Xmx300m')
        jvm.indexOf('-Xmx300m') < jvm.indexOf('-XX:TieredStopAtLevel=4')
        jvm.contains('--enable-native-access=ALL-UNNAMED')
        jvm.takeRight(arguments.size()) == arguments

        and: 'what the JVM starts, a Gradle build among it, inherits the environment, which is as the user set it'
        !(started.environment as List<String>).any { String line -> line.contains(OPTION) }

        where:
        arguments                                  | expected | given
        ['--version']                              | true     | 'given'
        ['create-app', 'two words', '-Da=b c']     | true     | 'given'
        ['compile']                                | true     | 'given'
        []                                         | false    | 'not given'
        [PlainOutput.OPTION]                       | false    | 'not given'
        [PlainOutput.OPTION, PlainOutput.OPTION]   | false    | 'not given'
        [PlainOutput.OPTION, 'compile']            | true     | 'given'
        ['compile', PlainOutput.OPTION]            | true     | 'given'
        [NON_INTERACTIVE]                          | true     | 'given'
        [PlainOutput.OPTION, NON_INTERACTIVE]      | true     | 'given'
        ['--plain-output=true']                    | true     | 'given'
        ['*']                                      | true     | 'given'
        ['']                                       | true     | 'given'
    }

    @Unroll
    void "started with #arguments the JVM of the command line compiles #how"() {
        when: 'the JVM the script starts is asked for its flags, and ends before the command line would start'
        Map flags = flagsOf(arguments)

        then:
        flags.exit == 0
        flags.tieredStopAtLevel == level

        where:
        arguments                                  | level | how
        []                                         | 4     | 'as the JVM does by default: that is the interactive mode'
        [PlainOutput.OPTION]                       | 4     | 'as the JVM does by default: that is the interactive mode'
        [PlainOutput.OPTION, PlainOutput.OPTION]   | 4     | 'as the JVM does by default: that is the interactive mode'
        ['compile']                                | 1     | 'with C1 alone'
        [PlainOutput.OPTION, 'compile']            | 1     | 'with C1 alone'
        ['compile', PlainOutput.OPTION]            | 1     | 'with C1 alone'
        [NON_INTERACTIVE]                          | 1     | 'with C1 alone'
        ['create-app', 'two words']                | 1     | 'with C1 alone'
    }

    void "both scripts say beside the option why it is there, what it costs and how it is overridden"() {
        expect:
        [new File(scripts, 'grails'), new File(scripts, 'grails.bat')].every { File script ->
            List<String> lines = script.readLines()
            int option = lines.findIndexOf { String line -> line.contains(OPTION) && !line.trim().startsWith('#') && !line.trim().startsWith('@rem') }
            int first = lines.findIndexOf { String line -> line in ['for GRAILS_ARGUMENT in "$@" ; do', 'set GRAILS_SHORT_RUN='] }
            String comment = lines.subList(0, first).reverse().takeWhile { String line -> line.startsWith('#') || line.startsWith('@rem') }.join(' ')
            first > 0 && option > first && comment.contains('SIGNIFICANTLY SLOWER') && comment.contains('short-lived') &&
                    comment.contains('GRAILS_OPTS=-XX:TieredStopAtLevel=4') && comment.contains('Interactive mode') &&
                    comment.contains("without an argument or with ${PlainOutput.OPTION} alone")
        }
    }

    void "the batch file gives the option when it has an argument other than --plain-output, and compares each one in a variable"() {
        given:
        List<String> lines = new File(scripts, 'grails.bat').readLines()
        int defaults = lines.findIndexOf { String line -> line.startsWith('set DEFAULT_JVM_OPTS=') }
        int start = lines.indexOf('set GRAILS_SHORT_RUN=')

        expect: 'after the options the script has by itself, and before Java is looked for and started'
        defaults > 0
        start > defaults
        lines.subList(start, start + 15) == [
                'set GRAILS_SHORT_RUN=',
                ':grailsNextArgument',
                'set GRAILS_ARGUMENT=%1',
                'if not defined GRAILS_ARGUMENT goto grailsArgumentsRead',
                'setlocal EnableDelayedExpansion',
                'set GRAILS_INTERACTIVE_OPTION=',
                "if \"!GRAILS_ARGUMENT!\"==\"${PlainOutput.OPTION}\" set GRAILS_INTERACTIVE_OPTION=1".toString(),
                'if not defined GRAILS_INTERACTIVE_OPTION (endlocal & set GRAILS_SHORT_RUN=1& goto grailsArgumentsRead)',
                'endlocal',
                'shift',
                'goto grailsNextArgument',
                ':grailsArgumentsRead',
                "if defined GRAILS_SHORT_RUN set DEFAULT_JVM_OPTS=${OPTION} %DEFAULT_JVM_OPTS%".toString(),
                'set GRAILS_ARGUMENT=',
                'set GRAILS_SHORT_RUN=',
        ]
        lines.findIndexOf { String line -> line.contains('%DEFAULT_JVM_OPTS% %JAVA_OPTS% %GRAILS_OPTS%') && line.contains(' %* ') } > start + 14
        // shift moves %1 to %9 and leaves %* as it was: nothing after the loop reads the numbered arguments
        lines.subList(start + 15, lines.size()).every { String line -> !(line ==~ /.*%[1-9].*/) }
    }

    // Starts the script with the Java that runs this specification, which is asked to print its flags and the version
    // and to end before it would start the command line
    private Map flagsOf(List<String> arguments) {
        File bin = new File(dir, 'home/bin')
        bin.mkdirs()
        boolean windows = System.getProperty('os.name').toLowerCase(Locale.ROOT).contains('windows')
        File script = new File(bin, windows ? 'grails.bat' : 'grails')
        script.bytes = new File(scripts, script.name).bytes
        script.setExecutable(true)
        List<String> command = windows ? ['cmd.exe', '/c', script.path] : [script.path]
        ProcessBuilder builder = new ProcessBuilder(command + arguments).directory(dir).redirectErrorStream(true)
        builder.environment().put('JAVA_HOME', System.getProperty('java.home'))
        builder.environment().put('JAVA_OPTS', '-XX:+PrintFlagsFinal -version')
        builder.environment().remove('GRAILS_OPTS')
        Process process = builder.start()
        String said = process.inputStream.text
        int exit = process.waitFor()
        Matcher level = said =~ /(?m)^\s*intx\s+TieredStopAtLevel\s+=\s+(\d+)/
        return [exit: exit, tieredStopAtLevel: level.find() ? level.group(1) as int : null, said: said]
    }

    // Starts the script with a Java that writes down what it was started with, and nothing else
    private Map start(List<String> arguments, Map<String, String> environment) {
        File home = new File(dir, 'java')
        new File(home, 'bin').mkdirs()
        File java = new File(home, 'bin/java')
        java.text = '#!/bin/sh\nfor each in "$@"; do printf \'%s\\n\' "$each"; done > "$RECORD"\nenv > "$RECORD.env"\n'
        java.setExecutable(true)
        File record = new File(dir, 'record.txt')
        // a copy, as an installation has it: the script finds its home from where it is
        File bin = new File(dir, 'home/bin')
        bin.mkdirs()
        File script = new File(bin, 'grails')
        script.text = new File(scripts, 'grails').text
        script.setExecutable(true)
        ProcessBuilder builder = new ProcessBuilder([script.path] + arguments).directory(dir).redirectErrorStream(true)
        builder.environment().putAll(environment)
        builder.environment().put('JAVA_HOME', home.path)
        builder.environment().put('RECORD', record.path)
        Process process = builder.start()
        String said = process.inputStream.text
        assert process.waitFor() == 0 : said
        return [arguments: record.readLines(), environment: new File(record.path + '.env').readLines().findAll { String line -> !line.startsWith('RECORD=') }]
    }
}
