package org.grails.orm.hibernate.cfg.domainbinding

import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.orm.hibernate.cfg.PropertyConfig
import org.hibernate.MappingException
import spock.lang.Specification
import spock.lang.Subject

class PersistentPropertyToPropertyConfigSpec extends Specification {

    @Subject
    PersistentPropertyToPropertyConfig mapper = new PersistentPropertyToPropertyConfig()

    def "should return PropertyConfig when mappedForm is present"() {
        given:
        def propertyConfig = new PropertyConfig()
        def persistentProperty = Mock(PersistentProperty)
        persistentProperty.getMappedForm() >> propertyConfig

        when:
        def result = mapper.toPropertyConfig(persistentProperty)

        then:
        result == propertyConfig
    }

    def "should throw MappingException when mappedForm is null"() {
        given:
        def persistentProperty = Mock(PersistentProperty)
        persistentProperty.getMappedForm() >> null

        when:
        mapper.toPropertyConfig(persistentProperty)

        then:
        thrown(MappingException)
    }
}
