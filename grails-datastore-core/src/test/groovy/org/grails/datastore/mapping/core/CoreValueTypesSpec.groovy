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
package org.grails.datastore.mapping.core

import org.springframework.util.ConcurrentReferenceHashMap
import spock.lang.Specification

import org.grails.datastore.mapping.core.exceptions.ConfigurationException
import org.grails.datastore.mapping.model.PersistentEntity

class CoreValueTypesSpec extends Specification {

    void "merge is not implemented by default"() {
        given:
        Session session = Stub(Session) { merge(_) >> { callRealMethod() } }

        when:
        session.merge('x')

        then:
        MethodNotImplementedException e = thrown()
        e.message == 'merge(Object) is not implemented for this Session'
        e instanceof UnsupportedOperationException
    }

    void "ordered defaults to zero"() {
        expect:
        new Ordered() { }.order == 0
    }

    void "the session creation event carries the session and its datastore"() {
        given:
        Datastore datastore = Stub(Datastore)
        Session session = Stub(Session) { getDatastore() >> datastore }

        when:
        SessionCreationEvent event = new SessionCreationEvent(session)

        then:
        event.session.is(session)
        event.source.is(datastore)
    }

    void "optimistic locking exceptions carry the entity and key"() {
        given:
        PersistentEntity entity = Stub(PersistentEntity)

        when:
        OptimisticLockingException e = new OptimisticLockingException(entity, 7L)

        then:
        e.persistentEntity.is(entity)
        e.key == 7L
        e.message == 'The instance was updated by another user while you were editing'
    }

    void "the runtime exceptions preserve message and cause"() {
        given:
        Throwable cause = new IllegalStateException('cause')

        expect:
        new ConnectionNotFoundException('m').message == 'm'
        new IdentityGenerationException('m').message == 'm'
        new EntityCreationException('m').message == 'm'
        new EntityCreationException('m', cause).cause.is(cause)
        new DatastoreException('m').message == 'm'
        new DatastoreException('m', cause).cause.is(cause)
        new MethodNotImplementedException('m').message == 'm'
        new MethodNotImplementedException('m', cause).cause.is(cause)
        new ConfigurationException('m').message == 'm'
        new ConfigurationException('m', cause).cause.is(cause)
    }

    void "the soft thread local map starts empty and never shares state with child threads"() {
        given:
        SoftThreadLocalMap map = new SoftThreadLocalMap()

        expect:
        map.get() instanceof ConcurrentReferenceHashMap
        map.get().isEmpty()

        when:
        map.get().put('k', 'v')
        ConcurrentReferenceHashMap parentValue = map.get()

        then:
        map.childValue(parentValue).isEmpty()
        !map.childValue(parentValue).is(parentValue)

        cleanup:
        map.remove()
    }
}
