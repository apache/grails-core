package org.grails.datastore.gorm.timestamp

import groovy.transform.InheritConstructors
import org.grails.datastore.gorm.events.AutoTimestampEventListener
import org.grails.datastore.mapping.core.Datastore
import org.grails.datastore.mapping.model.MappingContext
import spock.lang.Specification

class AutoTimestampEventListenerSpec extends Specification {

    TestEventListener listener
    Map<String, Optional<Set<String>>> lastUpdatedBaseState = [:]
    Map<String, Optional<Set<String>>> dateCreatedBaseState = [:]

    void setup() {
        listener = new TestEventListener(Stub(Datastore) {
            getMappingContext() >> null
        })
        [Foo, Bar, FooBar].each {
            lastUpdatedBaseState.put(it.getName(), Optional.of(['lastUpdated'] as Set<String>))
            dateCreatedBaseState.put(it.getName(), Optional.of(['dateCreated'] as Set<String>))
        }
    }

    void updateBaseStates() {
        listener.dateCreated.entrySet().each {
            dateCreatedBaseState.put(it.key, it.value)
        }
        listener.lastUpdated.entrySet().each {
            lastUpdatedBaseState.put(it.key, it.value)
        }
    }

    void "test withoutLastUpdated"() {
        when:
        listener.withoutLastUpdated {
            updateBaseStates()
        }

        then:
        lastUpdatedBaseState[Foo.getName()].isEmpty()
        lastUpdatedBaseState[Bar.getName()].isEmpty()
        lastUpdatedBaseState[FooBar.getName()].isEmpty()
        dateCreatedBaseState[Foo.getName()].isPresent()
        dateCreatedBaseState[Bar.getName()].isPresent()
        dateCreatedBaseState[FooBar.getName()].isPresent()

        when:
        updateBaseStates()

        then:
        lastUpdatedBaseState[Foo.getName()].isPresent()
        lastUpdatedBaseState[Bar.getName()].isPresent()
        lastUpdatedBaseState[FooBar.getName()].isPresent()
        dateCreatedBaseState[Foo.getName()].isPresent()
        dateCreatedBaseState[Bar.getName()].isPresent()
        dateCreatedBaseState[FooBar.getName()].isPresent()
    }

    void "test withoutLastUpdated(Class)"() {
        when:
        listener.withoutLastUpdated(Bar) {
            updateBaseStates()
        }

        then:
        lastUpdatedBaseState[Foo.getName()].isPresent()
        lastUpdatedBaseState[Bar.getName()].isEmpty()
        lastUpdatedBaseState[FooBar.getName()].isPresent()
        dateCreatedBaseState[Foo.getName()].isPresent()
        dateCreatedBaseState[Bar.getName()].isPresent()
        dateCreatedBaseState[FooBar.getName()].isPresent()

        when:
        updateBaseStates()

        then:
        lastUpdatedBaseState[Foo.getName()].isPresent()
        lastUpdatedBaseState[Bar.getName()].isPresent()
        lastUpdatedBaseState[FooBar.getName()].isPresent()
        dateCreatedBaseState[Foo.getName()].isPresent()
        dateCreatedBaseState[Bar.getName()].isPresent()
        dateCreatedBaseState[FooBar.getName()].isPresent()
    }

    void "test withoutLastUpdated(Class[])"() {
        when:
        listener.withoutLastUpdated([Bar, FooBar]) {
            updateBaseStates()
        }

        then:
        lastUpdatedBaseState[Foo.getName()].isPresent()
        lastUpdatedBaseState[Bar.getName()].isEmpty()
        lastUpdatedBaseState[FooBar.getName()].isEmpty()
        dateCreatedBaseState[Foo.getName()].isPresent()
        dateCreatedBaseState[Bar.getName()].isPresent()
        dateCreatedBaseState[FooBar.getName()].isPresent()

        when:
        updateBaseStates()

        then:
        lastUpdatedBaseState[Foo.getName()].isPresent()
        lastUpdatedBaseState[Bar.getName()].isPresent()
        lastUpdatedBaseState[FooBar.getName()].isPresent()
        dateCreatedBaseState[Foo.getName()].isPresent()
        dateCreatedBaseState[Bar.getName()].isPresent()
        dateCreatedBaseState[FooBar.getName()].isPresent()
    }

    void "test withoutDateCreated"() {
        when:
        listener.withoutDateCreated() {
            updateBaseStates()
        }

        then:
        lastUpdatedBaseState[Foo.getName()].isPresent()
        lastUpdatedBaseState[Bar.getName()].isPresent()
        lastUpdatedBaseState[FooBar.getName()].isPresent()
        dateCreatedBaseState[Foo.getName()].isEmpty()
        dateCreatedBaseState[Bar.getName()].isEmpty()
        dateCreatedBaseState[FooBar.getName()].isEmpty()

        when:
        updateBaseStates()

        then:
        lastUpdatedBaseState[Foo.getName()].isPresent()
        lastUpdatedBaseState[Bar.getName()].isPresent()
        lastUpdatedBaseState[FooBar.getName()].isPresent()
        dateCreatedBaseState[Foo.getName()].isPresent()
        dateCreatedBaseState[Bar.getName()].isPresent()
        dateCreatedBaseState[FooBar.getName()].isPresent()
    }

