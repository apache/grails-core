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
import grails.gorm.annotation.Entity
import org.apache.grails.data.hibernate5.core.GrailsDataHibernate5TckManager
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec
import spock.lang.Issue

/**
 * A path given to createAlias on a DetachedCriteria that starts with an alias declared earlier continues the path
 * of that alias: after createAlias('children', 'c'), the path c.related is children.related.
 */
@Issue('https://github.com/apache/grails-core/issues/14647')
class DetachedCriteriaAliasChainSpec extends GrailsDataTckSpec<GrailsDataHibernate5TckManager> {

    def setupSpec() {
        manager.domainClasses.addAll([DcChainParent, DcChainChild, DcChainRelated, DcChainTag])
    }

    private void seed() {
        DcChainTag blue = new DcChainTag(label: 'blue').save()
        DcChainRelated named = new DcChainRelated(name: 'x', tag: blue).save()
        DcChainRelated unnamed = new DcChainRelated().save()
        new DcChainParent(name: 'with related').addToChildren(new DcChainChild(name: 'c1', related: named)).save()
        new DcChainParent(name: 'without name').addToChildren(new DcChainChild(name: 'c2', related: unnamed)).save()
        new DcChainParent(name: 'none').save(flush: true)
        manager.session.clear()
    }

    def 'an alias whose path starts with an alias declared earlier joins from that alias'() {
        given:
        seed()

        expect: 'the path c.related is the related of the children that c names, as createAlias(children.related) is'
        new DetachedCriteria(DcChainParent).build {
            createAlias('children', 'c')
            createAlias('c.related', 'r')
            isNotNull('r.name')
        }.list()*.name == ['with related']
    }

    def 'an alias that has the name of its association still joins from it'() {
        given:
        seed()

        expect:
        new DetachedCriteria(DcChainParent).build {
            createAlias('children', 'children')
            createAlias('children.related', 'r')
            isNotNull('r.name')
        }.list()*.name == ['with related']
    }

    def 'a chain of three aliases joins each from the one before'() {
        given:
        seed()

        expect:
        new DetachedCriteria(DcChainParent).build {
            createAlias('children', 'c')
            createAlias('c.related', 'r')
            createAlias('r.tag', 't')
            eq('t.label', 'blue')
        }.list()*.name == ['with related']
    }

    def 'a path that continues an alias declared inside #block is refused with the way to declare it'() {
        when:
        new DetachedCriteria(DcChainParent).build(criteria)

        then:
        IllegalArgumentException e = thrown()
        e.message.contains('Declare the alias at the top level')

        where:
        block   | criteria
        'and'   | { and { createAlias('children', 'c'); createAlias('c.related', 'r') } }
        'or'    | { or { createAlias('children', 'c'); createAlias('c.related', 'r') } }
        'not'   | { not { createAlias('children', 'c'); createAlias('c.related', 'r') } }
        'a nested block' | { and { or { createAlias('children', 'c'); createAlias('c.related', 'r') } } }
        'and, with an alias named like a property of the class' | { and { createAlias('children', 'name'); createAlias('name.related', 'r') } }
        'or, with an alias named like a property of the class' | { or { createAlias('children', 'name'); createAlias('name.related', 'r') } }
        'a nested block, with an alias named like a property of the class' | { and { or { createAlias('children', 'name'); createAlias('name.related', 'r') } } }
    }

    def 'an alias declared at the top level and used inside a block still works'() {
        given:
        seed()

        expect:
        new DetachedCriteria(DcChainParent).build {
            createAlias('children', 'c')
            createAlias('c.related', 'r')
            or {
                isNotNull('r.name')
                eq('name', 'without name')
            }
        }.list()*.name.sort() == ['with related', 'without name']
    }

    def 'a path from an alias that continues with a property that is not an association is refused'() {
        when:
        new DetachedCriteria(DcChainParent).build {
            createAlias('children', 'c')
            createAlias('c.name', 'n')
        }

        then:
        thrown(IllegalArgumentException)
    }
}

@Entity
class DcChainTag {
    String label
}

@Entity
class DcChainRelated {
    String name
    DcChainTag tag

    static constraints = {
        name nullable: true
        tag nullable: true
    }
}

@Entity
class DcChainChild {
    String name
    DcChainRelated related
}

@Entity
class DcChainParent {
    String name

    static hasMany = [children: DcChainChild]
}
