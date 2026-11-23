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
package org.grails.gradle.plugin.commands

import groovy.json.JsonOutput

import org.gradle.testkit.runner.BuildResult

/**
 * The task that runs a script command, through a build Gradle really runs: the name of a script and
 * its arguments arrive in the JVM that runs scripts as the strings they were given as. The class
 * that runs a script is a stand-in that says what it was started with and runs none.
 */
class ScriptCommandTaskSpec extends CommandProjectSpecification {

    // the stand-in is a class of the test project, which the class path of the task does not have by itself
    private static final String WITH_STAND_IN = '''
        tasks.named('runScriptCommand') {
            classpath += sourceSets.main.runtimeClasspath
        }
    '''

    private static final List<String> HOSTILE = [
            'a b',
            "it's",
            'say "hi"',
            '$HOME ${user.home} `id` $(id)',
            '*',
            'C:\\dir\\',
            '--force',
            '-x',
            '-Dname=value',
            '--arg=inner',
            '--args-file=inner',
            '--grails-args-file=/etc/passwd',
            '--',
            '@file',
            'a,b;c|d&e<f>g^h!i#j',
            ' leading and trailing ',
            'line one\nline two',
            '\u00e9 \u00df \u65e5\u672c \ud83d\ude00',
    ].asImmutable()

    def "the name and each argument given with --arg is one argument of the script, as it is"() {
        given:
        setupCommandProject(WITH_STAND_IN)

        when:
        BuildResult result = executeTask('runScriptCommand', (['data-import'] + HOSTILE).collect { String argument -> "--arg=${argument}".toString() })

        then:
        arguments(result) == ['data-import'] + HOSTILE

        and: 'the JVM was started with what says that a name and arguments follow'
        started(result) == ['--', 'data-import'] + HOSTILE

        and: 'it is told where the project is'
        new File(result.output.readLines().find { it.startsWith('BASE=') }.substring(5)).canonicalFile == projectDir.canonicalFile
    }

    def "the name and the arguments of a file given with --args-file are those of the script, an empty one among them"() {
        given:
        setupCommandProject(WITH_STAND_IN)
        List<String> given = ['data-import'] + HOSTILE + ['', 'x' * 5000, 'correct-horse-battery-staple']
        projectFile(file, JsonOutput.toJson(given))

        when: 'the path is in the project directory unless it is absolute, and the build says everything it does'
        BuildResult result = executeTask('runScriptCommand', ["--args-file=${absolute ? new File(projectDir, file).absolutePath : file}".toString(), '--info'])

        then:
        arguments(result) == given

        and: 'the JVM was started with the path of the file and not with what it holds'
        started(result).size() == 1
        started(result)[0].startsWith('--grails-args-file=')
        new File(started(result)[0].substring('--grails-args-file='.length())).canonicalFile == new File(projectDir, file).canonicalFile
        !result.output.contains('correct-horse-battery-staple')

        and: 'the file is the caller\'s and stays'
        new File(projectDir, file).isFile()

        where:
        file                            | absolute
        'build/tmp/args/arguments.json' | false
        'arguments of a script.json'    | true
    }

    def "a build that reads the script from a file reuses its configuration when the script and its arguments change"() {
        given:
        setupCommandProject(WITH_STAND_IN)
        projectFile('arguments.json', JsonOutput.toJson(['first']))

        when:
        BuildResult first = executeTask('runScriptCommand', ['--args-file=arguments.json'])
        projectFile('arguments.json', JsonOutput.toJson(['second', 'time']))
        BuildResult second = executeTask('runScriptCommand', ['--args-file=arguments.json'])

        then:
        first.output.contains('Configuration cache entry stored')
        arguments(first) == ['first']
        second.output.contains('Configuration cache entry reused')
        arguments(second) == ['second', 'time']
    }

    def "a script is run with what the project depends on and the command line tier, and the application is not compiled for it"() {
        given: 'the task as the plugin registers it, and a task that says what its class path is'
        setupCommandProject('''
            def scriptClasspath = tasks.named('runScriptCommand').get().classpath
            tasks.register('scriptClasspath') {
                doLast {
                    println 'CLASSPATH=' + scriptClasspath.files*.name.sort().join(',')
                }
            }
        ''')

        when:
        BuildResult classpath = executeTask('scriptClasspath')
        List<String> names = classpath.output.readLines().find { it.startsWith('CLASSPATH=') }.substring(10).split(',').toList()

        then:
        names.contains("grails-core-cli-${GRAILS_VERSION}.jar".toString())
        names.contains("grails-console-${GRAILS_VERSION}.jar".toString())
        !names.contains('main')

        when: 'what a run of the task would do'
        BuildResult planned = executeTask('runScriptCommand', ['--arg=data-import', '--dry-run'])

        then:
        planned.output.contains(':runScriptCommand SKIPPED')
        !planned.output.contains(':compileGroovy')
        !planned.output.contains(':classes')
    }

    def "a project the project depends on is built for a script, and the project itself is not compiled"() {
        given:
        setupCommandProject('''
            dependencies {
                implementation project(':companion')
            }
        ''')

        when:
        BuildResult planned = executeTask('runScriptCommand', ['--arg=data-import', '--dry-run'])

        then:
        planned.output.contains(':companion:jar SKIPPED')
        planned.output.contains(':runScriptCommand SKIPPED')
        !planned.output.contains(':compileGroovy')
        !planned.output.readLines().contains(':classes SKIPPED')
    }

