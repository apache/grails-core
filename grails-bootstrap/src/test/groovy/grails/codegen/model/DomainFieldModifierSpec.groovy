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
package grails.codegen.model

import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

class DomainFieldModifierSpec extends Specification {

    @TempDir
    Path tempDir

    DomainFieldModifier modifier = new DomainFieldModifier()

    def "should find domain class file by simple name"() {
        given:
        createDomainFile('example', 'Book', '''
            package example
            class Book {
            }
        ''')

        when:
        def found = modifier.findDomainFile(tempDir.toFile(), 'Book')

        then:
        found != null
        found.name == 'Book.groovy'
    }

    def "should find domain class file by fully qualified name"() {
        given:
        createDomainFile('com/example', 'Author', '''
            package com.example
            class Author {
            }
        ''')

        when:
        def found = modifier.findDomainFile(tempDir.toFile(), 'com.example.Author')

        then:
        found != null
        found.name == 'Author.groovy'
    }

    def "should return null when domain class not found"() {
        given:
        createDomainDir()

        expect:
        modifier.findDomainFile(tempDir.toFile(), 'NonExistent') == null
    }

    def "should detect existing property"() {
        given:
        def domainFile = createDomainFile('example', 'Book', '''
            package example
            class Book {
                String title
            }
        ''')

        expect:
        modifier.memberExists(domainFile, 'title') == true
        modifier.memberExists(domainFile, 'author') == false
    }

    def "should add property to empty domain class"() {
        given:
        def domainFile = createDomainFile('example', 'Book', '''
package example

class Book {

    static constraints = {
    }
}
''')
        def property = PropertyDefinition.builder()
            .name('title')
            .type('String')
            .blank(false)
            .maxSize(255)
            .build()

        when:
        modifier.addProperty(domainFile, property)
        def content = domainFile.text

        then:
        content.contains('String title')
        content.contains('title blank: false, maxSize: 255')
    }

    def "should add property to domain class with existing properties"() {
        given:
        def domainFile = createDomainFile('example', 'Book', '''
package example

class Book {
    String title

    static constraints = {
        title blank: false
    }
}
''')
        def property = PropertyDefinition.builder()
            .name('author')
            .type('String')
            .nullable(true)
            .build()

        when:
        modifier.addProperty(domainFile, property)
        def content = domainFile.text

        then:
        content.contains('String author')
        content.contains('author nullable: true')
    }

    def "should create constraints block if not present"() {
        given:
        def domainFile = createDomainFile('example', 'Book', '''
package example

class Book {
}
''')
        def property = PropertyDefinition.builder()
            .name('title')
            .type('String')
            .nullable(false)
            .build()

        when:
        modifier.addProperty(domainFile, property)
        def content = domainFile.text

        then:
        content.contains('String title')
        content.contains('static constraints = {')
        content.contains('title nullable: false')
    }

    def "should not add constraints block when no constraints specified"() {
        given:
        def domainFile = createDomainFile('example', 'Book', '''
package example

class Book {
}
''')
        def property = new PropertyDefinition(name: 'title', type: 'String')

        when:
        modifier.addProperty(domainFile, property)
        def content = domainFile.text

        then:
        content.contains('String title')
        !content.contains('static constraints = {')
    }

    def "should handle domain class with multiple properties"() {
        given:
        def domainFile = createDomainFile('example', 'Book', '''
package example

class Book {
    String title
    Integer pages
    BigDecimal price

    static constraints = {
        title blank: false
        pages nullable: true
    }
}
''')
        def property = PropertyDefinition.builder()
            .name('author')
            .type('String')
            .maxSize(100)
            .build()

        when:
        modifier.addProperty(domainFile, property)
        def content = domainFile.text

        then:
        content.contains('String author')
        content.contains('author maxSize: 100')
        // Original constraints should still be there
        content.contains('title blank: false')
        content.contains('pages nullable: true')
    }

    def "should throw exception when domain file does not exist"() {
        given:
        def nonExistentFile = new File(tempDir.toFile(), 'NonExistent.groovy')
        def property = new PropertyDefinition(name: 'title', type: 'String')

        when:
        modifier.addProperty(nonExistentFile, property)

        then:
        thrown(IllegalStateException)
    }

    def "should add multiple properties sequentially"() {
        given:
        def domainFile = createDomainFile('example', 'Book', '''
package example

class Book {

    static constraints = {
    }
}
''')

        when:
        modifier.addProperty(domainFile, PropertyDefinition.builder()
            .name('title')
            .type('String')
            .blank(false)
            .build())
        modifier.addProperty(domainFile, PropertyDefinition.builder()
            .name('author')
            .type('String')
            .nullable(true)
            .build())
        modifier.addProperty(domainFile, PropertyDefinition.builder()
            .name('pages')
            .type('Integer')
            .nullable(true)
            .build())
        def content = domainFile.text

        then:
        content.contains('String title')
        content.contains('String author')
        content.contains('Integer pages')
        content.contains('title blank: false')
        content.contains('author nullable: true')
        content.contains('pages nullable: true')
    }

