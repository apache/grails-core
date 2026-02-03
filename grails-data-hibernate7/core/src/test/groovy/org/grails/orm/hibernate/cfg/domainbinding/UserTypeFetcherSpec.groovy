package org.grails.orm.hibernate.cfg.domainbinding

import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.orm.hibernate.cfg.GrailsHibernatePersistentProperty
import org.grails.orm.hibernate.cfg.PropertyConfig
import spock.lang.Specification
import spock.lang.Subject

class UserTypeFetcherSpec extends Specification {

    @Subject
    UserTypeFetcher fetcher = new UserTypeFetcher()

    def "should return user type when it is already a Class"() {
        given:
        def persistentProperty = Mock(GrailsHibernatePersistentProperty)
        def config = new PropertyConfig()
        config.setType(String)
        
        persistentProperty.getMappedForm() >> config

        when:
        def result = fetcher.getUserType(persistentProperty)

        then:
        result == String
    }

    def "should return user type when it is a valid class name string"() {
        given:
        def persistentProperty = Mock(GrailsHibernatePersistentProperty)
        def config = new PropertyConfig()
        config.setType("java.lang.Integer")
        
        persistentProperty.getMappedForm() >> config

        when:
        def result = fetcher.getUserType(persistentProperty)

        then:
        result == Integer
    }

    def "should return null if class name is invalid"() {
        given:
        def persistentProperty = Mock(GrailsHibernatePersistentProperty)
        def config = new PropertyConfig()
        config.setType("com.nonexistent.MyType")
        
        persistentProperty.getMappedForm() >> config

        when:
        def result = fetcher.getUserType(persistentProperty)

        then:
        result == null
    }

    def "should return null if type object is null"() {
        given:
        def persistentProperty = Mock(GrailsHibernatePersistentProperty)
        def config = new PropertyConfig()
        config.setType(null)
        
        persistentProperty.getMappedForm() >> config

        when:
        def result = fetcher.getUserType(persistentProperty)

        then:
        result == null
    }
}
