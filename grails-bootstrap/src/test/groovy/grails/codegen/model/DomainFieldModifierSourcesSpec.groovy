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

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

import spock.lang.Specification
import spock.lang.TempDir
import spock.lang.Unroll

import grails.codegen.model.AbstractMemberDefinition.ConstraintStyle

/**
 * The sources a member is added to, by how they are written: each case is the whole text before and
 * the whole text after, so that a line that is moved, lost or written anew fails it, and each
 * refusal is the reason that is given. The member is {@code isbn}, a {@code String}.
 */
class DomainFieldModifierSourcesSpec extends Specification {

    @TempDir
    Path tempDir

    DomainFieldModifier modifier = new DomainFieldModifier()

    @Unroll
    def "a member is added to a class that is declared with #how"() {
        expect:
        modifier.withMember('Book.groovy', source, member('nullable')) == expected

        where:
        [how, source, expected] << [
                ['annotations on lines of their own', 'import groovy.transform.ToString\n\n@ToString(includes = \'title\')\n@Deprecated\nclass Book {\n    String title\n}\n', 'import groovy.transform.ToString\n\n@ToString(includes = \'title\')\n@Deprecated\nclass Book {\n    String title\n    String isbn\n\n    static constraints = {\n        isbn nullable: true\n    }\n}\n'],
                ['an annotation on the line of the class', '@Deprecated class Book {\n    String title\n}\n', '@Deprecated class Book {\n    String title\n    String isbn\n\n    static constraints = {\n        isbn nullable: true\n    }\n}\n'],
                ['a property with an annotation', 'class Book {\n    @Deprecated\n    String title\n}\n', 'class Book {\n    @Deprecated\n    String title\n    String isbn\n\n    static constraints = {\n        isbn nullable: true\n    }\n}\n'],
                ['an abstract class with an abstract method', 'abstract class Book {\n    String title\n\n    abstract String label()\n}\n', 'abstract class Book {\n    String title\n    String isbn\n\n    static constraints = {\n        isbn nullable: true\n    }\n\n    abstract String label()\n}\n'],
                ['a class that extends and implements', 'class Book extends Publication implements Serializable, Comparable<Book> {\n    String title\n}\n', 'class Book extends Publication implements Serializable, Comparable<Book> {\n    String title\n    String isbn\n\n    static constraints = {\n        isbn nullable: true\n    }\n}\n'],
                ['a class with a type parameter', 'class Book<T extends Comparable<T>> {\n    T key\n}\n', 'class Book<T extends Comparable<T>> {\n    T key\n    String isbn\n\n    static constraints = {\n        isbn nullable: true\n    }\n}\n'],
                ['the opening brace on the line after the name', 'class Book\n{\n    String title\n}\n', 'class Book\n{\n    String title\n    String isbn\n\n    static constraints = {\n        isbn nullable: true\n    }\n}\n'],
                ['the opening brace on the line after the name, of a class without anything', 'class Book\n{\n}\n', 'class Book\n{\n    String isbn\n\n    static constraints = {\n        isbn nullable: true\n    }\n}\n'],
                ['a semicolon after the closing brace', 'class Book {\n};\n', 'class Book {\n    String isbn\n\n    static constraints = {\n        isbn nullable: true\n    }\n};\n'],
                ['a comment after the opening brace', 'class Book { // a book\n}\n', 'class Book { // a book\n    String isbn\n\n    static constraints = {\n        isbn nullable: true\n    }\n}\n'],
                ['a comment after the closing brace', 'class Book {\n    String title\n} // end\n', 'class Book {\n    String title\n    String isbn\n\n    static constraints = {\n        isbn nullable: true\n    }\n} // end\n'],
                ['a trait', 'trait Book {\n    String title\n}\n', 'trait Book {\n    String title\n    String isbn\n\n    static constraints = {\n        isbn nullable: true\n    }\n}\n'],
                ['a trait without anything', 'trait Book {\n}\n', 'trait Book {\n    String isbn\n\n    static constraints = {\n        isbn nullable: true\n    }\n}\n'],
                ['a trait with a method', 'trait Book {\n    String title\n\n    String label() { title }\n}\n', 'trait Book {\n    String title\n    String isbn\n\n    static constraints = {\n        isbn nullable: true\n    }\n\n    String label() { title }\n}\n'],
                ['an enum that has a property', 'enum Book {\n    NOVEL, ESSAY\n\n    String label\n}\n', 'enum Book {\n    NOVEL, ESSAY\n\n    String label\n    String isbn\n\n    static constraints = {\n        isbn nullable: true\n    }\n}\n'],
        ]
    }

