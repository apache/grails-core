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
package org.grails.orm.hibernate

import java.lang.reflect.UndeclaredThrowableException

import org.hibernate.FlushMode
import org.hibernate.Session
import spock.lang.AutoCleanup
import spock.lang.Specification

import org.grails.orm.hibernate.AbstractHibernateDatastore.FlushMode as GormFlushMode
import org.grails.orm.hibernate.cfg.Settings

/**
 * Created by graemerocher on 22/09/2016.
 */
class HibernateDatastoreSpec extends Specification {

    @AutoCleanup
    HibernateDatastore datastore

    void "test configure via map"() {
        when:"The map constructor is used"
        def config = Collections.singletonMap(Settings.SETTING_DB_CREATE,  "create-drop")
        datastore = new HibernateDatastore(config, Book)

        then:"GORM is configured correctly"
        Book.withNewSession {
            Book.count()
        } == 0
    }

    void "withFlushMode applies the flush mode while the callable runs and restores it unless the callable returns false"() {
        given:
        datastore = new HibernateDatastore(Collections.singletonMap(Settings.SETTING_DB_CREATE, 'create-drop'), Book)

        when:
        List<FlushMode> modes = Book.withNewSession {
            Session session = datastore.sessionFactory.currentSession
            FlushMode before = session.hibernateFlushMode
            FlushMode during = null
            datastore.withFlushMode(GormFlushMode.MANUAL) {
                during = session.hibernateFlushMode
                reset
            }
            [before, during, session.hibernateFlushMode]
        }

        then:
        modes[1] == FlushMode.MANUAL
        modes[2] == (restored ? modes[0] : FlushMode.MANUAL)

        where:
        reset || restored
        true  || true
        null  || true
        false || false
    }

    void "withFlushMode rethrows #exception.class.simpleName from the callable and restores the flush mode"() {
        given:
        datastore = new HibernateDatastore(Collections.singletonMap(Settings.SETTING_DB_CREATE, 'create-drop'), Book)

        when:
        Throwable thrown = null
        List<FlushMode> modes = Book.withNewSession {
            Session session = datastore.sessionFactory.currentSession
            FlushMode before = session.hibernateFlushMode
            try {
                datastore.withFlushMode(GormFlushMode.MANUAL) {
                    throw exception
                }
            }
            catch (Throwable e) {
                thrown = e
            }
            [before, session.hibernateFlushMode]
        }

        then:
        thrown.class == expectedType
        (thrown.is(exception) || thrown.cause.is(exception))
        modes[1] == modes[0]

        where:
        exception                         || expectedType
        new IllegalStateException('boom') || IllegalStateException
        new IOException('io')             || UndeclaredThrowableException
    }
}
