/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package grails.ui.command

import groovy.json.JsonOutput
import spock.lang.Specification
import spock.lang.TempDir
import spock.util.environment.RestoreSystemProperties

@RestoreSystemProperties
class CommandArgumentsSpec extends Specification {

    @TempDir
    File dir

    void 'the arguments of the file the system property names follow the others, before the application class'() {
        given: 'arguments a command line could not carry without quoting, and a password'
        List<String> inFile = ['two words', 'it\'s "x" y', '', '--password', 's3cr3t !$']
        System.setProperty(CommandArguments.ARGS_FILE_SYSTEM_PROPERTY, file(JsonOutput.toJson(inFile)).absolutePath)

        expect:
        CommandArguments.expand(['dbm-update', '--verbose', 'com.example.Application'] as String[]).toList() ==
                ['dbm-update', '--verbose'] + inFile + ['com.example.Application']

        and: 'the application does not see the property'
        System.getProperty(CommandArguments.ARGS_FILE_SYSTEM_PROPERTY) == null
    }

    void 'the system properties of the file are set as it is read, so the command runs where its arguments say'() {
        given:
        System.setProperty('grails.env', 'development')
        System.setProperty(CommandArguments.ARGS_FILE_SYSTEM_PROPERTY,
                file(JsonOutput.toJson(['dbm-update', ' -Dgrails.env=test ', '"-Dquoted=yes"', '-D', '-Dnovalue'])).absolutePath)

        when:
        CommandArguments.expand(['com.example.Application'] as String[])

        then:
        System.getProperty('grails.env') == 'test'
        System.getProperty('quoted') == 'yes'
    }

    void 'arguments without a file are passed on as they are, also one that looks like the name of a file'() {
        given:
        String[] given = ['url-mappings-report', '--grails-args-file=/tmp/x.json', 'com.example.Application'] as String[]

        expect:
        CommandArguments.expand(given).toList() == given.toList()
    }

    void 'an arguments file that #problem is reported, without what it holds'() {
        given:
        File file = new File(dir, 'arguments.json')
        if (content != null) {
            file.text = content
        }
        System.setProperty(CommandArguments.ARGS_FILE_SYSTEM_PROPERTY, file.absolutePath)

        when:
        CommandArguments.expand(['com.example.Application'] as String[])

        then:
        IllegalArgumentException e = thrown()
        e.message.contains(file.absolutePath)
        !e.message.contains('s3cr3t')
        !(e.cause?.message?.contains('s3cr3t'))

        where:
        problem                         | content
        'does not exist'                | null
        'is empty'                      | ''
        'is not JSON'                   | '["dbm-update", "--password", "s3cr3t" oops]'
        'does not hold strings'         | '["a", 1]'
        'has text after its array'      | '["dbm-update"] ["s3cr3t"]'
    }

    void 'the system properties among the words of a script runner are no script names'() {
        expect:
        CommandArguments.withoutSystemProperties(['script.groovy', '-Dgrails.env=test', ' "-Da=b" ', '-D', 'other.groovy']) ==
                ['script.groovy', '-D', 'other.groovy']
    }

    private File file(String content) {
        File file = new File(dir, 'arguments.json')
        file.text = content
        file
    }
}
