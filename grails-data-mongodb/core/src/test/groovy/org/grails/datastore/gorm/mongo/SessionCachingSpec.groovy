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
package org.grails.datastore.gorm.mongo

import grails.gorm.tests.Person
import org.apache.grails.data.mongo.core.GrailsDataMongoTckManager
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec

/**
 * Tests related to caching of entities.
 */
class SessionCachingSpec extends GrailsDataTckSpec<GrailsDataMongoTckManager> {
    void setupSpec() {
        manager.domainClasses += [Person]
    }

    void "test cache used for get"() {
        given:
        def a = new Person(firstName: "Bob", lastName: "Builder").save()
        manager.session.flush()

        when:
        def aa = Person.get(a.id)

        then:
        a.attached
        aa != null
        aa.is(a)
    }

    void "test cache used for getAll"() {
        given:
        def a = new Person(firstName: "Bob", lastName: "Builder").save()
        def b = new Person(firstName: "Another", lastName: "Builder").save()
        manager.session.flush()
        manager.session.clear()

        when:
        a = Person.get(a.id)
        def list = Person.getAll(b.id, a.id)

        then:
        a != null
        list.size() == 2
        list.every { it != null }
        list.every { it.attached }
        list[0].id == b.id
        list[1].is(a)
    }

    void "test unique queried elements are from cache"() {
        given:
        def p = new Person(firstName: "Bob", lastName: "Builder").save()
        manager.session.flush()

        when:
        def pp = Person.findByFirstName("Bob")

        then:
        p.attached
        pp != null
        pp.attached
        p.is(pp)
    }

    void "test multi-queried elements are in cache"() {
        given:
        def p = new Person(firstName: "Bob", lastName: "Builder").save()
        manager.session.flush()

        when:
        def test = Person.findAllByFirstName("Bob")

        then:
        test.size() == 1
        manager.session.contains(test[0])
        test[0].attached
        p.is(test[0])
    }
}
