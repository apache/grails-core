package org.grails.orm.hibernate.cfg.domainbinding

import org.grails.orm.hibernate.cfg.HibernateToManyProperty
import spock.lang.Specification

class ShouldCollectionBindWithJoinColumnSpec extends Specification {

    def "returns true when property is unidirectional one-to-many"() {
        given:
        def calc = new ShouldCollectionBindWithJoinColumn()
        HibernateToManyProperty prop = Mock(HibernateToManyProperty) {
            isUnidirectionalOneToMany() >> true // Test the unidirectional path
        }

        expect:
        calc.apply(prop)
    }

    def "returns false when property is not unidirectional one-to-many and not Basic"() {
        given:
        def calc = new ShouldCollectionBindWithJoinColumn()
        // Mocking HibernateToManyProperty, and isUnidirectionalOneToMany is false.
        // The 'property instanceof Basic' check will be false for a mock of HibernateToManyProperty.
        // Therefore, the result should be false.
        HibernateToManyProperty prop = Mock(HibernateToManyProperty) {
            isUnidirectionalOneToMany() >> false // Test the non-unidirectional path
        }

        expect:
        !calc.apply(prop)
    }

    def "returns false when property is null"() {
        given:
        def calc = new ShouldCollectionBindWithJoinColumn()

        expect:
        !calc.apply(null)
    }
}
