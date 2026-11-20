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

import groovy.json.JsonOutput

import spock.lang.Specification
import spock.lang.TempDir
import spock.lang.Unroll

/**
 * Script commands as the build of a project runs them: a script of the project or of a plugin is
 * found by the name of its command, compiled and run with what the guide documents for scripts.
 */
class ScriptCommandRunnerSpec extends Specification {

    @TempDir
    File project

    @TempDir
    File plugin

    ByteArrayOutputStream err = new ByteArrayOutputStream()

    def 'a script of the project is run with its arguments and flags, and its description is not evaluated'() {
        given:
        script('hello-world', '''
            description('Says hello') {
                usage 'grails hello-world [NAME]'
                new File(baseDir, 'described.txt').text = 'the description was evaluated'
            }
            file('said.txt').text = [name, args, flag('greeting'), flag('loud'), flag('absent'), argsMap.keySet().sort(), commandLine.rawArguments.toList()].inspect()
        ''')

        when:
        int exit = run('hello-world', 'Alice', '--greeting=Hi', 'Bob', '--loud')

        then:
        exit == 0
        said() == ''
        new File(project, 'said.txt').text ==
                "['hello-world', ['Alice', 'Bob'], 'Hi', true, null, ['greeting', 'loud'], ['hello-world', 'Alice', '--greeting=Hi', 'Bob', '--loud']]"
        !new File(project, 'described.txt').exists()
    }

    def 'the arguments of a file are those of the script, each as it is'() {
        given:
        script('show', 'file("raw.txt").text = commandLine.rawArguments.collect { it.getBytes("UTF-8").encodeHex().toString() }.join(" ")')
        List<String> hostile = ['a b', "it's", 'say "hi"', '$HOME `id`', '--', '', 'line\nbreak', '\u00e9 \ud83d\ude00', '@|red x|@', '--grails-args-file=/etc/passwd']
        File arguments = new File(plugin, 'arguments.json')
        arguments.setText(JsonOutput.toJson(['show'] + hostile), 'UTF-8')

        when:
        int exit = runner().start("--grails-args-file=${arguments.absolutePath}".toString())

        then:
        exit == 0
        new File(project, 'raw.txt').text == (['show'] + hostile).collect { it.getBytes('UTF-8').encodeHex().toString() }.join(' ')
        arguments.isFile()
    }

    def 'a script renders a template of the project into the package the application configures'() {
        given:
        new File(project, 'grails-app/conf').mkdirs()
        new File(project, 'grails-app/conf/application.yml').text = 'grails:\n    codegen:\n        defaultPackage: com.example\n'
        new File(project, 'src/main/templates').mkdirs()
        new File(project, 'src/main/templates/Thing.groovy').text = 'package ${packageName}\n\nclass ${className}Thing { }\n'
        script('create-thing', '''
            def model = model(args[0])
            render template: template('Thing.groovy'),
                   destination: file("src/main/groovy/${model.packagePath}/${model.simpleName}Thing.groovy"),
                   model: model,
                   overwrite: flag('force') ? true : false
        ''')

        when:
        int exit = run('create-thing', 'Book')

        then:
        exit == 0
        new File(project, 'src/main/groovy/com/example/BookThing.groovy').text == 'package com.example\n\nclass BookThing { }\n'

        when: 'the file is there, and is kept unless the script is told otherwise'
        new File(project, 'src/main/groovy/com/example/BookThing.groovy').text = 'edited'
        exit = run('create-thing', 'Book')

        then:
        exit == 0
        new File(project, 'src/main/groovy/com/example/BookThing.groovy').text == 'edited'

        when:
        exit = run('create-thing', 'Book', '--force')

        then:
        exit == 0
        new File(project, 'src/main/groovy/com/example/BookThing.groovy').text == 'package com.example\n\nclass BookThing { }\n'
    }

    def 'a script and a template of a plugin are found on the class path, a script of the project first'() {
        given: 'a plugin with a script named as a class and a template'
        new File(plugin, 'META-INF/commands').mkdirs()
        new File(plugin, 'META-INF/templates').mkdirs()
        new File(plugin, 'META-INF/commands/CreateJob.groovy').text = '''
            def model = model(args[0])
            render template: 'Job.groovy', destination: file("grails-app/jobs/${model.packagePath}/${model.simpleName}Job.groovy"), model: model
        '''
        new File(plugin, 'META-INF/templates/Job.groovy').text = 'package ${packageName}\n\nclass ${className}Job { }\n'
        new File(plugin, 'META-INF/commands/both.groovy').text = 'file("from.txt").text = "plugin"'

        when:
        int exit = run('create-job', 'demo.Nightly')

        then:
        said() == ''
        exit == 0
        new File(project, 'grails-app/jobs/demo/NightlyJob.groovy').text == 'package demo\n\nclass NightlyJob { }\n'

        when: 'the plugin and the project have a script of one name'
        script('both', 'file("from.txt").text = "project"')
        exit = run('both')

        then:
        exit == 0
        new File(project, 'from.txt').text == 'project'
    }

    def 'a file named as a class is the command of its words, and of two files of one name the first is run'() {
        given:
        script('DataImport', 'file("ran.txt").text = "DataImport " + name')
        script('data-import', 'file("ran.txt").text = "data-import " + name')

        when:
        int exit = run('data-import')

        then:
        exit == 0
        new File(project, 'ran.txt').text == 'DataImport data-import'
        ScriptCommandRunner.commandName('DataImport.groovy') == 'data-import'
        ScriptCommandRunner.commandName('data-IMPORT.groovy') == 'data-import'
        ScriptCommandRunner.commandName('_Events.groovy') == null
        ScriptCommandRunner.commandName('data import.groovy') == null
        ScriptCommandRunner.commandName('data-import.yml') == null
    }

