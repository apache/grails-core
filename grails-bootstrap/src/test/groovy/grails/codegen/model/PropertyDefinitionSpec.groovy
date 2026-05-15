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

class PropertyDefinitionSpec extends Specification {

    def 'should parse simple property specification'() {
        when:
        def prop = PropertyDefinition.parse('title')

        then:
        prop.name == 'title'
        prop.type == 'String'  // default type
    }

    def 'should parse property specification with type'() {
        when:
        def prop = PropertyDefinition.parse('count:Integer')

        then:
        prop.name == 'count'
        prop.type == 'Integer'
    }

    def 'should throw exception for null property spec'() {
        when:
        PropertyDefinition.parse(null)

        then:
        thrown(IllegalArgumentException)
    }

    def 'should throw exception for empty property spec'() {
        when:
        PropertyDefinition.parse('')

        then:
        thrown(IllegalArgumentException)
    }

    def 'should validate valid property names'() {
        given:
        def prop = new PropertyDefinition(name: name, type: 'String')

        when:
        prop.validate()

        then:
        noExceptionThrown()

        where:
        name << ['title', 'firstName', 'count1']
    }

    @Unroll
    def "should reject invalid property name '#name'"() {
        given:
        def prop = new PropertyDefinition(name: name, type: 'String')

        when:
        prop.validate()

        then:
        thrown(IllegalArgumentException)

        where:
        name << ['Title', '1count', 'first_name', '', null]
    }

    def 'should validate built-in types'() {
        given:
        def prop = new PropertyDefinition(name: 'test', type: type)

        when:
        prop.validate()

        then:
        noExceptionThrown()
        prop.isBuiltinType()

        where:
        type << ['String', 'Integer', 'Long', 'Boolean', 'Date', 'BigDecimal', 'Double', 'Float']
    }

    def 'should accept custom types like enums and domain classes'() {
        given:
        def prop = new PropertyDefinition(name: 'test', type: type)

        when:
        prop.validate()

        then:
        noExceptionThrown()
        !prop.isBuiltinType()

        where:
        type << ['Status', 'Author', 'BookCategory', 'UUID', 'LocalDate', 'LocalDateTime']
    }

    def 'should accept fully qualified type names'() {
        given:
        def prop = new PropertyDefinition(name: 'test', type: type)

        when:
        prop.validate()

        then:
        noExceptionThrown()

        where:
        type << ['java.util.Date', 'java.time.LocalDate', 'com.example.Status']
    }

    @Unroll
    def "should reject invalid type name '#type'"() {
        given:
        def prop = new PropertyDefinition(name: 'test', type: type)

        when:
        prop.validate()

        then:
        thrown(IllegalArgumentException)

        where:
        type << ['string', 'integer', '123Type', '', null]
    }

    def 'should reject blank constraint for non-String type'() {
        given:
        def prop = new PropertyDefinition(name: 'count', type: 'Integer', blank: false)

        when:
        prop.validate()

        then:
        thrown(IllegalArgumentException)
    }

    def 'should reject maxSize constraint for non-String type'() {
        given:
        def prop = new PropertyDefinition(name: 'count', type: 'Integer', maxSize: 255)

        when:
        prop.validate()

        then:
        thrown(IllegalArgumentException)
    }

    def 'should generate property declaration'() {
        given:
        def prop = new PropertyDefinition(name: 'title', type: 'String')

        expect:
        prop.toDeclaration() == 'String title'
    }

    def 'should generate constraint line with all constraints'() {
        given:
        def prop = new PropertyDefinition(
            name: 'title',
            type: 'String',
            nullable: false,
            blank: false,
            maxSize: 255
        )

        expect:
        prop.toConstraintLine() == 'title nullable: false, blank: false, maxSize: 255'
    }

    def 'should return null constraint line when no constraints'() {
        given:
        def prop = new PropertyDefinition(name: 'title', type: 'String')

        expect:
        prop.toConstraintLine() == null
    }

    def 'should generate constraint line with nullable only'() {
        given:
        def prop = new PropertyDefinition(name: 'description', type: 'String', nullable: true)

        expect:
        prop.toConstraintLine() == 'description nullable: true'
    }

