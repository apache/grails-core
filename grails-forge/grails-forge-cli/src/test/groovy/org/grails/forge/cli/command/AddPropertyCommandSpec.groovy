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
import java.nio.file.attribute.PosixFilePermissions
import org.grails.forge.application.ApplicationType
import org.grails.forge.application.OperatingSystem
import org.grails.forge.application.generator.ProjectGenerator
import org.grails.forge.cli.CommandSpec
import org.grails.forge.cli.ForgeCli
import org.grails.forge.io.ConsoleOutput
import org.grails.forge.io.FileSystemOutputHandler
import org.grails.forge.options.Options
import org.grails.forge.util.NameUtils
import spock.lang.IgnoreIf
import spock.lang.Shared
import spock.lang.Unroll

/**
 * {@code add-property}: the command changes a class the user wrote. What it writes into the class is
 * held to the whole text of the file, and a call that is refused leaves the file as it was, byte for
 * byte. The project is the record of a generated application and the domain classes of the spec.
 */
class AddPropertyCommandSpec extends CommandSpec {

    static final String PATH = 'grails-app/domain/example/grails/Book.groovy'

    static final String BOOK = '''\
package example.grails

class Book {
    String title

    static constraints = {
        title blank: false
    }
}
'''

    @Shared
    File record

    PrintStream originalOut
    PrintStream originalErr
    ByteArrayOutputStream out = new ByteArrayOutputStream()
    ByteArrayOutputStream err = new ByteArrayOutputStream()

    void setup() {
        originalOut = System.out
        originalErr = System.err
        System.setOut(new PrintStream(out, true))
        System.setErr(new PrintStream(err, true))
        if (record == null) {
            record = Files.createTempDirectory('grailsforgerecord').toFile()
            beanContext.getBean(ProjectGenerator).generate(ApplicationType.WEB, NameUtils.parse('example.grails.foo'), new Options(), OperatingSystem.MACOS_ARCH64,
                    [], new FileSystemOutputHandler(record, ConsoleOutput.NOOP), ConsoleOutput.NOOP)
        }
        // the record of the project, as it was written when the project was generated, and nothing else of it
        new File(record, '.grails').eachFileRecurse { File each ->
            if (each.file) {
                File copy = new File(dir, record.toPath().relativize(each.toPath()).toString())
                copy.parentFile.mkdirs()
                copy.bytes = each.bytes
            }
        }
    }

    void cleanup() {
        System.setOut(originalOut)
        System.setErr(originalErr)
    }

    void cleanupSpec() {
        record?.deleteDir()
    }

    void "a property is added to the class create-domain-class wrote, below the line the class begins with"() {
        given:
        assert ForgeCli.execute(beanContext, 'create-domain-class', 'Book') == 0
        File book = new File(dir, PATH)
        assert book.getText('UTF-8').startsWith('package example.grails\n\nclass Book {\n\n    static constraints = {\n    }\n}')
        out.reset()

        when:
        int exit = ForgeCli.execute(beanContext, 'add-property', 'Book', 'title:String')

        then:
        exit == 0
        err.toString().isEmpty()
        out.toString().readLines() == ["| Added property 'title' to ${PATH}".toString()]
        book.getText('UTF-8').startsWith('package example.grails\n\nclass Book {\n    String title\n\n    static constraints = {\n    }\n}')

        when: 'a second one, with a constraint, in the class as it is now'
        exit = ForgeCli.execute(beanContext, 'add-property', 'Book', 'isbn', '--nullable')

        then: 'it follows the first, and its constraint is in the block the class has'
        exit == 0
        book.getText('UTF-8').startsWith('package example.grails\n\nclass Book {\n    String title\n    String isbn\n\n    static constraints = {\n' +
                '        isbn nullable: true\n    }\n}')
    }