    // Jakarta Validation annotation tests

    def "should add property with Jakarta annotations only"() {
        given:
        def domainFile = createDomainFile('example', 'Book', '''
package example

class Book {
}
''')
        def property = PropertyDefinition.builder()
            .name('title')
            .type('String')
            .nullable(false)
            .blank(false)
            .maxSize(255)
            .constraintStyle(AbstractMemberDefinition.ConstraintStyle.JAKARTA)
            .build()

        when:
        modifier.addProperty(domainFile, property)
        def content = domainFile.text

        then:
        content.contains('import jakarta.validation.constraints.NotNull')
        content.contains('import jakarta.validation.constraints.NotBlank')
        content.contains('import jakarta.validation.constraints.Size')
        content.contains('@NotNull')
        content.contains('@NotBlank')
        content.contains('@Size(max = 255)')
        content.contains('String title')
        !content.contains('static constraints = {')
    }

    def "should add property with both Grails constraints and Jakarta annotations"() {
        given:
        def domainFile = createDomainFile('example', 'Book', '''
package example

class Book {

    static constraints = {
    }
}
''')
        def property = PropertyDefinition.builder()
            .name('title')
            .type('String')
            .nullable(false)
            .maxSize(255)
            .constraintStyle(AbstractMemberDefinition.ConstraintStyle.BOTH)
            .build()

        when:
        modifier.addProperty(domainFile, property)
        def content = domainFile.text

        then:
        // Jakarta annotations
        content.contains('import jakarta.validation.constraints.NotNull')
        content.contains('import jakarta.validation.constraints.Size')
        content.contains('@NotNull')
        content.contains('@Size(max = 255)')
        // Grails constraints
        content.contains('title nullable: false, maxSize: 255')
        content.contains('String title')
    }

    def "should not duplicate imports when adding multiple properties"() {
        given:
        def domainFile = createDomainFile('example', 'Book', '''
package example

class Book {
}
''')

        when:
        modifier.addProperty(domainFile, PropertyDefinition.builder()
            .name('title')
            .type('String')
            .nullable(false)
            .constraintStyle(AbstractMemberDefinition.ConstraintStyle.JAKARTA)
            .build())
        modifier.addProperty(domainFile, PropertyDefinition.builder()
            .name('author')
            .type('String')
            .nullable(false)
            .constraintStyle(AbstractMemberDefinition.ConstraintStyle.JAKARTA)
            .build())
        def content = domainFile.text

        then:
        // Should have NotNull import only once
        content.count('import jakarta.validation.constraints.NotNull') == 1
        // Both fields should have the annotation
        content.count('@NotNull') == 2
    }

    def "should add imports after existing imports"() {
        given:
        def domainFile = createDomainFile('example', 'Book', '''
package example

import java.util.Date

class Book {
    Date publishedDate
}
''')
        def property = PropertyDefinition.builder()
            .name('title')
            .type('String')
            .nullable(false)
            .constraintStyle(AbstractMemberDefinition.ConstraintStyle.JAKARTA)
            .build()

        when:
        modifier.addProperty(domainFile, property)
        def content = domainFile.text
        def lines = content.readLines()
        def dateImportIndex = lines.findIndexOf { it.contains('import java.util.Date') }
        def notNullImportIndex = lines.findIndexOf { it.contains('import jakarta.validation.constraints.NotNull') }

        then:
        notNullImportIndex > dateImportIndex
    }

    def "should use Grails constraints by default"() {
        given:
        def domainFile = createDomainFile('example', 'Book', '''
package example

class Book {

    static constraints = {
    }
}
''')
        def property = PropertyDefinition.builder()
            .name('title')
            .type('String')
            .nullable(false)
            .build()

        when:
        modifier.addProperty(domainFile, property)
        def content = domainFile.text

        then:
        content.contains('title nullable: false')
        !content.contains('@NotNull')
        !content.contains('import jakarta.validation.constraints')
    }

    def "should throw exception when multiple domain classes have same simple name"() {
        given:
        createDomainFile('com/foo', 'Book', '''
            package com.foo
            class Book {
            }
        ''')
        createDomainFile('com/bar', 'Book', '''
            package com.bar
            class Book {
            }
        ''')

        when:
        modifier.findDomainFile(tempDir.toFile(), 'Book')

        then:
        def e = thrown(IllegalStateException)
        e.message.contains('Multiple domain classes found')
        e.message.contains('Book')
        e.message.contains('fully qualified class name')
    }

