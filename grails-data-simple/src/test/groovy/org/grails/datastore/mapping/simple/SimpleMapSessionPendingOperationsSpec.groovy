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
import spock.lang.AutoCleanup
import spock.lang.Specification

/**
 * Exercises {@link Session#hasPendingOperations()} against a real session, following queued
 * operations through the session lifecycle: persist, flush, delete and clear.
 */
class SimpleMapSessionPendingOperationsSpec extends Specification {

    @AutoCleanup
    SimpleMapDatastore datastore = new SimpleMapDatastore(PendingThing)

    @AutoCleanup('disconnect')
    Session session = datastore.connect()

    void "a fresh session has nothing pending"() {
        expect:
        !session.hasPendingOperations()
    }

    void "persist queues an insert until the session is flushed"() {
        when:
        session.persist(new PendingThing(name: 'queued'))

        then:
        session.hasPendingOperations()

        when:
        session.flush()

        then:
        !session.hasPendingOperations()
    }

    void "persisting a written entity again queues an update until the session is flushed"() {
        given: "an entity that has been written"
        PendingThing thing = new PendingThing(name: 'original')
        session.persist(thing)
        session.flush()

        when:
        thing.name = 'changed'
        session.persist(thing)

        then:
        session.hasPendingOperations()

        when:
        session.flush()

        then:
        !session.hasPendingOperations()
    }

    void "clear discards queued operations without writing them"() {
        given:
        session.persist(new PendingThing(name: 'discarded'))

        when:
        session.clear()

        then:
        !session.hasPendingOperations()

        and: "another session sees nothing written"
        Session other = datastore.connect()
        other.createQuery(PendingThing).list().empty

        cleanup:
        other?.disconnect()
    }
}

@Entity
class PendingThing {
    Long id
    Long version
    String name
}