    @Unroll
    void "add-property Book #arguments adds #declaration, and to the constraints the class has #constraint"() {
        given:
        File book = domain(BOOK)

        when:
        int exit = ForgeCli.execute(beanContext, (['add-property', 'Book'] + arguments) as String[])

        then: 'the property follows the last one, the constraint is the last of the block, and nothing else is another'
        exit == 0
        err.toString().isEmpty()
        book.getText('UTF-8') == BOOK.replace('    String title\n', "    String title\n    ${declaration}\n")
                .replace('        title blank: false\n', '        title blank: false\n' + (constraint == null ? '' : "        ${constraint}\n"))

        where:
        arguments                                                                 || declaration                    | constraint
        ['isbn']                                                                  || 'String isbn'                  | null
        ['isbn:String']                                                           || 'String isbn'                  | null
        ['pages:Integer']                                                         || 'Integer pages'                | null
        ['author:Author']                                                         || 'Author author'                | null
        ['tags:List<String>']                                                     || 'List<String> tags'            | null
        ['published:java.time.LocalDate']                                         || 'java.time.LocalDate published' | null
        ['isbn', '--nullable']                                                    || 'String isbn'                  | 'isbn nullable: true'
        ['isbn', '--not-nullable']                                                || 'String isbn'                  | 'isbn nullable: false'
        ['author:Author', '--nullable']                                           || 'Author author'                | 'author nullable: true'
        ['isbn', '--blank']                                                       || 'String isbn'                  | 'isbn blank: true'
        ['isbn', '--not-blank']                                                   || 'String isbn'                  | 'isbn blank: false'
        ['isbn', '--max-size', '20']                                              || 'String isbn'                  | 'isbn maxSize: 20'
        ['isbn', '--min-size=2']                                                  || 'String isbn'                  | 'isbn minSize: 2'
        ['isbn', '--min-size', '2', '--max-size', '20']                           || 'String isbn'                  | 'isbn maxSize: 20, minSize: 2'
        ['isbn', '--not-nullable', '--not-blank', '--max-size', '20', '--min-size', '2'] || 'String isbn'           | 'isbn nullable: false, blank: false, maxSize: 20, minSize: 2'
        ['isbn', '--constraint-style', 'grails', '--nullable']                    || 'String isbn'                  | 'isbn nullable: true'
        ['isbn', '--constraint-style', 'GRAILS', '--nullable']                    || 'String isbn'                  | 'isbn nullable: true'
    }

    @Unroll
    void "with --constraint-style #style #options the property has #annotations, their imports, and in the constraints #constraint"() {
        given:
        File book = domain(BOOK)

        when:
        int exit = ForgeCli.execute(beanContext, (['add-property', 'Book', 'isbn', '--constraint-style', style] + options) as String[])

        then:
        exit == 0
        err.toString().isEmpty()
        book.getText('UTF-8') == BOOK
                .replace('package example.grails\n', 'package example.grails\n' + (imports ? '\n' + imports.collect { "import jakarta.validation.constraints.${it}\n" }.join('') : ''))
                .replace('    String title\n', '    String title\n' + annotations.collect { "    ${it}\n" }.join('') + '    String isbn\n')
                .replace('        title blank: false\n', '        title blank: false\n' + (constraint == null ? '' : "        ${constraint}\n"))

        where:
        style     | options                                           || imports                       | annotations                                      | constraint
        'jakarta' | ['--not-nullable']                                || ['NotNull']                   | ['@NotNull']                                     | null
        'jakarta' | ['--not-blank']                                   || ['NotBlank']                  | ['@NotBlank']                                    | null
        'jakarta' | ['--max-size', '20']                              || ['Size']                      | ['@Size(max = 20)']                              | null
        'jakarta' | ['--min-size', '2']                               || ['Size']                      | ['@Size(min = 2)']                               | null
        'jakarta' | ['--max-size', '20', '--min-size', '2']           || ['Size']                      | ['@Size(min = 2, max = 20)']                     | null
        'jakarta' | ['--not-nullable', '--not-blank', '--max-size', '20'] || ['NotNull', 'NotBlank', 'Size'] | ['@NotNull', '@NotBlank', '@Size(max = 20)'] | null
        'Jakarta' | ['--not-nullable']                                || ['NotNull']                   | ['@NotNull']                                     | null
        'both'    | ['--not-nullable', '--max-size', '20']            || ['NotNull', 'Size']           | ['@NotNull', '@Size(max = 20)']                  | 'isbn nullable: false, maxSize: 20'
        'both'    | ['--nullable']                                    || []                            | []                                               | 'isbn nullable: true'
        'both'    | []                                                || []                            | []                                               | null
        'jakarta' | []                                                || []                            | []                                               | null
    }

