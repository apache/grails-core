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

import grails.gorm.annotation.Entity
import grails.gorm.tests.HibernateGormDatastoreSpec

/**
 * The shared TCK {@code GetAllSpec} covers the portable contract (order, duplicates, null slots).
 * This spec covers the Hibernate-specific part: a requested id that the {@code ConversionService}
 * cannot coerce is passed to the query raw and coerced by Hibernate instead, so the entity that
 * comes back does not carry the requested key as its identifier and has to be matched back to it.
 */
class HibernateSessionRetrieveAllSpec extends HibernateGormDatastoreSpec {

    void setupSpec() {
        manager.registerDomainClasses(HSRABook)
    }

    void "retrieveAll resolves an id supplied as a String, which Hibernate coerces for the query"() {
        given:
        def session = manager.session
        def id1 = session.persist(new HSRABook(title: 'Coerce1'))
        def id2 = session.persist(new HSRABook(title: 'Coerce2'))
        session.flush()
        session.clear()

        when: "ids arrive as strings, as they do straight off a request parameter"
        def results = session.retrieveAll(HSRABook, id1.toString(), id2.toString())

        then: "the entity's own identifier is matched back to the requested key, not left as a null slot"
        results.size() == 2
        results*.title == ['Coerce1', 'Coerce2']
    }

    void "retrieveAll preserves the requested order, duplicates and a null slot for a missing id"() {
        given:
        def session = manager.session
        def id1 = session.persist(new HSRABook(title: 'Order1'))
        def id2 = session.persist(new HSRABook(title: 'Order2'))
        session.flush()
        session.clear()
        def missingId = id2 + 100000L

        when:
        def results = session.retrieveAll(HSRABook, id2, id1, id2, missingId)

        then:
        results.size() == 4
        results[0].title == 'Order2'
        results[1].title == 'Order1'
        results[2].title == 'Order2'
        results[3] == null
    }
}

@Entity
class HSRABook {
    String title
}
