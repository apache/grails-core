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

class BulkWriteQueryShapesSpec extends HibernateGormDatastoreSpec {

    def setupSpec() {
        manager.registerDomainClasses(BwOwner, BwPet)
    }

    private void seed() {
        BwOwner ned = new BwOwner(name: 'Ned').save(flush: true)
        BwOwner homer = new BwOwner(name: 'Homer').save(flush: true)
        new BwPet(name: 'rex', weight: 10, owner: ned).save(flush: true)
        new BwPet(name: 'fido', weight: 20, owner: ned).save(flush: true)
        new BwPet(name: 'tom', weight: 30, owner: homer).save(flush: true)
        new BwPet(name: 'bart', weight: 40, owner: homer).save(flush: true)
    }

    private Integer weightOf(String name) {
        BwPet.withSession { it.clear() }
        BwPet.findByName(name).weight
    }

    private List<String> names() {
        BwPet.list(sort: 'name')*.name
    }

    void "two subqueries in one bulk delete keep their own parameters"() {
        given:
        seed()
        DetachedCriteria<BwPet> light = new DetachedCriteria(BwPet).build { lt('weight', 25) }.property('name')
        DetachedCriteria<BwPet> notFido = new DetachedCriteria(BwPet).build { ne('name', 'fido') }.property('name')

        when:
        long deleted = new DetachedCriteria(BwPet).build {
            'in'('name', light)
            'in'('name', notFido)
        }.deleteAll()

        then:
        deleted == 1
        names() == ['bart', 'fido', 'tom']
    }

    void "two subqueries in one bulk update keep their own parameters"() {
        given:
        seed()
        DetachedCriteria<BwPet> light = new DetachedCriteria(BwPet).build { lt('weight', 25) }.property('name')
        DetachedCriteria<BwPet> notFido = new DetachedCriteria(BwPet).build { ne('name', 'fido') }.property('name')

        when:
        long updated = new DetachedCriteria(BwPet).build {
            'in'('name', light)
            'in'('name', notFido)
        }.updateAll(weight: 99)

        then:
        updated == 1
        weightOf('rex') == 99
        weightOf('fido') == 20
    }

    void "a subquery with two criteria keeps both in a bulk delete"() {
        given:
        seed()
        DetachedCriteria<BwPet> between = new DetachedCriteria(BwPet).build {
            gt('weight', 15)
            lt('weight', 35)
        }.property('name')

        when:
        long deleted = new DetachedCriteria(BwPet).build { 'in'('name', between) }.deleteAll()

        then:
        deleted == 2
        names() == ['bart', 'rex']
    }

    void "a subquery with two criteria keeps both in a bulk update"() {
        given:
        seed()
        DetachedCriteria<BwPet> between = new DetachedCriteria(BwPet).build {
            gt('weight', 15)
            lt('weight', 35)
        }.property('name')

        when:
        long updated = new DetachedCriteria(BwPet).build { 'in'('name', between) }.updateAll(weight: 0)

        then:
        updated == 2
        weightOf('fido') == 0
        weightOf('tom') == 0
        weightOf('rex') == 10
        weightOf('bart') == 40
    }

    void "a deferred subquery works in a bulk delete"() {
        given:
        seed()
        DetachedCriteria<BwPet> heavy = new DetachedCriteria(BwPet).buildLazy { gt('weight', 25) }.property('name')

        when:
        long deleted = new DetachedCriteria(BwPet).build { 'in'('name', heavy) }.deleteAll()

        then:
        deleted == 2
        names() == ['fido', 'rex']
    }

    void "exists in a bulk delete"() {
        given:
        seed()

        when:
        long deleted = new DetachedCriteria(BwPet).build {
            exists(new DetachedCriteria(BwOwner).build { eq('name', 'Homer') })
            lt('weight', 25)
        }.deleteAll()

        then:
        deleted == 2
        names() == ['bart', 'tom']
    }

