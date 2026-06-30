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

import java.util.concurrent.atomic.AtomicReference

import org.springframework.core.env.MapPropertySource
import org.springframework.core.env.PropertyResolver
import org.springframework.core.env.StandardEnvironment

import spock.lang.Specification

/**
 * Created by graemerocher on 01/03/2017.
 */
class DatastoreUtilsSpec extends Specification {

    void "test prepare property source from env"() {
        given:
        StandardEnvironment env = new StandardEnvironment()
        env.propertySources.addFirst(new MapPropertySource("test", ['grails.foo':'bar']))
        env.propertySources.addFirst(new MapPropertySource("test2", ['grails.foo':'baz']))

        PropertyResolver resolver = DatastoreUtils.preparePropertyResolver(env)
        expect:
        env != null
        resolver.getProperty('grails.foo') == 'baz'
    }

    void "deferred close lifecycle is isolated on a virtual thread"() {
        given:
        Datastore datastore = Mock()
        Session session = Mock()
        def repeatedCloseMessage = new AtomicReference<String>()

        when:
        runOnVirtualThread {
            DatastoreUtils.initDeferredClose(datastore)
            DatastoreUtils.closeSessionOrRegisterDeferredClose(session, datastore)
            DatastoreUtils.processDeferredClose(datastore)
            try {
                DatastoreUtils.processDeferredClose(datastore)
            }
            catch (IllegalStateException e) {
                repeatedCloseMessage.set(e.message)
            }
        }

        then:
        1 * session.disconnect()
        repeatedCloseMessage.get().contains('Deferred close not active')
    }

    void "soft thread local map does not expose parent entries to child threads"() {
        given:
        def threadLocal = new SoftThreadLocalMap()
        threadLocal.get().put('tenant', 'parent')
        def childTenant = new AtomicReference<String>()

        when:
        runOnVirtualThread {
            childTenant.set(threadLocal.get().get('tenant') as String)
        }

        then:
        childTenant.get() == null
        threadLocal.get().get('tenant') == 'parent'

        cleanup:
        threadLocal.remove()
    }

    private static <T> T runOnVirtualThread(Closure<T> callable) {
        def result = new AtomicReference<T>()
        def error = new AtomicReference<Throwable>()
        Runnable runnable = {
            try {
                result.set(callable.call())
            }
            catch (Throwable t) {
                error.set(t)
            }
        } as Runnable

        try {
            Thread thread = Thread.class.getMethod('startVirtualThread', Runnable).invoke(null, runnable) as Thread
            thread.join()
        }
        catch (NoSuchMethodException ignored) {
            Thread thread = new Thread(runnable)
            thread.start()
            thread.join()
        }

        if (error.get() != null) {
            throw error.get()
        }
        result.get()
    }
}
