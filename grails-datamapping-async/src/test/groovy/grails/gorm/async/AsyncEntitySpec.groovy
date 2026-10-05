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
package grails.gorm.async

import grails.async.Promises
import org.grails.async.factory.SynchronousPromiseFactory
import org.grails.datastore.gorm.GormRegistry
import org.grails.datastore.gorm.GormStaticApi
import org.grails.datastore.gorm.async.GormAsyncStaticApi
import org.grails.datastore.mapping.model.PersistentEntity
import spock.lang.Specification

class AsyncEntitySpec extends Specification {

    private def originalPromiseFactory

    void setup() {
        GormRegistry.instance.reset()
        originalPromiseFactory = Promises.promiseFactory
        Promises.promiseFactory = new SynchronousPromiseFactory()
    }

    void cleanup() {
        Promises.promiseFactory = originalPromiseFactory
        GormRegistry.instance.reset()
    }

    void "getAsync wraps the entity's own registered static API in a GormAsyncStaticApi"() {
        given: "the entity's static API is registered with GORM"
        GormStaticApi staticApi = stubStaticApi()
        registerStaticApi(AsyncEntityFixture, staticApi)

        when:
        def asyncApi = AsyncEntityFixture.async

        then:
        asyncApi instanceof GormAsyncStaticApi
        asyncApi.staticApi.is(staticApi)
    }

    void "getAsync's task runs the closure inside a new session opened by the entity's own static API"() {
        given: "the entity's static API is registered with GORM"
        boolean ranInsideNewSession = false
        GormStaticApi staticApi = stubStaticApi()
        staticApi.withNewSession(_) >> { Closure c -> ranInsideNewSession = true; c.call() }
        registerStaticApi(AsyncEntityFixture, staticApi)

        when:
        def result = AsyncEntityFixture.async.task { "done" }.get()

        then:
        result == "done"
        ranInsideNewSession
    }

    private GormStaticApi stubStaticApi() {
        Mock(GormStaticApi) {
            getGormPersistentEntity() >> Stub(PersistentEntity) {
                getJavaClass() >> AsyncEntityFixture
            }
        }
    }

    /**
     * Registers the given static API as the GORM static API for {@code cls}.
     *
     * <p>{@code GormEnhancer.findStaticApi} is deprecated and delegates to
     * {@link GormRegistry}, so registering through the registry is what
     * {@code AsyncEntity.getAsync()} actually reads.</p>
     */
    private void registerStaticApi(Class cls, GormStaticApi staticApi) {
        GormRegistry.instance.staticApiRegistry.register(cls.name, staticApi)
    }
}

class AsyncEntityFixture implements AsyncEntity<AsyncEntityFixture> {
}