    def 'a script calls another script as a method, and hears when one starts and ends'() {
        given:
        script('outer', '''
            before('inner') { file('order.txt') << 'before ' }
            after('inner') { file('order.txt') << 'after ' }
            on('custom') { String what -> file('order.txt') << "custom:${what} " }
            boolean succeeded = inner('one', '--two')
            notify('custom', 'x')
            file('order.txt') << "outer:${succeeded}"
        ''')
        script('inner', 'file("order.txt") << "inner:${args}:${flag(\'two\')} "')

        when:
        int exit = run('outer')

        then:
        said() == ''
        exit == 0
        new File(project, 'order.txt').text == 'before inner:[one]:true after custom:x outer:true'
    }

    @Unroll
    def 'a script that #what is a failure with one line that says why'() {
        given:
        script('broken', source)
        script('fails', 'return false')

        when:
        int exit = run('broken')

        then:
        exit == 1
        said().readLines()[0] == message
        said().readLines().size() == lines

        where:
        what                                  | source                                   | lines | message
        'returns false'                       | 'return false'                           | 0     | null
        'throws'                              | 'throw new IllegalStateException("no")'  | 1     | 'The script failed: java.lang.IllegalStateException: no'
        'runs Gradle'                         | 'gradle.compileGroovy()'                 | 1     | 'The script broken uses gradle, which a script has only in the Grails shell: a script that the build runs cannot run a build. Run the Gradle tasks from the command line, or make the script a task of the build.'
        'uses the profile'                    | 'profile.name'                           | 1     | 'The script broken uses profile, which a script has only in the Grails shell: there are no profiles here.'
        'runs a command that is no script'    | 'testApp("--debug-jvm")'                 | 1     | 'The script broken calls testApp(), and there is neither such a method nor a script command test-app. A script that the build runs can call another script command; a command of the Grails command line that is no script, such as run-app or test-app, is run from the command line.'
        'calls a script that fails'           | 'return fails()'                         | 0     | null
    }

    def 'a script that uses a class of the Grails shell is not compiled, and it is said why'() {
        given:
        script('old', 'import org.grails.cli.interactive.completers.DomainClassCompleter\nprintln DomainClassCompleter')

        when:
        int exit = run('old')

        then:
        exit == 1
        said().readLines()[0] == 'The script could not be compiled. A script is compiled without the Grails shell: what it imports from org.grails.cli is not there.'
        said().contains('DomainClassCompleter')
    }

    @Unroll
    def 'nothing is run for #what, and what was given is not repeated'() {
        given:
        script('there', 'file("ran.txt").text = "ran"')

        when:
        int exit = runner().start(args as String[])

        then:
        exit == 1
        said().readLines() == [message]
        !new File(project, 'ran.txt').exists()

        where:
        what                           | args                               | message
        'a name that is none'          | ['--', '../there']                 | 'No script was run: the first argument is the name of a script command, in lower-case letters and digits in words joined by single hyphens, of at most 64 characters.'
        'a name with markup'           | ['--', '@|red there|@']            | 'No script was run: the first argument is the name of a script command, in lower-case letters and digits in words joined by single hyphens, of at most 64 characters.'
        'no name'                      | ['--']                             | 'No script was run: the first argument is the name of a script command, in lower-case letters and digits in words joined by single hyphens, of at most 64 characters.'
        'a script there is none of'    | ['--', 'absent']                   | 'There is no script command absent: the project has no script of that name in src/main/scripts, and no plugin on the class path has META-INF/commands/absent.groovy.'
        'a name without what leads it' | ['there']                          | 'A script command is started with -- and then its name and its arguments, or with --grails-args-file= and the path of a file that holds them.'
        'nothing'                      | []                                 | 'A script command is started with -- and then its name and its arguments, or with --grails-args-file= and the path of a file that holds them.'
        'a file that is none'          | ['--grails-args-file=/no/such']    | 'The file of the arguments does not exist or is not a file.'
    }

    def 'the name of a file gives the name of a command by the table the Forge CLI is held to as well'() {
        given: 'the table, which this build and the build of the Forge CLI each have a copy of'
        URL table = getClass().getResource('/script-command-names.tsv')
        List<List<String>> cases = table.getText('UTF-8').readLines().findAll { !it.startsWith('#') && !it.isEmpty() }*.split('\t')*.toList()

        expect:
        cases.size() > 30
        cases.each { List<String> each ->
            assert ScriptCommandRunner.commandName(each[0]) == (each[1] == '-' ? null : each[1])
        }

        and: 'the two copies are one table'
        File root = new File(table.toURI())
        while (root != null && !(new File(root, 'grails-forge/grails-forge-cli').isDirectory() && new File(root, 'grails-core/src/test-cli').isDirectory())) {
            root = root.parentFile
        }
        root != null
        new File(root, 'grails-forge/grails-forge-cli/src/test/resources/script-command-names.tsv').bytes ==
                new File(root, 'grails-core/src/test-cli/resources/script-command-names.tsv').bytes
    }

    private void script(String base, String source) {
        File directory = new File(project, 'src/main/scripts')
        directory.mkdirs()
        new File(directory, "${base}.groovy").text = source
    }

    private ScriptCommandRunner runner() {
        new ScriptCommandRunner(project, new URLClassLoader([plugin.toURI().toURL()] as URL[], getClass().classLoader), new PrintStream(err, true))
    }

    private int run(String... vector) {
        runner().start((['--'] + vector.toList()) as String[])
    }

    private String said() {
        err.toString()
    }
}
