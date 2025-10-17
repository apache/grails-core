/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.grails.data.testing.tck.tests

import org.apache.grails.data.testing.tck.domains.ChildEntity
import org.apache.grails.data.testing.tck.domains.TestEntity
import groovy.transform.InheritConstructors
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec

/**
 * Transaction tests.
 */
class WithTransactionSpec extends GrailsDataTckSpec {

    void setupSpec() {
        manager.addAllDomainClasses([TestEntity, ChildEntity])
    }

    void "Test save() with transaction"() {
        given:
        TestEntity.withTransaction {
            new TestEntity(name: "Bob", age: 50, child: new ChildEntity(name: "Bob Child")).save()
            new TestEntity(name: "Fred", age: 45, child: new ChildEntity(name: "Fred Child")).save()
        }

        when:
        int count = TestEntity.count()
//            def results = TestEntity.list(sort:"name") // TODO this fails but doesn't appear to be tx-related, so manually sorting
        def results = TestEntity.list().sort { it.name }

        then:
        2 == count
        "Bob" == results[0].name
        "Fred" == results[1].name
    }

    void "Test rollback transaction"() {
        given:
        TestEntity.withNewTransaction { status ->
            new TestEntity(name: "Bob", age: 50, child: new ChildEntity(name: "Bob Child")).save()
            status.setRollbackOnly()
            new TestEntity(name: "Fred", age: 45, child: new ChildEntity(name: "Fred Child")).save()
        }

        when:
        int count = TestEntity.count()
        def results = TestEntity.list()

        then:
        count == 0
        results.size() == 0
    }

    void "Test rollback transaction with Runtime Exception"() {
        given:
        def ex
        try {
            TestEntity.withNewTransaction { status ->
                new TestEntity(name: "Bob", age: 50, child: new ChildEntity(name: "Bob Child")).save()
                throw new RuntimeException("bad")
            }
        }
        catch (e) {
            ex = e
        }

        when:
        int count = TestEntity.count()
        def results = TestEntity.list()

        then:
        count == 0
        results.size() == 0
        ex instanceof RuntimeException
        ex.message == 'bad'
    }

    void "Test rollback transaction with Exception"() {
        given:
        def ex
        try {
            TestEntity.withNewTransaction { status ->
                new TestEntity(name: "Bob", age: 50, child: new ChildEntity(name: "Bob Child")).save()
                throw new TestCheckedException("bad")
            }
        }
        catch (e) {
            ex = e
        }

        when:
        int count = TestEntity.count()
        def results = TestEntity.list()

        then:
        count == 0
        results.size() == 0
        ex instanceof TestCheckedException
        ex.message == 'bad'
    }
}

@InheritConstructors
class TestCheckedException extends Exception {}
