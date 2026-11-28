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

import java.nio.file.Files
import java.util.concurrent.TimeUnit
import java.util.jar.Attributes
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import java.util.jar.Manifest

import groovy.json.JsonOutput

import grails.build.logging.GrailsConsole
import org.apache.grails.core.cli.ExecutionContext
import spock.lang.IgnoreIf
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

    ByteArrayOutputStream out = new ByteArrayOutputStream()
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

    def 'a listener of the start of a command reads the arguments of that command from the context it is given'() {
        given:
        script('outer', '''
            before('inner') { context ->
                assert context.commandLine.remainingArgs == ['one']
            }
            return inner('one')
        ''')
        script('inner', 'true')

        when:
        int exit = run('outer')

        then:
        said() == ''
        exit == 0
    }

    @Unroll
    def 'a listener registered with #listener is given the context the command it hears runs with'() {
        given: 'outer runs middle, which runs inner with the flags middle was given; each listener writes down the context it is given'
        script('outer', """
            void record(Object context, Object heard) {
                file('heard.txt') << [context.getClass().name, context.commandLine.commandName, context.commandLine.remainingArgs,
                                      context.commandLine.undeclaredOptions.keySet().toList(), context.is(heard.executionContext)].inspect() + '\\n'
            }
            ${listener}
            return middle('m1', '--fast')
        """)
        script('middle', 'return inner("i1", "i2")')
        script('inner', 'true')

        when:
        int exit = run('outer', 'o1')

        then:
        said() == ''
        exit == 0
        new File(project, 'heard.txt').text == [ExecutionContext.name, command, remaining, flags, true].inspect() + '\n'

        where:
        listener                                                                                    | command  | remaining    | flags
        "before('middle') { context -> record(context, delegate) }"                                 | 'middle' | ['m1']       | ['fast']
        "after('middle') { context -> record(context, delegate) }"                                  | 'middle' | ['m1']       | ['fast']
        "before('inner') { context -> record(context, delegate) }"                                  | 'inner'  | ['i1', 'i2'] | ['fast']
        "after('inner') { record(it, delegate) }"                                                   | 'inner'  | ['i1', 'i2'] | ['fast']
        "on('innerStart') { org.apache.grails.core.cli.ExecutionContext context -> record(context, delegate) }" | 'inner'  | ['i1', 'i2'] | ['fast']
        "on('middleEnd') { context -> record(context, delegate) }"                                  | 'middle' | ['m1']       | ['fast']
        "after('outer') { context -> record(context, delegate) }"                                   | 'outer'  | ['o1']       | []
    }

    @Unroll
    def 'the example of the guide says what the listener reads, and after is heard when the script #ended'() {
        given:
        script('outer', '''
            before('data-import') { context ->
                console.addStatus "Importing ${context.commandLine.remainingArgs.join(', ')}"
            }
            after('data-import') { context -> console.addStatus "Imported ${context.commandLine.remainingArgs.size()}" }
            try {
                dataImport('orders.csv')
            } catch (IllegalStateException ignored) {
            }
            return true
        ''')
        script('data-import', body)

        when:
        int exit = run('outer')

        then:
        said() == ''
        exit == 0
        out.toString().readLines() == ['| Importing orders.csv'] + heard

        where:
        ended               | body                                   | heard
        'returned true'     | 'true'                                 | ['| Imported 1']
        'returned false'    | 'false'                                | ['| Imported 1']
        'threw, and is not' | 'throw new IllegalStateException("x")' | []
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
        'returns false'                       | 'return false'                           | 1     | 'The script broken returned false, which is how a script says that it did not succeed.'
        'throws'                              | 'throw new IllegalStateException("no")'  | 1     | 'The script failed: java.lang.IllegalStateException: no'
        'runs Gradle'                         | 'gradle.compileGroovy()'                 | 1     | 'The script broken uses gradle, which a script has only in the Grails shell: a script that the build runs cannot run a build. Run the Gradle tasks from the command line, or make the script a task of the build.'
        'uses the profile'                    | 'profile.name'                           | 1     | 'The script broken uses profile, which a script has only in the Grails shell: there are no profiles here.'
        'runs a command that is no script'    | 'testApp("--debug-jvm")'                 | 1     | 'The script broken calls testApp(), and there is neither such a method nor a script command test-app. A script that the build runs can call another script command; a command of the Grails command line that is no script, such as run-app or test-app, is run from the command line.'
        'calls a script that fails'           | 'return fails()'                         | 1     | 'The script broken returned false, which is how a script says that it did not succeed.'
    }

    def 'a script that uses a class of the Grails shell is not compiled, and it is said why'() {
        given:
        script('old', 'import org.grails.cli.interactive.completers.DomainClassCompleter\nprintln DomainClassCompleter')

        when:
        int exit = run('old')

        then:
        exit == 1
        said().readLines() == ['The script could not be compiled, line 1: unable to resolve class org.grails.cli.interactive.completers.DomainClassCompleter. ' +
                                       'A script is compiled without the Grails shell: what it imports from org.grails.cli is not there.']
    }

    def 'a script with a mistake in it is not compiled, and the mistake and its line are said, not the Grails shell'() {
        given:
        script('wrong', source)

        when:
        int exit = run('wrong')

        then:
        exit == 1
        said().readLines().size() == 1
        said().startsWith("The script could not be compiled, line ${line}: ")
        said().contains(mistake)
        !said().contains('org.grails.cli')
        !said().contains('Grails shell')

        where:
        source                                      | line | mistake
        'println 1\nprintln 2\ndef x = (\n'        | 3    | "Missing ')'"    
        'println 1\nimport no.such.Type\nprintln 2' | 2    | 'unable to resolve class no.such.Type'
    }

    def 'what a script says through the console is whole lines of plain text, and the stack trace of what it threw is shown when asked for'() {
        given:
        script('talk', '''
            console.addStatus "model: Book"
            console.updateStatus "working"
            addStatus "added"
            console.error 'it went wrong'
            error 'so did this'
            console.warning 'careful'
            console.error 'Label', 'with a label'
            console.indicateProgress()
            println 'printed'
            console.info 'info'
            grailsConsole.log 'logged'
            throw new IllegalStateException('thrown')
        ''')

        when:
        int exit = run('talk')

        then:
        exit == 1
        out.toString().readLines() == ['| model: Book', '| working', '| added', 'printed', 'info', 'logged']
        err.toString().readLines() == ['| Error it went wrong', '| Error so did this', '| Warning careful', '| Label with a label',
                                       'The script failed: java.lang.IllegalStateException: thrown']
        !(out.toString() + err.toString()).contains('\u001b')

        when: 'the build was started for stack traces'
        err.reset()
        System.setProperty('grails.full.stacktrace', 'true')
        run('talk')

        then:
        err.toString().readLines().findAll { it.startsWith('The script failed') || it.startsWith('java.lang.IllegalStateException: thrown') }.size() == 2
        err.toString().contains('\tat ')

        cleanup:
        System.clearProperty('grails.full.stacktrace')
    }

    @Unroll
    def 'a closure of a script reaches what its delegate has: #what'() {
        given:
        new File(project, 'src/main/templates').mkdirs()
        new File(project, 'src/main/templates/c.txt').text = 'c'
        new File(plugin, 'META-INF/templates/t').mkdirs()
        new File(plugin, 'META-INF/templates/t/a.txt').text = 'a'
        new File(plugin, 'META-INF/templates/t/b.txt').text = 'b'
        script('delegates', source + "\nfile('result.txt').text = String.valueOf(result)\n")

        when:
        int exit = run('delegates')

        then:
        said() == ''
        exit == 0
        new File(project, 'result.txt').text == expected

        where:
        what                           | expected                    | source
        'copy with from and into'      | '[a.txt, b.txt]'            | 'mkdir "build/cc"\ncopy {\n from templates("t/*.txt")\n into "build/cc"\n}\ndef result = new File(baseDir, "build/cc").list().toList().sort()'
        'an Ant task with a file set'  | 'true'                      | 'ant.zip(destfile: "${baseDir}/build/x.zip") { fileset(dir: "${baseDir}/src/main/templates") }\ndef result = new File(baseDir, "build/x.zip").isFile()'
        'a builder'                    | "<root><child a='1' /></root>" | 'def w = new StringWriter()\nnew groovy.xml.MarkupBuilder(new IndentPrinter(new PrintWriter(w), "", false)).root { child(a: 1) }\ndef result = w'
        'a delegate that is set'       | '3'                         | 'def c = { size() }\nc.delegate = "abc"\ndef result = c()'
        'a method that is caught'      | 'caught noSuchThing'        | 'def result\ntry { noSuchThing(1) } catch (MissingMethodException e) { result = "caught " + e.method }'
        'a property that is caught'    | 'caught gradle'             | 'def result\ntry { gradle.build() } catch (MissingPropertyException e) { result = "caught " + e.property }'
        'a property of the delegate'   | 'p'                         | 'def c = { profile }\nc.delegate = [profile: "p"]\ndef result = c()'
    }

    def 'the arguments of a script are data: none of them changes the environment or a property of the JVM'() {
        given:
        script('show', '''
            file('seen.txt').text = [System.getProperty('grails.env'), System.getProperty('script.probe'), grailsEnv, commandLine.systemProperties['grails.env'],
                                     commandLine.systemProperties['script.probe'], args, flag('after')].inspect()
        ''')
        String before = System.getProperty('grails.env')

        when:
        int exit = run('show', '-Dgrails.env=production', '-Dscript.probe=1', 'one', '--', '--after')

        then:
        exit == 0
        System.getProperty('grails.env') == before
        System.getProperty('script.probe') == null
        new File(project, 'seen.txt').text == [before, null, grails.util.Environment.current.name, 'production', '1', ['one'], true].inspect()
    }

    @IgnoreIf({ os.windows })
    def 'the script of a project that is run for a name is the one the Forge CLI lists, by the table that is held to as well'() {
        given:
        List<List<String>> cases = table('script-command-files.tsv')

        expect:
        cases.size() > 12
        cases.eachWithIndex { List<String> each, int number ->
            File at = layout(new File(plugin, "case${number}"), each[0])
            ByteArrayOutputStream errors = new ByteArrayOutputStream()
            int exit = new ScriptCommandRunner(at, getClass().classLoader, new PrintStream(errors, true)).start('--', each[1])
            File ran = new File(at, 'ran.txt')
            assert (ran.isFile() ? ran.text : '-') == each[2], each.toString() + ' ' + errors
            assert exit == (each[2] == '-' ? 1 : 0), each.toString()
            assert each[2] != '-' || errors.toString().startsWith("There is no script command ${each[1]}: "), each.toString()
        }
    }

    def 'a script in the jar of a plugin is found by the name its file gives it, whichever way the file is named'() {
        given: 'a jar as a build writes it, with a manifest and entries for its directories'
        File jar = new File(plugin, 'plugin-cli.jar')
        jar.withOutputStream { OutputStream output ->
            Manifest manifest = new Manifest()
            manifest.mainAttributes.put(Attributes.Name.MANIFEST_VERSION, '1.0')
            JarOutputStream written = new JarOutputStream(output, manifest)
            ['META-INF/commands/'].each { String directory ->
                written.putNextEntry(new JarEntry(directory))
                written.closeEntry()
            }
            ['XMLImport', 'dataImport', 'CreateThing', 'plain-name'].each { String each ->
                written.putNextEntry(new JarEntry("META-INF/commands/${each}.groovy"))
                written.write("new File(baseDir, 'ran.txt').text = '${each}'".getBytes('UTF-8'))
                written.closeEntry()
            }
            written.putNextEntry(new JarEntry('META-INF/commands/deeper/nested.groovy'))
            written.write("new File(baseDir, 'ran.txt').text = 'nested'".getBytes('UTF-8'))
            written.closeEntry()
            written.close()
        }
        URLClassLoader loader = new URLClassLoader([jar.toURI().toURL()] as URL[], getClass().classLoader)

        expect:
        new ScriptCommandRunner(project, loader, new PrintStream(err, true)).start('--', name) == (base == null ? 1 : 0)
        (new File(project, 'ran.txt').isFile() ? new File(project, 'ran.txt').text : null) == base

        cleanup:
        loader?.close()

        where:
        name           | base
        'xmli-mport'   | 'XMLImport'
        'data-import'  | 'dataImport'
        'create-thing' | 'CreateThing'
        'plain-name'   | 'plain-name'
        'nested'       | null
    }

    @Unroll
    def 'the script #name of #module runs as the build runs it: #outcome'() {
        given: 'the scripts and templates of the plugin, where its jar has them'
        File root = new File(getClass().getResource('/script-command-names.tsv').toURI())
        while (!(new File(root, 'grails-forge/grails-forge-cli').isDirectory() && new File(root, 'grails-core/src/test-cli').isDirectory())) {
            root = root.parentFile
        }
        copyTree(new File(root, "${module}/src/main/scripts"), new File(plugin, 'META-INF/commands'))
        if (new File(root, "${module}/src/main/templates").isDirectory()) {
            copyTree(new File(root, "${module}/src/main/templates"), new File(plugin, 'META-INF/templates'))
        }
        new File(project, 'grails-app/conf').mkdirs()
        new File(project, 'grails-app/conf/application.yml').text = 'grails:\n    codegen:\n        defaultPackage: com.example\n'
        new File(project, 'grails-app/i18n').mkdirs()
        new File(project, 'grails-app/i18n/messages.properties').text = ''
        new File(project, 'gradle.properties').text = 'grailsVersion=9.0.0\n'
        new File(project, 'grails-app/domain/com/example').mkdirs()
        new File(project, 'grails-app/domain/com/example/Book.groovy').text = 'package com.example\n\nclass Book {\n    String title\n}\n'

        when:
        int exit = run(([name] + arguments) as String[])
        String first = said().readLines().find { !it.isEmpty() } ?: ''

        then:
        first.startsWith(outcome)
        exit == (outcome.isEmpty() ? 0 : 1)
        written.every { String path -> new File(project, path).isFile() }

        where:
        module                               | name                              | arguments          | outcome | written
        'grails-core/src/test-cli/resources/script-command-plugin' | 'hello-script' | ['hello'] | '' | []
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
        given:
        List<List<String>> cases = table('script-command-names.tsv')

        expect:
        cases.size() > 30
        cases.each { List<String> each ->
            assert ScriptCommandRunner.commandName(each[0]) == (each[1] == '-' ? null : each[1])
        }
    }

    def 'a warning and an error of a script go to the errors, what it appends to the output, and neither fails it'() {
        given:
        script('talk', """
            console.warn 'a warning'
            warn 'another'
            console.append 'appended'
            console.verbose 'a detail'
            verbose 'another detail'
            console.error 'it went wrong'
            file('ended.txt').text = 'the script went on'
        """)

        when:
        int exit = run('talk')

        then: 'an error that is said is not a failure: the script goes on, and succeeds unless it returns false'
        exit == 0
        out.toString().readLines() == ['appended']
        err.toString().readLines() == ['| Warning a warning', '| Warning another', '| Error it went wrong']
        new File(project, 'ended.txt').text == 'the script went on'

        when: 'the build was started for the details'
        out.reset()
        System.setProperty('grails.verbose', 'true')
        exit = run('talk')

        then:
        exit == 0
        out.toString().readLines() == ['appended', 'a detail', 'another detail']

        cleanup:
        System.clearProperty('grails.verbose')
    }

    @Unroll
    def 'an error a script says with what was thrown is one line, and the stack trace follows it with #property'() {
        given:
        script('talk', """
            console.error 'with a cause', new IllegalStateException('the cause')
            console.error new IllegalArgumentException('alone')
            return false
        """)

        when:
        int exit = run('talk')

        then:
        exit == 1
        out.toString() == ''
        err.toString().readLines() == ['| Error with a cause', '| Error alone',
                                       'The script talk returned false, which is how a script says that it did not succeed.']

        when:
        err.reset()
        System.setProperty(property, 'true')
        exit = run('talk')
        List<String> lines = err.toString().readLines()

        then:
        exit == 1
        lines.indexOf('java.lang.IllegalStateException: the cause') == lines.indexOf('| Error with a cause') + 1
        lines.indexOf('java.lang.IllegalArgumentException: alone') == lines.indexOf('| Error alone') + 1
        lines.count { String line -> line.startsWith('\tat ') } > 2
        lines.last() == 'The script talk returned false, which is how a script says that it did not succeed.'

        cleanup:
        System.clearProperty(property)

        where:
        property << ['grails.full.stacktrace', 'grails.show.stacktrace']
    }

    @Unroll
    def 'a script that #what fails with the type of what was thrown and its message'() {
        given:
        script('broken', source)

        when:
        int exit = run('broken')

        then:
        exit == 1
        out.toString() == ''
        said().readLines()[0].startsWith(message)
        !said().contains('\tat ')

        where:
        what                                   | source                                    | message
        'throws an error'                      | 'throw new AssertionError((Object) "no")' | 'The script failed: java.lang.AssertionError: no'
        'throws without a message'             | 'throw new IllegalStateException()'       | 'The script failed: java.lang.IllegalStateException: null'
        'calls a method a value of it has not' | '"text".noSuchMethod()'                   | 'The script failed: groovy.lang.MissingMethodException: No signature of method: noSuchMethod for class: java.lang.String'
        'uses a property there is none of'     | 'println noSuchThing'                     | 'The script failed: groovy.lang.MissingPropertyException: No such property: noSuchThing for class: broken'
        'uses the Spring context of the shell' | 'spring.getBean("x")'                     | 'The script broken uses spring, which a script has only in the Grails shell.'
        'is a class and has no statements'     | 'class Thing { }'                         | 'broken is not a script: its file declares a class and has no statements of its own.'
    }

    def 'whether there is a script of a name is told without running it'() {
        given:
        script('there', 'file("ran.txt").text = "ran"')
        new File(plugin, 'META-INF/commands').mkdirs()
        new File(plugin, 'META-INF/commands/OfPlugin.groovy').text = 'file("ran.txt").text = "ran"'

        expect:
        runner().has('there')
        runner().has('of-plugin')
        !runner().has('absent')
        !runner().has('../there')
        !runner().has(null)
        !new File(project, 'ran.txt').exists()
    }

    @Unroll
    def 'a script whose file is named #file, in a jar without entries for its directories #withManifest, is #found as #name'() {
        given: 'a jar that has the file and no entry for META-INF/commands'
        File jar = jar('plugin-cli.jar', manifest ? 'example.com' : null, false, [(file): file])
        URLClassLoader loader = new URLClassLoader([jar.toURI().toURL()] as URL[], getClass().classLoader)

        expect:
        new ScriptCommandRunner(project, loader, new PrintStream(err, true)).start('--', name) == (found == 'found' ? 0 : 1)
        (new File(project, 'ran.txt').isFile() ? new File(project, 'ran.txt').text : null) == (found == 'found' ? file : null)

        cleanup:
        loader?.close()

        where:
        file                | name                  | manifest || found
        'createThing'       | 'create-thing'        | true     || 'found'
        'CreateThing'       | 'create-thing'        | true     || 'found'
        'XMLImport'         | 'xmli-mport'          | true     || 'found'
        'plain-name'        | 'plain-name'          | true     || 'found'
        'Create-Thing'      | 'create-thing'        | true     || 'found'
        'CREATE-thing'      | 'create-thing'        | true     || 'found'
        'plain-name'        | 'absent'              | true     || 'not found'
        // a jar without a manifest is no jar of a plugin: no script is taken from it, whatever its name
        'createThing'       | 'create-thing'        | false    || 'not found'
        'CreateThing'       | 'create-thing'        | false    || 'not found'
        'plain-name'        | 'plain-name'          | false    || 'not found'
        'Create-Thing'      | 'create-thing'        | false    || 'not found'
        'XMLImport'         | 'xmli-mport'          | false    || 'not found'

        withManifest = manifest ? 'with a manifest' : 'without a manifest'
    }

    def 'a jar without a manifest is not searched for scripts, also when it has an entry for the directory'() {
        given: 'a jar that has the directory and the file, and no manifest'
        File jar = jar('plugin-cli.jar', null, true, ['plain-name': 'plain-name'])
        URLClassLoader loader = new URLClassLoader([jar.toURI().toURL()] as URL[], getClass().classLoader)

        expect:
        new ScriptCommandRunner(project, loader, new PrintStream(err, true)).start('--', 'plain-name') == 1
        !new File(project, 'ran.txt').exists()

        and: 'which jar it is and why is said, once'
        err.toString().readLines().findAll { it.startsWith('No script is taken from ') } ==
                ["No script is taken from ${jar.toURI().toURL().path}: it has files in META-INF/commands and no manifest, and the jar of a plugin has a manifest.".toString()]

        cleanup:
        loader?.close()
    }

    @Unroll
    def 'a script of a jar of Grails is run before one of the same name in another jar, whatever the order of the class path: #order'() {
        given: 'a jar of Grails, whose name comes after that of the other, and another jar without entries for its directories'
        Map<String, File> jars = [
                grails: jar('zz-grails-thing.jar', 'grails.apache.org', true, [both: 'grails']),
                other : jar('aa-plugin.jar', 'example.com', false, [both: 'other']),
        ]
        URLClassLoader loader = new URLClassLoader(order.collect { String each -> jars[each].toURI().toURL() } as URL[], getClass().classLoader)

        expect:
        new ScriptCommandRunner(project, loader, new PrintStream(err, true)).start('--', 'both') == 0
        new File(project, 'ran.txt').text == 'grails'

        cleanup:
        loader?.close()

        where:
        order << [['grails', 'other'], ['other', 'grails']]
    }

    @Unroll
    def 'of two jars that are not of Grails, the script of the jar whose name comes first is run, whatever the order of the class path: #order'() {
        given: 'a jar with entries for its directories, and one without them whose name comes first'
        Map<String, File> jars = [
                b: jar('b-plugin.jar', 'example.com', true, [both: 'b']),
                a: jar('a-plugin.jar', 'example.org', false, [both: 'a']),
        ]
        URLClassLoader loader = new URLClassLoader(order.collect { String each -> jars[each].toURI().toURL() } as URL[], getClass().classLoader)

        expect:
        new ScriptCommandRunner(project, loader, new PrintStream(err, true)).start('--', 'both') == 0
        new File(project, 'ran.txt').text == 'a'

        cleanup:
        loader?.close()

        where:
        order << [['a', 'b'], ['b', 'a']]
    }

    def 'a script that asks is asked once and has no answer: the build gives it nothing to read'() {
        given:
        script('ask', '''
            file('answers.txt').text = [console.userInput('Name?'), console.userInput('Sure?', ['y', 'n'] as String[]),
                                        console.secureUserInput('Password?'), console.showPrompt()].inspect()
        ''')

        expect:
        run('ask') == 0
        new File(project, 'answers.txt').text == '[null, null, null, null]'
        out.toString().readLines() == ['Name?', 'Sure?[y,n]', 'Password?']
        said() == ''
    }

    def 'the console there was is the console again after a script, and none is made when there was none'() {
        given:
        script('quiet', 'console.addStatus "said"')

        when: 'there is none'
        GrailsConsole.removeInstance()
        int exit = run('quiet')

        then:
        exit == 0
        !GrailsConsole.hasInstance()

        when: 'there is one'
        GrailsConsole mine = new ScriptConsole(null, null)
        GrailsConsole.setInstance(mine)
        exit = run('quiet')

        then:
        exit == 0
        GrailsConsole.getInstance().is(mine)

        cleanup:
        GrailsConsole.removeInstance()
    }

    @Unroll
    def 'the JVM the build starts for a script ends with #status when the script #what, and writes where the JVM writes'() {
        given:
        script('talk', """
            console.addStatus 'a status'
            println 'printed'
            console.error 'an error'
            file('given.txt').text = args.inspect()
            ${statement}
        """)
        File stdout = new File(plugin, 'stdout.txt')
        File stderr = new File(plugin, 'stderr.txt')
        File options = new File(plugin, 'java.options')
        options.setText("-cp\n\"${System.getProperty('java.class.path').replace('\\', '\\\\')}\"\n", 'UTF-8')
        // the JVM that is started is counted as tested: the build says how (the grails-jacoco convention plugin)
        String coverage = System.getProperty('grails.test.fork.jacoco')
        List<String> counted = coverage ? [coverage.replace('@fork@', UUID.randomUUID().toString())] : []
        File home = new File(plugin, 'home')
        home.mkdirs()

        when:
        Process process = new ProcessBuilder([new File(System.getProperty('java.home'), 'bin/java').path, "@${options.path}".toString()] + counted +
                ["-Dbase.dir=${project.path}".toString(), "-Duser.home=${home.path}".toString(),
                 ScriptCommandRunner.name, '--', 'talk', 'two words', 'three'])
                .directory(plugin)
                .redirectOutput(stdout)
                .redirectError(stderr)
                .start()
        boolean ended = process.waitFor(5, TimeUnit.MINUTES)

        then:
        ended
        process.exitValue() == status
        new File(project, 'given.txt').text == "['two words', 'three']"
        stdout.readLines() == ['| a status', 'printed']

        and: 'beside what the JVM itself warns of, and the empty line it ends a warning with'
        stderr.readLines().findAll { String line -> line && !line.startsWith('OpenJDK') && !line.startsWith('WARNING') } == ['| Error an error'] + said

        and: 'no terminal was opened for it, so no history of one is in the home directory of the user'
        home.list().toList() == []

        cleanup:
        process?.destroyForcibly()?.waitFor()

        where:
        what            | statement                          | status | said
        'succeeds'      | ''                                 | 0      | []
        'returns false' | 'return false'                     | 1      | ['The script talk returned false, which is how a script says that it did not succeed.']
        'throws'        | 'throw new RuntimeException("no")' | 1      | ['The script failed: java.lang.RuntimeException: no']
    }

    // A project and what is outside it, as a line of the table of cases says: see script-command-files.tsv.
    private static File layout(File top, String what) {
        File project = new File(top, 'project')
        File outside = new File(top, 'outside')
        new File(outside, 'main/scripts').mkdirs()
        String elsewhere = "new File(baseDir, 'ran.txt').text = 'outside'\n"
        [outside, new File(outside, 'main'), new File(outside, 'main/scripts')].each { File each -> new File(each, 'planted.groovy').text = elsewhere }
        new File(outside, 'script.groovy').text = elsewhere
        List<String> items = what.split(' ').toList()
        String link = items.find { String item -> item.startsWith('L:') }?.substring(2)
        File scripts = new File(project, 'src/main/scripts')
        if (link == null) {
            scripts.mkdirs()
        } else {
            File at = new File(project, link)
            at.parentFile.mkdirs()
            Files.createSymbolicLink(at.toPath(), (link == 'src' ? outside : new File(outside, link.substring(4))).toPath())
        }
        items.each { String item ->
            File file = new File(scripts, item.substring(2))
            if (item.startsWith('f:')) {
                file.text = "new File(baseDir, 'ran.txt').text = '${file.name}'\n"
            } else if (item.startsWith('l:')) {
                Files.createSymbolicLink(file.toPath(), new File(outside, 'script.groovy').toPath())
            } else if (item.startsWith('d:')) {
                file.mkdirs()
            }
        }
        return project
    }

    private List<List<String>> table(String name) {
        URL table = getClass().getResource('/' + name)
        File root = new File(table.toURI())
        while (root != null && !(new File(root, 'grails-forge/grails-forge-cli').isDirectory() && new File(root, 'grails-core/src/test-cli').isDirectory())) {
            root = root.parentFile
        }
        // the two copies are one table
        assert root != null
        assert new File(root, "grails-forge/grails-forge-cli/src/test/resources/${name}").bytes == new File(root, "grails-core/src/test-cli/resources/${name}").bytes
        return table.getText('UTF-8').readLines().findAll { String line -> !line.startsWith('#') && !line.isEmpty() }*.split('\t')*.toList()
    }

    private static void copyTree(File from, File to) {
        to.mkdirs()
        from.eachFileRecurse { File each ->
            File copy = new File(to, from.toPath().relativize(each.toPath()).toString())
            if (each.directory) {
                copy.mkdirs()
            } else {
                copy.parentFile.mkdirs()
                copy.bytes = each.bytes
            }
        }
    }

    // a jar of a plugin: each script writes ran.txt with the text it is given; a vendor of null writes no manifest
    private File jar(String fileName, String vendor, boolean directories, Map<String, String> scripts) {
        File jar = new File(plugin, fileName)
        jar.withOutputStream { OutputStream output ->
            JarOutputStream written
            if (vendor == null) {
                written = new JarOutputStream(output)
            } else {
                Manifest manifest = new Manifest()
                manifest.mainAttributes.put(Attributes.Name.MANIFEST_VERSION, '1.0')
                manifest.mainAttributes.put(Attributes.Name.IMPLEMENTATION_VENDOR, vendor)
                written = new JarOutputStream(output, manifest)
            }
            if (directories) {
                ['META-INF/commands/'].each { String directory ->
                    written.putNextEntry(new JarEntry(directory))
                    written.closeEntry()
                }
            }
            scripts.each { String base, String text ->
                written.putNextEntry(new JarEntry("META-INF/commands/${base}.groovy"))
                written.write("new File(baseDir, 'ran.txt').text = '${text}'".getBytes('UTF-8'))
                written.closeEntry()
            }
            written.close()
        }
        jar
    }

    private void script(String base, String source) {
        File directory = new File(project, 'src/main/scripts')
        directory.mkdirs()
        new File(directory, "${base}.groovy").text = source
    }

    private ScriptCommandRunner runner() {
        new ScriptCommandRunner(project, new URLClassLoader([plugin.toURI().toURL()] as URL[], getClass().classLoader), new PrintStream(out, true), new PrintStream(err, true))
    }

    private int run(String... vector) {
        runner().start((['--'] + vector.toList()) as String[])
    }

    private String said() {
        err.toString()
    }
}
