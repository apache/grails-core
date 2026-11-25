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
import org.hibernate.FlushMode
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

    @Unroll
    void "the total of a page with flushMode #mode #outcome the pending change"() {
        given: 'a session that flushes by itself, so only the flush mode of the arguments keeps a change out'
        TestEntity.withSession { Session s -> s.setHibernateFlushMode(FlushMode.AUTO) }
        TestEntity first = TestEntity.findByName('a')
        first.age = 99

        when:
        def page = TestEntity.where { age == 99 }.list(max: 10, flushMode: mode)

        then: 'the page and the count behind its total read the same data'
        page.size() == expected
        page.totalCount == expected

        where:
        mode     || expected
        'MANUAL' || 0
        'ALWAYS' || 1
        outcome = expected ? 'flushes' : 'leaves out'
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

    private static List<TestEntity> listFor(String route, Map args) {
        List<TestEntity> found
        switch (route) {
            case 'where.list':
                found = TestEntity.where { age > 0 }.list(args)
                break
            case 'where.find':
                found = [TestEntity.where { name == 'b' }.find(args)]
                break
            case 'findAll closure':
                found = TestEntity.findAll(args) { age > 0 }
                break
            case 'findAllBy':
                found = TestEntity.findAllByAgeGreaterThan(0, args)
                break
            case 'findBy':
                found = [TestEntity.findByName('b', args)]
                break
            case 'paged where':
                found = TestEntity.where { age > 0 }.list(args + [max: 10])
                break
            case 'paged criteria':
                found = TestEntity.createCriteria().list(args + [max: 10]) { gt('age', 0) }
                break
            case 'paged finder':
                found = TestEntity.findAllByAgeGreaterThan(0, args + [max: 10])
                break
            default:
                throw new IllegalArgumentException(route)
        }
        found.findAll { it != null }
    }

    private static List<TestEntity> changedFor(String route, Map args) {
        List<TestEntity> found
        switch (route) {
            case 'where.list':
                found = TestEntity.where { age == 99 }.list(args)
                break
            case 'where.find':
                found = [TestEntity.where { age == 99 }.find(args)]
                break
            case 'findAll closure':
                found = TestEntity.findAll(args) { age == 99 }
                break
            case 'findAllBy':
                found = TestEntity.findAllByAge(99, args)
                break
            case 'findBy':
                found = [TestEntity.findByAge(99, args)]
                break
            case 'paged where':
                found = TestEntity.where { age == 99 }.list(args + [max: 10])
                break
            case 'paged finder':
                found = TestEntity.findAllByAge(99, args + [max: 10])
                break
            default:
                throw new IllegalArgumentException(route)
        }
        found.findAll { it != null }
    }

    @Unroll
    void "readOnly: #readOnly on #route loads #outcome entities"() {
        when:
        List<TestEntity> found = listFor(route, [readOnly: readOnly])

        then:
        found
        found.every { isReadOnly(it) == readOnly }

        where:
        route             | readOnly
        'where.list'      | true
        'where.list'      | false
        'where.find'      | true
        'where.find'      | false
        'findAll closure' | true
        'findAll closure' | false
        'findAllBy'       | true
        'findAllBy'       | false
        'findBy'          | true
        'findBy'          | false
        'paged where'     | true
        'paged where'     | false
        'paged finder'    | true
        'paged finder'    | false
        outcome = readOnly ? 'read-only' : 'modifiable'
    }

    @Unroll
    void "flushMode: #mode on #route #outcome a pending change"() {
        given: 'a session that flushes by itself, so only the flush mode of the arguments keeps a change out'
        TestEntity.withSession { Session s -> s.setHibernateFlushMode(FlushMode.AUTO) }
        TestEntity first = TestEntity.findByName('a')
        first.age = 99

        when:
        List<TestEntity> found = changedFor(route, [flushMode: mode])

        then:
        found*.name == expected

        where:
        route             | mode     || expected
        'where.list'      | 'MANUAL' || []
        'where.list'      | 'ALWAYS' || ['a']
        'where.find'      | 'MANUAL' || []
        'where.find'      | 'ALWAYS' || ['a']
        'findAll closure' | 'MANUAL' || []
        'findAll closure' | 'ALWAYS' || ['a']
        'findAllBy'       | 'MANUAL' || []
        'findAllBy'       | 'ALWAYS' || ['a']
        'findBy'          | 'MANUAL' || []
        'findBy'          | 'ALWAYS' || ['a']
        'paged where'     | 'MANUAL' || []
        'paged where'     | 'ALWAYS' || ['a']
        'paged finder'    | 'MANUAL' || []
        'paged finder'    | 'ALWAYS' || ['a']
        outcome = expected ? 'flushes' : 'leaves out'
    }

    @Unroll
    void "#argument: #value is refused on #route and the message names the argument"() {
        when:
        listFor(route, [(argument): value])

        then:
        IllegalArgumentException e = thrown()
        e.message.contains(argument)
        e.message.contains(value)

        where:
        route             | argument    | value
        'where.list'      | 'fetchSize' | 'lots'
        'findAll closure' | 'fetchSize' | 'lots'
        'findAllBy'       | 'fetchSize' | 'lots'
        'findBy'          | 'fetchSize' | 'lots'
        'paged where'     | 'fetchSize' | 'lots'
        'where.list'      | 'timeout'   | 'soon'
        'findAll closure' | 'timeout'   | 'soon'
        'findAllBy'       | 'timeout'   | 'soon'
        'where.list'      | 'flushMode' | 'BOGUS'
        'findAll closure' | 'flushMode' | 'BOGUS'
        'findAllBy'       | 'flushMode' | 'BOGUS'
        'findBy'          | 'flushMode' | 'BOGUS'
        'paged where'     | 'flushMode' | 'BOGUS'
        'paged criteria'  | 'flushMode' | 'BOGUS'
        'paged criteria'  | 'fetchSize' | 'lots'
    }

    @Unroll
    void "#argument: #description is #outcome on #route"() {
        when:
        List<TestEntity> found = null
        Exception refused = null
        try {
            found = listFor(route, [(argument): value])
        }
        catch (Exception e) {
            refused = e
        }

        then: 'a whole number is used, anything else is refused with the name of the argument'
        accepted ? (refused == null && found) : (refused instanceof IllegalArgumentException && refused.message.contains(argument))

        where:
        [route, argument, description, value, accepted] << [
                ['where.list', 'findAll closure', 'findAllBy', 'paged where'],
                ['timeout', 'fetchSize'],
                [
                        ['the whole number 5', 5, true],
                        ['the whole number 0', 0, true],
                        ['the long 7', 7L, true],
                        ['the double 3.0', 3.0d, true],
                        ['the decimal 4', new BigDecimal('4'), true],
                        ['the text 6', '6', true],
                        ['the padded text 8', ' 8 ', true],
                        ['the text 9.0', '9.0', true],
                        ['the half 0.5', 0.5d, false],
                        ['the float 2.5', 2.5f, false],
                        ['the decimal 1.1', new BigDecimal('1.1'), false],
                        ['the text 0.5', '0.5', false],
                        ['the text lots', 'lots', false],
                        ['an empty text', '', false],
                        ['a number past the largest int', 3000000000L, false],
                        ['a decimal past the largest int', new BigDecimal('1e20'), false],
                        ['not a number', Double.NaN, false],
                        ['infinity', Double.POSITIVE_INFINITY, false]
                ]
        ].combinations().collect { List row -> [row[0], row[1], row[2][0], row[2][1], row[2][2]] }
        outcome = accepted ? 'accepted' : 'refused'
    }

    @Unroll
    void "the total of a paged criteria with flushMode #mode #outcome the pending change"() {
        given: 'a session that flushes by itself, so only the flush mode of the arguments keeps a change out'
        TestEntity.withSession { Session s -> s.setHibernateFlushMode(FlushMode.AUTO) }
        TestEntity.findByName('a').age = 99

        when:
        def page = TestEntity.createCriteria().list(max: 10, flushMode: mode) { eq('age', 99) }

        then: 'the page and the count behind its total read the same data'
        page.size() == expected
        page.totalCount == expected

        where:
        mode     || expected
        'MANUAL' || 0
        'ALWAYS' || 1
        outcome = expected ? 'flushes' : 'leaves out'
    }

    @Unroll
    void "an argument that is null or absent changes nothing on #route"() {
        expect:
        listFor(route, args).size() == 3 || route in ['where.find', 'findBy']

        where:
        route             | args
        'where.list'      | [readOnly: null, flushMode: null, fetchSize: null, timeout: null]
        'findAll closure' | [readOnly: null, flushMode: null, fetchSize: null, timeout: null]
        'findAllBy'       | [readOnly: null, flushMode: null, fetchSize: null, timeout: null]
        'paged where'     | [readOnly: null, flushMode: null, fetchSize: null, timeout: null]
        'where.find'      | [readOnly: null, flushMode: null]
        'findBy'          | [readOnly: null, flushMode: null]
    }
}
