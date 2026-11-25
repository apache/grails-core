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
package org.grails.forge.features.scaffolding

import java.util.zip.ZipFile

import org.grails.forge.application.ApplicationType
import org.grails.forge.utils.CommandSpec

/**
 * What {@code generate-all} of the scaffolding plugin writes into a REST API application is built and tested by
 * the build of that application: the controller, the service and the JSON views compile, and the two specs that
 * are generated pass once the places they mark with TODO are filled in, which is all they ask of the developer.
 *
 * <p>The application has the commands of the plugin and not the plugin: neither it nor GSP nor the fields plugin is
 * on the class path the application runs with or in the archive it is packaged as.</p>
 */
class RestScaffoldingBuildSpec extends CommandSpec {

    /** The plugin and the two it depends on, with what follows the name of each in a dependency report */
    private static final List<String> OF_THE_PLUGIN = ['grails-scaffolding:', 'grails-gsp:', 'grails-fields:']

    void 'the specs generate-all writes into a REST API application pass once their TODOs are filled in'() {
        given: 'a REST API application with the scaffolding feature and a domain class'
        generateProjectWithDefaults(ApplicationType.REST_API, ['scaffolding'])
        write('grails-app/domain/example/grails/Bird.groovy', 'package example.grails\n\nclass Bird {\n    String name\n}\n')

        expect: 'the build declares the commands of the plugin for the class path a command is run with, and not the plugin'
        file('build.gradle').text.contains('    grailsCli "org.apache.grails:grails-scaffolding-cli"\n')
        !file('build.gradle').text.contains('"org.apache.grails:grails-scaffolding"')

        when:
        String output = executeGradle('runCommand', '-Pargs=generate-all example.grails.Bird').output

        then: 'the templates of a REST API application were taken: it has JSON views and no GSP'
        ['RestController.groovy', 'RestService.groovy', 'RestSpec.groovy', 'RestServiceSpec.groovy', 'index.gson', 'show.gson', '_domain.gson'].every {
            output.contains("Rendered template ${it} to destination")
        }
        file('grails-app/controllers/example/grails/BirdController.groovy').text.contains("static responseFormats = ['json']")
        ['index.gson', 'show.gson', '_bird.gson'].every { file("grails-app/views/bird/${it}").isFile() }
        !file('grails-app/views/bird/index.gsp').exists()

        when: 'the controller spec is told what a valid bird is'
        replace('src/test/groovy/example/grails/BirdControllerSpec.groovy', [
                'assert false, "TODO: Provide a populateValidParams() implementation for this generated test suite"': "params[\"name\"] = 'Robin'",
        ])

        and: 'the service spec is given its birds, and says which of them a page of two after the first two has'
        replace('src/integration-test/groovy/example/grails/BirdServiceSpec.groovy', [
                'assert false, "TODO: Provide a setupData() implementation for this generated test suite"':
                        "['a', 'b'].each { new Bird(name: it).save(flush: true, failOnError: true) }\n" +
                        "        Bird bird = new Bird(name: 'c').save(flush: true, failOnError: true)\n" +
                        "        ['d', 'e'].each { new Bird(name: it).save(flush: true, failOnError: true) }",
                '//bird.id'                                                                              : 'bird.id',
                'assert false, "TODO: Verify the correct instances are returned"'                        : "birdList*.name == ['c', 'd']",
                'assert false, "TODO: Provide a valid instance to save"\n        Bird bird = new Bird()' : "Bird bird = new Bird(name: 'f')",
        ])

        then: 'nothing else in either of them fails by itself'
        !file('src/test/groovy/example/grails/BirdControllerSpec.groovy').text.contains('assert false')
        !file('src/integration-test/groovy/example/grails/BirdServiceSpec.groovy').text.contains('assert false')

        when: 'the application runs its unit tests and its integration tests'
        // the build is given the environment of the tests, which it has by itself when it is run from a command line: the
        // JVM of this spec has the generator's, production, and TestKit hands its system properties to the build
        executeGradle('test', 'integrationTest', '-Dgrails.env=test')

        then: 'every test of the two specs ran and passed'
        results('test', 'BirdControllerSpec') == [tests: 11, failures: 0, errors: 0, skipped: 0]
        results('integrationTest', 'BirdServiceSpec') == [tests: 5, failures: 0, errors: 0, skipped: 0]

        and: 'the build listed the commands of the plugin for the command line when it compiled the application'
        String catalog = file('.grails/cache/command-catalog.json').text
        ['generate-all', 'generate-controller', 'generate-views', 'install-templates'].every {
            catalog.contains("\"name\":\"${it}\"")
        }
        catalog.contains('org.apache.grails:grails-scaffolding-cli:')

        when: 'the class path the application runs with, and the class path a command is run with'
        String runtime = executeGradle('dependencies', '--configuration', 'runtimeClasspath').output
        String commands = executeGradle('dependencies', '--configuration', 'grailsCliClasspath').output

        then: 'the application runs without the scaffolding plugin and what it brings, GSP and the fields plugin'
        runtime.contains('org.apache.grails:grails-views-gson')
        OF_THE_PLUGIN.every { !runtime.contains("org.apache.grails:${it}") }

        and: 'which the commands have'
        (OF_THE_PLUGIN + 'grails-scaffolding-cli').every { commands.contains("org.apache.grails:${it}") }

        when: 'the application is packaged'
        executeGradle('bootWar')
        List<String> libraries = []
        new ZipFile(file('build/libs').listFiles().find { it.name.endsWith('.war') }).withCloseable { ZipFile war ->
            libraries.addAll(war.entries().toList()*.name.findAll { it.endsWith('.jar') })
        }

        then: 'the archive has the JSON views plugin and none of the three'
        libraries.any { it.contains('/grails-views-gson-') }
        !libraries.any { String library -> ['/grails-scaffolding-', '/grails-gsp-9', '/grails-fields-'].any { library.contains(it) } }

        when: 'the templates of the plugin are copied into the project, and the views are generated again from them'
        String installed = executeGradle('runCommand', '-Pargs=install-templates').output
        file('src/main/templates/scaffolding/show.gson') << '// of the project\n'
        executeGradle('runCommand', '-Pargs=generate-views example.grails.Bird --force')

        then:
        !installed.contains('Error')
        ['RestController.groovy', 'index.gson', 'show.gson', '_domain.gson'].every { file("src/main/templates/scaffolding/${it}").isFile() }
        file('grails-app/views/bird/show.gson').text.endsWith('// of the project\n')
    }

    private File file(String path) {
        new File(dir, path)
    }

    private void write(String path, String text) {
        File file = file(path)
        file.parentFile.mkdirs()
        file.text = text
    }

    private void replace(String path, Map<String, String> replacements) {
        String text = file(path).text
        replacements.each { String from, String to ->
            assert text.count(from) == 1: "${path} has ${from} once"
            text = text.replace(from, to)
        }
        file(path).text = text
    }

    private Map<String, Integer> results(String task, String spec) {
        String suite = (file("build/test-results/${task}/TEST-example.grails.${spec}.xml").text =~ /<testsuite [^>]*>/)[0]
        ['tests', 'failures', 'errors', 'skipped'].collectEntries { String name ->
            [(name): ((suite =~ / ${name}="(\d+)"/)[0] as List)[1] as int]
        }
    }

    @Override
    String getTempDirectoryPrefix() {
        'testapp'
    }
}
