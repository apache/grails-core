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
import org.grails.datastore.gorm.GormStaticApi
import org.grails.datastore.mapping.model.PersistentEntity
import spock.lang.Specification

class GormAsyncStaticApiSpec extends Specification {

    private def originalPromiseFactory

    void setup() {
        originalPromiseFactory = Promises.promiseFactory
        Promises.promiseFactory = new SynchronousPromiseFactory()
    }

    void cleanup() {
        Promises.promiseFactory = originalPromiseFactory
    }

    void "task runs the closure inside a new session and returns its result via a promise"() {
        given:
        def entity = Mock(PersistentEntity) {
            getJavaClass() >> String
        }
        def staticApi = Mock(GormStaticApi) {
            getGormPersistentEntity() >> entity
        }
        def api = new GormAsyncStaticApi(staticApi)

        when:
        Promise<String> promise = api.task { "done" }

        then:
        1 * staticApi.withNewSession(_) >> { Closure c -> c.call() }
        promise.get() == "done"
    }

    void "task does not invoke the closure when withNewSession is never called"() {
        given:
        def entity = Mock(PersistentEntity) {
            getJavaClass() >> String
        }
        def staticApi = Mock(GormStaticApi) {
            getGormPersistentEntity() >> entity
        }
        def api = new GormAsyncStaticApi(staticApi)

        when:
        Promise<String> promise = api.task { "unreached" }

        then:
        1 * staticApi.withNewSession(_) >> null
        promise.get() == null
    }

    void "list delegates to the static API asynchronously within a new session"() {
        given:
        def staticApi = Mock(GormStaticApi) {
            withNewSession(_) >> { Closure c -> c.call() }
        }
        def api = new GormAsyncStaticApi(staticApi)

        when:
        Promise<List> promise = api.list()

        then:
        1 * staticApi.list() >> ["a", "b"]
        promise.get() == ["a", "b"]
    }

    void "count delegates to the static API asynchronously within a new session"() {
        given:
        def staticApi = Mock(GormStaticApi) {
            withNewSession(_) >> { Closure c -> c.call() }
        }
        def api = new GormAsyncStaticApi(staticApi)

        when:
        Promise<Integer> promise = api.count()

        then:
        1 * staticApi.count() >> 5
        promise.get() == 5
    }

    void "getDecorators returns a single decorator that wraps calls in a new session"() {
        given:
        def staticApi = Mock(GormStaticApi)
        def api = new GormAsyncStaticApi(staticApi)

        expect:
        api.decorators.size() == 1
    }
}
