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
