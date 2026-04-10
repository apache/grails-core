package org.grails.orm.hibernate.cfg.domainbinding.hibernate

import org.hibernate.type.StandardBasicTypes
import spock.lang.Specification

class HibernateToManyCollectionPropertySpec extends Specification {

    void "test getElementTypeName uses componentType when available"() {
        given:
        def property = Spy(HibernateToManyCollectionProperty)
        property.getComponentType() >> String.class
        property.getTypeName(String.class) >> "custom_string_type"

        expect:
        property.getElementTypeName() == "custom_string_type"
    }

    void "test getElementTypeName falls back to getTypeName when componentType is null"() {
        given:
        def property = Spy(HibernateToManyCollectionProperty)
        property.getComponentType() >> null
        property.getTypeName() >> "fallback_type"

        expect:
        property.getElementTypeName() == "fallback_type"
    }

    void "test getElementTypeName falls back to STRING when typeName is null"() {
        given:
        def property = Spy(HibernateToManyCollectionProperty)
        property.getComponentType() >> null
        property.getTypeName() >> null

        expect:
        property.getElementTypeName() == StandardBasicTypes.STRING.getName()
    }

    void "test getElementTypeName falls back to STRING when typeName is Object"() {
        given:
        def property = Spy(HibernateToManyCollectionProperty)
        property.getComponentType() >> Object.class
        property.getTypeName(Object.class) >> Object.class.name

        expect:
        property.getElementTypeName() == StandardBasicTypes.STRING.getName()
    }
}