    def 'should use builder pattern'() {
        when:
        def prop = PropertyDefinition.builder()
            .name('title')
            .type('String')
            .nullable(false)
            .blank(false)
            .maxSize(255)
            .build()

        then:
        prop.name == 'title'
        prop.type == 'String'
        prop.nullable == false
        prop.blank == false
        prop.maxSize == 255
    }

    // Jakarta Validation annotation tests

    def 'should generate NotNull annotation for nullable false'() {
        given:
        def prop = new PropertyDefinition(name: 'title', type: 'String', nullable: false)

        expect:
        prop.toAnnotations() == ['@NotNull']
    }

    def 'should generate NotBlank annotation for blank false on String'() {
        given:
        def prop = new PropertyDefinition(name: 'title', type: 'String', blank: false)

        expect:
        prop.toAnnotations() == ['@NotBlank']
    }

    def 'should generate Size annotation for maxSize'() {
        given:
        def prop = new PropertyDefinition(name: 'title', type: 'String', maxSize: 255)

        expect:
        prop.toAnnotations() == ['@Size(max = 255)']
    }

    def 'should generate Size annotation with min and max'() {
        given:
        def prop = new PropertyDefinition(name: 'title', type: 'String', minSize: 5, maxSize: 255)

        expect:
        prop.toAnnotations() == ['@Size(min = 5, max = 255)']
    }

    def 'should generate multiple annotations'() {
        given:
        def prop = new PropertyDefinition(
            name: 'title',
            type: 'String',
            nullable: false,
            blank: false,
            maxSize: 255
        )

        expect:
        prop.toAnnotations() == ['@NotNull', '@NotBlank', '@Size(max = 255)']
    }

    def 'should return empty annotations when no constraints'() {
        given:
        def prop = new PropertyDefinition(name: 'title', type: 'String')

        expect:
        prop.toAnnotations() == []
    }

    def 'should return empty annotations when nullable is true'() {
        given:
        def prop = new PropertyDefinition(name: 'title', type: 'String', nullable: true)

        expect:
        prop.toAnnotations() == []
    }

    def 'should get required imports for NotNull'() {
        given:
        def prop = new PropertyDefinition(name: 'title', type: 'String', nullable: false)

        expect:
        prop.getRequiredImports() == ['jakarta.validation.constraints.NotNull'] as Set
    }

    def 'should get required imports for multiple annotations'() {
        given:
        def prop = new PropertyDefinition(
            name: 'title',
            type: 'String',
            nullable: false,
            blank: false,
            maxSize: 255
        )

        expect:
        prop.getRequiredImports() == [
            'jakarta.validation.constraints.NotNull',
            'jakarta.validation.constraints.NotBlank',
            'jakarta.validation.constraints.Size'
        ] as Set
    }

    def 'should default to GRAILS constraint style'() {
        given:
        def prop = new PropertyDefinition(name: 'title', type: 'String')

        expect:
        prop.constraintStyle == AbstractMemberDefinition.ConstraintStyle.GRAILS
        prop.usesGrailsConstraints()
        !prop.usesJakartaAnnotations()
    }

    def 'should support JAKARTA constraint style'() {
        given:
        def prop = new PropertyDefinition(
            name: 'title',
            type: 'String',
            constraintStyle: AbstractMemberDefinition.ConstraintStyle.JAKARTA
        )

        expect:
        !prop.usesGrailsConstraints()
        prop.usesJakartaAnnotations()
    }

    def 'should support BOTH constraint style'() {
        given:
        def prop = new PropertyDefinition(
            name: 'title',
            type: 'String',
            constraintStyle: AbstractMemberDefinition.ConstraintStyle.BOTH
        )

        expect:
        prop.usesGrailsConstraints()
        prop.usesJakartaAnnotations()
    }

    def 'should use builder with constraint style'() {
        when:
        def prop = PropertyDefinition.builder()
            .name('title')
            .type('String')
            .nullable(false)
            .constraintStyle(AbstractMemberDefinition.ConstraintStyle.JAKARTA)
            .build()

        then:
        prop.constraintStyle == AbstractMemberDefinition.ConstraintStyle.JAKARTA
    }
}
