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
package org.grails.datastore.mapping.core.impl

import spock.lang.Specification

import org.grails.datastore.mapping.engine.EntityAccess
import org.grails.datastore.mapping.model.PersistentEntity

class PendingOperationSpec extends Specification {

    PersistentEntity entity = Stub(PersistentEntity)
    EntityAccess access = Stub(EntityAccess) { getEntity() >> 'the entity' }

    private PendingOperationAdapter adapter(List<String> log, String name, Object nativeEntry = 'entry') {
        new PendingOperationAdapter<Object, Long>(entity, 1L, nativeEntry) {
            void run() {
                log << name
            }
        }
    }

    void "the base adapter exposes its state and collects pre and cascade operations"() {
        given:
        List<String> log = []
        PendingOperationAdapter op = adapter(log, 'op')
        PendingOperation pre = adapter(log, 'pre')
        PendingOperation cascade = adapter(log, 'cascade')

        expect:
        op.entity.is(entity)
        op.nativeKey == 1L
        op.nativeEntry == 'entry'
        op.object == 'entry'
        !op.vetoed
        !op.wasExecuted()
        op.preOperations.empty
        op.cascadeOperations.empty

        when:
        op.addPreOperation(pre)
        op.addCascadeOperation(cascade)
        op.vetoed = true
        op.executed = true

        then:
        op.preOperations == [pre]
        op.cascadeOperations == [cascade]
        op.vetoed
        op.wasExecuted()

        when:
        op.preOperations.add(cascade)

        then:
        thrown(UnsupportedOperationException)
    }

    void "insert and update adapters expose the entity through the entity access"() {
        when:
        PendingInsertAdapter insert = new PendingInsertAdapter<Object, Long>(entity, 2L, 'entry', access) {
            void run() { }
        }
        PendingUpdateAdapter update = new PendingUpdateAdapter<Object, Long>(entity, 3L, 'entry', access) {
            void run() { }
        }
        PendingInsertAdapter detached = new PendingInsertAdapter<Object, Long>(entity, 4L, 'entry', null) {
            void run() { }
        }

        then:
        insert.entityAccess.is(access)
        insert.object == 'the entity'
        update.entityAccess.is(access)
        update.object == 'the entity'
        detached.object == null
        !insert.vetoed
        !update.vetoed

        when:
        insert.vetoed = true
        update.vetoed = true

        then:
        insert.vetoed
        update.vetoed
    }

    void "delete adapters expose the native entry as the object"() {
        when:
        PendingDeleteAdapter delete = new PendingDeleteAdapter<Object, Long>(entity, 5L, 'deleted') {
            void run() { }
        }

        then:
        delete.object == 'deleted'
        !delete.vetoed

        when:
        delete.vetoed = true

        then:
        delete.vetoed
    }

    void "execution runs pre operations, the operation and then the cascades"() {
        given:
        List<String> log = []
        PendingOperationAdapter op = adapter(log, 'op')
        op.addPreOperation(adapter(log, 'pre1'))
        op.addPreOperation(adapter(log, 'pre2'))
        op.addCascadeOperation(adapter(log, 'cascade'))

        when:
        PendingOperationExecution.executePendingOperation(op)

        then:
        log == ['pre1', 'pre2', 'op', 'cascade']
    }

    void "a vetoed operation skips its cascades"() {
        given:
        List<String> log = []
        PendingOperationAdapter op = new PendingOperationAdapter<Object, Long>(entity, 1L, 'entry') {
            void run() {
                log << 'op'
                vetoed = true
            }
        }
        op.addPreOperation(adapter(log, 'pre'))
        op.addCascadeOperation(adapter(log, 'cascade'))

        when:
        PendingOperationExecution.executePendingOperation(op)

        then:
        log == ['pre', 'op']
    }
}