    def "the stack trace of a script is asked for with the system property of the build, also by a build whose configuration is reused"() {
        given:
        setupCommandProject(WITH_STAND_IN)

        when:
        BuildResult without = executeTask('runScriptCommand', ['--arg=data-import'])
        BuildResult with = executeTask('runScriptCommand', ['--arg=data-import', '-Dgrails.full.stacktrace=true'])
        BuildResult again = executeTask('runScriptCommand', ['--arg=data-import'])

        then:
        without.output.contains('STACKTRACE=false')
        with.output.contains('STACKTRACE=true')
        again.output.contains('STACKTRACE=false')
    }

    def "a system property of the build that starts with grails. reaches the JVM of the script, also in a build whose configuration is reused"() {
        given:
        setupCommandProject(WITH_STAND_IN)

        when:
        BuildResult without = executeTask('runScriptCommand', ['--arg=data-import'])
        BuildResult with = executeTask('runScriptCommand', ['--arg=data-import', '-Dgrails.probe.one=a b'])
        BuildResult other = executeTask('runScriptCommand', ['--arg=data-import', '-Dgrails.probe.one=c'])

        then:
        without.output.contains('PROBE=null')
        with.output.contains('PROBE=a b')
        with.output.contains('Configuration cache entry reused')
        other.output.contains('PROBE=c')
        other.output.contains('Configuration cache entry reused')
    }

    def "a system property the task sets as it starts is kept over the one the build is given, with the configuration cache disabled, stored and reused"() {
        given:
        setupCommandProject(WITH_STAND_IN + '''
            tasks.named('runScriptCommand') {
                doFirst { systemProperty 'grails.probe.one', 'task' }
            }
        ''')
        List<String> arguments = ['--arg=data-import', '-Dgrails.probe.one=given']

        when:
        BuildResult disabled = executeTask('runScriptCommand', arguments + ['--no-configuration-cache'])
        BuildResult stored = executeTask('runScriptCommand', arguments)
        BuildResult reused = executeTask('runScriptCommand', arguments)

        then:
        stored.output.contains('Configuration cache entry stored')
        reused.output.contains('Configuration cache entry reused')
        verifyAll {
            probe(disabled) == ['task']
            probe(stored) == ['task']
            probe(reused) == ['task']
        }
    }

    def "a script that fails is a failed build"() {
        given:
        setupCommandProject(WITH_STAND_IN)

        when:
        BuildResult result = runner.withArguments('runScriptCommand', '--arg=data-import', '--arg=fail').buildAndFail()

        then:
        result.output.contains('ARGUMENTS=2')
        result.output.contains("Execution failed for task ':runScriptCommand'")
    }

    def "a task that is given #what is refused before anything is started"() {
        given:
        setupCommandProject(WITH_STAND_IN)
        projectFile('arguments.json', JsonOutput.toJson(['data-import']))
        projectFile('empty.json', '[]')

        when:
        BuildResult result = runner.withArguments(['runScriptCommand'] + options).buildAndFail()

        then:
        result.output.contains(message)
        !result.output.contains('STARTEDS=')

        where:
        what                        | options                                             | message
        'no script'                 | []                                                  | "Task 'runScriptCommand' was given no script. Name one with --arg=<name>, and give each of its arguments with another --arg."
        'a script in both ways'     | ['--arg=data-import', '--args-file=arguments.json'] | "Task 'runScriptCommand' was given a script in more than one way. Use one of --arg and --args-file."
        'a file without a name'     | ['--args-file=empty.json']                          | "The file given to task 'runScriptCommand' with --args-file names no script: its first argument is the name of the script."
        'a file that is not there'  | ['--args-file=absent.json']                         | "The file given to task 'runScriptCommand' with --args-file does not exist or is not a file."
    }

    def "a file of arguments that #problem is refused, and nothing of it is repeated"() {
        given:
        setupCommandProject(WITH_STAND_IN)
        projectFile('arguments.json', content)

        when:
        BuildResult result = runner.withArguments('runScriptCommand', '--args-file=arguments.json').buildAndFail()

        then:
        result.output.contains("The file given to task 'runScriptCommand' with --args-file ${message}")
        !result.output.contains('STARTEDS=')
        !result.output.contains('SECRET')

        where:
        problem                      | content                                      | message
        'is not JSON'                | 'data-import SECRET'                         | 'is not a JSON array of strings in UTF-8.'
        'is an object'               | '{"SECRET": "data-import"}'                  | 'is not a JSON array of strings in UTF-8.'
        'is too large'               | '["' + 'x' * (1024 * 1024) + '"]'            | 'has more than 1048576 bytes.'
        'has too many arguments'     | JsonOutput.toJson((0..4096).collect { 'a' }) | 'has more than 4096 arguments.'
        'has the character U+0000'   | '["data-import", "SECRET\\u0000"]'           | 'has an argument with the character U+0000 in it, which no command can be given.'
    }

    private static List<String> arguments(BuildResult result) {
        said(result, 'ARGUMENT')
    }

    private static List<String> started(BuildResult result) {
        said(result, 'STARTED')
    }

    // the value of grails.probe.one the JVM of the script was started with
    private static List<String> probe(BuildResult result) {
        result.output.readLines().findAll { String line -> line.startsWith('PROBE=') }.collect { String line -> line.substring('PROBE='.length()) }
    }

    private static List<String> said(BuildResult result, String what) {
        List<String> lines = result.output.readLines()
        int count = lines.find { String line -> line.startsWith("${what}S=") }.substring(what.length() + 2) as int
        List<String> values = lines.findAll { String line -> line.startsWith("${what}[") }.collect { String line ->
            new String(line.substring(line.indexOf('<') + 1, line.lastIndexOf('>')).decodeHex(), 'UTF-8')
        }
        assert values.size() == count
        values
    }
}
