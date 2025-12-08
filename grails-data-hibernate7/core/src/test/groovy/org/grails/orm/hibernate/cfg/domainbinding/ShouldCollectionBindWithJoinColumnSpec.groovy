package org.grails.orm.hibernate.cfg.domainbinding

import org.grails.datastore.mapping.model.types.Basic
import org.grails.datastore.mapping.model.types.ToMany
import spock.lang.Specification

class ShouldCollectionBindWithJoinColumnSpec extends Specification {

    def "returns true when property is unidirectional one-to-many"() {
        given:
        def calc = new ShouldCollectionBindWithJoinColumn()
        ToMany prop = Mock(ToMany) {
            isUnidirectionalOneToMany() >> true
        }

        expect:
        calc.apply(prop)
    }

    def "returns true when property is Basic collection"() {
        given:
        def calc = new ShouldCollectionBindWithJoinColumn()
        // Basic in datastore implements ToMany, so a Basic mock should be acceptable
        Basic prop = Mock(Basic) {
            isUnidirectionalOneToMany() >> false
        }

        expect:
        calc.apply(prop as ToMany)
    }

    def "returns false when not unidirectional and not Basic"() {
        given:
        def calc = new ShouldCollectionBindWithJoinColumn()
        ToMany prop = Mock(ToMany) {
            isUnidirectionalOneToMany() >> false
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