    void "test withoutDateCreated(Class)"() {
        when:
        listener.withoutDateCreated(Bar) {
            updateBaseStates()
        }

        then:
        lastUpdatedBaseState[Foo.getName()].isPresent()
        lastUpdatedBaseState[Bar.getName()].isPresent()
        lastUpdatedBaseState[FooBar.getName()].isPresent()
        dateCreatedBaseState[Foo.getName()].isPresent()
        dateCreatedBaseState[Bar.getName()].isEmpty()
        dateCreatedBaseState[FooBar.getName()].isPresent()

        when:
        updateBaseStates()

        then:
        lastUpdatedBaseState[Foo.getName()].isPresent()
        lastUpdatedBaseState[Bar.getName()].isPresent()
        lastUpdatedBaseState[FooBar.getName()].isPresent()
        dateCreatedBaseState[Foo.getName()].isPresent()
        dateCreatedBaseState[Bar.getName()].isPresent()
        dateCreatedBaseState[FooBar.getName()].isPresent()
    }

    void "test withoutDateCreated(Class[])"() {
        when:
        listener.withoutDateCreated([Bar, FooBar]) {
            updateBaseStates()
        }

        then:
        lastUpdatedBaseState[Foo.getName()].isPresent()
        lastUpdatedBaseState[Bar.getName()].isPresent()
        lastUpdatedBaseState[FooBar.getName()].isPresent()
        dateCreatedBaseState[Foo.getName()].isPresent()
        dateCreatedBaseState[Bar.getName()].isEmpty()
        dateCreatedBaseState[FooBar.getName()].isEmpty()

        when:
        updateBaseStates()

        then:
        lastUpdatedBaseState[Foo.getName()].isPresent()
        lastUpdatedBaseState[Bar.getName()].isPresent()
        lastUpdatedBaseState[FooBar.getName()].isPresent()
        dateCreatedBaseState[Foo.getName()].isPresent()
        dateCreatedBaseState[Bar.getName()].isPresent()
        dateCreatedBaseState[FooBar.getName()].isPresent()
    }


    void "test withoutTimestamps"() {
        when:
        listener.withoutTimestamps() {
            updateBaseStates()
        }

        then:
        lastUpdatedBaseState[Foo.getName()].isEmpty()
        lastUpdatedBaseState[Bar.getName()].isEmpty()
        lastUpdatedBaseState[FooBar.getName()].isEmpty()
        dateCreatedBaseState[Foo.getName()].isEmpty()
        dateCreatedBaseState[Bar.getName()].isEmpty()
        dateCreatedBaseState[FooBar.getName()].isEmpty()

        when:
        updateBaseStates()

        then:
        lastUpdatedBaseState[Foo.getName()].isPresent()
        lastUpdatedBaseState[Bar.getName()].isPresent()
        lastUpdatedBaseState[FooBar.getName()].isPresent()
        dateCreatedBaseState[Foo.getName()].isPresent()
        dateCreatedBaseState[Bar.getName()].isPresent()
        dateCreatedBaseState[FooBar.getName()].isPresent()
    }

    void "test withoutTimestamps(Class)"() {
        when:
        listener.withoutTimestamps(Bar) {
            updateBaseStates()
        }

        then:
        lastUpdatedBaseState[Foo.getName()].isPresent()
        lastUpdatedBaseState[Bar.getName()].isEmpty()
        lastUpdatedBaseState[FooBar.getName()].isPresent()
        dateCreatedBaseState[Foo.getName()].isPresent()
        dateCreatedBaseState[Bar.getName()].isEmpty()
        dateCreatedBaseState[FooBar.getName()].isPresent()

        when:
        updateBaseStates()

        then:
        lastUpdatedBaseState[Foo.getName()].isPresent()
        lastUpdatedBaseState[Bar.getName()].isPresent()
        lastUpdatedBaseState[FooBar.getName()].isPresent()
        dateCreatedBaseState[Foo.getName()].isPresent()
        dateCreatedBaseState[Bar.getName()].isPresent()
        dateCreatedBaseState[FooBar.getName()].isPresent()
    }

    void "test withoutTimestamps(Class[])"() {
        when:
        listener.withoutTimestamps([Bar, FooBar]) {
            updateBaseStates()
        }

        then:
        lastUpdatedBaseState[Foo.getName()].isPresent()
        lastUpdatedBaseState[Bar.getName()].isEmpty()
        lastUpdatedBaseState[FooBar.getName()].isEmpty()
        dateCreatedBaseState[Foo.getName()].isPresent()
        dateCreatedBaseState[Bar.getName()].isEmpty()
        dateCreatedBaseState[FooBar.getName()].isEmpty()

        when:
        updateBaseStates()

        then:
        lastUpdatedBaseState[Foo.getName()].isPresent()
        lastUpdatedBaseState[Bar.getName()].isPresent()
        lastUpdatedBaseState[FooBar.getName()].isPresent()
        dateCreatedBaseState[Foo.getName()].isPresent()
        dateCreatedBaseState[Bar.getName()].isPresent()
        dateCreatedBaseState[FooBar.getName()].isPresent()
    }
}

class Foo {

}

class Bar {

}

class FooBar {

}

@InheritConstructors
class TestEventListener extends AutoTimestampEventListener {

    Map<String, Optional<Set<String>>> getDateCreated() {
        this.entitiesWithDateCreated
    }
    Map<String, Optional<Set<String>>> getLastUpdated() {
        this.entitiesWithLastUpdated
    }

    protected void initForMappingContext(MappingContext mappingContext) {
        [Foo, Bar, FooBar].each {
            entitiesWithLastUpdated.put(it.getName(), Optional.of(['lastUpdated'] as Set<String>))
            entitiesWithDateCreated.put(it.getName(), Optional.of(['dateCreated'] as Set<String>))
        }
    }
}