    @Unroll
    def "the class the member is added to is the one the file is named after, and what is in it is told from what is around it: #how"() {
        expect:
        modifier.withMember(fileName, source, member('nullable')) == expected

        where:
        [how, fileName, source, expected] << [
                ['an inner class after the property', 'Book.groovy', 'class Book {\n    String title\n\n    static class Chapter {\n        String heading\n    }\n}\n', 'class Book {\n    String title\n    String isbn\n\n    static constraints = {\n        isbn nullable: true\n    }\n\n    static class Chapter {\n        String heading\n    }\n}\n'],
                ['an inner class before the property', 'Book.groovy', 'class Book {\n    static class Chapter {\n        String heading\n    }\n\n    String title\n}\n', 'class Book {\n    static class Chapter {\n        String heading\n    }\n\n    String title\n    String isbn\n\n    static constraints = {\n        isbn nullable: true\n    }\n}\n'],
                ['an inner class and nothing else', 'Book.groovy', 'class Book {\n    static class Chapter {\n        String heading\n    }\n}\n', 'class Book {\n    String isbn\n\n    static constraints = {\n        isbn nullable: true\n    }\n\n    static class Chapter {\n        String heading\n    }\n}\n'],
                ['the class of the file after another, each with its block', 'Book.groovy', 'class Shelf {\n    String room\n\n    static constraints = {\n        room blank: false\n    }\n}\n\nclass Book {\n    String title\n\n    static constraints = {\n        title blank: false\n    }\n}\n', 'class Shelf {\n    String room\n\n    static constraints = {\n        room blank: false\n    }\n}\n\nclass Book {\n    String title\n    String isbn\n\n    static constraints = {\n        title blank: false\n        isbn nullable: true\n    }\n}\n'],
                ['the class of the file before another', 'Book.groovy', 'class Book {\n    String title\n}\n\nclass Shelf {\n    String room\n}\n', 'class Book {\n    String title\n    String isbn\n\n    static constraints = {\n        isbn nullable: true\n    }\n}\n\nclass Shelf {\n    String room\n}\n'],
                ['a block that is in the other class only', 'Book.groovy', 'class Shelf {\n    static constraints = {\n    }\n}\n\nclass Book {\n    String title\n}\n', 'class Shelf {\n    static constraints = {\n    }\n}\n\nclass Book {\n    String title\n    String isbn\n\n    static constraints = {\n        isbn nullable: true\n    }\n}\n'],
                ['no class of the name of the file: the first', 'Book.groovy', 'class Shelf {\n    String room\n}\n\nclass Box {\n    String label\n}\n', 'class Shelf {\n    String room\n    String isbn\n\n    static constraints = {\n        isbn nullable: true\n    }\n}\n\nclass Box {\n    String label\n}\n'],
                ['the name of the file on an inner class only: the class around it', 'Book.groovy', 'class Shelf {\n    static class Book {\n        String title\n    }\n}\n', 'class Shelf {\n    String isbn\n\n    static constraints = {\n        isbn nullable: true\n    }\n\n    static class Book {\n        String title\n    }\n}\n'],
                ['a file name without .groovy', 'Book', 'class Book {\n    String title\n}\n', 'class Book {\n    String title\n    String isbn\n\n    static constraints = {\n        isbn nullable: true\n    }\n}\n'],
                ['a script with a class', 'setup.groovy', 'println \'before\'\n\nclass Book {\n    String title\n}\n\nprintln new Book()\n', 'println \'before\'\n\nclass Book {\n    String title\n    String isbn\n\n    static constraints = {\n        isbn nullable: true\n    }\n}\n\nprintln new Book()\n'],
        ]
    }

