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
package org.grails.datastore.gorm

import grails.gorm.annotation.Entity
import spock.lang.AutoCleanup
import spock.lang.Specification

import org.grails.datastore.mapping.core.Datastore
import org.grails.datastore.mapping.core.Session
import org.grails.datastore.mapping.simple.SimpleMapDatastore

/**
 * {@code withDatastoreSession} must always run its callback with the GORM datastore
 * {@link Session}, regardless of any {@code withSession()} override. Persistence adapters (e.g.
 * Hibernate) override {@code withSession()} to expose the native session (a raw
 * {@code org.hibernate.Session}); routing {@code withDatastoreSession} through {@code withSession}
 * therefore handed callers such as {@code DetachedCriteria} a native session instead of the GORM
 * one. Regression guard for that.
 */
class GormStaticApiWithDatastoreSessionSpec extends Specification {

    @AutoCleanup
    SimpleMapDatastore datastore = new SimpleMapDatastore(WithDatastoreSessionThing)

    void "withDatastoreSession runs with the GORM Session and ignores a withSession() override"() {
        given: "a static api whose withSession() override exposes a non-GORM 'native' session"
        def api = new NativeSessionStaticApi(WithDatastoreSessionThing, datastore, [])

        when: "a datastore-session callback is executed"
        def captured = api.withDatastoreSession { it }

        then: "the callback receives the GORM datastore Session, not the withSession() override value"
        captured instanceof Session
    }

    static class NativeSessionStaticApi<D> extends GormStaticApi<D> {

        NativeSessionStaticApi(Class<D> persistentClass, Datastore datastore, List finders) {
            super(persistentClass, datastore, finders)
        }

        @Override
        def <T1> T1 withSession(Closure<T1> callable) {
            (T1) callable.call('NATIVE-RAW-SESSION')
        }
    }
}

@Entity
class WithDatastoreSessionThing {

    String name
}
