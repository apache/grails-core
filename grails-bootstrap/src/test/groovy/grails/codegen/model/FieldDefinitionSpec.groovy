/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  'License'); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  'AS IS' BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package grails.codegen.model

import spock.lang.Specification
import spock.lang.Unroll

class FieldDefinitionSpec extends Specification {

    def 'should parse simple field specification'() {
        when:
        def field = FieldDefinition.parse('title')

        then:
        field.name == 'title'
        field.type == 'String'  // default type
        field.accessModifier == null  // must be set separately
    }

    def 'should parse field specification with type'() {
        when:
        def field = FieldDefinition.parse('count:Integer')

        then:
        field.name == 'count'
        field.type == 'Integer'
    }

    def 'should throw exception for null field spec'() {
        when:
        FieldDefinition.parse(null)

        then:
        thrown(IllegalArgumentException)
    }

    def 'should throw exception for empty field spec'() {
        when:
        FieldDefinition.parse('')

        then:
        thrown(IllegalArgumentException)
    }

    def 'should require access modifier'() {
        given:
        def field = new FieldDefinition(name: 'title', type: 'String')

        when:
        field.validate()

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains('Access modifier is required')
    }

    def 'should validate with access modifier'() {
        given:
        def field = new FieldDefinition(
            name: 'title',
            type: 'String',
            accessModifier: FieldDefinition.AccessModifier.PRIVATE
        )

        when:
        field.validate()

        then:
        noExceptionThrown()
    }

    @Unroll
    def 'should generate declaration with #modifier access'() {
        given:
        def field = new FieldDefinition(
            name: 'title',
            type: 'String',
            accessModifier: modifier
        )

        expect:
        field.toDeclaration() == expected

        where:
        modifier                                  | expected
        FieldDefinition.AccessModifier.PRIVATE   | 'private String title'
        FieldDefinition.AccessModifier.PROTECTED | 'protected String title'
        FieldDefinition.AccessModifier.PUBLIC    | 'public String title'
    }

    def 'should use builder pattern with access modifier'() {
        when:
        def field = FieldDefinition.builder()
            .name('title')
            .type('String')
            .accessModifier(FieldDefinition.AccessModifier.PRIVATE)
            .nullable(false)
            .maxSize(255)
            .build()

        then:
        field.name == 'title'
        field.type == 'String'
        field.accessModifier == FieldDefinition.AccessModifier.PRIVATE
        field.nullable == false
        field.maxSize == 255
        field.toDeclaration() == 'private String title'
    }

    def 'should generate constraint line'() {
        given:
        def field = new FieldDefinition(
            name: 'title',
            type: 'String',
            accessModifier: FieldDefinition.AccessModifier.PRIVATE,
            nullable: false,
            blank: false,
            maxSize: 255
        )

        expect:
        field.toConstraintLine() == 'title nullable: false, blank: false, maxSize: 255'
    }

    def 'should generate Jakarta annotations'() {
        given:
        def field = new FieldDefinition(
            name: 'title',
            type: 'String',
            accessModifier: FieldDefinition.AccessModifier.PRIVATE,
            nullable: false,
            blank: false,
            maxSize: 255
        )

        expect:
        field.toAnnotations() == ['@NotNull', '@NotBlank', '@Size(max = 255)']
    }

    def 'AccessModifier toString should return keyword'() {
        expect:
        FieldDefinition.AccessModifier.PRIVATE.toString() == 'private'
        FieldDefinition.AccessModifier.PROTECTED.toString() == 'protected'
        FieldDefinition.AccessModifier.PUBLIC.toString() == 'public'
    }
}