    @Unroll
    def "the member follows the last property or field of an instance, and begins the class that has none: #how"() {
        expect:
        modifier.withMember('Book.groovy', source, member('nullable')) == expected

        where:
        [how, source, expected] << [
                ['a property without a type', 'class Book {\n    def title\n}\n', 'class Book {\n    def title\n    String isbn\n\n    static constraints = {\n        isbn nullable: true\n    }\n}\n'],
                ['a final property with a value', 'class Book {\n    final String title = \'x\'\n}\n', 'class Book {\n    final String title = \'x\'\n    String isbn\n\n    static constraints = {\n        isbn nullable: true\n    }\n}\n'],
                ['a property with a value over several lines', 'class Book {\n    List<String> tags = [\n        \'a\',\n        \'b\',\n    ]\n}\n', 'class Book {\n    List<String> tags = [\n        \'a\',\n        \'b\',\n    ]\n    String isbn\n\n    static constraints = {\n        isbn nullable: true\n    }\n}\n'],
                ['a property with a closure for its value', 'class Book {\n    String title\n    Closure label = {\n        title\n    }\n}\n', 'class Book {\n    String title\n    Closure label = {\n        title\n    }\n    String isbn\n\n    static constraints = {\n        isbn nullable: true\n    }\n}\n'],
                ['a property of a generic type', 'class Book {\n    Map<String, List<String>> index = [:]\n}\n', 'class Book {\n    Map<String, List<String>> index = [:]\n    String isbn\n\n    static constraints = {\n        isbn nullable: true\n    }\n}\n'],
                ['two properties in one declaration', 'class Book {\n    String title, subtitle\n}\n', 'class Book {\n    String title, subtitle\n    String isbn\n\n    static constraints = {\n        isbn nullable: true\n    }\n}\n'],
                ['two properties on one line', 'class Book {\n    String title; String subtitle\n}\n', 'class Book {\n    String title; String subtitle\n    String isbn\n\n    static constraints = {\n        isbn nullable: true\n    }\n}\n'],
                ['a property with a semicolon and a comment', 'class Book {\n    String title; // the title\n}\n', 'class Book {\n    String title; // the title\n    String isbn\n\n    static constraints = {\n        isbn nullable: true\n    }\n}\n'],
                ['a comment on the line after the last property', 'class Book {\n    String title\n    // the rest\n\n    String label() { title }\n}\n', 'class Book {\n    String title\n    String isbn\n\n    static constraints = {\n        isbn nullable: true\n    }\n\n    // the rest\n\n    String label() { title }\n}\n'],
                ['a transient property', 'class Book {\n    String title\n    transient String cache\n}\n', 'class Book {\n    String title\n    transient String cache\n    String isbn\n\n    static constraints = {\n        isbn nullable: true\n    }\n}\n'],
                ['a private field after the properties', 'class Book {\n    String title\n    private int count = 0\n}\n', 'class Book {\n    String title\n    private int count = 0\n    String isbn\n\n    static constraints = {\n        isbn nullable: true\n    }\n}\n'],
                ['a static property after the properties', 'class Book {\n    String title\n    static String shared\n}\n', 'class Book {\n    String title\n    String isbn\n\n    static constraints = {\n        isbn nullable: true\n    }\n\n    static String shared\n}\n'],
                ['static members only', 'class Book {\n    static hasMany = [chapters: String]\n    static transients = [\'label\']\n}\n', 'class Book {\n    String isbn\n\n    static constraints = {\n        isbn nullable: true\n    }\n\n    static hasMany = [chapters: String]\n    static transients = [\'label\']\n}\n'],
                ['static members only, with tabs', 'class Book {\n\tstatic hasMany = [chapters: String]\n}\n', 'class Book {\n\tString isbn\n\n\tstatic constraints = {\n\t\tisbn nullable: true\n\t}\n\n\tstatic hasMany = [chapters: String]\n}\n'],
                ['hasMany and mapping after the properties', 'class Book {\n    String title\n\n    static hasMany = [chapters: String]\n\n    static mapping = {\n        table \'books\'\n    }\n}\n', 'class Book {\n    String title\n    String isbn\n\n    static constraints = {\n        isbn nullable: true\n    }\n\n    static hasMany = [chapters: String]\n\n    static mapping = {\n        table \'books\'\n    }\n}\n'],
                ['methods only', 'class Book {\n    String label() {\n        \'x\'\n    }\n}\n', 'class Book {\n    String isbn\n\n    static constraints = {\n        isbn nullable: true\n    }\n\n    String label() {\n        \'x\'\n    }\n}\n'],
                ['methods only, indented by two spaces', 'class Book {\n  String label() {\n    \'x\'\n  }\n}\n', 'class Book {\n  String isbn\n\n  static constraints = {\n    isbn nullable: true\n  }\n\n  String label() {\n    \'x\'\n  }\n}\n'],
                ['a property after a method', 'class Book {\n    String label() {\n        title\n    }\n\n    String title\n}\n', 'class Book {\n    String label() {\n        title\n    }\n\n    String title\n    String isbn\n\n    static constraints = {\n        isbn nullable: true\n    }\n}\n'],
                ['a method on the line after the last property', 'class Book {\n    String title\n    String label() {\n        title\n    }\n}\n', 'class Book {\n    String title\n    String isbn\n\n    static constraints = {\n        isbn nullable: true\n    }\n\n    String label() {\n        title\n    }\n}\n'],
                ['a constructor only', 'class Book {\n    Book() {\n    }\n}\n', 'class Book {\n    String isbn\n\n    static constraints = {\n        isbn nullable: true\n    }\n\n    Book() {\n    }\n}\n'],
        ]
    }

    @Unroll
    def "a brace that is not one of the class or of its block is not taken for one: #how"() {
        expect:
        modifier.withMember('Book.groovy', source, member('nullable')) == expected

        where:
        [how, source, expected] << [
                ['the class in a comment before it', '/*\n * class Book {\n * }\n */\nclass Book {\n    String title\n}\n', '/*\n * class Book {\n * }\n */\nclass Book {\n    String title\n    String isbn\n\n    static constraints = {\n        isbn nullable: true\n    }\n}\n'],
                ['braces in the documentation of the class', '/**\n * A book {@code class Book { }}.\n */\nclass Book {\n    String title\n}\n', '/**\n * A book {@code class Book { }}.\n */\nclass Book {\n    String title\n    String isbn\n\n    static constraints = {\n        isbn nullable: true\n    }\n}\n'],
                ['braces in strings and in a pattern', 'class Book {\n    String title = \'}\'\n    String other = "{ ${title} }"\n\n    static constraints = {\n        title matches: /[{}]+/\n    }\n}\n', 'class Book {\n    String title = \'}\'\n    String other = "{ ${title} }"\n    String isbn\n\n    static constraints = {\n        title matches: /[{}]+/\n        isbn nullable: true\n    }\n}\n'],
                ['a string of several lines that looks like the end of the class', 'class Book {\n    String title = \'\'\'\n}\nclass Book {\n\'\'\'\n}\n', 'class Book {\n    String title = \'\'\'\n}\nclass Book {\n\'\'\'\n    String isbn\n\n    static constraints = {\n        isbn nullable: true\n    }\n}\n'],
                ['a brace in a comment of the class', 'class Book {\n    /* String old } */\n    String title\n}\n', 'class Book {\n    /* String old } */\n    String title\n    String isbn\n\n    static constraints = {\n        isbn nullable: true\n    }\n}\n'],
                ['a closure with a brace in a string, in the block', 'class Book {\n    String title\n\n    static constraints = {\n        title validator: { val, obj ->\n            val != \'}\'\n        }\n    }\n}\n', 'class Book {\n    String title\n    String isbn\n\n    static constraints = {\n        title validator: { val, obj ->\n            val != \'}\'\n        }\n        isbn nullable: true\n    }\n}\n'],
        ]
    }

