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
package org.apache.grails.core.cli.script

import spock.lang.Specification
import spock.lang.TempDir
import spock.lang.Unroll

/**
 * A script that another script calls as a method has the flags of the command line the calling
 * script was given, as it had in the Grails shell: what the call gives comes over them.
 */
class NestedScriptCommandSpec extends Specification {

    private static final String RECORD = 'file(name + ".txt").text = ' +
            '[args, flag("format"), flag("force"), flag("dry-run"), argsMap.keySet().sort(), ' +
            'commandLine.systemProperties.getProperty("probe.key"), commandLine.rawArguments.toList()].inspect()\n'

    @TempDir
    File project

    ByteArrayOutputStream out = new ByteArrayOutputStream()
    ByteArrayOutputStream err = new ByteArrayOutputStream()

    @Unroll
    def 'a script called by a script is given #what'() {
        given:
        script('outer', "${RECORD}return inner(${call})")
        script('inner', RECORD)

        when:
        int exit = run((['outer'] + given) as String[])

        then:
        exit == 0
        err.toString() == ''
        new File(project, 'inner.txt').text == inner

        where:
        what                                                | given                           | call                   || inner
        'the value of a flag of the calling script'         | ['--format=json']               | ''                     || "[[], 'json', null, null, ['format'], null, ['inner']]"
        'a flag without a value of the calling script'      | ['--force', '--dry-run']        | ''                     || "[[], null, true, true, ['dry-run', 'force'], null, ['inner']]"
        'its own value of a flag over the inherited one'    | ['--format=json', '--force']    | "'--format=xml'"       || "[[], 'xml', true, null, ['force', 'format'], null, ['inner', '--format=xml']]"
        'its own arguments, and none of the calling script' | ['one', '--format=json', 'two'] | "'three'"              || "[['three'], 'json', null, null, ['format'], null, ['inner', 'three']]"
        'its own flags beside the inherited ones'           | ['--format=json']               | "'three', '--dry-run'" || "[['three'], 'json', null, true, ['dry-run', 'format'], null, ['inner', 'three', '--dry-run']]"
        'the system properties among the arguments'         | ['-Dprobe.key=given']           | ''                     || "[[], null, null, null, [], 'given', ['inner']]"
        'its own system property over the inherited one'    | ['-Dprobe.key=given']           | "'-Dprobe.key=own'"    || "[[], null, null, null, [], 'own', ['inner', '-Dprobe.key=own']]"
        'nothing when the calling script was given nothing' | []                              | "'three'"              || "[['three'], null, null, null, [], null, ['inner', 'three']]"
    }

    def 'the flags reach a script through every script between it and the one that was run'() {
        given:
        script('outer', 'return middle("--force")')
        script('middle', "${RECORD}return inner('--format=xml')")
        script('inner', RECORD)

        when:
        int exit = run('outer', 'one', '--format=json', '--dry-run')

        then:
        exit == 0
        err.toString() == ''
        new File(project, 'middle.txt').text == "[[], 'json', true, true, ['dry-run', 'force', 'format'], null, ['middle', '--force']]"
        new File(project, 'inner.txt').text == "[[], 'xml', true, true, ['dry-run', 'force', 'format'], null, ['inner', '--format=xml']]"
    }

    def 'what a called script is given does not change what the calling script has'() {
        given:
        script('outer', "boolean ran = inner('--format=xml', '--extra', 'three')\n${RECORD}return ran")
        script('inner', 'true')

        when:
        int exit = run('outer', '--format=json', 'one')

        then:
        exit == 0
        new File(project, 'outer.txt').text == "[['one'], 'json', null, null, ['format'], null, ['outer', '--format=json', 'one']]"
    }

    def 'reading the arguments of a called script does not set the environment of the build'() {
        given:
        script('outer', "return inner('-Dgrails.env=production')")
        script('inner', 'file("env.txt").text = String.valueOf(System.getProperty("grails.env"))')
        String before = System.getProperty('grails.env')

        when:
        int exit = run('outer')

        then:
        exit == 0
        new File(project, 'env.txt').text == String.valueOf(before)
        System.getProperty('grails.env') == before
    }

    private void script(String base, String source) {
        File directory = new File(project, 'src/main/scripts')
        directory.mkdirs()
        new File(directory, "${base}.groovy").text = source
    }

    private int run(String... vector) {
        new ScriptCommandRunner(project, getClass().classLoader, new PrintStream(out, true), new PrintStream(err, true))
                .start((['--'] + vector.toList()) as String[])
    }
}
