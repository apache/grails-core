package org.grails.orm.hibernate.cfg.domainbinding

import spock.lang.Specification
import spock.lang.Unroll

class GrailsEnumTypeSpec extends Specification {

    @Unroll
    def "should return correct type for #enumConstant"() {
        expect:
        enumConstant.getType() == expectedType

        where:
        enumConstant            | expectedType
        GrailsEnumType.DEFAULT  | "default"
        GrailsEnumType.STRING   | "string"
        GrailsEnumType.ORDINAL  | "ordinal"
        GrailsEnumType.IDENTITY | "identity"
    }

    def "should have all expected enum constants"() {
        expect:
        GrailsEnumType.values().length == 4
        GrailsEnumType.valueOf("DEFAULT") == GrailsEnumType.DEFAULT
        GrailsEnumType.valueOf("STRING") == GrailsEnumType.STRING
        GrailsEnumType.valueOf("ORDINAL") == GrailsEnumType.ORDINAL
        GrailsEnumType.valueOf("IDENTITY") == GrailsEnumType.IDENTITY
    }
}