    @Unroll
    def "the lines that are added end and are indented as the lines of the source are: #how"() {
        expect:
        modifier.withMember('Book.groovy', source, member(kind)) == expected

        where:
        [how, kind, source, expected] << [
                ['the line endings of Windows, with a package, an import and a block', 'both', 'package example\r\n\r\nimport java.time.LocalDate\r\n\r\nclass Book {\r\n    String title\r\n\r\n    static constraints = {\r\n        title blank: false\r\n    }\r\n}\r\n', 'package example\r\n\r\nimport java.time.LocalDate\r\nimport jakarta.validation.constraints.NotNull\r\nimport jakarta.validation.constraints.Size\r\n\r\nclass Book {\r\n    String title\r\n    @NotNull\r\n    @Size(max = 13)\r\n    String isbn\r\n\r\n    static constraints = {\r\n        title blank: false\r\n        isbn nullable: false, maxSize: 13\r\n    }\r\n}\r\n'],
                ['the line endings of Windows, a class on one line', 'nullable', 'class Book {}\r\n', 'class Book {\r\n    String isbn\r\n\r\n    static constraints = {\r\n        isbn nullable: true\r\n    }\r\n}\r\n'],
                ['the line endings of Windows, imports at the beginning of the file', 'jakarta', 'class Book {\r\n}\r\n', 'import jakarta.validation.constraints.NotNull\r\nimport jakarta.validation.constraints.NotBlank\r\nimport jakarta.validation.constraints.Size\r\n\r\nclass Book {\r\n    @NotNull\r\n    @NotBlank\r\n    @Size(min = 10, max = 13)\r\n    String isbn\r\n}\r\n'],
                ['the line endings of Windows, a block on one line', 'nullable', 'class Book {\r\n    String title\r\n    static constraints = { title blank: false }\r\n}\r\n', 'class Book {\r\n    String title\r\n    String isbn\r\n    static constraints = {\r\n        title blank: false\r\n        isbn nullable: true\r\n    }\r\n}\r\n'],
                ['no line ending at the end, a class without anything', 'nullable', 'class Book {\n}', 'class Book {\n    String isbn\n\n    static constraints = {\n        isbn nullable: true\n    }\n}'],
                ['no line ending at all', 'nullable', 'class Book {}', 'class Book {\n    String isbn\n\n    static constraints = {\n        isbn nullable: true\n    }\n}'],
                ['no line ending at all, a space between the braces', 'nullable', 'class Book { }', 'class Book {\n    String isbn\n\n    static constraints = {\n        isbn nullable: true\n    }\n}'],
                ['two spaces', 'nullable', 'class Book {\n  String title\n\n  static constraints = {\n    title blank: false\n  }\n}\n', 'class Book {\n  String title\n  String isbn\n\n  static constraints = {\n    title blank: false\n    isbn nullable: true\n  }\n}\n'],
                ['a class that is indented itself', 'nullable', '  class Book {\n      String title\n  }\n', '  class Book {\n      String title\n      String isbn\n\n      static constraints = {\n          isbn nullable: true\n      }\n  }\n'],
                ['tabs, with a block', 'nullable', 'class Book {\n\tString title\n\n\tstatic constraints = {\n\t\ttitle blank: false\n\t}\n}\n', 'class Book {\n\tString title\n\tString isbn\n\n\tstatic constraints = {\n\t\ttitle blank: false\n\t\tisbn nullable: true\n\t}\n}\n'],
                ['no indentation', 'nullable', 'class Book {\nString title\n}\n', 'class Book {\nString title\nString isbn\n\nstatic constraints = {\n    isbn nullable: true\n}\n}\n'],
                ['a class without anything', 'nullable', 'class Book {\n}\n', 'class Book {\n    String isbn\n\n    static constraints = {\n        isbn nullable: true\n    }\n}\n'],
                ['a class with an empty line', 'nullable', 'class Book {\n\n}\n', 'class Book {\n    String isbn\n\n    static constraints = {\n        isbn nullable: true\n    }\n\n}\n'],
        ]
    }

    @Unroll
    def "a class that is indented in an unusual way has the member where it belongs: #how"() {
        expect:
        modifier.withMember('Book.groovy', source, member('nullable')) == expected

        where:
        how                                                 | source                                         || expected
        'a static member that ends on the line of the class' | 'class Book {\n    static shared = 1 }\n'      || 'class Book {\n    String isbn\n\n    static constraints = {\n        isbn nullable: true\n    }\n\n    static shared = 1 }\n'
        'spaces before the class and a tab before its members' | '  class Book {\n\tString title\n  }\n'      || '  class Book {\n\tString title\n\tString isbn\n\n\tstatic constraints = {\n\t    isbn nullable: true\n\t}\n  }\n'
    }

