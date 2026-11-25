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
package org.apache.grails.cli

import java.nio.file.Files
import java.util.concurrent.TimeUnit
import spock.lang.IgnoreIf
import spock.lang.Specification
import spock.lang.Unroll

/**
 * The completion script of the distribution, {@code bin/grails_completion}, for the commands of
 * {@code feature} and of {@code plugin}: the script is written from the commands the command line
 * has, so each of them is completed by every name it has, with its options and the names of the
 * features it takes.
 */
@IgnoreIf({ os.windows })
class FeatureAndPluginCompletionSpec extends Specification {

    static final int SECONDS = 60

    File script = new File(System.getProperty('grails.cli.completion'))
    File dir = Files.createTempDirectory('grailscompletion').toFile().canonicalFile

    void cleanup() {
        dir.deleteDir()
    }

    @Unroll
    void "bash completes '#typed' with #expected"() {
        when:
        List<String> offered = bash(typed)

        then:
        offered.containsAll(expected)
        !offered.any { it in absent }

        where:
        typed                               | expected                                                     | absent
        'grails feature '                   | ['add', 'list', 'search', 'info', 'show', 'diff']            | ['create-app']
        'grails feature info grails-q'      | ['grails-quartz']                                            | ['info']
        'grails feature show ca'            | ['cache']                                                    | ['show']
        'grails feature diff ca'            | ['cache']                                                    | ['diff']
        'grails feature info --'            | ['--json', '--type', '--feature-pack-dir']                   | ['--replace']
        'grails feature search --'          | ['--json', '--type']                                         | ['--replace']
        'grails feature diff --'            | ['--replace', '--type']                                      | ['--json']
        'grails feature info --type '       | ['web', 'rest-api', 'plugin', 'web-plugin', 'rest-api-plugin'] | []
        'grails pl'                         | ['plugin', 'plugin-info']                                    | ['list-plugins']
        'grails plugin '                    | ['list', 'search', 'show', 'info']                           | ['add']
        'grails plugin show --'             | ['--json', '--offline', '--plugin-index', '--snapshots']     | ['--deprecated']
        'grails plugin info --'             | ['--json', '--offline', '--plugin-index', '--snapshots']     | ['--deprecated']
        'grails plugin list --'             | ['--json', '--offline', '--plugin-index', '--deprecated']    | ['--snapshots']
        'grails plugin search --'           | ['--json', '--offline', '--plugin-index', '--deprecated']    | ['--snapshots']
        'grails plugin show grails-ca'      | ['grails-cache']                                             | []
    }

    // what the completion function of the script offers for a line, as bash calls it on TAB at the end of the line
    private List<String> bash(String line) {
        List<String> words = line.split(' ', -1).toList()
        String program = """\
            source '${script.absolutePath}'
            COMP_LINE='${line}'
            COMP_POINT=${line.length()}
            COMP_WORDS=(${words.collect { "'${it}'" }.join(' ')})
            COMP_CWORD=${words.size() - 1}
            _complete_grails
            printf '%s\\n' "\${COMPREPLY[@]}"
            """.stripIndent()
        return run(['bash', '--norc', '--noprofile', '-c', program]).readLines()*.trim().findAll { !it.isEmpty() }
    }

    private String run(List<String> command) {
        File output = new File(dir, 'output.txt')
        Process process = new ProcessBuilder(command).directory(dir).redirectOutput(output).redirectErrorStream(true).start()
        assert process.waitFor(SECONDS, TimeUnit.SECONDS): "${command[0]} did not end"
        String said = output.text
        assert process.exitValue() == 0: said
        return said
    }
}
