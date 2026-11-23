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

import grails.gorm.DetachedCriteria
import org.apache.grails.data.hibernate5.core.GrailsDataHibernate5TckManager
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec
import org.apache.grails.data.testing.tck.domains.TestEntity
import org.springframework.dao.InvalidDataAccessResourceUsageException
import spock.lang.Issue

/**
 * A bulk write has no LIMIT or OFFSET, so a criteria with max or an offset, zero included, is refused and writes nothing.
 */
@Issue('https://github.com/apache/grails-core/issues/16832')
class BulkWritePaginationGuardSpec extends GrailsDataTckSpec<GrailsDataHibernate5TckManager> {

    void setupSpec() {
        manager.domainClasses.addAll([TestEntity])
    }

    void setup() {
        ['a', 'b', 'c'].each { new TestEntity(name: it, age: 40).save(failOnError: true) }
        manager.session.flush()
        manager.session.clear()
    }

    private static DetachedCriteria<TestEntity> named(String name) {
        new DetachedCriteria(TestEntity).build { eq('name', name) }
    }

    private static int rows() {
        TestEntity.count()
    }

    private static int olderRows() {
        TestEntity.countByAge(41)
    }

    void "deleteAll refuses max and deletes nothing"() {
        when:
        TestEntity.where { age > 0 }.max(1).deleteAll()

        then:
        InvalidDataAccessResourceUsageException e = thrown()
        e.message.contains('max(1)')
        rows() == 3
    }

    void "updateAll refuses an offset above 0 and updates nothing"() {
        when:
        TestEntity.where { age > 0 }.offset(1).updateAll(age: 41)

        then:
        InvalidDataAccessResourceUsageException e = thrown()
        e.message.contains('offset(1)')
        olderRows() == 0
    }

    void "deleteAll refuses a negative offset and deletes nothing"() {
        when:
        TestEntity.where { age > 0 }.offset(-1).deleteAll()

        then:
        InvalidDataAccessResourceUsageException e = thrown()
        e.message.contains('offset(-1)')
        rows() == 3
    }

    void "max and offset together are both named"() {
        when:
        TestEntity.where { age > 0 }.max(2).offset(1).deleteAll()

        then:
        InvalidDataAccessResourceUsageException e = thrown()
        e.message.contains('max(2)')
        e.message.contains('offset(1)')
        rows() == 3
    }

    void "a lazy where query and a derived criteria are refused too"() {
        when:
        TestEntity.whereLazy { age > 0 }.max(1).deleteAll()

        then:
        thrown(InvalidDataAccessResourceUsageException)

        when:
        TestEntity.where { age > 0 }.max(1).where { name != 'z' }.deleteAll()

        then:
        thrown(InvalidDataAccessResourceUsageException)

        when:
        new DetachedCriteria(TestEntity).build { gt('age', 0) }.max(2).updateAll(age: 41)

        then:
        thrown(InvalidDataAccessResourceUsageException)
        rows() == 3
        olderRows() == 0
    }

    void "an offset of 0 is refused like any other offset, since a bulk write cannot apply one"() {
        when:
        named('a').offset(0).updateAll(age: 41)

        then:
        InvalidDataAccessResourceUsageException e = thrown()
        e.message.contains('offset(0)')

        and:
        olderRows() == 0
        rows() == 3
    }

    void "deleteAll with an offset of 0 is refused and deletes nothing"() {
        when:
        TestEntity.where { age > 0 }.offset(0).deleteAll()

        then:
        InvalidDataAccessResourceUsageException e = thrown()
        e.message.contains('offset(0)')
        rows() == 3
    }

    void "without max and offset the matching rows are written"() {
        expect:
        named('a').updateAll(age: 41) == 1
        new DetachedCriteria(TestEntity).build { eq('age', 40) }.deleteAll() == 2
        rows() == 1
    }
}
