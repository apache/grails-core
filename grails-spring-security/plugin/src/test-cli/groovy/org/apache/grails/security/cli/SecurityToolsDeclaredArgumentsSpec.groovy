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
package org.apache.grails.security.cli

import java.util.concurrent.TimeUnit

import spock.lang.Specification
import spock.lang.TempDir
import spock.lang.Unroll

import org.apache.grails.core.cli.StandaloneToolRunner

/**
 * The tools of the Spring Security Core plugin declare their options and parameters, and are held to them as
 * they are run: {@code s2-quickstart} by the class the {@code runTool} task of a build starts, and the
 * standalone tools by {@link StandaloneToolRunner}, as the {@code grails} command line starts them, each in a
 * JVM of its own. What a tool does not declare is refused with exit code 2 before it runs, and it writes nothing.
 */
class SecurityToolsDeclaredArgumentsSpec extends Specification {

    private static final String RUNNER = 'grails.ui.command.GrailsApplicationContextCommandRunner'

    private static final String YAML = 'grails-app/conf/application.yml'

    private static final String PRESENT_YAML = 'grails:\n    profile: web\n'

    private static final int TIMEOUT_SECONDS = 120

    @TempDir
    File project

    /** What the JVM that is started is given and writes, outside the project */
    @TempDir
    File jvm

    def setup() {
        write(YAML, PRESENT_YAML)
    }

    def "s2-quickstart takes the value of its declared option from the next argument"() {
        when:
        Map run = runTool('s2-quickstart', 'com.example', 'User', 'Role', '--groupClassName', 'RoleGroup')

        then:
        run.exit == 0
        new File(project, 'grails-app/domain/com/example/RoleGroup.groovy').isFile()
        new File(project, 'grails-app/domain/com/example/UserRoleGroup.groovy').isFile()
        new File(project, YAML).text.contains('useRoleGroups: true')
    }

    def "s2-quickstart is refused an option it does not have, and says why it does not take --salt, which it declares for that"() {
        when:
        Map mistyped = runTool('s2-quickstart', 'com.example', 'User', 'Role', '--groupClasName=RoleGroup')

        then: 'the line is in what the tool printed: the tools make the console of Grails when they are made, which writes what is printed to System.err to the output'
        mistyped.exit == 2
        (mistyped.out + mistyped.err).readLines().contains('| Error s2-quickstart: --groupClasName is not one of its options. grails help s2-quickstart shows its options')
        written() == [YAML]

        when:
        Map salt = runTool('s2-quickstart', 'com.example', 'User', 'Role', '--salt')

        then:
        salt.exit == 1
        (salt.out + salt.err).contains('The option --salt is no longer supported')
        !(salt.out + salt.err).contains('| Error s2-quickstart:')
        written() == [YAML]
    }

    def "s2-create-persistent-token is given the name of the class it declares"() {
        when:
        Map run = standalone('s2-create-persistent-token', 'com.example.PersistentLogin')

        then:
        run.exit == 0
        new File(project, 'grails-app/domain/com/example/PersistentLogin.groovy').isFile()
    }

    @Unroll
    def "#arguments is refused with exit code 2, says #what, and writes nothing"() {
        when:
        Map run = standalone(arguments as String[])

        then: 'the line is in what the tool printed, as above'
        run.exit == 2
        (run.out + run.err).readLines().contains("| Error ${arguments[0]}: ${what}. grails help ${arguments[0]} shows its options".toString())
        written() == [YAML]
        new File(project, YAML).text == PRESENT_YAML

        where:
        arguments                                                                       || what
        ['s2-create-persistent-token', 'com.example.PersistentLogin', '--force']       || '--force is not one of its options'
        ['s2-create-persistent-token']                                                  || 'DOMAIN-CLASS-NAME is required'
        ['s2-create-role-hierarchy-entry', 'com.example.A', 'com.example.B']            || 'it takes at most 1 parameter, and was given 2'
    }

    /**
     * Runs a tool as the runTool task of a build does: the class that runs a tool, the name of the tool and its
     * arguments, and the class of the application last, which a tool that does not start the application never
     * starts.
     */
    private Map runTool(String... arguments) {
        List<String> command = [java(), '-cp', System.getProperty('java.class.path'), "-Dbase.dir=${project.path}".toString()]
        command.addAll(coverage())
        command.add(RUNNER)
        command.addAll(arguments.toList())
        command.add(SecurityToolsSpecApplication.name)
        started(command)
    }

    /**
     * Starts a standalone tool as the command line does: in a JVM of its own, by the runner of standalone tools.
     */
    private Map standalone(String... arguments) {
        List<String> command = [java(), '-cp', System.getProperty('java.class.path'), "-Dbase.dir=${project.path}".toString()]
        command.addAll(coverage())
        command.add(StandaloneToolRunner.name)
        command.addAll(arguments.toList())
        started(command)
    }

    private Map started(List<String> command) {
        File home = new File(jvm, 'home')
        home.mkdirs()
        command.add(1, "-Duser.home=${home.path}".toString())
        File out = File.createTempFile('out', '.txt', jvm)
        File err = File.createTempFile('err', '.txt', jvm)
        Process process = new ProcessBuilder(command).directory(project).redirectOutput(out).redirectError(err).start()
        boolean ended = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        if (!ended) {
            process.destroyForcibly().waitFor()
        }
        assert ended
        [exit: process.exitValue(), out: out.getText('UTF-8'), err: err.getText('UTF-8')]
    }

    private static String java() {
        new File(System.getProperty('java.home'), 'bin/java').path
    }

    // the JVM that is started is counted as tested: the build says how (the grails-jacoco convention plugin)
    private static List<String> coverage() {
        String agent = System.getProperty('grails.test.fork.jacoco')
        agent ? [agent.replace('@fork@', UUID.randomUUID().toString())] : []
    }

    private void write(String path, String text) {
        File file = new File(project, path)
        file.parentFile.mkdirs()
        file.text = text
    }

    /**
     * @return the files of the project, by their paths in it
     */
    private List<String> written() {
        List<String> files = []
        project.eachFileRecurse { File each ->
            if (each.isFile()) {
                files << project.toPath().relativize(each.toPath()).toString().replace(File.separator, '/')
            }
        }
        files.sort()
    }
}

/**
 * Stands for the class of the application, which a tool that does not start the application is run for.
 */
class SecurityToolsSpecApplication {
}
