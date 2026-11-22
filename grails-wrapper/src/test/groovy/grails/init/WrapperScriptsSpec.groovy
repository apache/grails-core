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
package grails.init

import java.nio.file.Files
import spock.lang.IgnoreIf
import spock.lang.Specification
import spock.lang.Unroll

/**
 * The JVM options of the start scripts of the Grails wrapper: {@code -XX:TieredStopAtLevel=1} for a start with a
 * command, first on the command line of the JVM so that what a user sets comes after it, and in nothing
 * the JVM hands on to what it starts. The scripts are the ones the build generates.
 */
class WrapperScriptsSpec extends Specification {

    static final String OPTION = '-XX:TieredStopAtLevel=1'

    File scripts = new File(System.getProperty('grails.wrapper.scripts'))
    File dir = Files.createTempDirectory('grailsscripts').toFile().canonicalFile

    void cleanup() {
        dir.deleteDir()
    }

    @IgnoreIf({ os.windows })
    @Unroll
    void "started with #arguments the JVM is #given the option, before the options of the user, and its environment does not have it"() {
        when:
        Map started = start(arguments, [JAVA_OPTS: '-Xmx300m', GRAILSW_OPTS: '-XX:TieredStopAtLevel=4 -Dmine=1'])
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
    }

    void "both scripts say beside the option why it is there, what it costs and how it is overridden"() {
        expect:
        [new File(scripts, 'grailsw'), new File(scripts, 'grailsw.bat')].every { File script ->
            List<String> lines = script.readLines()
            int option = lines.findIndexOf { String line -> line.contains(OPTION) && !line.trim().startsWith('#') && !line.trim().startsWith('@rem') }
            String comment = lines.subList(0, option).reverse().takeWhile { String line -> line.startsWith('#') || line.startsWith('@rem') || line.startsWith('if ') || line.startsWith('set GRAILS_FIRST_ARGUMENT') }.join(' ')
            option > 0 && comment.contains('SIGNIFICANTLY SLOWER') && comment.contains('short-lived') &&
                    comment.contains('GRAILSW_OPTS=-XX:TieredStopAtLevel=4') && comment.contains('Interactive mode')
        }
    }

    void "the batch file gives the option when it has a first argument, and asks that of a variable"() {
        given:
        List<String> lines = new File(scripts, 'grailsw.bat').readLines()
        int defaults = lines.findIndexOf { String line -> line.startsWith('set DEFAULT_JVM_OPTS=') }

        expect: 'after the options the script has by itself, and before Java is looked for and started'
        defaults > 0
        lines.indexOf('set GRAILS_FIRST_ARGUMENT=%1') > defaults
        lines.indexOf("if defined GRAILS_FIRST_ARGUMENT set DEFAULT_JVM_OPTS=${OPTION} %DEFAULT_JVM_OPTS%".toString()) == lines.indexOf('set GRAILS_FIRST_ARGUMENT=%1') + 1
        lines.indexOf('set GRAILS_FIRST_ARGUMENT=') == lines.indexOf('set GRAILS_FIRST_ARGUMENT=%1') + 2
        lines.findIndexOf { String line -> line.contains('%DEFAULT_JVM_OPTS% %JAVA_OPTS% %GRAILSW_OPTS%') } > lines.indexOf('set GRAILS_FIRST_ARGUMENT=')
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
        File script = new File(bin, 'grailsw')
        script.text = new File(scripts, 'grailsw').text
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