    void "an import the class has is not written a second time, and a new one follows the imports that are there"() {
        given:
        File book = domain(BOOK.replace('package example.grails\n', 'package example.grails\n\nimport java.time.LocalDate\nimport jakarta.validation.constraints.NotNull\n'))

        when:
        int exit = ForgeCli.execute(beanContext, 'add-property', 'Book', 'isbn', '--constraint-style', 'jakarta', '--not-nullable', '--min-size', '2')

        then:
        exit == 0
        book.getText('UTF-8') == BOOK.replace('package example.grails\n', 'package example.grails\n\nimport java.time.LocalDate\nimport jakarta.validation.constraints.NotNull\n' +
                'import jakarta.validation.constraints.Size\n')
                .replace('    String title\n', '    String title\n    @NotNull\n    @Size(min = 2)\n    String isbn\n')
    }

    void "a class without a constraints block gets one for the first constraint, after the property, and none without a constraint"() {
        given:
        String source = 'package example.grails\n\nclass Book {\n    String title\n}\n'
        File book = domain(source)

        when:
        int exit = ForgeCli.execute(beanContext, 'add-property', 'Book', 'pages:Integer')

        then:
        exit == 0
        book.getText('UTF-8') == 'package example.grails\n\nclass Book {\n    String title\n    Integer pages\n}\n'

        when:
        exit = ForgeCli.execute(beanContext, 'add-property', 'Book', 'isbn', '--nullable')

        then:
        exit == 0
        book.getText('UTF-8') == 'package example.grails\n\nclass Book {\n    String title\n    Integer pages\n    String isbn\n\n' +
                '    static constraints = {\n        isbn nullable: true\n    }\n}\n'

        when: 'with annotations there is no block'
        book.setText(source, 'UTF-8')
        exit = ForgeCli.execute(beanContext, 'add-property', 'Book', 'isbn', '--not-nullable', '--constraint-style', 'jakarta')

        then:
        exit == 0
        book.getText('UTF-8') == 'package example.grails\n\nimport jakarta.validation.constraints.NotNull\n\nclass Book {\n    String title\n    @NotNull\n    String isbn\n}\n'
    }