    def "should find correct domain class when FQN is used with duplicates"() {
        given:
        createDomainFile('com/foo', 'Book', '''
            package com.foo
            class Book {
            }
        ''')
        createDomainFile('com/bar', 'Book', '''
            package com.bar
            class Book {
            }
        ''')

        when:
        def found = modifier.findDomainFile(tempDir.toFile(), 'com.bar.Book')

        then:
        found != null
        found.path.contains("com${File.separator}bar${File.separator}Book.groovy")
    }

    def "should throw exception when domain class has syntax errors"() {
        given:
        def domainFile = createDomainFile('example', 'Book', '''
            package example
            class Book {
                String title
                // missing closing brace - syntax error
        ''')

        when:
        modifier.memberExists(domainFile, 'title')

        then:
        def e = thrown(IllegalStateException)
        e.message.contains('Failed to parse domain class')
        e.message.contains('Book.groovy')
        e.cause != null
    }

    def "the source with a member has every line it had, with its line ending, and the new lines where they belong: #how"() {
        given:
        def property = PropertyDefinition.builder().name('isbn').type('String').nullable(true).build()

        expect:
        modifier.withMember('Book.groovy', source, property) == expected

        where:
        how                             | source                                                                                              || expected
        'no line ending at the end'     | 'class Book {\n    String title\n}'                                                                 || 'class Book {\n    String title\n    String isbn\n\n    static constraints = {\n        isbn nullable: true\n    }\n}'
        'the line endings of Windows'   | 'class Book {\r\n    String title\r\n\r\n    static constraints = {\r\n    }\r\n}\r\n'             || 'class Book {\r\n    String title\r\n    String isbn\r\n\r\n    static constraints = {\r\n        isbn nullable: true\r\n    }\r\n}\r\n'
        'tabs'                          | 'class Book {\n\tString title\n}\n'                                                                 || 'class Book {\n\tString title\n\tString isbn\n\n\tstatic constraints = {\n\t\tisbn nullable: true\n\t}\n}\n'
        'a method with a local String'  | 'class Book {\n    String title\n\n    String label() {\n        String text = title\n        text\n    }\n}\n' || 'class Book {\n    String title\n    String isbn\n\n    static constraints = {\n        isbn nullable: true\n    }\n\n    String label() {\n        String text = title\n        text\n    }\n}\n'
        'a block on one line'           | 'class Book {\n    String title\n    static constraints = { title blank: false }\n}\n'             || 'class Book {\n    String title\n    String isbn\n    static constraints = {\n        title blank: false\n        isbn nullable: true\n    }\n}\n'
        'the block above the property'  | 'class Book {\n    static constraints = {\n    }\n    String title\n}\n'                           || 'class Book {\n    static constraints = {\n        isbn nullable: true\n    }\n    String title\n    String isbn\n}\n'
        'a class on one line'           | 'class Book {}\n'                                                                                   || 'class Book {\n    String isbn\n\n    static constraints = {\n        isbn nullable: true\n    }\n}\n'
        'an abstract class with a block' | 'abstract class Book {\n    static constraints = {\n    }\n}\n'                                    || 'abstract class Book {\n    String isbn\n    static constraints = {\n        isbn nullable: true\n    }\n}\n'
        'a brace in a comment'          | 'class Book {\n    String title // }\n\n    static mapping = {\n        table \'b{\'\n    }\n}\n'   || 'class Book {\n    String title // }\n    String isbn\n\n    static constraints = {\n        isbn nullable: true\n    }\n\n    static mapping = {\n        table \'b{\'\n    }\n}\n'
        'another class before it'       | 'class Shelf {\n    String room\n}\n\nclass Book {\n    String title\n}\n'                         || 'class Shelf {\n    String room\n}\n\nclass Book {\n    String title\n    String isbn\n\n    static constraints = {\n        isbn nullable: true\n    }\n}\n'
    }

    def "a private field is added as a property is"() {
        given:
        def field = new FieldDefinition(name: 'secret', type: 'String', nullable: true, accessModifier: FieldDefinition.AccessModifier.PRIVATE)

        expect:
        modifier.withMember('Book.groovy', 'class Book {\n    String title\n\n    static constraints = {\n    }\n}\n', field) ==
                'class Book {\n    String title\n    private String secret\n\n    static constraints = {\n        secret nullable: true\n    }\n}\n'
    }

    def "the imports of the annotations follow the package where the class follows it directly, and are not added where a star import has them"() {
        given:
        def property = PropertyDefinition.builder().name('isbn').type('String').nullable(false)
                .constraintStyle(AbstractMemberDefinition.ConstraintStyle.JAKARTA).build()

        expect:
        modifier.withMember('Book.groovy', 'package example\nclass Book {\n}\n', property) ==
                'package example\n\nimport jakarta.validation.constraints.NotNull\n\nclass Book {\n    @NotNull\n    String isbn\n}\n'
        modifier.withMember('Book.groovy', 'import jakarta.validation.constraints.*\n\nclass Book {\n}\n', property) ==
                'import jakarta.validation.constraints.*\n\nclass Book {\n    @NotNull\n    String isbn\n}\n'
    }

