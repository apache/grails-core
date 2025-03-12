package grails.gorm.tck

import grails.gorm.DetachedCriteria
import grails.gorm.tests.GormDatastoreSpec
import grails.gorm.tck.TestEntity
import org.grails.datastore.mapping.query.event.AbstractQueryEvent
import org.grails.datastore.mapping.query.event.PostQueryEvent
import org.grails.datastore.mapping.query.event.PreQueryEvent
import org.springframework.context.ApplicationEvent
import org.springframework.context.event.SmartApplicationListener
import spock.lang.Ignore

/**
 * Tests for query events.
 */
@Ignore('''
Cannot invoke method addApplicationListener() on null object
java.lang.NullPointerException: Cannot invoke method addApplicationListener() on null object
    at grails.gorm.tck.QueryEventsSpec.setup(QueryEventsSpec.groovy)
''')
class QueryEventsSpec extends GormDatastoreSpec {
    SpecQueryEventListener listener

    @Override
    List getDomainClasses() {
        [Simples]
    }

    def setup() {
        listener = new SpecQueryEventListener()
        session.datastore.applicationContext.addApplicationListener(listener)
    }

    void "pre-events are fired before queries are run"() {
        when:
        TestEntity.findByName 'bob'
        then:
        listener.events.size() >= 1
        listener.events[0] instanceof PreQueryEvent
        listener.events[0].query != null
        listener.PreExecution == 1

        when:
        TestEntity.where {name == 'bob'}.list()
        then:
        listener.PreExecution == 2

        when:
        new DetachedCriteria(TestEntity).build({name == 'bob'}).list()
        then:
        listener.PreExecution == 3
    }

    void "post-events are fired after queries are run"() {
        given:
        def entity = new TestEntity(name: 'bob').save(flush: true)
        new TestEntity(name: 'mark').save(flush: true)

        when:
        TestEntity.findByName 'bob'
        then:
        listener.events.size() >= 1
        listener.events[1] instanceof PostQueryEvent
        listener.events[1].query != null
        listener.events[1].query == listener.events[0].query
        listener.events[1].results instanceof List
        listener.events[1].results.size() == 1
        listener.events[1].results[0] == entity
        listener.PostExecution == 1

        when:
        TestEntity.where {name == 'bob'}.list()
        then:
        listener.PostExecution == 2

        when:
        new DetachedCriteria(TestEntity).build({name == 'bob'}).list()
        then:
        listener.PostExecution == 3
    }

    static class SpecQueryEventListener implements SmartApplicationListener {

        List<AbstractQueryEvent> events = []

        int PreExecution,
            PostExecution

        @Override
        void onApplicationEvent(ApplicationEvent event) {
            AbstractQueryEvent e = event as AbstractQueryEvent
            def typeName = e.eventType.name()
            this."$typeName"++
            events << event
        }

        @Override
        boolean supportsSourceType(Class<?> sourceType) {
            return true
        }

        @Override
        int getOrder() {
            Integer.MAX_VALUE / 2
        }

        @Override
        boolean supportsEventType(Class<? extends ApplicationEvent> eventType) {
            return eventType in [PreQueryEvent, PostQueryEvent]
        }
    }
}

