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
package org.grails.forge.cli.command

import java.nio.file.Files
import org.codehaus.groovy.control.CompilationUnit
import org.codehaus.groovy.control.Phases
import org.grails.forge.application.ApplicationType
import org.grails.forge.application.OperatingSystem
import org.grails.forge.application.generator.ProjectGenerator
import org.grails.forge.cli.CommandFixture
import org.grails.forge.cli.CommandSpec
import org.grails.forge.cli.ForgeCli
import org.grails.forge.io.ConsoleOutput
import org.grails.forge.io.FileSystemOutputHandler
import org.grails.forge.options.Options
import org.grails.forge.util.NameUtils
import spock.lang.Shared
import spock.lang.Unroll

/**
 * {@code create-controller} in a REST API application and a REST API plugin: the controller answers in
 * JSON only, as the one the command of the REST API profile wrote, and in every other project it is the
 * controller it was. Run in a directory that has the record of a generated project and nothing else of it.
 */
class RestApiCreateControllerSpec extends CommandSpec implements CommandFixture {

    static final String FORMATS = "    static responseFormats = ['json']\n"

    @Shared
    Map<ApplicationType, File> records = [:]

    PrintStream originalOut
    PrintStream originalErr
    ByteArrayOutputStream out = new ByteArrayOutputStream()

    void setup() {
        originalOut = System.out
        originalErr = System.err
        System.setOut(new PrintStream(out, true))
        System.setErr(new PrintStream(out, true))
    }

    void cleanup() {
        System.setOut(originalOut)
        System.setErr(originalErr)
    }

    void cleanupSpec() {
        records.values()*.deleteDir()
    }

    @Unroll
    void "create-controller in a #type project writes a controller that answers in JSON only, and its test"() {
        given:
        project(type)

        when:
        int first = ForgeCli.execute(beanContext, 'create-controller', 'fooBar')
        int second = ForgeCli.execute(beanContext, 'create-controller', 'other.pkg.Baz')

        then:
        first == 0
        second == 0
        files() == golden('rest-api-controller')
        out.toString().readLines() == [
                '| Rendered controller to grails-app/controllers/example/grails/FooBarController.groovy',
                '| Rendered controller to src/test/groovy/example/grails/FooBarControllerSpec.groovy',
                '| Rendered controller to grails-app/controllers/other/pkg/BazController.groovy',
                '| Rendered controller to src/test/groovy/other/pkg/BazControllerSpec.groovy'
        ]

        and: 'the formats are those create-restful-controller and create-domain-resource write'
        files()['grails-app/controllers/example/grails/FooBarController.groovy'].contains(FORMATS)

        where:
        type << [ApplicationType.REST_API, ApplicationType.REST_API_PLUGIN]
    }

    @Unroll
    void "create-controller in a #type project writes a controller that names no formats"() {
        given:
        project(type)

        expect:
        ForgeCli.execute(beanContext, 'create-controller', 'fooBar') == 0
        files()['grails-app/controllers/example/grails/FooBarController.groovy'] ==
                'package example.grails\n\n\nclass FooBarController {\n\n    def index() {\n\n    }\n}\n'

        where:
        type << [ApplicationType.WEB, ApplicationType.WEB_PLUGIN, ApplicationType.PLUGIN]
    }

    void "what is written for a REST API project is Groovy without an import"() {
        expect:
        golden('rest-api-controller').each { String path, String text ->
            CompilationUnit unit = new CompilationUnit()
            unit.addSource(path, text)
            unit.compile(Phases.CONVERSION)
            assert !path.startsWith('grails-app/') || !text.contains('import ')
        }
    }

    @Unroll
    void "install-templates in a #type project copies the controller template the project is written from"() {
        given:
        project(type)

        when: 'the controller of the command line'
        assert ForgeCli.execute(beanContext, 'create-controller', 'fooBar') == 0
        String before = files()['grails-app/controllers/example/grails/FooBarController.groovy']
        int installed = ForgeCli.execute(beanContext, 'install-templates')
        out.reset()
        int again = ForgeCli.execute(beanContext, 'create-controller', 'fooBar', '-f')

        then: 'the template is at the place the profile had it, and writes the same controller'
        installed == 0
        again == 0
        files()['src/main/templates/artifacts/Controller.groovy'].contains(FORMATS) == json
        files()['grails-app/controllers/example/grails/FooBarController.groovy'] == before
        before.contains(FORMATS) == json
        out.toString().contains('| Using the template src/main/templates/artifacts/Controller.groovy of the project')

        where:
        type                            | json
        ApplicationType.REST_API        | true
        ApplicationType.REST_API_PLUGIN | true
        ApplicationType.WEB             | false
    }

    void "a controller template of a REST API project is used in place of the one of the command line"() {
        given:
        project(ApplicationType.REST_API)
        File template = new File(dir, 'src/main/templates/artifacts/Controller.groovy')
        template.parentFile.mkdirs()
        template.setText('@artifact.package@\nclass @artifact.name@Controller { }\n', 'UTF-8')

        expect:
        ForgeCli.execute(beanContext, 'create-controller', 'fooBar') == 0
        files()['grails-app/controllers/example/grails/FooBarController.groovy'] == 'package example.grails\n\nclass FooBarController { }\n'
    }

    private void project(ApplicationType type) {
        if (!records.containsKey(type)) {
            File generated = Files.createTempDirectory('grailsforgerecord').toFile()
            beanContext.getBean(ProjectGenerator).generate(type, NameUtils.parse('example.grails.foo'), new Options(), OperatingSystem.MACOS_ARCH64,
                    [], new FileSystemOutputHandler(generated, ConsoleOutput.NOOP), ConsoleOutput.NOOP)
            records[type] = generated
        }
        // the record of the project, as it was written when the project was generated, and nothing else of it
        File record = records[type]
        new File(record, '.grails').eachFileRecurse { File each ->
            if (each.file) {
                File copy = new File(dir, record.toPath().relativize(each.toPath()).toString())
                copy.parentFile.mkdirs()
                copy.bytes = each.bytes
            }
        }
    }

    private Map<String, String> files() {
        Map<String, String> files = new TreeMap<>()
        dir.eachFileRecurse { File each ->
            String path = dir.toPath().relativize(each.toPath()).toString().replace(File.separator, '/')
            if (each.file && !path.startsWith('.grails/') && path != 'grails-forge-cli.yml') {
                files[path] = each.getText('UTF-8')
            }
        }
        return files
    }

    private Map<String, String> golden(String set) {
        File root = new File(getClass().getResource("/golden/artefacts/${set}").toURI())
        Map<String, String> files = new TreeMap<>()
        root.eachFileRecurse { File each ->
            if (each.file) {
                files[root.toPath().relativize(each.toPath()).toString().replace(File.separator, '/')] = each.getText('UTF-8')
            }
        }
        return files
    }
}