    @Unroll
    void "add-property #arguments is refused, and the class is what it was: #message"() {
        given:
        File book = domain(BOOK.replace('    String title\n', '    String title\n    private String secret\n'))
        byte[] before = book.bytes

        when:
        int exit = ForgeCli.execute(beanContext, (['add-property'] + arguments) as String[])

        then:
        exit == 1
        err.toString().contains(message)
        !err.toString().contains('Exception')
        !err.toString().contains('\tat ')
        out.toString().isEmpty()
        book.bytes == before
        new File(dir, 'grails-app/domain/example/grails').list() as List == ['Book.groovy']

        where:
        arguments                                         || message
        ['Book', 'Isbn:String']                           || "Invalid property definition: Property name 'Isbn' must start with a lowercase letter and contain only alphanumeric characters"
        ['Book', 'my-isbn']                               || "Invalid property definition: Property name 'my-isbn' must start with a lowercase letter"
        ['Book', 'my_isbn:String']                        || "Invalid property definition: Property name 'my_isbn' must start with a lowercase letter"
        ['Book', ':String']                               || 'Invalid property definition: Property name is required'
        ['Book', '']                                      || 'Invalid property specification: Property specification cannot be null or empty'
        ['Book', 'isbn:string']                           || "Invalid property definition: Invalid property type 'string'. Type must start with an uppercase letter"
        ['Book', 'isbn:Str ing']                          || "Invalid property definition: Invalid property type 'Str ing'"
        ['Book', 'isbn:String = "x"\n    def evil']       || 'Invalid property definition: Invalid property type'
        ['Book', 'pages:Integer', '--blank']              || "Invalid property definition: The 'blank' constraint is only applicable to String properties"
        ['Book', 'pages:Integer', '--not-blank']          || "Invalid property definition: The 'blank' constraint is only applicable to String properties"
        ['Book', 'pages:Integer', '--max-size', '3']      || "Invalid property definition: The 'maxSize' constraint is only applicable to String properties"
        ['Book', 'pages:Integer', '--min-size', '3']      || "Invalid property definition: The 'minSize' constraint is only applicable to String properties"
        ['Book', 'isbn', '--constraint-style', 'xml']     || 'Invalid constraint style: xml. Use: grails, jakarta, or both'
        ['Book', 'title:String']                          || "Property 'title' already exists in Book"
        ['Book', 'title:Integer', '--nullable']           || "Property 'title' already exists in Book"
        ['Book', 'secret']                                || "Property 'secret' already exists in Book"
        ['example.grails.Book', 'title']                  || "Property 'title' already exists in example.grails.Book"
        ['Nope', 'isbn']                                  || 'Domain class not found: Nope'
        ['example.Book.groovy', 'isbn']                   || 'Domain class not found: example.Book.groovy'
        ['book', 'isbn']                                  || 'Domain class not found: book'
    }

    @Unroll
    void "add-property #arguments is a mistake in the command line, and the class is what it was"() {
        given:
        File book = domain(BOOK)

        when:
        int exit = ForgeCli.execute(beanContext, (['add-property'] + arguments) as String[])

        then:
        exit == 2
        err.toString().readLines()[0].contains(message)
        book.getText('UTF-8') == BOOK

        where:
        arguments                               || message
        []                                      || 'Missing required parameters'
        ['Book']                                || "Missing required parameter: 'PROPERTY:TYPE'"
        ['Book', 'isbn', '--max-size', 'many']  || "Invalid value for option '--max-size'"
        ['Book', 'isbn', '--min-size']          || "Missing required parameter for option '--min-size'"
        ['Book', 'isbn', '--unique']            || 'add-property has no option --unique'
        ['Book', 'isbn', 'pages:Integer']       || 'add-property takes no argument pages:Integer'
    }

    void "a class that is not there is said to be, with the command that creates it, and nothing is created"() {
        when: 'in a project that has no domain class at all'
        int exit = ForgeCli.execute(beanContext, 'add-property', 'Book', 'title')

        then:
        exit == 1
        err.toString().readLines() == ['| Error Domain class not found: Book', "| Error Run 'grails-forge-cli create-domain-class Book' first."]
        !new File(dir, 'grails-app').exists()
    }

    void "a class that cannot be parsed is not changed, and the answer is one line without a stack trace"() {
        given:
        String broken = 'package example.grails\n\nclass Book {\n    String title\n\n    static constraints = {\n'
        File book = domain(broken)

        when:
        int exit = ForgeCli.execute(beanContext, 'add-property', 'Book', 'isbn')

        then:
        exit == 1
        err.toString().readLines() == ["| Error Failed to parse domain class 'Book.groovy'. Please fix any syntax errors and try again."]
        out.toString().isEmpty()
        book.getText('UTF-8') == broken
    }