    @Unroll
    def "the constraint is the last entry of the block the class has, however the block is written: #how"() {
        expect:
        modifier.withMember('Book.groovy', source, member(kind)) == expected

        where:
        [how, kind, source, expected] << [
                ['a block and nothing else', 'nullable', 'class Book {\n    static constraints = {\n    }\n}\n', 'class Book {\n    String isbn\n    static constraints = {\n        isbn nullable: true\n    }\n}\n'],
                ['an empty block on one line', 'nullable', 'class Book {\n    String title\n\n    static constraints = {}\n}\n', 'class Book {\n    String title\n    String isbn\n\n    static constraints = {\n        isbn nullable: true\n    }\n}\n'],
                ['an empty block on one line, with a space', 'nullable', 'class Book {\n    String title\n\n    static constraints = { }\n}\n', 'class Book {\n    String title\n    String isbn\n\n    static constraints = {\n        isbn nullable: true\n    }\n}\n'],
                ['the closing brace on the line of the last entry', 'nullable', 'class Book {\n    String title\n\n    static constraints = {\n        title blank: false }\n}\n', 'class Book {\n    String title\n    String isbn\n\n    static constraints = {\n        title blank: false\n        isbn nullable: true\n    }\n}\n'],
                ['an empty line at the end of the block', 'nullable', 'class Book {\n    String title\n\n    static constraints = {\n        title blank: false\n\n    }\n}\n', 'class Book {\n    String title\n    String isbn\n\n    static constraints = {\n        title blank: false\n\n        isbn nullable: true\n    }\n}\n'],
                ['entries indented further than the block is', 'nullable', 'class Book {\n    String title\n\n    static constraints = {\n            title blank: false\n    }\n}\n', 'class Book {\n    String title\n    String isbn\n\n    static constraints = {\n            title blank: false\n            isbn nullable: true\n    }\n}\n'],
                ['a comment at the end of the block', 'nullable', 'class Book {\n    String title\n\n    static constraints = {\n        title blank: false\n        // more to come\n    }\n}\n', 'class Book {\n    String title\n    String isbn\n\n    static constraints = {\n        title blank: false\n        // more to come\n        isbn nullable: true\n    }\n}\n'],
                ['a block declared as a Closure', 'nullable', 'class Book {\n    String title\n\n    static Closure constraints = {\n    }\n}\n', 'class Book {\n    String title\n    String isbn\n\n    static Closure constraints = {\n        isbn nullable: true\n    }\n}\n'],
                ['a block with an arrow', 'nullable', 'class Book {\n    String title\n\n    static constraints = { ->\n        title blank: false\n    }\n}\n', 'class Book {\n    String title\n    String isbn\n\n    static constraints = { ->\n        title blank: false\n        isbn nullable: true\n    }\n}\n'],
                ['the closing braces of the block and of the class on one line', 'nullable', 'class Book {\n    String title\n    static constraints = {\n    } }\n', 'class Book {\n    String title\n    String isbn\n    static constraints = {\n        isbn nullable: true\n    } }\n'],
                ['an empty block on one line, above the properties', 'nullable', 'class Book {\n    static constraints = {}\n    String title\n}\n', 'class Book {\n    static constraints = {\n        isbn nullable: true\n    }\n    String title\n    String isbn\n}\n'],
                ['a member without a constraint: the block is left alone', 'plain', 'class Book {\n    String title\n\n    static constraints = {\n    }\n}\n', 'class Book {\n    String title\n    String isbn\n\n    static constraints = {\n    }\n}\n'],
                ['a member without a constraint: no block is written', 'plain', 'class Book {\n    String title\n}\n', 'class Book {\n    String title\n    String isbn\n}\n'],
                ['annotations only: the block is left alone', 'jakarta', 'class Book {\n    String title\n\n    static constraints = {\n    }\n}\n', 'import jakarta.validation.constraints.NotNull\nimport jakarta.validation.constraints.NotBlank\nimport jakarta.validation.constraints.Size\n\nclass Book {\n    String title\n    @NotNull\n    @NotBlank\n    @Size(min = 10, max = 13)\n    String isbn\n\n    static constraints = {\n    }\n}\n'],
                ['annotations and a constraint: a block is written', 'both', 'package example\n\nclass Book {\n    String title\n}\n', 'package example\n\nimport jakarta.validation.constraints.NotNull\nimport jakarta.validation.constraints.Size\n\nclass Book {\n    String title\n    @NotNull\n    @Size(max = 13)\n    String isbn\n\n    static constraints = {\n        isbn nullable: false, maxSize: 13\n    }\n}\n'],
                ['a private field with a constraint', 'field', 'class Book {\n}\n', 'class Book {\n    private String isbn\n\n    static constraints = {\n        isbn nullable: true\n    }\n}\n'],
        ]
    }

