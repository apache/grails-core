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

import spock.lang.Issue

import grails.gorm.DetachedCriteria
import grails.gorm.annotation.Entity
import org.grails.datastore.gorm.query.criteria.DetachedAssociationCriteria
import org.apache.grails.data.hibernate5.core.GrailsDataHibernate5TckManager
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec

@Issue('https://github.com/apache/grails-core/issues/16851')
class DetachedCriteriaAliasSpec extends GrailsDataTckSpec<GrailsDataHibernate5TckManager> {

    def setupSpec() {
        manager.domainClasses.addAll([AliasedTeam, AliasedItem, ChainParent, ChainChild, ChainRelated])
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

    private static DetachedAssociationCriteria aliased(DetachedCriteria criteria, String alias) {
        criteria.criteria.find { it instanceof DetachedAssociationCriteria && it.alias == alias } as DetachedAssociationCriteria
    }

    def 'a path that starts with a later alias of an association says which alias it started from'() {
        given:
        DetachedCriteria criteria = new KeepsEveryAliasCriteria(ChainParent)

        when:
        criteria.createAlias('children', 'c1')
        criteria.createAlias('children', 'c2')
        criteria.createAlias('c2.related', 'r')

        then: 'the association path is the one of the association, and the alias it started from is kept'
        aliased(criteria, 'r').associationPath == 'children.related'
        aliased(criteria, 'r').pathFromAlias == 'c2.related'
    }

    def 'a path that starts with the first alias of an association is the path of that association'() {
        given:
        DetachedCriteria criteria = new KeepsEveryAliasCriteria(ChainParent)

        when:
        criteria.createAlias('children', 'c1')
        criteria.createAlias('children', 'c2')
        criteria.createAlias('c1.related', 'r')

        then:
        aliased(criteria, 'r').associationPath == 'children.related'
        aliased(criteria, 'r').pathFromAlias == null
    }

    def 'a datastore that keeps one alias per path has no later alias to start from'() {
        given:
        DetachedCriteria criteria = new DetachedCriteria(ChainParent)

        when:
        criteria.createAlias('children', 'c1')
        criteria.createAlias('children', 'c2')
        criteria.createAlias('c2.related', 'r')

        then: 'the second alias replaced the first, so the path is the path of the one alias there is'
        aliased(criteria, 'r').associationPath == 'children.related'
        aliased(criteria, 'r').pathFromAlias == null
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

    def 'an alias whose path starts with an alias declared earlier joins from that alias'() {
        given:
        new ChainParent(name: 'with related').addToChildren(new ChainChild(name: 'c1', related: new ChainRelated(name: 'x').save())).save()
        new ChainParent(name: 'without name').addToChildren(new ChainChild(name: 'c2', related: new ChainRelated().save())).save(flush: true)
        manager.session.clear()

        expect: 'the path c.related is the related of the children that c names, as createAlias(children.related) is'
        new DetachedCriteria(ChainParent).build {
            createAlias('children', 'c')
            createAlias('c.related', 'r')
            isNotNull('r.name')
        }.list()*.name == ['with related']
    }

    def 'an alias whose path starts with an alias that has the name of its association still joins from it'() {
        given:
        new ChainParent(name: 'with related').addToChildren(new ChainChild(name: 'c1', related: new ChainRelated(name: 'x').save())).save()
        new ChainParent(name: 'without name').addToChildren(new ChainChild(name: 'c2', related: new ChainRelated().save())).save(flush: true)
        manager.session.clear()

        expect:
        new DetachedCriteria(ChainParent).build {
            createAlias('children', 'children')
            createAlias('children.related', 'r')
            isNotNull('r.name')
        }.list()*.name == ['with related']
    }

    def 'the second of two aliases of one association path replaces the first on Hibernate 5'() {
        given:
        seed()

        expect: 'Hibernate 5 joins an association once, and a DetachedCriteria keeps the latest alias of a path'
        new DetachedCriteria(AliasedTeam).build {
            createAlias('items', 'a')
            createAlias('items', 'b')
            eq('b.label', 'second')
        }.list(sort: 'name')*.name == ['both', 'second only']
    }

    def 'a restriction on the replaced alias fails on Hibernate 5'() {
        given:
        seed()

        when:
        new DetachedCriteria(AliasedTeam).build {
            createAlias('items', 'a')
            createAlias('items', 'b')
            eq('a.label', 'first')
        }.list()

        then:
        thrown(org.hibernate.QueryException)
    }
}

class KeepsEveryAliasCriteria extends DetachedCriteria {

    KeepsEveryAliasCriteria(Class targetClass) {
        super(targetClass)
    }

    @Override
    protected boolean keepsEveryAlias() {
        true
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

@Entity
class ChainRelated {
    String name

    static constraints = {
        name nullable: true
    }
}

@Entity
class ChainChild {
    String name
    ChainRelated related
}

@Entity
class ChainParent {
    String name

    static hasMany = [children: ChainChild]
}