    void "of two classes with one name neither is changed until the package is given, and a package that is given is the one that counts"() {
        given:
        File book = domain(BOOK)
        File other = domain('grails-app/domain/example/library/Book.groovy', BOOK.replace('example.grails', 'example.library'))

        when:
        int exit = ForgeCli.execute(beanContext, 'add-property', 'Book', 'isbn')

        then: 'both are named, by their paths in the project'
        exit == 1
        err.toString().readLines() == ["| Error Multiple domain classes found with name 'Book': grails-app/domain/example/grails/Book.groovy, " +
                                               'grails-app/domain/example/library/Book.groovy. Please specify the fully qualified class name.']
        book.getText('UTF-8') == BOOK
        other.getText('UTF-8') == BOOK.replace('example.grails', 'example.library')

        when:
        err.reset()
        exit = ForgeCli.execute(beanContext, 'add-property', 'example.library.Book', 'isbn')

        then:
        exit == 0
        out.toString().readLines() == ["| Added property 'isbn' to grails-app/domain/example/library/Book.groovy"]
        book.getText('UTF-8') == BOOK
        other.getText('UTF-8') == BOOK.replace('example.grails', 'example.library').replace('    String title\n', '    String title\n    String isbn\n')
    }

    void "the class is found from any directory of the project, and the path that is said is the one in the project"() {
        given:
        File book = domain(BOOK)
        new File(dir, 'grails-app/views/book').mkdirs()
        System.setProperty('user.dir', new File(dir, 'grails-app/views/book').absolutePath)

        when:
        int exit = ForgeCli.execute(beanContext, 'add-property', 'Book', 'isbn')

        then:
        exit == 0
        out.toString().readLines() == ["| Added property 'isbn' to ${PATH}".toString()]
        book.getText('UTF-8') == BOOK.replace('    String title\n', '    String title\n    String isbn\n')
        !new File(dir, 'grails-app/views/book/grails-app').exists()
    }

    /**
     * What the command does today where another answer could be argued for. Each case is pinned so
     * that a change of it is a decision: two options that say opposite things, sizes that no text
     * has, and a constraint the chosen style has no way to write.
     */
    @Unroll
    void "what is not refused today: #arguments writes #constraint and #annotations"() {
        given:
        File book = domain(BOOK)

        when:
        int exit = ForgeCli.execute(beanContext, (['add-property', 'Book', 'isbn'] + arguments) as String[])

        then:
        exit == 0
        book.getText('UTF-8') == BOOK
                .replace('package example.grails\n', 'package example.grails\n' + (annotations ? '\nimport jakarta.validation.constraints.Size\n' : ''))
                .replace('    String title\n', '    String title\n' + annotations.collect { "    ${it}\n" }.join('') + '    String isbn\n')
                .replace('        title blank: false\n', '        title blank: false\n' + (constraint == null ? '' : "        ${constraint}\n"))

        where:
        arguments                                              || constraint                        | annotations
        ['--nullable', '--not-nullable']                       || 'isbn nullable: true'             | []
        ['--blank', '--not-blank']                             || 'isbn blank: true'                | []
        ['--min-size', '20', '--max-size', '2']                || 'isbn maxSize: 2, minSize: 20'    | []
        ['--max-size', '-1']                                   || 'isbn maxSize: -1'                | []
        ['--constraint-style', 'jakarta', '--nullable']        || null                              | []
        ['--constraint-style', 'jakarta', '--blank']           || null                              | []
        ['--constraint-style', 'jakarta', '--max-size', '-1']  || null                              | ['@Size(max = -1)']
        ['-f']                                                 || null                              | []
    }

    void "a name with a package that is not the package of the class still finds the only class of that name"() {
        given:
        File book = domain(BOOK)

        when:
        int exit = ForgeCli.execute(beanContext, 'add-property', 'some.other.Book', 'isbn')

        then:
        exit == 0
        book.getText('UTF-8') == BOOK.replace('    String title\n', '    String title\n    String isbn\n')
    }