    @Unroll
    def "the imports of the annotations go where the imports are: #how"() {
        expect:
        modifier.withMember('Book.groovy', source, member(kind)) == expected

        where:
        [how, kind, source, expected] << [
                ['no package and no import: at the beginning of the file', 'jakarta', 'class Book {\n}\n', 'import jakarta.validation.constraints.NotNull\nimport jakarta.validation.constraints.NotBlank\nimport jakarta.validation.constraints.Size\n\nclass Book {\n    @NotNull\n    @NotBlank\n    @Size(min = 10, max = 13)\n    String isbn\n}\n'],
                ['a comment before the package: after the package', 'jakarta', '/*\n * Licence.\n */\npackage example\n\nclass Book {\n}\n', '/*\n * Licence.\n */\npackage example\n\nimport jakarta.validation.constraints.NotNull\nimport jakarta.validation.constraints.NotBlank\nimport jakarta.validation.constraints.Size\n\nclass Book {\n    @NotNull\n    @NotBlank\n    @Size(min = 10, max = 13)\n    String isbn\n}\n'],
                ['one of the imports is there: the others after it', 'jakarta', 'package example\n\nimport jakarta.validation.constraints.NotNull\n\nclass Book {\n}\n', 'package example\n\nimport jakarta.validation.constraints.NotNull\nimport jakarta.validation.constraints.NotBlank\nimport jakarta.validation.constraints.Size\n\nclass Book {\n    @NotNull\n    @NotBlank\n    @Size(min = 10, max = 13)\n    String isbn\n}\n'],
                ['every import is there: none is added', 'jakarta', 'package example\n\nimport jakarta.validation.constraints.NotBlank\nimport jakarta.validation.constraints.NotNull\nimport jakarta.validation.constraints.Size\n\nclass Book {\n}\n', 'package example\n\nimport jakarta.validation.constraints.NotBlank\nimport jakarta.validation.constraints.NotNull\nimport jakarta.validation.constraints.Size\n\nclass Book {\n    @NotNull\n    @NotBlank\n    @Size(min = 10, max = 13)\n    String isbn\n}\n'],
                ['a star import of another package: after it', 'both', 'import jakarta.validation.*\n\nclass Book {\n}\n', 'import jakarta.validation.*\nimport jakarta.validation.constraints.NotNull\nimport jakarta.validation.constraints.Size\n\nclass Book {\n    @NotNull\n    @Size(max = 13)\n    String isbn\n\n    static constraints = {\n        isbn nullable: false, maxSize: 13\n    }\n}\n'],
                ['an import with a semicolon and a comment is one that is there', 'both', 'package example;\n\nimport jakarta.validation.constraints.NotNull; // needed\n\nclass Book {\n}\n', 'package example;\n\nimport jakarta.validation.constraints.NotNull; // needed\nimport jakarta.validation.constraints.Size\n\nclass Book {\n    @NotNull\n    @Size(max = 13)\n    String isbn\n\n    static constraints = {\n        isbn nullable: false, maxSize: 13\n    }\n}\n'],
                ['the class on the line after the last import: an empty line between', 'both', 'import java.time.LocalDate\nclass Book {\n    LocalDate published\n}\n', 'import java.time.LocalDate\nimport jakarta.validation.constraints.NotNull\nimport jakarta.validation.constraints.Size\n\nclass Book {\n    LocalDate published\n    @NotNull\n    @Size(max = 13)\n    String isbn\n\n    static constraints = {\n        isbn nullable: false, maxSize: 13\n    }\n}\n'],
                ['an annotated class on the line after the package', 'both', 'package example\n@Deprecated\nclass Book {\n}\n', 'package example\n\nimport jakarta.validation.constraints.NotNull\nimport jakarta.validation.constraints.Size\n\n@Deprecated\nclass Book {\n    @NotNull\n    @Size(max = 13)\n    String isbn\n\n    static constraints = {\n        isbn nullable: false, maxSize: 13\n    }\n}\n'],
                ['an import under another name is not the import: the annotation needs its own', 'both', 'import jakarta.validation.constraints.NotNull as Required\n\nclass Book {\n}\n', 'import jakarta.validation.constraints.NotNull\nimport jakarta.validation.constraints.Size\n\nimport jakarta.validation.constraints.NotNull as Required\n\nclass Book {\n    @NotNull\n    @Size(max = 13)\n    String isbn\n\n    static constraints = {\n        isbn nullable: false, maxSize: 13\n    }\n}\n'],
                ['a static import only', 'both', 'import static java.lang.Math.max\n\nclass Book {\n}\n', 'import jakarta.validation.constraints.NotNull\nimport jakarta.validation.constraints.Size\n\nimport static java.lang.Math.max\n\nclass Book {\n    @NotNull\n    @Size(max = 13)\n    String isbn\n\n    static constraints = {\n        isbn nullable: false, maxSize: 13\n    }\n}\n'],
        ]
    }

    @Unroll
    def "a source the member cannot be added to is refused with the reason: #how"() {
        when:
        modifier.withMember(fileName, source, member('nullable'))

        then:
        IllegalStateException e = thrown()
        e.message == "Cannot add 'isbn' to '${fileName}': ${reason}. The file was not changed."

        where:
        [how, fileName, source, reason] << [
                ['a script', 'Book.groovy', 'def title = \'x\'\nprintln title\n', 'the file declares no class'],
                ['a file without anything', 'Book.groovy', '', 'the file declares no class'],
                ['an enum with its constants only, which a property cannot be written before', 'Book.groovy', 'enum Book {\n    NOVEL, ESSAY\n}\n', 'the class is written in a way the lines of the member could not be put into'],
                ['a record, whose properties are on the line it begins with', 'Book.groovy', 'record Book(String title) {\n}\n', 'the property title is on the line the class begins or ends with; write it on a line of its own'],
                ['a comment between the keyword and the name', 'Book.groovy', 'class /* a */ Book {\n}\n', 'where the class begins and ends could not be told'],
                ['a block that is not static', 'Book.groovy', 'class Book {\n    String title\n\n    def constraints = {\n    }\n}\n', 'the constraints of the class are not a block that is written in it, as static constraints = { }, so the constraint has no place'],
                ['a block that is cast', 'Book.groovy', 'class Book {\n    String title\n\n    static constraints = {\n    } as Closure\n}\n', 'the constraints of the class are not a block that is written in it, as static constraints = { }, so the constraint has no place'],
                ['constraints that are a map', 'Book.groovy', 'class Book {\n    String title\n\n    static constraints = [:]\n}\n', 'the constraints of the class are not a block that is written in it, as static constraints = { }, so the constraint has no place'],
                ['constraints without a value', 'Book.groovy', 'class Book {\n    String title\n\n    static constraints\n}\n', 'the constraints of the class are not a block that is written in it, as static constraints = { }, so the constraint has no place'],
                ['a block in parentheses', 'Book.groovy', 'class Book {\n    String title\n\n    static constraints = ({\n    })\n}\n', 'where the constraints block begins and ends could not be told'],
                ['a member after the opening brace, on its line', 'Book.groovy', 'class Book { static mapping = {\n    }\n}\n', 'there is something after the brace the class begins with, on its line; write it on a line of its own'],
                ['a method after the opening brace, on its line', 'Book.groovy', 'class Book { String label() { \'x\' }\n}\n', 'there is something after the brace the class begins with, on its line; write it on a line of its own'],
                ['a property on the line the class begins with', 'Book.groovy', 'class Book { String title\n}\n', 'the property title is on the line the class begins or ends with; write it on a line of its own'],
                ['a field on the line the class ends with', 'Book.groovy', 'class Book {\n    private String secret }\n', 'the property secret is on the line the class begins or ends with; write it on a line of its own'],
                ['a class on one line that has something', 'Book.groovy', 'class Book { String label() { \'x\' } }\n', 'the class is written on one line; write its closing brace on a line of its own'],
        ]
    }

