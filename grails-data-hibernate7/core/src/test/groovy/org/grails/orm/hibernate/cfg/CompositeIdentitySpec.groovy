package org.grails.orm.hibernate.cfg

import org.hibernate.MappingException
import spock.lang.Specification
import org.grails.orm.hibernate.cfg.GrailsHibernatePersistentEntity
import org.grails.orm.hibernate.cfg.GrailsHibernatePersistentProperty

class CompositeIdentitySpec extends Specification {

    def "test getHibernateProperties with property names"() {
        given:
        def domainClass = Mock(GrailsHibernatePersistentEntity)
        def prop1 = Mock(GrailsHibernatePersistentProperty)
        def prop2 = Mock(GrailsHibernatePersistentProperty)
        def compositeIdentity = new CompositeIdentity(propertyNames: ['prop1', 'prop2'] as String[])

        when:
        def properties = compositeIdentity.getHibernateProperties(domainClass)

        then:
        1 * domainClass.getPropertyByName("prop1") >> prop1
        1 * domainClass.getPropertyByName("prop2") >> prop2
        properties.length == 2
        properties[0] == prop1
        properties[1] == prop2
    }

    def "test getHibernateProperties with fallback to domain class"() {
        given:
        def domainClass = Mock(GrailsHibernatePersistentEntity)
        def prop1 = Mock(GrailsHibernatePersistentProperty)
        def compositeIdentity = new CompositeIdentity()

        when:
        def properties = compositeIdentity.getHibernateProperties(domainClass)

        then:
        1 * domainClass.getCompositeIdentity() >> ([prop1] as GrailsHibernatePersistentProperty[])
        0 * domainClass.getPropertyByName(_)
        properties.length == 1
        properties[0] == prop1
    }

    def "test getHibernateProperties throws exception if no properties found"() {
        given:
        def domainClass = Mock(GrailsHibernatePersistentEntity)
        def compositeIdentity = new CompositeIdentity()

        when:
        compositeIdentity.getHibernateProperties(domainClass)

        then:
        1 * domainClass.getCompositeIdentity() >> null
        thrown(MappingException)
    }

    def "test getHibernateProperties throws exception if a property is invalid"() {
        given:
        def domainClass = Mock(GrailsHibernatePersistentEntity)
        def compositeIdentity = new CompositeIdentity(propertyNames: ['invalid'] as String[])

        when:
        compositeIdentity.getHibernateProperties(domainClass)

        then:
        1 * domainClass.getPropertyByName("invalid") >> null
        thrown(MappingException)
    }
}
