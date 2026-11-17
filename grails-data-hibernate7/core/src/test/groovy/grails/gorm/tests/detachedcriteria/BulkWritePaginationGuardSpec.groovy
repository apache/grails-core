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
package grails.gorm.tests.detachedcriteria

import grails.gorm.annotation.Entity
import grails.gorm.hibernate.HibernateEntity
import grails.gorm.tests.HibernateGormDatastoreSpec
import spock.lang.Unroll

import org.springframework.dao.InvalidDataAccessResourceUsageException

class BulkWritePaginationGuardSpec extends HibernateGormDatastoreSpec {

    def setupSpec() {
        manager.registerDomainClasses(BulkGuardEntity)
    }

    private void seed() {
        (1..4).each { new BulkGuardEntity(name: "n${it}", amount: it).save(flush: true) }
    }

    void "deleteAll with max throws and deletes nothing"() {
        given:
        seed()

        when:
        BulkGuardEntity.where { amount > 0 }.max(2).deleteAll()

        then:
        InvalidDataAccessResourceUsageException e = thrown()
        e.message.contains('max')
        e.message.contains('2')
        BulkGuardEntity.count() == 4
    }

    void "deleteAll with offset throws and deletes nothing"() {
        given:
        seed()

        when:
        BulkGuardEntity.where { amount > 0 }.offset(1).deleteAll()

        then:
        InvalidDataAccessResourceUsageException e = thrown()
        e.message.contains('offset')
        BulkGuardEntity.count() == 4
    }

    void "deleteAll with max and offset names both values"() {
        given:
        seed()

        when:
        BulkGuardEntity.where { amount > 0 }.max(1).offset(2).deleteAll()

        then:
        InvalidDataAccessResourceUsageException e = thrown()
        e.message.contains('max') && e.message.contains('offset')
        BulkGuardEntity.count() == 4
    }

    void "updateAll with max throws and updates nothing"() {
        given:
        seed()

        when:
        BulkGuardEntity.where { amount > 0 }.max(2).updateAll(name: 'changed')

        then:
        thrown(InvalidDataAccessResourceUsageException)
        BulkGuardEntity.countByName('changed') == 0
    }

    void "updateAll with offset throws and updates nothing"() {
        given:
        seed()

        when:
        BulkGuardEntity.where { amount > 0 }.offset(3).updateAll(name: 'changed')

        then:
        thrown(InvalidDataAccessResourceUsageException)
        BulkGuardEntity.countByName('changed') == 0
    }

    void "a criteria derived from one that set max still throws"() {
        given:
        seed()

        when:
        BulkGuardEntity.where { amount > 0 }.max(2).build { lt('amount', 4) }.deleteAll()

        then:
        thrown(InvalidDataAccessResourceUsageException)
        BulkGuardEntity.count() == 4
    }

    void "selecting the ids with the limit and deleting by id removes only those rows"() {
        given:
        seed()

        when:
        def ids = BulkGuardEntity.where { amount > 0 }.max(2).property('id').list()
        long deleted = BulkGuardEntity.where { id in ids }.deleteAll()

        then:
        ids.size() == 2
        deleted == 2
        BulkGuardEntity.count() == 2
    }

    void "deleteAll and updateAll without pagination still affect every match"() {
        given:
        seed()

        when:
        def updated = BulkGuardEntity.where { amount <= 2 }.updateAll(name: 'changed')
        def deleted = BulkGuardEntity.where { amount > 2 }.deleteAll()

        then:
        updated == 2
        deleted == 2
        BulkGuardEntity.count() == 2
        BulkGuardEntity.countByName('changed') == 2
    }
}

@Entity
class BulkGuardEntity implements HibernateEntity<BulkGuardEntity> {
    String name
    Integer amount
}
