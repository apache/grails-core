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
package org.apache.grails.security.ui.cli

import java.util.concurrent.TimeUnit

import spock.lang.Specification
import spock.lang.TempDir
import spock.lang.Unroll

import org.apache.grails.core.cli.StandaloneToolRunner

/**
 * The tools of the Spring Security UI plugin declare their parameters, and are held to them as the
 * {@code grails} command line runs them: by {@link StandaloneToolRunner}, in a JVM of its own. A parameter that
 * is missing, one more than a tool takes and an option, of which they have none, are refused with exit code 2
 * before the tool runs, and it writes nothing.
 */
class S2uiDeclaredArgumentsSpec extends Specification {

    private static final int TIMEOUT_SECONDS = 120

    @TempDir
    File project

    /** What the JVM that is started is given and writes, outside the project */
    @TempDir
    File jvm

    def "s2ui-override is given the type it declares, and leaves out the package it can be given"() {
        when:
        Map run = start('s2ui-override', 'auth')

        then:
        run.exit == 0
        new File(project, 'grails-app/views/login/auth.gsp').isFile()
    }

    def "s2ui-override takes its type in any case, as it did before the types were declared"() {
        when:
        Map run = start('s2ui-override', 'Auth')

        then:
        run.exit == 0
        new File(project, 'grails-app/views/login/auth.gsp').isFile()
    }

    @Unroll
    def "#arguments is refused with exit code 2, says #what, and writes nothing"() {
        when:
        Map run = start(arguments as String[])

        then: 'the line is on the standard error the process was started with, and not in its output: the tools make the console of Grails when they are made, which writes what is printed to System.err to the output'
        run.exit == 2
        run.err.readLines().contains("| Error ${arguments[0]}: ${what}. grails help ${arguments[0]} shows its options".toString())
        !run.out.contains("| Error ${arguments[0]}: ${what}. grails help ${arguments[0]} shows its options".toString())
        !run.out.contains('Error |')
        project.list().toList() == []

        where:
        arguments                                                                                   || what
        ['s2ui-override']                                                                           || 'TYPE is required'
        ['s2ui-override', 'user', 'com.example', 'more']                                            || 'it takes at most 2 parameters, and was given 3'
        ['s2ui-override', 'auth', '--force']                                                        || '--force is not one of its options'
        ['s2ui-create-challenge-questions', 'com.example', 'Profile', 'com.example.User', '--questions=4'] || '--questions is not one of its options'
        ['s2ui-create-challenge-questions', 'com.example', 'Profile']                               || 'USER-DOMAIN-CLASS-NAME is required'
        ['s2ui-override', 'nothing', 'com.example']                                                 || 'TYPE takes one of aclclass, aclentry, aclobjectidentity, aclsid, persistentlogin, register, registrationcode, requestmap, role, securityinfo, user, auth, layout'
        ['s2ui-create-challenge-questions', 'com.example', 'Profile', 'com.example.User', 'two']    || 'NUMBER-OF-QUESTIONS takes a whole number'
    }

    /**
     * Starts a tool as the command line starts a standalone tool, and waits for it to end.
     */
    private Map start(String... arguments) {
        List<String> command = [new File(System.getProperty('java.home'), 'bin/java').path, '-cp', System.getProperty('java.class.path'),
                                "-Dbase.dir=${project.path}".toString(), "-Duser.home=${new File(jvm, 'home').path}".toString()]
        // the JVM that is started is counted as tested: the build says how (the grails-jacoco convention plugin)
        String coverage = System.getProperty('grails.test.fork.jacoco')
        if (coverage) {
            command.add(coverage.replace('@fork@', UUID.randomUUID().toString()))
        }
        command.add(StandaloneToolRunner.name)
        command.addAll(arguments.toList())
        File out = new File(jvm, 'out.txt')
        File err = new File(jvm, 'err.txt')
        Process process = new ProcessBuilder(command).directory(project).redirectOutput(out).redirectError(err).start()
        boolean ended = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        if (!ended) {
            process.destroyForcibly().waitFor()
        }
        assert ended
        [exit: process.exitValue(), out: out.getText('UTF-8'), err: err.getText('UTF-8')]
    }
}
