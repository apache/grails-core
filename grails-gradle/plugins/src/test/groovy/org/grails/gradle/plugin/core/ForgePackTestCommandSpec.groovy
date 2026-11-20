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
package org.grails.gradle.plugin.core

import spock.lang.Specification
import spock.lang.Unroll

/**
 * The command {@code forgePackTest} builds a generated application with, and what it shows of an
 * address: the parts of the task that do not need a build to be looked at, and the one for Windows
 * that no test of this build runs.
 */
class ForgePackTestCommandSpec extends Specification {

    void "the wrapper is started by itself, with each argument as one argument, whatever spaces the paths have"() {
        given:
        File application = new File('/work/my plugin/build/forge-pack-fixtures/web')

        when:
        List<String> command = ForgePackTestTask.wrapperCommand(application, false, ['assemble', 'test'], '/Users/a b/.gradle', false, ['--stacktrace'])

        then:
        command == [new File(application, 'gradlew').absolutePath, 'assemble', 'test', '--console=plain', '--gradle-user-home', '/Users/a b/.gradle',
                    '--stacktrace']
    }

    void "on Windows the batch file is the command, not a line for cmd that would have to quote it"() {
        given:
        File application = new File('C:\\work\\my plugin\\build\\forge-pack-fixtures\\web')

        when:
        List<String> command = ForgePackTestTask.wrapperCommand(application, true, ['assemble'], 'C:\\Users\\a b\\.gradle', true, ['-Pname=a value'])

        then: 'the process is given its arguments one by one, and quotes those with spaces itself'
        command.size() == 7
        command[0] == new File(application, 'gradlew.bat').absolutePath
        !command.any { it.equalsIgnoreCase('cmd') || it == '/c' }
        command.drop(1) == ['assemble', '--console=plain', '--gradle-user-home', 'C:\\Users\\a b\\.gradle', '--offline', '-Pname=a value']
    }

    @Unroll
    void "a line is shown without the user and the password of an address: #line"() {
        expect:
        ForgePackTestTask.withoutUserInfo(line) == shown

        where:
        line                                                         || shown
        'https://alice:hunter2@host.example/maven'                   || 'https://host.example/maven'
        "Could not GET 'https://alice:hunter2@host.example/a.pom'."  || "Could not GET 'https://host.example/a.pom'."
        'https://token@host.example/maven and sftp://u:p@other/x'    || 'https://host.example/maven and sftp://other/x'
        'https://host.example/maven'                                 || 'https://host.example/maven'
        'mail alice@host.example about https://host.example/a@b'     || 'mail alice@host.example about https://host.example/a@b'
    }

    void "the repositories stand for themselves as an input of the task by a digest, which tells them apart and does not hold them"() {
        expect:
        ForgePackFixtureRepositories.digest(['https://alice:hunter2@host.example/maven']) ==~ /[0-9a-f]{64}/
        ForgePackFixtureRepositories.digest(['a', 'b']) != ForgePackFixtureRepositories.digest(['ab'])
        ForgePackFixtureRepositories.digest(['a', 'b']) != ForgePackFixtureRepositories.digest(['b', 'a'])
        ForgePackFixtureRepositories.digest([]) == ForgePackFixtureRepositories.digest([])
    }
}
