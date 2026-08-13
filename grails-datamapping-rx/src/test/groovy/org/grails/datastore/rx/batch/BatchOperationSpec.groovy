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
package org.grails.datastore.rx.batch

import org.grails.datastore.mapping.model.PersistentEntity
import spock.lang.Specification

class BatchOperationSpec extends Specification {

    def "no-arg constructor defaults to empty arguments and no pending operations"() {
        when:
        BatchOperation operation = new BatchOperation()

        then:
        operation.arguments.isEmpty()
        !operation.hasPendingOperations()
        operation.inserts.isEmpty()
        operation.updates.isEmpty()
        operation.deletes.isEmpty()
    }

    def "constructor stores the given arguments"() {
        given:
        Map<String, Object> arguments = [flush: true]

        when:
        BatchOperation operation = new BatchOperation(arguments)

        then:
        operation.arguments == arguments
    }

    def "addInsert records an insert keyed by entity and id"() {
        given:
        BatchOperation operation = new BatchOperation()
        PersistentEntity entity = Stub(PersistentEntity)
        Object instance = new Object()

        when:
        operation.addInsert(entity, 1L, instance)

        then:
        operation.hasPendingOperations()
        operation.inserts[entity][1L].identity == 1L
        operation.inserts[entity][1L].object.is(instance)
        operation.updates.isEmpty()
        operation.deletes.isEmpty()
    }

    def "addUpdate records an update keyed by entity and id"() {
        given:
        BatchOperation operation = new BatchOperation()
        PersistentEntity entity = Stub(PersistentEntity)
        Object instance = new Object()

        when:
        operation.addUpdate(entity, 2L, instance)

        then:
        operation.hasPendingOperations()
        operation.updates[entity][2L].identity == 2L
        operation.updates[entity][2L].object.is(instance)
        operation.inserts.isEmpty()
        operation.deletes.isEmpty()
    }

    def "addDelete records a delete keyed by entity and id"() {
        given:
        BatchOperation operation = new BatchOperation()
        PersistentEntity entity = Stub(PersistentEntity)
        Object instance = new Object()

        when:
        operation.addDelete(entity, 3L, instance)

        then:
        operation.hasPendingOperations()
        operation.deletes[entity][3L].identity == 3L
        operation.deletes[entity][3L].object.is(instance)
        operation.inserts.isEmpty()
        operation.updates.isEmpty()
    }

    def "addInsert on the same entity accumulates operations for distinct ids"() {
        given:
        BatchOperation operation = new BatchOperation()
        PersistentEntity entity = Stub(PersistentEntity)

        when:
        operation.addInsert(entity, 1L, "one")
        operation.addInsert(entity, 2L, "two")

        then:
        operation.inserts[entity].size() == 2
        operation.inserts[entity][1L].object == "one"
        operation.inserts[entity][2L].object == "two"
    }

    def "isAlreadyPending is true when an insert exists for the entity and id"() {
        given:
        BatchOperation operation = new BatchOperation()
        PersistentEntity entity = Stub(PersistentEntity)
        operation.addInsert(entity, 1L, "value")

        expect:
        operation.isAlreadyPending(entity, 1L, "value")
    }

    def "isAlreadyPending is true when an update exists for the entity and id"() {
        given:
        BatchOperation operation = new BatchOperation()
        PersistentEntity entity = Stub(PersistentEntity)
        operation.addUpdate(entity, 1L, "value")

        expect:
        operation.isAlreadyPending(entity, 1L, "value")
    }

    def "isAlreadyPending is false when only a delete exists for the entity and id"() {
        given:
        BatchOperation operation = new BatchOperation()
        PersistentEntity entity = Stub(PersistentEntity)
        operation.addDelete(entity, 1L, "value")

        expect:
        !operation.isAlreadyPending(entity, 1L, "value")
    }

    def "isAlreadyPending is false for an id not recorded on a known entity"() {
        given:
        BatchOperation operation = new BatchOperation()
        PersistentEntity entity = Stub(PersistentEntity)
        operation.addInsert(entity, 1L, "value")

        expect:
        !operation.isAlreadyPending(entity, 2L, "value")
    }

    def "isAlreadyPending does not create a pending entry for an unknown entity"() {
        given:
        BatchOperation operation = new BatchOperation()
        PersistentEntity entity = Stub(PersistentEntity)

        when:
        boolean pending = operation.isAlreadyPending(entity, 1L, "value")

        then:
        !pending
        !operation.hasPendingOperations()
        !operation.inserts.containsKey(entity)
        !operation.updates.containsKey(entity)
    }
}
