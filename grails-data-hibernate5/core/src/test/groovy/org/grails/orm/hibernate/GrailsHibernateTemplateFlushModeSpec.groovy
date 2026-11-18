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

import jakarta.persistence.FlushModeType
import org.hibernate.FlushMode
import org.hibernate.Session
import spock.lang.AutoCleanup
import spock.lang.Specification

import org.grails.orm.hibernate.cfg.Settings

class GrailsHibernateTemplateFlushModeSpec extends Specification {

    @AutoCleanup
    HibernateDatastore datastore

    void "templates use the configured flush mode #flushMode"() {
        given:
        datastore = createDatastore(flushMode)

        expect:
        datastore.hibernateTemplate.flushMode == expected
        new GrailsHibernateTemplate(datastore.sessionFactory, datastore).flushMode == expected

        where:
        flushMode || expected
        'MANUAL'  || GrailsHibernateTemplate.FLUSH_NEVER
        'COMMIT'  || GrailsHibernateTemplate.FLUSH_COMMIT
        'AUTO'    || GrailsHibernateTemplate.FLUSH_AUTO
        'ALWAYS'  || GrailsHibernateTemplate.FLUSH_ALWAYS
    }

    void "a template operation in a transaction runs with the configured flush mode #flushMode"() {
        given:
        datastore = createDatastore(flushMode)

        when: 'the session of the transaction uses AUTO when a template operation runs'
        List<FlushMode> modes = Book.withTransaction {
            Session session = datastore.sessionFactory.currentSession
            session.hibernateFlushMode = FlushMode.AUTO
            FlushMode during = datastore.hibernateTemplate.execute { Session s -> s.hibernateFlushMode }
            [during, session.hibernateFlushMode]
        }

        then: 'the operation runs with the configured flush mode, and the session gets its flush mode back afterwards'
        modes == [expected, FlushMode.AUTO]

        where:
        flushMode || expected
        'MANUAL'  || FlushMode.MANUAL
        'COMMIT'  || FlushMode.COMMIT
        'AUTO'    || FlushMode.AUTO
        'ALWAYS'  || FlushMode.ALWAYS
    }

    void "the session of the datastore reports the configured flush mode #flushMode"() {
        given:
        datastore = createDatastore(flushMode)

        expect:
        datastore.currentSession.flushMode == expected

        where:
        flushMode || expected
        'COMMIT'  || FlushModeType.COMMIT
        'AUTO'    || FlushModeType.AUTO
    }

    private static HibernateDatastore createDatastore(String flushMode) {
        new HibernateDatastore([(Settings.SETTING_DB_CREATE): 'create-drop', 'hibernate.flush.mode': flushMode], Book)
    }
}
