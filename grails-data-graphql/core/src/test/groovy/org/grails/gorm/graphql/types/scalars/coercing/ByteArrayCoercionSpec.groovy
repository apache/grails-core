package org.grails.gorm.graphql.types.scalars.coercing

import graphql.language.ArrayValue
import graphql.language.IntValue
import spock.lang.Specification

class ByteArrayCoercionSpec extends Specification {

    void "test parseLiteral"() {
        given:
        def value = new ArrayValue([new IntValue(BigInteger.valueOf(1)), new IntValue(BigInteger.valueOf(22)), new IntValue(BigInteger.valueOf(32)), new IntValue(BigInteger.valueOf(54))])
        ByteArrayCoercion coercion = new ByteArrayCoercion()

        when:
        def result = coercion.parseLiteral(value)

        then:
        result == [1,22,32,54] as Byte[]
    }
}
