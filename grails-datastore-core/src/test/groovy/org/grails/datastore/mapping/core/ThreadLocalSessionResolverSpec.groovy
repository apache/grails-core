package org.grails.datastore.mapping.core

import spock.lang.Specification

class ThreadLocalSessionResolverSpec extends Specification {

    ThreadLocalSessionResolver<Session> resolver = new ThreadLocalSessionResolver<>()

    def "should bind and resolve session"() {
        given:
        Session session = Mock(Session)

        when:
        resolver.bind(session)

        then:
        resolver.resolve() == session

        cleanup:
        resolver.unbind()
    }

    def "should bind and resolve qualified session"() {
        given:
        Session session = Mock(Session)
        String qualifier = "secondary"

        when:
        resolver.bind(qualifier, session)

        then:
        resolver.resolve(qualifier) == session

        cleanup:
        resolver.unbind(qualifier)
    }

    def "should unbind session"() {
        given:
        Session session = Mock(Session)
        resolver.bind(session)

        when:
        resolver.unbind()

        then:
        resolver.resolve() == null
    }
}