    def "a source the member cannot be added to line by line is refused, and the file is what it was: #reason"() {
        given:
        createDomainDir()
        def domainFile = tempDir.resolve('grails-app/domain/Book.groovy').toFile()
        domainFile.bytes = bytes
        def property = PropertyDefinition.builder().name('isbn').type('String').nullable(true).build()

        when:
        modifier.addProperty(domainFile, property)

        then:
        def e = thrown(IllegalStateException)
        e.message.contains(reason)
        domainFile.bytes == bytes

        where:
        bytes                                                                           || reason
        'class Book {\n    static constraints = Shared.rules\n}\n'.bytes               || 'the constraints of the class are not a block that is written in it'
        'class Book { String title }\n'.bytes                                           || 'the class is written on one line'
        'class Book {\n    String title }\n'.bytes                                      || 'the property title is on the line the class begins or ends with'
        'println 1\n'.bytes                                                             || 'the file declares no class'
        'class Book {\n    String title\n'.bytes                                        || "Failed to parse domain class 'Book.groovy'"
        'class Book {\n    String café\n}\n'.getBytes('ISO-8859-1')                || "'Book.groovy' is not text in UTF-8"
    }

    def "classes of one name are named by their paths in the project"() {
        given:
        createDomainFile('com/foo', 'Book', 'package com.foo\nclass Book {\n}')
        createDomainFile('com/bar', 'Book', 'package com.bar\nclass Book {\n}')

        when:
        modifier.findDomainFile(tempDir.toFile(), 'Book')

        then:
        def e = thrown(IllegalStateException)
        e.message == "Multiple domain classes found with name 'Book': grails-app/domain/com/bar/Book.groovy, grails-app/domain/com/foo/Book.groovy. " +
                'Please specify the fully qualified class name.'
    }

    def "a constraint that cannot be told from the kind of member says which members it is for"() {
        when:
        PropertyDefinition.builder().name('pages').type('Integer').blank(false).build()

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "The 'blank' constraint is only applicable to String properties"
    }

    def "a member is not written into #what, which has no properties"() {
        given:
        def property = PropertyDefinition.builder().name('isbn').type('String').nullable(true).build()

        when:
        modifier.withMember('Book.groovy', source, property)

        then:
        def e = thrown(IllegalStateException)
        e.message == "Cannot add 'isbn' to 'Book.groovy': Book is an interface, and an interface has no properties. The file was not changed."

        where:
        what                         | source
        'an interface'               | 'interface Book {\n}\n'
        'an interface with a method' | 'interface Book {\n    String label()\n}\n'
        'an annotation type'         | '@interface Book {\n}\n'
    }

    def "a member that begins a class is indented as what the class has, of whatever kind: #what"() {
        given:
        def property = PropertyDefinition.builder().name('isbn').type('String').nullable(true).build()

        expect:
        modifier.withMember('Book.groovy', source, property) == expected

        where:
        what              | source                                                       || expected
        'an inner enum'   | 'class Book {\n\tenum Kind { NOVEL, ESSAY }\n}\n'           || 'class Book {\n\tString isbn\n\n\tstatic constraints = {\n\t\tisbn nullable: true\n\t}\n\n\tenum Kind { NOVEL, ESSAY }\n}\n'
        'an inner class'  | 'class Book {\n  static class Chapter {\n    String heading\n  }\n}\n' || 'class Book {\n  String isbn\n\n  static constraints = {\n    isbn nullable: true\n  }\n\n  static class Chapter {\n    String heading\n  }\n}\n'
        'a constructor'   | 'class Book {\n\tBook() {\n\t}\n}\n'                      || 'class Book {\n\tString isbn\n\n\tstatic constraints = {\n\t\tisbn nullable: true\n\t}\n\n\tBook() {\n\t}\n}\n'
        'a method'        | 'class Book {\n\tString label() {\n\t\t\'x\'\n\t}\n}\n'    || 'class Book {\n\tString isbn\n\n\tstatic constraints = {\n\t\tisbn nullable: true\n\t}\n\n\tString label() {\n\t\t\'x\'\n\t}\n}\n'
    }

    // Helper methods

    private void createDomainDir() {
        Files.createDirectories(tempDir.resolve('grails-app/domain'))
    }

    private File createDomainFile(String packagePath, String className, String content) {
        def domainDir = tempDir.resolve("grails-app/domain/${packagePath}")
        Files.createDirectories(domainDir)
        def domainFile = domainDir.resolve("${className}.groovy").toFile()
        domainFile.text = content.stripIndent().trim() + '\n'
        domainFile
    }
}