    @Unroll
    void "a class that #how gets #arguments where it belongs, and every other byte of the file is what it was"() {
        given:
        File book = domain(source)

        when:
        int exit = ForgeCli.execute(beanContext, (['add-property', 'Book'] + arguments) as String[])

        then:
        exit == 0
        err.toString().isEmpty()
        book.getText('UTF-8') == expected

        where:
        how << ['has no line ending at its end, as create-domain-class wrote it once',
                'has the line endings of Windows',
                'is indented with tabs',
                'is indented with two spaces and has an empty block',
                'has a method that returns a String and declares one',
                'has an empty constraints block on one line',
                'has a constraints block with an entry on one line',
                'ends its constraints block on the line of the last entry',
                'has its constraints above its properties',
                'is abstract',
                'begins its body on a line of its own',
                'has nothing and begins its body on a line of its own',
                'is written on one line with nothing in it',
                'has a property with a value of several lines, and one of a type with parameters',
                'has braces in a comment and in strings',
                'shares its file with an enum before it and a class after it',
                'follows its package without a line between, for an annotation',
                'has no package, for an annotation',
                'imports every constraint annotation with a star',
                'has a service and a property after an empty line']
        arguments << [['isbn', '--nullable'], ['isbn', '--nullable'], ['isbn', '--nullable'], ['isbn', '--nullable'], ['isbn', '--nullable'],
                      ['isbn', '--nullable'], ['isbn', '--nullable'], ['isbn', '--nullable'], ['isbn', '--nullable'], ['isbn', '--nullable'],
                      ['isbn'], ['isbn'], ['isbn', '--nullable'], ['isbn', '--nullable'], ['isbn', '--nullable'], ['isbn', '--nullable'],
                      ['isbn', '--not-nullable', '--constraint-style', 'jakarta'], ['isbn', '--not-nullable', '--constraint-style', 'jakarta'],
                      ['isbn', '--not-nullable', '--constraint-style', 'jakarta'], ['isbn']]
        source << ['package example.grails\n\nclass Book {\n\n    static constraints = {\n    }\n}',
                   BOOK.replace('\n', '\r\n'),
                   'package example.grails\n\nclass Book {\n\tString title\n\n\tstatic constraints = {\n\t\ttitle blank: false\n\t}\n}\n',
                   'class Book {\n  String title\n\n  static constraints = {\n  }\n}\n',
                   BOOK.replace('    }\n}\n', '    }\n\n    String toString() {\n        String label = title\n        return label\n    }\n}\n'),
                   'class Book {\n    String title\n\n    static constraints = {}\n}\n',
                   'class Book {\n    String title\n\n    static constraints = { title blank: false }\n}\n',
                   'class Book {\n    String title\n\n    static constraints = {\n        title blank: false }\n}\n',
                   'class Book {\n\n    static constraints = {\n        title blank: false\n    }\n\n    String title\n}\n',
                   BOOK.replace('class Book', 'abstract class Book'),
                   "@Resource(uri = '/books')\nclass Book implements Serializable\n{\n    String title\n}\n",
                   'class Book implements Serializable\n{\n}\n',
                   'package example.grails\n\nclass Book {}\n',
                   "class Book {\n    String title\n    List<String> tags = [\n            'new',\n            'unread'\n    ]\n    Map<String, Object> extra = [:]\n\n" +
                           '    static hasMany = [chapters: Chapter]\n}\n',
                   "class Book {\n    String title // the } of a title\n\n    static constraints = {\n        title matches: '[a-z]{3}', validator: { val -> val != '}' } // }\n" +
                           "    }\n\n    static mapping = {\n        table 'book{s'\n    }\n}\n",
                   'package example.grails\n\nenum Kind { PAPER, CLOTH }\n\nclass Book {\n    String title\n    Kind kind\n}\n\nclass Shelf {\n    String room\n}\n',
                   'package example.grails\nclass Book {\n    String title\n}\n',
                   'class Book {\n    String title\n}\n',
                   'package example.grails\n\nimport jakarta.validation.constraints.*\n\nclass Book {\n    String title\n}\n',
                   'class Book {\n    String title\n    def bookService\n\n    Date dateCreated\n\n    static hasMany = [chapters: Chapter]\n}\n']
        expected << ['package example.grails\n\nclass Book {\n    String isbn\n\n    static constraints = {\n        isbn nullable: true\n    }\n}',
                     BOOK.replace('    String title\n', '    String title\n    String isbn\n')
                             .replace('        title blank: false\n', '        title blank: false\n        isbn nullable: true\n').replace('\n', '\r\n'),
                     'package example.grails\n\nclass Book {\n\tString title\n\tString isbn\n\n\tstatic constraints = {\n\t\ttitle blank: false\n\t\tisbn nullable: true\n\t}\n}\n',
                     'class Book {\n  String title\n  String isbn\n\n  static constraints = {\n    isbn nullable: true\n  }\n}\n',
                     BOOK.replace('    String title\n', '    String title\n    String isbn\n')
                             .replace('        title blank: false\n', '        title blank: false\n        isbn nullable: true\n')
                             .replace('    }\n}\n', '    }\n\n    String toString() {\n        String label = title\n        return label\n    }\n}\n'),
                     'class Book {\n    String title\n    String isbn\n\n    static constraints = {\n        isbn nullable: true\n    }\n}\n',
                     'class Book {\n    String title\n    String isbn\n\n    static constraints = {\n        title blank: false\n        isbn nullable: true\n    }\n}\n',
                     'class Book {\n    String title\n    String isbn\n\n    static constraints = {\n        title blank: false\n        isbn nullable: true\n    }\n}\n',
                     'class Book {\n\n    static constraints = {\n        title blank: false\n        isbn nullable: true\n    }\n\n    String title\n    String isbn\n}\n',
                     BOOK.replace('class Book', 'abstract class Book').replace('    String title\n', '    String title\n    String isbn\n')
                             .replace('        title blank: false\n', '        title blank: false\n        isbn nullable: true\n'),
                     "@Resource(uri = '/books')\nclass Book implements Serializable\n{\n    String title\n    String isbn\n}\n",
                     'class Book implements Serializable\n{\n    String isbn\n}\n',
                     'package example.grails\n\nclass Book {\n    String isbn\n\n    static constraints = {\n        isbn nullable: true\n    }\n}\n',
                     "class Book {\n    String title\n    List<String> tags = [\n            'new',\n            'unread'\n    ]\n    Map<String, Object> extra = [:]\n" +
                             '    String isbn\n\n    static constraints = {\n        isbn nullable: true\n    }\n\n    static hasMany = [chapters: Chapter]\n}\n',
                     "class Book {\n    String title // the } of a title\n    String isbn\n\n    static constraints = {\n" +
                             "        title matches: '[a-z]{3}', validator: { val -> val != '}' } // }\n        isbn nullable: true\n" +
                             "    }\n\n    static mapping = {\n        table 'book{s'\n    }\n}\n",
                     'package example.grails\n\nenum Kind { PAPER, CLOTH }\n\nclass Book {\n    String title\n    Kind kind\n    String isbn\n\n' +
                             '    static constraints = {\n        isbn nullable: true\n    }\n}\n\nclass Shelf {\n    String room\n}\n',
                     'package example.grails\n\nimport jakarta.validation.constraints.NotNull\n\nclass Book {\n    String title\n    @NotNull\n    String isbn\n}\n',
                     'import jakarta.validation.constraints.NotNull\n\nclass Book {\n    String title\n    @NotNull\n    String isbn\n}\n',
                     'package example.grails\n\nimport jakarta.validation.constraints.*\n\nclass Book {\n    String title\n    @NotNull\n    String isbn\n}\n',
                     'class Book {\n    String title\n    def bookService\n\n    Date dateCreated\n    String isbn\n\n    static hasMany = [chapters: Chapter]\n}\n']
    }

