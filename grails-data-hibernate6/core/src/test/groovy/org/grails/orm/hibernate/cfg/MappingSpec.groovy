package org.grails.orm.hibernate.cfg

import grails.gorm.annotation.Entity
import grails.gorm.specs.HibernateGormDatastoreSpec
import org.grails.orm.hibernate.cfg.domainbinding.HibernateEntityWrapper
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
        def mapping = new HibernateEntityWrapper(entity).getMappedForm()
        def property = entity.getPropertyByName(propertyName)

        when: "The method is called on the mapping object"
        def result = mapping.isCompositeIdProperty(property)

        then: "The result is as expected"
        result == expectedResult

        where:
        description                               | domainClass       | propertyName | expectedResult
        "a property that is part of a composite id" | CompositeIdBook   | 'title'      | true
        "another property in the composite id"      | CompositeIdBook   | 'author'     | true
        "a property not in the composite id"        | CompositeIdBook   | 'pageCount'  | false
        "a property from a simple id class"         | SimpleIdBook      | 'title'      | false
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