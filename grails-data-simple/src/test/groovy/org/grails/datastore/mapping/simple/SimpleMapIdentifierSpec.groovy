/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.grails.datastore.mapping.simple

import grails.gorm.annotation.Entity

import org.grails.datastore.mapping.core.Session
import org.grails.datastore.mapping.model.ValueGenerator
import spock.lang.AutoCleanup
import spock.lang.Specification

/**
 * Identifiers the simple map datastore generates, through real sessions: they are unique across every
 * session of the datastore, not just within one.
 */
class SimpleMapIdentifierSpec extends Specification {

    @AutoCleanup
    SimpleMapDatastore datastore = new SimpleMapDatastore(IdThing, IdParent, IdChild, IdInteger, IdCustomGenerator, IdAssigned)

    void 'a datastore-specific generator does not prevent simple map persistence'() {
        given:
        def mapping = datastore.mappingContext.getPersistentEntity(IdCustomGenerator.name).mapping.identifier
        IdCustomGenerator thing = new IdCustomGenerator(name: 'custom')

        when:
        withSession { Session session ->
            session.persist(thing)
            session.flush()
        }

        then:
        mapping.generator == ValueGenerator.CUSTOM
        mapping.mappedForm.generator == 'example.CustomIdentifierGenerator'
        thing.id != null
        withSession { Session session -> session.retrieve(IdCustomGenerator, thing.id)?.name } == 'custom'
    }

    void 'built-in assigned generators retain their strategy and supplied identifier'() {
        given:
        IdAssigned thing = new IdAssigned(id: 'supplied-id', name: 'assigned')

        when:
        withSession { Session session ->
            session.persist(thing)
            session.flush()
        }

        then:
        datastore.mappingContext.getPersistentEntity(IdAssigned.name).mapping.identifier.generator == ValueGenerator.ASSIGNED
        thing.id == 'supplied-id'
        withSession { Session session -> session.retrieve(IdAssigned, 'supplied-id')?.name } == 'assigned'
    }

    void 'two sessions open at once give their inserts different identifiers'() {
        given:
        Session first = datastore.connect()
        Session second = datastore.connect()

        when: 'each queues an insert, and the second flushes before the first'
        IdThing queued = new IdThing(name: 'first')
        first.persist(queued)
        IdThing flushed = new IdThing(name: 'second')
        second.persist(flushed)
        second.flush()
        first.flush()

        then: 'neither overwrote the other'
        queued.id != flushed.id
        names() == ['first', 'second']

        cleanup:
        first?.disconnect()
        second?.disconnect()
    }

    void 'an insert in a new session after a delete does not reuse a live identifier'() {
        given: 'three things, the first of them deleted'
        withSession { Session session ->
            List<IdThing> things = ['a', 'b', 'c'].collect { new IdThing(name: it) }
            things.each { session.persist(it) }
            session.flush()
            session.delete(things.first())
            session.flush()
        }

        when:
        withSession { Session session ->
            session.persist(new IdThing(name: 'd'))
            session.flush()
        }

        then:
        names() == ['b', 'c', 'd']
    }

    void 'a subclass takes its identifiers from its root entity, in every session'() {
        when:
        withSession { Session session ->
            session.persist(new IdParent(name: 'parent'))
            session.flush()
        }
        withSession { Session session ->
            session.persist(new IdChild(name: 'child'))
            session.flush()
        }

        then:
        withSession { Session session -> session.createQuery(IdParent).list()*.id.sort() } == [1L, 2L]
    }

    void 'an Integer identifier is generated as an Integer, and found by one'() {
        given:
        IdInteger thing = new IdInteger(name: 'integer')

        when:
        withSession { Session session ->
            session.persist(thing)
            session.flush()
        }

        then:
        thing.id == 1
        thing.id instanceof Integer
        withSession { Session session -> session.retrieve(IdInteger, 1)?.name } == 'integer'
    }

    void 'identifiers start again once the data is cleared'() {
        given:
        withSession { Session session ->
            session.persist(new IdThing(name: 'before'))
            session.flush()
        }

        when:
        datastore.clearData()
        IdThing after = new IdThing(name: 'after')
        withSession { Session session ->
            session.persist(after)
            session.flush()
        }

        then:
        after.id == 1L
    }

    private List<String> names() {
        withSession { Session session -> session.createQuery(IdThing).list()*.name.sort() } as List<String>
    }

    private <T> T withSession(Closure<T> work) {
        Session session = datastore.connect()
        try {
            work(session)
        }
        finally {
            session.disconnect()
        }
    }
}

@Entity
class IdThing {
    Long id
    String name
}

@Entity
class IdParent {
    Long id
    String name
}

@Entity
class IdChild extends IdParent {
}

@Entity
class IdInteger {
    Integer id
    String name
}

@Entity
class IdCustomGenerator {
    String id
    String name

    static mapping = {
        id generator: 'example.CustomIdentifierGenerator'
    }
}

@Entity
class IdAssigned {
    String id
    String name

    static mapping = {
        id generator: 'assigned'
    }
}
