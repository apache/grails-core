package org.grails.gorm.graphql.types.scalars.coercing

import graphql.language.ArrayValue
import graphql.language.StringValue
import spock.lang.Specification

class CharacterArrayCoercionSpec extends Specification {

    void "test parseLiteral"() {
        given:
        def value = new ArrayValue([new StringValue('x'), new StringValue('y'), new StringValue('z'), new StringValue('a')])
        CharacterArrayCoercion coercion = new CharacterArrayCoercion()

        when:
        def result = coercion.parseLiteral(value)

        then:
        result == ['x', 'y', 'z', 'a'] as Character[]

        when: 'the values are too long for Character'
        result = coercion.parseLiteral(new ArrayValue([new StringValue('xy'), new StringValue('a')]))

        then:
        result == [null, 'a'] as Character[]
    }
}