    @Unroll
    def "every constraint is written in the style that is asked for: #style, nullable #nullable, blank #blank, maxSize #maxSize, minSize #minSize"() {
        given:
        PropertyDefinition property = new PropertyDefinition(name: 'isbn', type: 'String', nullable: nullable, blank: blank,
                maxSize: maxSize, minSize: minSize, constraintStyle: style)

        and: 'what the style asks for, as the guide has it'
        List<String> entries = []
        List<String> annotations = []
        if (nullable != null) {
            entries << "nullable: ${nullable}".toString()
        }
        if (blank != null) {
            entries << "blank: ${blank}".toString()
        }
        if (maxSize != null) {
            entries << "maxSize: ${maxSize}".toString()
        }
        if (minSize != null) {
            entries << "minSize: ${minSize}".toString()
        }
        if (nullable == false) {
            annotations << 'NotNull'
        }
        if (blank == false) {
            annotations << 'NotBlank'
        }
        if (maxSize != null || minSize != null) {
            annotations << 'Size'
        }
        String size = [minSize == null ? null : "min = ${minSize}", maxSize == null ? null : "max = ${maxSize}"].findAll().join(', ')
        boolean grails = style != ConstraintStyle.JAKARTA
        boolean jakarta = style != ConstraintStyle.GRAILS

        expect:
        modifier.withMember('Book.groovy', 'package example\n\nclass Book {\n    String title\n\n    static constraints = {\n        title blank: false\n    }\n}\n', property) ==
                'package example\n\n' +
                (jakarta && annotations ? annotations.collect { String name -> "import jakarta.validation.constraints.${name}\n" }.join('') + '\n' : '') +
                'class Book {\n    String title\n' +
                (jakarta ? annotations.collect { String name -> "    @${name}${name == 'Size' ? "(${size})" : ''}\n" }.join('') : '') +
                '    String isbn\n\n    static constraints = {\n        title blank: false\n' +
                (grails && entries ? "        isbn ${entries.join(', ')}\n" : '') +
                '    }\n}\n'

        where:
        [style, nullable, blank, maxSize, minSize] << [ConstraintStyle.values().toList(), [null, true, false], [null, true, false], [null, 255], [null, 1]].combinations()
    }

    @Unroll
    def "a #access field is added with its modifier, in the style #style"() {
        given:
        FieldDefinition field = FieldDefinition.builder().name('isbn').type('String').accessModifier(access).nullable(false).maxSize(13).constraintStyle(style).build()

        expect:
        modifier.withMember('Book.groovy', 'class Book {\n    String title\n}\n', field) == expected

        where:
        access                                   | style                   || expected
        FieldDefinition.AccessModifier.PRIVATE   | ConstraintStyle.GRAILS  || 'class Book {\n    String title\n    private String isbn\n\n    static constraints = {\n        isbn nullable: false, maxSize: 13\n    }\n}\n'
        FieldDefinition.AccessModifier.PROTECTED | ConstraintStyle.JAKARTA || 'import jakarta.validation.constraints.NotNull\nimport jakarta.validation.constraints.Size\n\nclass Book {\n    String title\n    @NotNull\n    @Size(max = 13)\n    protected String isbn\n}\n'
        FieldDefinition.AccessModifier.PUBLIC    | ConstraintStyle.BOTH    || 'import jakarta.validation.constraints.NotNull\nimport jakarta.validation.constraints.Size\n\nclass Book {\n    String title\n    @NotNull\n    @Size(max = 13)\n    public String isbn\n\n    static constraints = {\n        isbn nullable: false, maxSize: 13\n    }\n}\n'
    }

    def "a member of another type than String is added with the type as it is given"() {
        expect:
        modifier.withMember('Book.groovy', 'class Book {\n}\n', PropertyDefinition.builder().name('published').type(type).nullable(true).build()) ==
                "class Book {\n    ${type} published\n\n    static constraints = {\n        published nullable: true\n    }\n}\n"

        where:
        type << ['Integer', 'java.time.LocalDate', 'List<Author>', 'Author']
    }

    def "a member that is added is one the class has, and is added after the one before it"() {
        given:
        File file = domainFile('class Book {\n}\n')

        when:
        modifier.addProperty(file, PropertyDefinition.builder().name('title').type('String').blank(false).build())
        modifier.addField(file, FieldDefinition.builder().name('secret').type('String').accessModifier(FieldDefinition.AccessModifier.PRIVATE).build())
        modifier.addMember(file, PropertyDefinition.builder().name('pages').type('Integer').nullable(true).build())

        then:
        file.text == 'class Book {\n    String title\n    private String secret\n    Integer pages\n\n    static constraints = {\n        title blank: false\n        pages nullable: true\n    }\n}\n'
        modifier.memberExists(file, 'title')
        modifier.memberExists(file, 'secret')
        modifier.memberExists(file, 'pages')
        modifier.memberExists(file, 'constraints')
        !modifier.memberExists(file, 'isbn')
    }

