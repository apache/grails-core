package org.grails.orm.hibernate.cfg.domainbinding

import grails.gorm.specs.HibernateGormDatastoreSpec
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.datastore.mapping.model.types.Association
import org.hibernate.mapping.ManyToOne
import org.hibernate.mapping.Property
import org.hibernate.mapping.Table
import org.hibernate.mapping.Value
import spock.lang.Subject

class BidirectionalManyToOneWithListMappingSpec extends HibernateGormDatastoreSpec {

    @Subject
    BidirectionalManyToOneWithListMapping checker = new BidirectionalManyToOneWithListMapping()

    def "should return true for a bidirectional many-to-one with list mapping"() {
        given:
        def inverseSide = Mock(Association)
        inverseSide.getType() >> List
        
        def grailsProperty = Mock(Association)
        grailsProperty.isBidirectional() >> true
        grailsProperty.getInverseSide() >> inverseSide
        
        def table = new Table("test")
        def manyToOne = new ManyToOne(getGrailsDomainBinder().getMetadataBuildingContext(), table)
        def prop = new Property()
        prop.setValue(manyToOne)

        when:
        boolean result = checker.isBidirectionalManyToOneWithListMapping(grailsProperty, prop)

        then:
        result == true
    }

    def "should return false if not an association"() {
        given:
        def grailsProperty = Mock(PersistentProperty)
        def prop = new Property()

        when:
        boolean result = checker.isBidirectionalManyToOneWithListMapping(grailsProperty, prop)

        then:
        result == false
    }

    def "should return false if not bidirectional"() {
        given:
        def grailsProperty = Mock(Association)
        grailsProperty.isBidirectional() >> false
        def prop = new Property()

        when:
        boolean result = checker.isBidirectionalManyToOneWithListMapping(grailsProperty, prop)

        then:
        result == false
    }

    def "should return false if inverse side is null"() {
        given:
        def grailsProperty = Mock(Association)
        grailsProperty.isBidirectional() >> true
        grailsProperty.getInverseSide() >> null
        def prop = new Property()

        when:
        boolean result = checker.isBidirectionalManyToOneWithListMapping(grailsProperty, prop)

        then:
        result == false
    }

    def "should return false if inverse side is not a list"() {
        given:
        def inverseSide = Mock(Association)
        inverseSide.getType() >> Set
        
        def grailsProperty = Mock(Association)
        grailsProperty.isBidirectional() >> true
        grailsProperty.getInverseSide() >> inverseSide
        def prop = new Property()

        when:
        boolean result = checker.isBidirectionalManyToOneWithListMapping(grailsProperty, prop)

        then:
        result == false
    }

    def "should return false if hibernate property value is not many-to-one"() {
        given:
        def inverseSide = Mock(Association)
        inverseSide.getType() >> List
        
        def grailsProperty = Mock(Association)
        grailsProperty.isBidirectional() >> true
        grailsProperty.getInverseSide() >> inverseSide
        
        def otherValue = Mock(Value)
        def prop = new Property()
        prop.setValue(otherValue)

        when:
        boolean result = checker.isBidirectionalManyToOneWithListMapping(grailsProperty, prop)

        then:
        result == false
    }

    def "should return false if hibernate property is null"() {
        given:
        def inverseSide = Mock(Association)
        inverseSide.getType() >> List
        
        def grailsProperty = Mock(Association)
        grailsProperty.isBidirectional() >> true
        grailsProperty.getInverseSide() >> inverseSide

        when:
        boolean result = checker.isBidirectionalManyToOneWithListMapping(grailsProperty, null)

        then:
        result == false
    }
}
