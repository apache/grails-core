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

import org.codehaus.groovy.control.CompilationUnit
import org.codehaus.groovy.control.Phases
import org.grails.forge.application.ApplicationType
import org.grails.forge.cli.CommandFixture
import org.grails.forge.cli.CommandSpec
import org.grails.forge.cli.ForgeCli
import spock.lang.Unroll

/**
 * {@code create-functional-test} in the projects that have it: what it writes in a project with a Geb
 * feature, which the script of the Geb plugin wrote before, and in a REST API project. The projects are
 * generated and are not built.
 */
class CreateFunctionalTestCommandSpec extends CommandSpec implements CommandFixture {

    static final String CONTAINER = 'geb-with-testcontainers'
    static final String LOCAL = 'geb-with-local-browsers'
    static final String BOOK_SPEC = 'src/integration-test/groovy/example/grails/BookSpec.groovy'
    // the template the Geb plugin had for its script, as a project that took a copy of it has it
    static final String GEB_PLUGIN_TEMPLATE = 'package ${packageName}\n\nimport geb.spock.GebSpec\nimport grails.testing.mixin.integration.Integration\n\n' +
            '/**\n * See https://groovy.apache.org/geb/manual/current/ for more instructions\n */\n@Integration\nclass ${className}Spec extends GebSpec {\n\n' +
            '    void "home page loads"() {\n        when: \'The home page is visited\'\n            go(\'/\')\n\n' +
            '        then: \'The title is correct\'\n            waitFor { title == \'Welcome to Grails\' }\n    }\n}\n'

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

    @Unroll
    void "in a #type project with #feature the test extends #superClass"() {
        given:
        generateProject(type, features as String[])

        when:
        int exit = ForgeCli.execute(beanContext, 'create-functional-test', 'Book')
        File written = new File(dir, BOOK_SPEC)

        then:
        exit == 0
        out.toString().readLines() == ['| Rendered functional test to ' + BOOK_SPEC]
        written.getText('UTF-8') == """\
package example.grails

import ${imported}
import grails.testing.mixin.integration.Integration

/**
 * See https://grails.apache.org/docs/latest/guide/testing.html#functionalTesting and https://groovy.apache.org/geb/manual/current/
 * for more instructions on how to write functional tests with Grails and Geb.
 */
@Integration
class BookSpec extends ${superClass} {

    void 'should display the correct title on the home page'() {
        when: 'visiting the home page'
            go('/')

        then: 'the page title is correct'
            title == 'Welcome to Grails'
    }
}
"""

        and: 'it is Groovy'
        parses(written)

        where:
        type                       | features    | imported                              | superClass
        ApplicationType.WEB        | []          | 'grails.plugin.geb.ContainerGebSpec'  | 'ContainerGebSpec'
        ApplicationType.WEB        | [LOCAL]     | 'geb.spock.GebSpec'                   | 'GebSpec'
        ApplicationType.WEB_PLUGIN | [CONTAINER] | 'grails.plugin.geb.ContainerGebSpec'  | 'ContainerGebSpec'
        ApplicationType.WEB_PLUGIN | [LOCAL]     | 'geb.spock.GebSpec'                   | 'GebSpec'

        feature = features ? features[0] : CONTAINER + ', which it has by default'
    }

    @Unroll
    void "the test is the one the #feature feature writes for the application, under another name"() {
        given:
        generateProject(ApplicationType.WEB, features as String[])
        File generated = new File(dir, 'src/integration-test/groovy/example/grails/FooSpec.groovy')

        when:
        int exit = ForgeCli.execute(beanContext, 'create-functional-test', 'Book')

        then:
        exit == 0
        generated.isFile()

        and: 'line for line, but for the empty lines'
        new File(dir, BOOK_SPEC).getText('UTF-8').readLines().findAll { !it.isEmpty() } ==
                generated.getText('UTF-8').replace('class FooSpec ', 'class BookSpec ').readLines().findAll { !it.trim().isEmpty() }

        where:
        feature   | features
        CONTAINER | []
        LOCAL     | [LOCAL]
    }

    @Unroll
    void "#given is the class #file"() {
        given:
        generateProject(ApplicationType.WEB)

        when:
        int exit = ForgeCli.execute(beanContext, 'create-functional-test', given)
        File written = new File(dir, file)

        then:
        exit == 0
        out.toString().readLines() == ['| Rendered functional test to ' + file]
        written.getText('UTF-8').startsWith(starts)
        written.getText('UTF-8').contains("class ${name} extends ContainerGebSpec {")

        where:
        given                   | file                                                             | name           | starts
        'checkout'              | 'src/integration-test/groovy/example/grails/CheckoutSpec.groovy' | 'CheckoutSpec' | 'package example.grails\n\nimport '
        'CheckoutSpec'          | 'src/integration-test/groovy/example/grails/CheckoutSpec.groovy' | 'CheckoutSpec' | 'package example.grails\n\nimport '
        'org.shop.Checkout'     | 'src/integration-test/groovy/org/shop/CheckoutSpec.groovy'       | 'CheckoutSpec' | 'package org.shop\n\nimport '
        'org.shop.CheckoutSpec' | 'src/integration-test/groovy/org/shop/CheckoutSpec.groovy'       | 'CheckoutSpec' | 'package org.shop\n\nimport '
        'check-out'             | 'src/integration-test/groovy/example/grails/CheckOutSpec.groovy' | 'CheckOutSpec' | 'package example.grails\n\nimport '
    }