    def "a file is written in UTF-8 with the bytes it had, and the line endings it had"() {
        given:
        String source = 'class Book {\r\n    String título = \'日本 😀\'\r\n}\r\n'
        File file = domainFile('')
        file.setBytes(source.getBytes(StandardCharsets.UTF_8))

        when:
        modifier.addProperty(file, member('plain'))

        then:
        file.bytes == 'class Book {\r\n    String título = \'日本 😀\'\r\n    String isbn\r\n}\r\n'.getBytes(StandardCharsets.UTF_8)
    }

    def "a member is one a class has when the class declares a property or a field of the name, and not what a method or the compiler has"() {
        given:
        File file = domainFile('''\
            class Book {
                String title
                private int count
                static String shared

                String getLabel() {
                    String local = title
                    local
                }
            }
        '''.stripIndent())

        expect:
        modifier.memberExists(file, name) == exists

        where:
        name      || exists
        'title'   || true
        'count'   || true
        'shared'  || true
        'label'   || false
        'local'   || false
        'Title'   || false
    }

    def "no member is one of a file that is not there, of none at all, or of a file without a class"() {
        expect:
        !modifier.memberExists(new File(tempDir.toFile(), 'Missing.groovy'), 'title')
        !modifier.memberExists(null, 'title')
        !modifier.memberExists(domainFile('println 1\n'), 'title')
    }

    def "a member is not added to a file that is not there, or to none"() {
        when:
        modifier.addMember(file, member('plain'))

        then:
        IllegalStateException e = thrown()
        e.message.startsWith('Domain file does not exist: ')

        where:
        file << [null, new File('no/such/Book.groovy')]
    }

    def "a member that is not valid is refused before the source is read"() {
        when:
        modifier.withMember('Book.groovy', 'not Groovy {{{', new PropertyDefinition(name: 'Isbn', type: 'String'))

        then:
        IllegalArgumentException e = thrown()
        e.message == "Property name 'Isbn' must start with a lowercase letter and contain only alphanumeric characters"
    }

    def "a project without domain classes has none to find, and a class is found below the directory of its package or anywhere by its name"() {
        expect: 'no grails-app/domain'
        modifier.findDomainFile(tempDir.toFile(), 'Book') == null

        when:
        File book = domainFile('package com.example\n\nclass Book {\n}\n', 'com/example/Book.groovy')

        then:
        modifier.findDomainFile(tempDir.toFile(), 'com.example.Book') == book
        modifier.findDomainFile(tempDir.toFile(), 'Book') == book
        modifier.findDomainFile(tempDir.toFile(), 'other.pkg.Book') == book
        modifier.findDomainFile(tempDir.toFile(), 'Author') == null
        modifier.findDomainFile(tempDir.toFile(), 'com.example.Author') == null
    }

    @Unroll
    def "a constraint of a String is refused for a #kind of another type: #constraint"() {
        when:
        definition.type = 'Integer'
        definition[constraint] = value
        definition.validate()

        then:
        IllegalArgumentException e = thrown()
        e.message == "The '${constraint}' constraint is only applicable to String ${plural}"

        where:
        kind       | definition                                                                            | constraint | value || plural
        'property' | new PropertyDefinition(name: 'pages')                                                 | 'blank'    | false || 'properties'
        'property' | new PropertyDefinition(name: 'pages')                                                 | 'maxSize'  | 10    || 'properties'
        'property' | new PropertyDefinition(name: 'pages')                                                 | 'minSize'  | 1     || 'properties'
        'field'    | new FieldDefinition(name: 'pages', accessModifier: FieldDefinition.AccessModifier.PRIVATE) | 'blank'    | false || 'fields'
        'field'    | new FieldDefinition(name: 'pages', accessModifier: FieldDefinition.AccessModifier.PRIVATE) | 'maxSize'  | 10    || 'fields'
        'field'    | new FieldDefinition(name: 'pages', accessModifier: FieldDefinition.AccessModifier.PRIVATE) | 'minSize'  | 1     || 'fields'
    }

    // The members of the cases, by what they ask for
    private static AbstractMemberDefinition member(String kind) {
        switch (kind) {
            case 'nullable':
                return PropertyDefinition.builder().name('isbn').type('String').nullable(true).build()
            case 'plain':
                return PropertyDefinition.builder().name('isbn').type('String').build()
            case 'jakarta':
                return PropertyDefinition.builder().name('isbn').type('String').nullable(false).blank(false).maxSize(13).minSize(10)
                        .constraintStyle(ConstraintStyle.JAKARTA).build()
            case 'both':
                return PropertyDefinition.builder().name('isbn').type('String').nullable(false).maxSize(13)
                        .constraintStyle(ConstraintStyle.BOTH).build()
            case 'field':
                return new FieldDefinition(name: 'isbn', type: 'String', nullable: true, accessModifier: FieldDefinition.AccessModifier.PRIVATE)
            default:
                throw new IllegalArgumentException(kind)
        }
    }

    private File domainFile(String source, String path = 'Book.groovy') {
        File file = tempDir.resolve("grails-app/domain/${path}").toFile()
        Files.createDirectories(file.parentFile.toPath())
        file.setBytes(source.getBytes(StandardCharsets.UTF_8))
        file
    }
}