    @Unroll
    void "a class that #how is not changed, and why is said in one line"() {
        given:
        File book = new File(dir, PATH)
        book.parentFile.mkdirs()
        book.bytes = source
        byte[] before = book.bytes

        when:
        int exit = ForgeCli.execute(beanContext, 'add-property', 'Book', 'isbn', '--nullable')

        then:
        exit == 1
        err.toString().readLines() == ['| Error ' + message]
        out.toString().isEmpty()
        book.bytes == before

        where:
        how                                        | source                                                                                || message
        'has constraints that are not a block'     | 'class Book {\n    String title\n\n    static constraints = Shared.rules\n}\n'.bytes || "Cannot add 'isbn' to 'Book.groovy': the constraints of the class are not a block that is written in it, as static constraints = { }, so the constraint has no place. The file was not changed."
        'is written on one line'                   | 'class Book { String title }\n'.bytes                                                 || "Cannot add 'isbn' to 'Book.groovy': the class is written on one line; write its closing brace on a line of its own. The file was not changed."
        'ends on the line of its last property'    | 'class Book {\n    String title }\n'.bytes                                            || "Cannot add 'isbn' to 'Book.groovy': the property title is on the line the class begins or ends with; write it on a line of its own. The file was not changed."
        'has something on the line it begins with' | 'class Book { static hasMany = [chapters: Chapter]\n}\n'.bytes                       || "Cannot add 'isbn' to 'Book.groovy': there is something after the brace the class begins with, on its line; write it on a line of its own. The file was not changed."
        'is a script and declares no class'        | 'println "no class"\n'.bytes                                                           || "Cannot add 'isbn' to 'Book.groovy': the file declares no class. The file was not changed."
        'is not text in UTF-8'                     | 'class Book {\n    String café\n}\n'.getBytes('ISO-8859-1')                     || "'Book.groovy' is not text in UTF-8, and was not changed."
    }