    void "a test that is there is kept, and replaced only when the command is forced"() {
        given:
        generateProject(ApplicationType.WEB)
        File written = new File(dir, BOOK_SPEC)
        written.parentFile.mkdirs()
        written.text = 'mine'

        when:
        int exit = ForgeCli.execute(beanContext, 'create-functional-test', 'Book')

        then:
        exit == 1
        out.toString().readLines() == ['| Error ' + BOOK_SPEC + ' already exists. Run again with -f to overwrite.']
        written.text == 'mine'

        when:
        out.reset()
        exit = ForgeCli.execute(beanContext, 'create-functional-test', 'Book', option)

        then:
        exit == 0
        out.toString().readLines() == ['| Rendered functional test to ' + BOOK_SPEC]
        written.text.contains('class BookSpec extends ContainerGebSpec {')

        where:
        option << ['-f', '--force']
    }

    @Unroll
    void "#shown is refused as a mistake in the command line, and nothing is written"() {
        given:
        generateProject(ApplicationType.WEB)
        Set<String> before = filesOfTests()

        when:
        int exit = ForgeCli.execute(beanContext, (['create-functional-test'] + arguments) as String[])

        then:
        exit == 2
        out.toString().contains(said)
        filesOfTests() == before

        where:
        shown                   | arguments           | said
        'no name'               | []                  | "Missing required parameter: 'TEST-NAME'"
        'a path'                | ['a/Book']          | 'is not a name for a class: it is a path'
        'two names'             | ['Book', 'Author']  | 'create-functional-test takes no argument Author'
        'an option it has not'  | ['Book', '--page']  | 'create-functional-test has no option --page'
    }

    @Unroll
    void "a project with #feature has the template of the Geb plugin at src/main/templates/FunctionalSpec.groovy, and its own is used"() {
        given:
        generateProject(ApplicationType.WEB, features as String[])

        when: 'the templates of the command line are copied into the project'
        int exit = ForgeCli.execute(beanContext, 'install-templates')
        File template = new File(dir, 'src/main/templates/FunctionalSpec.groovy')

        then: 'the one of the kind of browser the project has is among them, and no other functional test'
        exit == 0
        out.toString().readLines().contains('| Copied template to src/main/templates/FunctionalSpec.groovy')
        template.getText('UTF-8').contains('class ${className}Spec extends ' + superClass + ' {')
        !new File(dir, 'src/main/templates/testing/Functional.groovy').exists()
        !new File(dir, 'src/main/templates/geb').exists()

        when: 'the template the Geb plugin had for its script is put there'
        template.setText(GEB_PLUGIN_TEMPLATE, 'UTF-8')
        out.reset()
        exit = ForgeCli.execute(beanContext, 'create-functional-test', 'org.shop.Book')

        then: 'it is read as it is'
        exit == 0
        out.toString().readLines() == ['| Using the template src/main/templates/FunctionalSpec.groovy of the project',
                                       '| Rendered functional test to src/integration-test/groovy/org/shop/BookSpec.groovy']
        new File(dir, 'src/integration-test/groovy/org/shop/BookSpec.groovy').getText('UTF-8') ==
                GEB_PLUGIN_TEMPLATE.replace('${packageName}', 'org.shop').replace('${className}', 'Book')

        when: 'a template that would be run'
        template.setText('class ${className.toUpperCase()}Spec {}\n', 'UTF-8')
        out.reset()
        exit = ForgeCli.execute(beanContext, 'create-functional-test', 'Other')

        then: 'it is refused, and nothing is written'
        exit == 1
        out.toString().contains('The template src/main/templates/FunctionalSpec.groovy cannot be rendered: line 1: ')
        out.toString().contains('Templates are not run')
        !new File(dir, 'src/integration-test/groovy/example/grails/OtherSpec.groovy').exists()

        where:
        feature   | features | superClass
        CONTAINER | []       | 'ContainerGebSpec'
        LOCAL     | [LOCAL]  | 'GebSpec'
    }

    void "in a REST API application the test calls the application over HTTP, and the template of a Geb test is not read"() {
        given:
        generateProject(ApplicationType.REST_API)
        File template = new File(dir, 'src/main/templates/FunctionalSpec.groovy')
        template.parentFile.mkdirs()
        template.setText(GEB_PLUGIN_TEMPLATE, 'UTF-8')

        when:
        int exit = ForgeCli.execute(beanContext, 'create-functional-test', 'Book')
        String written = new File(dir, BOOK_SPEC).getText('UTF-8')

        then:
        exit == 0
        out.toString().readLines() == ['| Rendered functional test to ' + BOOK_SPEC]
        written.contains('class BookSpec extends Specification {')
        written.contains('import java.net.http.HttpClient')
        !written.contains('Geb')
    }

