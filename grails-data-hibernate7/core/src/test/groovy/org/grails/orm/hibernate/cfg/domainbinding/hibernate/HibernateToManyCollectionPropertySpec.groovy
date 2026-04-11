package org.grails.orm.hibernate.cfg.domainbinding.hibernate

import org.hibernate.type.StandardBasicTypes
import org.hibernate.boot.model.naming.ImplicitNamingStrategy
import org.hibernate.boot.model.naming.ImplicitNamingStrategyJpaCompliantImpl
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

    void "test getRole with path"() {
        given:
        def property = Spy(HibernateToManyCollectionProperty)
        property.getOwner() >> [getName: { "MyEntity" }] as org.grails.datastore.mapping.model.PersistentEntity
        property.getName() >> "tags"

        expect:
        property.getRole("foo.bar") == "MyEntity.foo.bar.tags"
        property.getRole(null) == "MyEntity.tags"
    }

    void "test getIndexColumnName fallback"() {
        given:
        def property = Spy(HibernateToManyCollectionProperty)
        property.getName() >> "tags"
        def namingStrategy = ImplicitNamingStrategyJpaCompliantImpl.INSTANCE

        expect:
        // By default ImplicitNamingStrategyJpaCompliantImpl uses the property name directly for the index
        // but HibernateToManyProperty adds _idx suffix in the default implementation
        property.getIndexColumnName(namingStrategy) == "tags_idx"
    }

    void "test getMapElementName fallback"() {
        given:
        def property = Spy(HibernateToManyCollectionProperty)
        property.getName() >> "tags"
        def namingStrategy = ImplicitNamingStrategyJpaCompliantImpl.INSTANCE

        expect:
        property.getMapElementName(namingStrategy) == "tags_elt"
    }
}
