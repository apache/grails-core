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
package grails.gorm.tests

import org.apache.grails.data.hibernate5.core.GrailsDataHibernate5TckManager
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec
import org.apache.grails.data.testing.tck.domains.TestEntity
import org.hibernate.Session
import spock.lang.Issue
import spock.lang.Unroll

/**
 * The readOnly, fetchSize, timeout and flushMode arguments documented for findAll(Map, Closure), the dynamic
 * finders and the where queries reach the Hibernate criteria that runs the query.
 */
@Issue('https://github.com/apache/grails-core/issues/16808')
class CriteriaQueryArgumentsSpec extends GrailsDataTckSpec<GrailsDataHibernate5TckManager> {

    void setupSpec() {
        manager.domainClasses.addAll([TestEntity])
    }

    void setup() {
        ['a', 'b', 'c'].each { new TestEntity(name: it, age: 40).save(failOnError: true) }
        manager.session.flush()
        manager.session.clear()
    }

    private static boolean isReadOnly(TestEntity entity) {
        TestEntity.withSession { Session s -> s.isReadOnly(entity) }
    }

    @Unroll
    void "readOnly: #readOnly reaches #route"() {
        when:
        List<TestEntity> found = route == 'where.list' ? TestEntity.where { age > 0 }.list(readOnly: readOnly) :
                route == 'findAll closure' ? TestEntity.findAll([readOnly: readOnly]) { age > 0 } :
                TestEntity.findAllByAgeGreaterThan(0, [readOnly: readOnly])

        then:
        found.size() == 3
        found.every { isReadOnly(it) == readOnly }

        where:
        route             | readOnly
        'where.list'      | true
        'where.list'      | false
        'findAll closure' | true
        'findAll closure' | false
        'dynamic finder'  | true
        'dynamic finder'  | false
    }

    void "readOnly on a single result where query is honoured"() {
        when:
        TestEntity found = TestEntity.where { name == 'b' }.find(readOnly: true)

        then:
        found.name == 'b'
        isReadOnly(found)
    }

    void "flushMode MANUAL keeps a pending change out of a where query and ALWAYS flushes it"() {
        given:
        TestEntity first = TestEntity.findByName('a')
        first.age = 99

        when:
        List<TestEntity> manual = TestEntity.where { age == 99 }.list(flushMode: 'MANUAL')
        List<TestEntity> always = TestEntity.where { age == 99 }.list(flushMode: 'ALWAYS')

        then:
        manual.isEmpty()
        always*.name == ['a']
    }

    void "a fetchSize that is not a whole number is rejected with the argument name"() {
        when:
        TestEntity.where { age > 0 }.list(fetchSize: 'lots')

        then:
        IllegalArgumentException e = thrown()
        e.message.contains('fetchSize')
        e.message.contains('lots')
    }

    void "fetchSize and timeout that are whole numbers are accepted"() {
        when:
        List<TestEntity> found = TestEntity.where { age > 0 }.list(fetchSize: '50', timeout: 30)

        then:
        found.size() == 3
    }
}
