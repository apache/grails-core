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

import grails.gorm.DetachedCriteria
import grails.gorm.annotation.Entity
import grails.gorm.hibernate.HibernateEntity
import grails.gorm.tests.HibernateGormDatastoreSpec

/**
 * A property of an association compared with a property of the queried entity, as
 * {@code eqProperty('owner.age', 'age')}, keeps each side on its own entity when the criteria are written by
 * {@code deleteAll} and {@code updateAll}: the right side is the age of the pet, not that of the owner, so the
 * bulk statement changes the rows that {@code list} returns for the same criteria.
 */
class BulkWritePropertyComparisonSpec extends HibernateGormDatastoreSpec {

    def setupSpec() {
        manager.registerDomainClasses(BcOwner, BcPet)
    }

    private void seed() {
        BcOwner ned = new BcOwner(name: 'Ned', age: 40, height: 40).save(flush: true)
        BcOwner homer = new BcOwner(name: 'Homer', age: 50, height: 60).save(flush: true)
        new BcPet(name: 'young', age: 2, owner: ned).save(flush: true)
        new BcPet(name: 'sameAge', age: 40, owner: ned).save(flush: true)
        new BcPet(name: 'homers', age: 3, owner: homer).save(flush: true)
        manager.session.clear()
    }

    private List<String> names() {
        BcPet.withSession { it.clear() }
        BcPet.list(sort: 'name')*.name
    }

    void 'a bulk delete compares a property of the association with a property of the queried entity'() {
        given:
        seed()
        DetachedCriteria<BcPet> criteria = new DetachedCriteria(BcPet).build { eqProperty('owner.age', 'age') }

        expect: 'list finds the pet that has the age of its owner'
        criteria.list()*.name == ['sameAge']

        when:
        long deleted = criteria.deleteAll()

        then: 'the pet aged 2 of an owner aged 40 stays'
        deleted == 1
        names() == ['homers', 'young']
    }

    void 'a bulk update compares a property of the association with a property of the queried entity'() {
        given:
        seed()
        DetachedCriteria<BcPet> criteria = new DetachedCriteria(BcPet).build { gtProperty('owner.age', 'age') }

        expect:
        criteria.list()*.name.sort() == ['homers', 'young']

        when:
        long updated = criteria.updateAll(name: 'older')

        then:
        updated == 2
        names() == ['older', 'older', 'sameAge']
    }

    void 'a bulk delete compares two properties of the same association on the association'() {
        given:
        seed()
        DetachedCriteria<BcPet> criteria = new DetachedCriteria(BcPet).build { eqProperty('owner.age', 'owner.height') }

        expect: 'every pet of the owner whose age is his height'
        criteria.list()*.name.sort() == ['sameAge', 'young']

        when:
        long deleted = criteria.deleteAll()

        then:
        deleted == 2
        names() == ['homers']
    }
}

@Entity
class BcOwner implements HibernateEntity<BcOwner> {
    String name
    Integer age
    Integer height
}

@Entity
class BcPet implements HibernateEntity<BcPet> {
    String name
    Integer age
    BcOwner owner
}
