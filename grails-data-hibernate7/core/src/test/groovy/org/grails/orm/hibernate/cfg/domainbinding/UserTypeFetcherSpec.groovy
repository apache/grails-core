package org.grails.orm.hibernate.cfg.domainbinding

import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.orm.hibernate.cfg.PropertyConfig
import spock.lang.Specification
import spock.lang.Subject

class UserTypeFetcherSpec extends Specification {

    def mapper = Mock(PersistentPropertyToPropertyConfig)
    
    @Subject
    UserTypeFetcher fetcher = new UserTypeFetcher(mapper)

    def "should return user type when it is already a Class"() {
        given:
        def persistentProperty = Mock(PersistentProperty)
        def config = new PropertyConfig()
        config.setType(String)
        
        mapper.toPropertyConfig(persistentProperty) >> config

        when:
        def result = fetcher.getUserType(persistentProperty)

        then:
        result == String
    }

    def "should return user type when it is a valid class name string"() {
        given:
        def persistentProperty = Mock(PersistentProperty)
        def config = new PropertyConfig()
        config.setType("java.lang.Integer")
        
        mapper.toPropertyConfig(persistentProperty) >> config

        when:
        def result = fetcher.getUserType(persistentProperty)

        then:
        result == Integer
    }

    def "should return null if class name is invalid"() {
        given:
        def persistentProperty = Mock(PersistentProperty)
        def config = new PropertyConfig()
        config.setType("com.nonexistent.MyType")
        
        mapper.toPropertyConfig(persistentProperty) >> config

        when:
        def result = fetcher.getUserType(persistentProperty)

        then:
        result == null
    }

    def "should return null if type object is null"() {
        given:
        def persistentProperty = Mock(PersistentProperty)
        def config = new PropertyConfig()
        config.setType(null)
        
        mapper.toPropertyConfig(persistentProperty) >> config

        when:
        def result = fetcher.getUserType(persistentProperty)

        then:
        result == null
    }
}