    @Unroll
    void "it is no command of a #type project without a Geb feature, and what is said names the features"() {
        given:
        generateProject(type)
        Set<String> before = filesOfTests()

        when:
        int exit = ForgeCli.execute(beanContext, 'create-functional-test', 'Book')
        List<String> lines = out.toString().readLines()

        then:
        exit == 2
        lines[0].startsWith('There is no command create-functional-test')
        lines.contains('create-functional-test is a command in the directory of a REST API application or REST API plugin, and of a web application or web plugin ' +
                'that has a Geb feature (geb-with-testcontainers or geb-with-local-browsers), which grails-forge-cli feature add geb-with-testcontainers adds, ' +
                'and the current directory is in none')
        filesOfTests() == before

        where:
        type << [ApplicationType.WEB_PLUGIN, ApplicationType.PLUGIN]
    }

    void "the feature that is added to a web plugin makes it a command there"() {
        given:
        generateProject(ApplicationType.WEB_PLUGIN)
        assert ForgeCli.execute(beanContext, 'create-functional-test', '--help') == 2

        when:
        int added = ForgeCli.execute(beanContext, 'feature', 'add', CONTAINER)
        out.reset()
        int exit = ForgeCli.execute(beanContext, 'create-functional-test', 'Book')

        then:
        added == 0
        exit == 0
        new File(dir, BOOK_SPEC).getText('UTF-8').contains('class BookSpec extends ContainerGebSpec {')
    }

    @Unroll
    void "an application without a record has the command once project migrate wrote one #how"() {
        given: 'an application as it is without the record of how it was generated'
        generateProject(ApplicationType.WEB, features as String[])
        new File(dir, '.grails').deleteDir()

        expect:
        ForgeCli.execute(beanContext, 'create-functional-test', 'Book') == 1
        out.toString().contains('create-functional-test is a command of a Grails project')

        when:
        out.reset()
        int migrated = ForgeCli.execute(beanContext, (['project', 'migrate'] + options) as String[])
        String said = out.toString()
        out.reset()
        int exit = ForgeCli.execute(beanContext, 'create-functional-test', 'Book')

        then:
        migrated == 0
        said.contains("    ${feature} (${found}")
        exit == 0
        new File(dir, BOOK_SPEC).getText('UTF-8').contains("class BookSpec extends ${superClass} {")

        where:
        feature   | features | options                   | found                      | superClass
        LOCAL     | [LOCAL]  | []                        | 'build.gradle declares '   | 'GebSpec'
        CONTAINER | []       | ['--features', CONTAINER] | 'given with --features'    | 'ContainerGebSpec'

        how = options ? "with ${options.join(' ')}" : "and found ${feature} in the build"
    }

    void "project migrate finds geb-with-testcontainers in a build that declares the Geb plugin and none of the drivers the other Geb feature adds"() {
        given:
        generateProject(ApplicationType.WEB)
        new File(dir, '.grails').deleteDir()

        when:
        int migrated = ForgeCli.execute(beanContext, 'project', 'migrate')
        String said = out.toString()
        out.reset()
        int exit = ForgeCli.execute(beanContext, 'create-functional-test', 'Book')

        then:
        migrated == 0
        said.contains("${CONTAINER} (build.gradle declares org.apache.grails:grails-geb, and nothing that the feature ${LOCAL} would add to it)")
        !said.contains("    ${LOCAL} (")
        exit == 0
        new File(dir, BOOK_SPEC).getText('UTF-8').contains('class BookSpec extends ContainerGebSpec {')
    }

    void "project migrate finds neither Geb feature in a build without the Geb plugin, and the one for local browsers where its drivers are"() {
        given:
        generateProject(ApplicationType.WEB, features as String[])
        new File(dir, '.grails').deleteDir()
        if (without) {
            File build = new File(dir, 'build.gradle')
            build.text = build.readLines().findAll { String line -> !line.contains('grails-geb') }.join('\n') + '\n'
        }

        when:
        int migrated = ForgeCli.execute(beanContext, 'project', 'migrate')
        String said = out.toString()

        then:
        migrated == 0
        said.contains("    ${CONTAINER} (") == container
        said.contains("    ${LOCAL} (") == local

        where:
        features | without || container | local
        []       | true    || false     | false
        [LOCAL]  | false   || false     | true
    }

    private static boolean parses(File source) {
        CompilationUnit unit = new CompilationUnit()
        unit.addSource(source.name, source.getText('UTF-8'))
        unit.compile(Phases.CONVERSION)
        return true
    }

    private Set<String> filesOfTests() {
        Set<String> files = new TreeSet<>()
        File tests = new File(dir, 'src/integration-test')
        if (tests.isDirectory()) {
            tests.eachFileRecurse { File each -> files << each.path }
        }
        return files
    }
}
