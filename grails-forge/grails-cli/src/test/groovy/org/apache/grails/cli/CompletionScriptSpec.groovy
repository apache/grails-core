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
import spock.lang.Requires
import spock.lang.Specification
import spock.lang.Unroll

/**
 * The completion script of the program {@code grails} that the distribution has in its {@code bin}
 * directory, loaded into bash and zsh as the guide says: it completes the name {@code grails}, with the
 * commands of the command line as the program has them, their options and the values it knows. The
 * script is the one the build writes for the distribution.
 */
@IgnoreIf({ os.windows })
class CompletionScriptSpec extends Specification {

    static final int SECONDS = 60

    File script = new File(System.getProperty('grails.cli.completion'))
    File dir = Files.createTempDirectory('grailscompletion').toFile().canonicalFile

    void cleanup() {
        dir.deleteDir()
    }

    void "the script completes the program grails, and no other name"() {
        expect:
        script.isFile()
        script.text.contains('complete -F _complete_grails ')
        !script.text.contains('grails-forge-cli')
        !script.text.contains('grails_forge_cli')
    }

    @Unroll
    void "bash completes '#typed' with #expected"() {
        when:
        List<String> offered = bash(typed)

        then:
        offered.containsAll(expected)
        !offered.any { it in absent }

        where:
        typed                                | expected                                                                      | absent
        'grails '                            | ['create-app', 'run-app', 'test-app', 'feature', 'help', 'plugin-info', 'run-command', 'create-controller', 'package-plugin'] | []
        'grails create-'                     | ['create-app', 'create-plugin', 'create-controller', 'create-domain-class', 'create-command', 'create-job'] | ['run-app', 'create-script']
        'grails run-'                        | ['run-app', 'run-command', 'run-script']                                     | ['create-app']
        'grails feature '                    | ['add', 'list']                                                              | ['create-app']
        'grails feature add ca'              | ['cache']                                                                    | ['add']
        'grails create-app --serv'           | ['--servlet']                                                                | []
        'grails create-app --servlet '       | ['tomcat', 'jetty', 'undertow', 'none']                                      | []
        'grails create-app --features grails-q' | ['grails-quartz']                                                         | []
        'grails run-app --'                  | ['--detach', '--foreground', '--port']                                       | []
        'grails ru'                          | ['run', 'run-app', 'run-command', 'run-script']                              | ['create-app']
        'grails st'                          | ['stats', 'stop', 'stop-app']                                                | ['run']
        'grails run --'                      | ['--detach', '--foreground', '--port']                                       | []
        'grails project migrate --type '     | ['web', 'rest-api', 'plugin', 'web-plugin', 'rest-api-plugin']               | []
    }

    @Requires({ new File('/bin/zsh').canExecute() || new File('/usr/bin/zsh').canExecute() })
    void "zsh loads the script through its support for completion scripts of bash, and completes grails with it"() {
        given:
        File zsh = new File('/bin/zsh').canExecute() ? new File('/bin/zsh') : new File('/usr/bin/zsh')

        when:
        String said = run([zsh.path, '-f', '-c', 'autoload -U +X compinit && compinit -u -D; autoload -U +X bashcompinit && bashcompinit; ' +
                "source '${script.absolutePath}' && print -r -- \"\${_comps[grails]}\""])

        then:
        said.trim().startsWith('_bash_complete -F _complete_grails ')
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
