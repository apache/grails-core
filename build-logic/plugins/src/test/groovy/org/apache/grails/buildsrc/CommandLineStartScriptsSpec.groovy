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

import org.gradle.api.GradleException
import spock.lang.IgnoreIf
import spock.lang.Specification
import spock.lang.Unroll

class CommandLineStartScriptsSpec extends Specification {

    static final String DEFAULTS = 'DEFAULT_JVM_OPTS=\'"--enable-native-access=ALL-UNNAMED"\''
    static final String START = 'for GRAILS_ARGUMENT in "$@" ; do'

    static final String UNIX = '#!/bin/sh\n' +
            '# Add default JVM options here. You can also use JAVA_OPTS and GRAILS_OPTS to pass JVM options to this script.\n' +
            DEFAULTS + '\n' +
            '\n' +
            'exec "$JAVACMD" "$@"\n'

    static final String WINDOWS = '@rem Add default JVM options here.\r\n' +
            'set DEFAULT_JVM_OPTS="--enable-native-access=ALL-UNNAMED"\r\n' +
            '\r\n' +
            '"%JAVA_EXE%" %DEFAULT_JVM_OPTS% %JAVA_OPTS% %GRAILS_OPTS% -classpath "%CLASSPATH%" Main %*\r\n'

    void "the option is put in front of the options a script has by itself, for a start with an argument that is not --plain-output"() {
        when:
        List<String> lines = CommandLineStartScripts.unix(UNIX, 'GRAILS_OPTS').readLines()
        int defaults = lines.indexOf(DEFAULTS)
        int start = lines.indexOf(START)

        then:
        lines[defaults + 1].startsWith('# -XX:TieredStopAtLevel=1 for a command line that runs one command')
        start > defaults
        lines.subList(start, start + 6) == [
                START,
                '    case $GRAILS_ARGUMENT in',
                '        --plain-output) ;;',
                '        *) DEFAULT_JVM_OPTS="-XX:TieredStopAtLevel=1 $DEFAULT_JVM_OPTS" ; break ;;',
                '    esac',
                'done',
        ]
        lines.any { String line -> line.contains('SIGNIFICANTLY SLOWER') }
        lines.any { String line -> line.contains('GRAILS_OPTS=-XX:TieredStopAtLevel=4') }
        lines.any { String line -> line.contains('without an argument or with --plain-output alone') }
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
        arguments                            | given
        []                                   | false
        ['--plain-output']                   | false
        ['--plain-output', '--plain-output'] | false
        ['--plain-output', 'compile']        | true
        ['compile', '--plain-output']        | true
        ['--non-interactive']                | true
        ['*']                                | true
        ['']                                 | true
        ['two words']                        | true
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
}
