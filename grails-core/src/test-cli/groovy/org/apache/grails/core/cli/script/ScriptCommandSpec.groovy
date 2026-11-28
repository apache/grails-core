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

import grails.util.Environment

/**
 * What a script command has to work with where the build runs it: the configuration of the
 * application as its files have it, where it runs, and a console that writes whole lines and draws
 * nothing. Each is used by a script, which is how an application or a plugin gets at it.
 */
class ScriptCommandSpec extends Specification {

    @TempDir
    File project

    ByteArrayOutputStream out = new ByteArrayOutputStream()
    ByteArrayOutputStream err = new ByteArrayOutputStream()

    def 'the configuration a script reads is application.yml and then application.groovy, and it is read once'() {
        given:
        File conf = new File(project, 'grails-app/conf')
        conf.mkdirs()
        new File(conf, 'application.yml').text = 'grails:\n    codegen:\n        defaultPackage: com.yml\napp:\n    title: From YAML\n    owner: yml\n'
        new File(conf, 'application.groovy').text = "app.owner = 'groovy'\napp.pages = 12\n"
        script('show-config', '''
            def first = config
            new File(baseDir, 'grails-app/conf/application.groovy').text = "app.owner = 'changed while the script ran'"
            file('config.txt').text = [navigateConfig('app', 'title'), navigateConfig('app', 'owner'), navigateConfig('app', 'pages'),
                                       navigateConfig('app', 'absent'), navigateConfig('grails', 'codegen', 'defaultPackage'),
                                       config.is(first), model('Book').packageName].inspect()
        ''')

        expect:
        run('show-config') == 0
        new File(project, 'config.txt').text == "['From YAML', 'groovy', 12, null, 'com.yml', true, 'com.yml']"
        err.toString() == ''
    }

    def 'a project that configures nothing in Groovy, or nothing at all, has the configuration its files have'() {
        given:
        script('show-config', '''
            file('config.txt').text = [navigateConfig('grails', 'codegen', 'defaultPackage'), model('Book').packageName].inspect()
        ''')

        when: 'no configuration at all'
        int none = run('show-config')

        then:
        none == 0
        new File(project, 'config.txt').text == "[null, '']"

        when: 'application.groovy only'
        File conf = new File(project, 'grails-app/conf')
        conf.mkdirs()
        new File(conf, 'application.groovy').text = "grails.codegen.defaultPackage = 'com.groovy'\n"
        int groovy = run('show-config')

        then:
        groovy == 0
        new File(project, 'config.txt').text == "['com.groovy', 'com.groovy']"
    }

    def 'a script that describes itself with how it is used is run, and nothing is done with the description'() {
        given:
        script('describe', '''
            description('Says what it is', 'grails describe [NAME]')
            file('ran.txt').text = name
        ''')

        expect:
        run('describe') == 0
        new File(project, 'ran.txt').text == 'describe'
        out.toString() == ''
        err.toString() == ''
    }

    def 'a script knows the directory of the project, the environment, the home of the user and the version, and has one console'() {
        given:
        String environment = System.getProperty(Environment.KEY)
        System.setProperty(Environment.KEY, 'staging')
        script('where', '''
            file('where.txt').text = [baseDir.canonicalPath, grailsEnv, userHome == System.getProperty('user.home'),
                                      grailsVersion == org.apache.grails.core.cli.script.ScriptCommand.package?.implementationVersion,
                                      console.is(grailsConsole), console.getClass().simpleName, commandLine.commandName].inspect()
        ''')

        when: 'an argument that names another environment is data'
        int exit = run('where', '-Dgrails.env=production')

        then:
        exit == 0
        new File(project, 'where.txt').text == [project.canonicalPath, Environment.current.name, true, true, true, 'ScriptConsole', 'where'].inspect()

        and: 'the environment the JVM was started with is what it was'
        System.getProperty(Environment.KEY) == 'staging'

        cleanup:
        if (environment == null) {
            System.clearProperty(Environment.KEY)
        } else {
            System.setProperty(Environment.KEY, environment)
        }
    }

    def 'the console of a script draws no progress, has no colours and no line to edit, and writes where the script is told it writes'() {
        given:
        script('progress', '''
            console.indicateProgress()
            console.indicateProgress(1, 2)
            console.indicateProgressPercentage(1L, 2L)
            console.indicateProgress(3)
            console.out.println 'through out'
            console.err.println 'through err'
            console.verbose 'only when it is asked for'
            console.info 'for information'
            console.log 'logged'
            console.warn 'careful'
            console.error 'Problem', 'with a label of its own'
            def answer = console.userInput('Sure?', null as String[])
            file('console.txt').text = [console.ansiEnabled, console.interactiveEnabled, answer, console.lastMessage].inspect()
        ''')

        expect:
        run('progress') == 0
        new File(project, 'console.txt').text == "[false, false, null, 'Sure?']"
        out.toString().readLines() == ['through out', 'for information', 'logged', 'Sure?']
        err.toString().readLines() == ['through err', '| Warning careful', '| Problem with a label of its own']
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
