package org.grails.datastore.mapping.core

import org.grails.datastore.mapping.model.MappingContext
import org.springframework.core.env.PropertyResolver
import spock.lang.Specification

class SessionResolverIntegrationSpec extends Specification {

    void "test session resolution through datastore"() {
        given:
        def datastore = new TestDatastore(Mock(MappingContext))
        def session = Mock(Session)
        
        // Ensure resolver is available
        def resolver = datastore.getSessionResolver()
        
        when:
        resolver.bind(session)
        
        then:
        resolver.resolve() == session
        
        when:
        resolver.unbind()
        
        then:
        resolver.resolve() == null
    }

    static class TestDatastore extends AbstractDatastore {
        TestDatastore(MappingContext mappingContext) {
            super(mappingContext)
            // Manually inject the resolver since we are testing the integration
            this.sessionResolver = new ThreadLocalSessionResolver<Session>()
        }

        @Override
        protected Session createSession(PropertyResolver connectionDetails) {
            return null
        }
    }
}
