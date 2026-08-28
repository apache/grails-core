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
package org.grails.datastore.gorm.finders

import grails.gorm.annotation.Entity
import org.grails.datastore.mapping.simple.SimpleMapDatastore
import spock.lang.AutoCleanup
import spock.lang.Specification

/**
 * {@code AbstractFinder} is the common base for the "named" dynamic finder classes
 * ({@code FindAllByFinder}, {@code CountByFinder}, {@code ListOrderByFinder}, etc.) that
 * {@code DefaultGormApiFactory#createDynamicFinders} registers using the new
 * {@code DatastoreResolver}-based constructor this PR added. Drives real dynamic finder calls
 * through a {@code SimpleMapDatastore}-backed entity to exercise the lazy resolver path and the
 * additional-criteria closure support.
 */
class AbstractFinderSpec extends Specification {

    @AutoCleanup
    SimpleMapDatastore datastore = new SimpleMapDatastore(AbstractFinderThing)

    void "findAllBy resolves its datastore via the lazy DatastoreResolver and executes a real query"() {
        given:
        AbstractFinderThing.newInstance(title: 'Alpha').save(flush: true)
        AbstractFinderThing.newInstance(title: 'Beta').save(flush: true)

        expect:
        AbstractFinderThing.findAllByTitle('Alpha').size() == 1
    }

    void "countBy resolves its datastore via the lazy DatastoreResolver and executes a real query"() {
        given:
        AbstractFinderThing.newInstance(title: 'Same').save(flush: true)
        AbstractFinderThing.newInstance(title: 'Same').save(flush: true)

        expect:
        AbstractFinderThing.countByTitle('Same') == 2
    }

    void "listOrderBy resolves its datastore via the lazy DatastoreResolver and executes a real query"() {
        given:
        AbstractFinderThing.newInstance(title: 'B').save(flush: true)
        AbstractFinderThing.newInstance(title: 'A').save(flush: true)

        expect:
        AbstractFinderThing.listOrderByTitle()*.title == ['A', 'B']
    }

    void "listOrderBy with an additional criteria closure applies it via applyAdditionalCriteria"() {
        given: "ListOrderByFinder.invoke(Class, methodName, Closure, Object[]) is the one caller of\n" +
                "applyAdditionalCriteria in this class hierarchy - called directly since routing a\n" +
                "trailing closure through GormStaticApi's own dynamic dispatch into this specific\n" +
                "4-arg overload isn't part of what this class itself needs proving"
        AbstractFinderThing.newInstance(title: 'Matched', age: 30).save(flush: true)
        AbstractFinderThing.newInstance(title: 'Matched', age: 99).save(flush: true)
        def finder = new ListOrderByFinder(datastore)

        when:
        def results = finder.invoke(AbstractFinderThing, 'listOrderByTitle', { lt('age', 50) }, [] as Object[])

        then:
        results.size() == 1
        results[0].age == 30
    }

    void "execute(SessionCallback) throws IllegalStateException when no datastore can be resolved"() {
        given:
        def finder = new ListOrderByFinder((org.grails.datastore.mapping.core.Datastore) null)

        when:
        finder.invoke(AbstractFinderThing, 'listOrderByTitle', [] as Object[])

        then:
        def e = thrown(IllegalStateException)
        e.message == 'Cannot execute session query with null datastore'
    }
}

@Entity
class AbstractFinderThing {
    String title
    Integer age
}
