package org.grails.orm.hibernate.cfg

import grails.gorm.annotation.Entity
import grails.gorm.specs.HibernateGormDatastoreSpec
import spock.lang.Unroll

/**
 * Specification for GORM Mapping features, specifically for composite ID detection.
 */
class MappingSpec extends HibernateGormDatastoreSpec {

    @Unroll
    void "test isCompositeIdProperty should return #expectedResult for #description"() {
        given: "A persistent entity and its mapping"
        def binder = grailsDomainBinder
        // Ensure all related entities are processed by the mapping context
        createPersistentEntity(Author, binder)
        def entity = createPersistentEntity(domainClass, binder)
        def mapping = (Mapping) entity.getMappedForm()
        def property = entity.getPropertyByName(propertyName)

        when: "The method is called on the property itself"
        def resultProperty = property.isCompositeIdProperty()

        then: "The results are as expected"
        resultProperty == expectedResult

        where:
        description                               | domainClass       | propertyName | expectedResult
        "a property that is part of a composite id" | CompositeIdBook   | 'title'      | true
        "another property in the composite id"      | CompositeIdBook   | 'author'     | true
        "a property not in the composite id"        | CompositeIdBook   | 'pageCount'  | false
        "a property from a simple id class"         | SimpleIdBook      | 'title'      | false
    }

    @Unroll
    void "test isIdentityProperty should return #expectedResult for #description"() {
        given: "A persistent entity and its property"
        def binder = grailsDomainBinder
        def entity = createPersistentEntity(domainClass, binder)
        def property = entity.getPropertyByName(propertyName)

        when: "The method is called on the property itself"
        def resultProperty = property.isIdentityProperty()

        then: "The result is as expected"
        resultProperty == expectedResult

        where:
        description                        | domainClass     | propertyName | expectedResult
        "the identity property"            | SimpleIdBook    | 'id'         | true
        "a non-identity property"          | SimpleIdBook    | 'title'      | false
        "the identity in composite entity" | CompositeIdBook | 'id'         | true
        "a property in composite identity" | CompositeIdBook | 'title'      | false
    }

}

// --- Test Domain Classes ---
// These are top-level, non-static classes to ensure they are
// correctly discovered and processed by the GORM testing framework.

@Entity
class Author {
    String name
}

@Entity
class CompositeIdBook {
    String title
    Author author
    Integer pageCount

    static mapping = {
        id composite: ['title', 'author']
    }
}

@Entity
class SimpleIdBook {
    String title
}