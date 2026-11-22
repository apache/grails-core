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
import spock.lang.Specification

class CommandLineStartScriptsSpec extends Specification {

    static final String DEFAULTS = 'DEFAULT_JVM_OPTS=\'"--enable-native-access=ALL-UNNAMED"\''
    static final String START = 'if [ "$#" -gt 0 ] ; then'

    static final String UNIX = '#!/bin/sh\n' +
            '# Add default JVM options here. You can also use JAVA_OPTS and GRAILS_OPTS to pass JVM options to this script.\n' +
            DEFAULTS + '\n' +
            '\n' +
            'exec "$JAVACMD" "$@"\n'

    static final String WINDOWS = '@rem Add default JVM options here.\r\n' +
            'set DEFAULT_JVM_OPTS="--enable-native-access=ALL-UNNAMED"\r\n' +
            '\r\n' +
            '"%JAVA_EXE%" %DEFAULT_JVM_OPTS% %JAVA_OPTS% %GRAILS_OPTS% -classpath "%CLASSPATH%" Main %*\r\n'

    void "the option is put in front of the options a script has by itself, for a start with an argument"() {
        when:
        List<String> lines = CommandLineStartScripts.unix(UNIX, 'GRAILS_OPTS').readLines()
        int defaults = lines.indexOf(DEFAULTS)

        then:
        lines[defaults + 1].startsWith('# -XX:TieredStopAtLevel=1 for a command line that runs one command')
        lines.indexOf(START) > defaults
        lines[lines.indexOf(START) + 1] == '    DEFAULT_JVM_OPTS="-XX:TieredStopAtLevel=1 $DEFAULT_JVM_OPTS"'
        lines[lines.indexOf(START) + 2] == 'fi'
        lines.any { String line -> line.contains('SIGNIFICANTLY SLOWER') }
        lines.any { String line -> line.contains('GRAILS_OPTS=-XX:TieredStopAtLevel=4') }
        lines.last() == 'exec "$JAVACMD" "$@"'
    }

    void "the batch file keeps its line ends, and names the variable of its own script"() {
        when:
        String script = CommandLineStartScripts.windows(WINDOWS, 'GRAILSW_OPTS')

        then:
        !script.replace('\r\n', '').contains('\n')
        script.contains('set GRAILS_FIRST_ARGUMENT=%1\r\nif defined GRAILS_FIRST_ARGUMENT set DEFAULT_JVM_OPTS=-XX:TieredStopAtLevel=1 %DEFAULT_JVM_OPTS%\r\nset GRAILS_FIRST_ARGUMENT=\r\n')
        script.contains('@rem To override it: set GRAILSW_OPTS=-XX:TieredStopAtLevel=4')
        script.indexOf('set GRAILS_FIRST_ARGUMENT=%1') > script.indexOf('set DEFAULT_JVM_OPTS="--enable-native-access=ALL-UNNAMED"')
        script.indexOf('set GRAILS_FIRST_ARGUMENT=%1') < script.indexOf('"%JAVA_EXE%"')
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
