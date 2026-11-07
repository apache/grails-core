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

import spock.lang.Issue

import grails.gorm.DetachedCriteria
import grails.gorm.annotation.Entity
import grails.gorm.tests.HibernateGormDatastoreSpec

@Issue('https://github.com/apache/grails-core/issues/16851')
class DetachedCriteriaAliasSpec extends HibernateGormDatastoreSpec {

    def setupSpec() {
        manager.registerDomainClasses(AliasedTeam, AliasedItem)
    }

    private void seed() {
        def first = new AliasedItem(label: 'first').save()
        def second = new AliasedItem(label: 'second').save()
        new AliasedTeam(name: 'both').addToItems(first).addToItems(second).save()
        new AliasedTeam(name: 'first only').addToItems(first).save()
        new AliasedTeam(name: 'second only').addToItems(second).save()
        new AliasedTeam(name: 'none').save(flush: true)
        manager.session.clear()
    }

    def 'two aliases of one association path can be restricted independently'() {
        given:
        seed()
        def criteria = new DetachedCriteria(AliasedTeam).build {
            createAlias('items', 'a')
            createAlias('items', 'b')
            eq('a.label', 'first')
            eq('b.label', 'second')
        }

        expect: 'one item must satisfy the first alias and another the second'
        criteria.list()*.name == ['both']
        criteria.count() == 1
    }

    def 'each of two aliases of one path restricts on its own'() {
        given:
        seed()

        expect:
        new DetachedCriteria(AliasedTeam).build {
            createAlias('items', 'a')
            createAlias('items', 'b')
            eq('a.label', 'first')
        }.list(sort: 'name')*.name == ['both', 'first only']
        new DetachedCriteria(AliasedTeam).build {
            createAlias('items', 'a')
            createAlias('items', 'b')
            eq('b.label', 'second')
        }.list(sort: 'name')*.name == ['both', 'second only']
    }

    def 'declaring the same alias again for its path is still one alias'() {
        given:
        seed()

        expect:
        new DetachedCriteria(AliasedTeam).build {
            createAlias('items', 'a')
            createAlias('items', 'a')
            eq('a.label', 'first')
        }.list(sort: 'name')*.name == ['both', 'first only']
    }

    def 'the order of the two alias declarations does not change what each alias names'() {
        given:
        seed()
        def criteria = new DetachedCriteria(AliasedTeam).build {
            eq('b.label', 'second')
            eq('a.label', 'first')
            createAlias('items', 'a')
            createAlias('items', 'b')
        }

        expect:
        criteria.list()*.name == ['both']
    }
}

@Entity
class AliasedTeam {
    String name

    static hasMany = [items: AliasedItem]
}

@Entity
class AliasedItem {
    String label
}
