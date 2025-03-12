package grails.gorm.tests

/**
 * Test session properties
 */
class SessionPropertiesSpec extends GormDatastoreSpec {

    void "test session properties"() {
        when:
        session.setSessionProperty('Hello', 'World')
        then:
        session.getSessionProperty('Hello') == 'World'
        session.getSessionProperty('World') == null

        when:
        session.setSessionProperty('One', 'Two')
        then:
        session.getSessionProperty('Hello') == 'World'
        session.getSessionProperty('One') == 'Two'

        when:
        def old = session.setSessionProperty('One', 'Three')
        then:
        session.getSessionProperty('Hello') == 'World'
        session.getSessionProperty('One') == 'Three'
        old == 'Two'

        when:"Clearing the session doesn't clear the properties"
        session.clear()
        then:
        session.getSessionProperty('Hello') == 'World'
        session.getSessionProperty('One') == 'Three'

        when:
        old = session.clearSessionProperty('Hello')
        then:
        session.getSessionProperty('Hello') == null
        session.getSessionProperty('One') == 'Three'
        old == 'World'
    }
}
