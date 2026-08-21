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
package org.grails.datastore.gorm.async

import grails.async.Promise
import grails.async.Promises
import org.grails.async.factory.SynchronousPromiseFactory
import org.grails.datastore.gorm.query.GormOperations
import spock.lang.Specification

class AsyncQuerySpec extends Specification {

    private def originalPromiseFactory

    void setup() {
        originalPromiseFactory = Promises.promiseFactory
        Promises.promiseFactory = new SynchronousPromiseFactory()
        AsyncQuerySpecEntity.newSessionCallCount = 0
    }

    void cleanup() {
        Promises.promiseFactory = originalPromiseFactory
    }

    void "list delegates to the wrapped GORM operations asynchronously within a new session"() {
        given:
        def gormOperations = Mock(GormOperations) {
            getPersistentClass() >> AsyncQuerySpecEntity
        }
        def query = new AsyncQuery(gormOperations)

        when:
        Promise<List> promise = query.list()

        then:
        1 * gormOperations.list() >> ["a", "b"]
        promise.get() == ["a", "b"]
        AsyncQuerySpecEntity.newSessionCallCount == 1
    }

    void "count delegates to the wrapped GORM operations asynchronously within a new session"() {
        given:
        def gormOperations = Mock(GormOperations) {
            getPersistentClass() >> AsyncQuerySpecEntity
        }
        def query = new AsyncQuery(gormOperations)

        when:
        Promise<Number> promise = query.count()

        then:
        1 * gormOperations.count() >> 3
        promise.get() == 3
        AsyncQuerySpecEntity.newSessionCallCount == 1
    }

    void "getDecorators returns a single decorator that wraps calls in a new session"() {
        given:
        def gormOperations = Mock(GormOperations)
        def query = new AsyncQuery(gormOperations)

        expect:
        query.decorators.size() == 1
    }
}

class AsyncQuerySpecEntity {

    static int newSessionCallCount = 0

    static withNewSession(Closure callable) {
        newSessionCallCount++
        callable.call()
    }
}
