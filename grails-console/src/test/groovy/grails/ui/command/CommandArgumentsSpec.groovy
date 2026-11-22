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

class CommandArgumentsSpec extends Specification {

    @TempDir
    File dir

    void 'an arguments file is replaced by its arguments, in its place and unchanged'() {
        given: 'arguments a command line could not carry without quoting, and a password'
        List<String> inFile = ['two words', 'it\'s "x" y', '', '--password', 's3cr3t !$']
        File file = new File(dir, 'arguments.json')
        file.text = JsonOutput.toJson(inFile)

        expect:
        CommandArguments.expand(['dbm-update', CommandArguments.ARGS_FILE_ARGUMENT + file.absolutePath, 'com.example.Application'] as String[]).toList() ==
                ['dbm-update'] + inFile + ['com.example.Application']
    }

    void 'arguments without a file are passed on as they are'() {
        expect:
        CommandArguments.expand(['url-mappings-report', '--verbose', 'com.example.Application'] as String[]).toList() ==
                ['url-mappings-report', '--verbose', 'com.example.Application']
    }

    void 'an arguments file that #problem is reported'() {
        given:
        File file = new File(dir, 'arguments.json')
        if (content != null) {
            file.text = content
        }

        when:
        CommandArguments.expand([CommandArguments.ARGS_FILE_ARGUMENT + file.absolutePath, 'com.example.Application'] as String[])

        then:
        IllegalArgumentException e = thrown()
        e.message.contains(file.absolutePath)

        where:
        problem                         | content
        'does not exist'                | null
        'is empty'                      | ''
        'is not JSON'                   | 'not json'
        'does not hold strings'         | '["a", 1]'
    }
}