    void "notExists in a bulk update"() {
        given:
        seed()

        when:
        long updated = new DetachedCriteria(BwPet).build {
            notExists(new DetachedCriteria(BwOwner).build { eq('name', 'Nobody') })
            eq('name', 'rex')
        }.updateAll(weight: 1)

        then:
        updated == 1
        weightOf('rex') == 1
    }

    void "a to-one association path filters a bulk delete"() {
        given:
        seed()

        when:
        long deleted = BwPet.where { owner.name == 'Ned' }.deleteAll()

        then:
        deleted == 2
        names() == ['bart', 'tom']
    }

    void "a to-one association path filters a bulk update"() {
        given:
        seed()

        when:
        long updated = new DetachedCriteria(BwPet).build { eq('owner.name', 'Homer') }.updateAll(weight: 0)

        then:
        updated == 2
        weightOf('bart') == 0
        weightOf('tom') == 0
        weightOf('rex') == 10
    }

    void "an association alias filters a bulk delete"() {
        given:
        seed()

        when:
        long deleted = new DetachedCriteria(BwPet).build {
            createAlias('owner', 'o')
            eq('o.name', 'Ned')
        }.deleteAll()

        then:
        deleted == 2
        names() == ['bart', 'tom']
    }

    void "an association block filters a bulk delete"() {
        given:
        seed()

        when:
        long deleted = new DetachedCriteria(BwPet).build {
            owner { eq('name', 'Ned') }
        }.deleteAll()

        then:
        deleted == 2
        names() == ['bart', 'tom']
    }

    void "a parameter after an association block keeps its own position in a bulk delete"() {
        given:
        seed()

        when:
        long deleted = new DetachedCriteria(BwPet).build {
            owner { eq('name', 'Ned') }
            lt('weight', 15)
        }.deleteAll()

        then:
        deleted == 1
        names() == ['bart', 'fido', 'tom']
    }

    void "parameters before and after a to-one path keep their own positions in a bulk update"() {
        given:
        seed()

        when:
        long updated = new DetachedCriteria(BwPet).build {
            gt('weight', 15)
            eq('owner.name', 'Homer')
            lt('weight', 35)
        }.updateAll(name: 'renamed')
        BwPet.withSession { it.clear() }

        then:
        updated == 1
        BwPet.countByName('renamed') == 1
        BwPet.findByName('renamed').weight == 30
    }

    void "a to-one path that is a criterion on the whole row with no match changes nothing"() {
        given:
        seed()

        when:
        long deleted = new DetachedCriteria(BwPet).build { eq('owner.name', 'Nobody') }.deleteAll()

        then:
        deleted == 0
        names() == ['bart', 'fido', 'rex', 'tom']
    }

    void "a sql restriction with a placeholder value works in a bulk update"() {
        given:
        seed()

        when:
        long updated = new DetachedCriteria(BwPet).build {
            sqlRestriction('{alias}.weight > ? and {alias}.weight < ?', [15, 35])
        }.updateAll(weight: 0)

        then:
        updated == 2
        weightOf('fido') == 0
        weightOf('tom') == 0
        weightOf('rex') == 10
    }

    void "a sql restriction that quotes a literal works in a bulk delete"() {
        given:
        seed()

        when:
        long deleted = new DetachedCriteria(BwPet).build {
            sqlRestriction("{alias}.name = 'rex'")
        }.deleteAll()

        then:
        deleted == 1
        names() == ['bart', 'fido', 'tom']
    }

    void "the alias placeholder in a sql restriction works in a bulk delete"() {
        given:
        seed()

        when:
        long deleted = new DetachedCriteria(BwPet).build {
            sqlRestriction('{alias}.weight > 25')
        }.deleteAll()

        then:
        deleted == 2
        names() == ['fido', 'rex']
    }
}

@Entity
class BwOwner implements HibernateEntity<BwOwner> {
    String name
}

@Entity
class BwPet implements HibernateEntity<BwPet> {
    String name
    Integer weight
    BwOwner owner
}