    void "the property is added where the constraints are no block, when it has no constraint"() {
        given:
        File book = domain('class Book {\n    String title\n\n    static constraints = Shared.rules\n}\n')

        expect:
        ForgeCli.execute(beanContext, 'add-property', 'Book', 'isbn') == 0
        book.getText('UTF-8') == 'class Book {\n    String title\n    String isbn\n\n    static constraints = Shared.rules\n}\n'
    }

    @IgnoreIf({ os.windows || System.getProperty('user.name') == 'root' })
    void "a class that cannot be written to is said to be by its path in the project, and is what it was"() {
        given:
        File book = domain(BOOK)
        Files.setPosixFilePermissions(book.toPath(), PosixFilePermissions.fromString('r--r--r--'))

        when:
        int exit = ForgeCli.execute(beanContext, 'add-property', 'Book', 'isbn')

        then:
        exit == 1
        err.toString().readLines() == ["| Error Cannot change ${PATH}: it, or the directory it is in, cannot be written to".toString()]
        !err.toString().contains(dir.name)
        book.getText('UTF-8') == BOOK
    }

    void "--constraint-style is read the same in a language whose upper case of i is not I"() {
        given:
        File book = domain(BOOK)
        Locale locale = Locale.getDefault()
        Locale.setDefault(Locale.forLanguageTag('tr-TR'))

        when:
        int exit = ForgeCli.execute(beanContext, 'add-property', 'Book', 'isbn', '--constraint-style', 'grails', '--nullable')

        then:
        exit == 0
        err.toString().isEmpty()
        book.getText('UTF-8').contains('        isbn nullable: true\n')

        cleanup:
        Locale.setDefault(locale)
    }

    private File domain(String text) {
        return domain(PATH, text)
    }

    private File domain(String path, String text) {
        File file = new File(dir, path)
        file.parentFile.mkdirs()
        file.setText(text, 'UTF-8')
        return file
    }
}
