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
import grails.gorm.tests.HibernateGormDatastoreSpec

/**
 * An alias whose path starts with an alias that itself continued from another alias keeps the whole chain: after
 * {@code createAlias('children', 'c2')}, {@code createAlias('c2.related', 'r')} and
 * {@code createAlias('r.related', 'rr')}, the alias {@code rr} names the leaf of the related that {@code r} names,
 * which belongs to the child that {@code c2} names. An alias may also be declared inside an {@code and} block, and
 * a later alias of the block may continue from an earlier one.
 */
class DetachedCriteriaAliasChainSpec extends HibernateGormDatastoreSpec {

    def setupSpec() {
        manager.registerDomainClasses(DepParent, DepChild, DepRelated, DepLeaf)
    }

    private void seed() {
        new DepParent(name: 'split')
                .addToChildren(new DepChild(name: 'one'))
                .addToChildren(new DepChild(name: 'two', related: new DepRelated(leaf: new DepLeaf(name: 'x').save()).save()))
                .save()
        new DepParent(name: 'wrong')
                .addToChildren(new DepChild(name: 'one', related: new DepRelated(leaf: new DepLeaf(name: 'x').save()).save()))
                .addToChildren(new DepChild(name: 'two'))
                .save(flush: true)
        manager.session.clear()
    }

    def 'a chain of three aliases from the second alias of an association stays on that alias'() {
        given:
        seed()

        expect: 'rr is the leaf of the related of the child c2 names; the child c1 names has none for the first parent'
        new DetachedCriteria(DepParent).build {
            createAlias('children', 'c1')
            createAlias('children', 'c2')
            createAlias('c2.related', 'r')
            createAlias('r.leaf', 'rr')
            eq('c1.name', 'one')
            eq('c2.name', 'two')
            eq('rr.name', 'x')
        }.list()*.name == ['split']
    }

    def 'a chain of three aliases from the second alias stays on it when the query is built by the criteria builder'() {
        given:
        seed()

        expect:
        DepParent.withCriteria {
            createAlias('children', 'c1')
            createAlias('children', 'c2')
            createAlias('c2.related', 'r')
            createAlias('r.leaf', 'rr')
            eq('c1.name', 'one')
            eq('c2.name', 'two')
            eq('rr.name', 'x')
        }*.name == ['split']
    }

    def 'a property path that continues past the second alias of an association stays on that alias'() {
        given:
        seed()

        expect: 'c2.related.leaf.name is the leaf of the child c2 names, so the parent whose other child has that leaf is not returned'
        new DetachedCriteria(DepParent).build {
            createAlias('children', 'c1')
            createAlias('children', 'c2')
            eq('c1.name', 'one')
            eq('c2.name', 'two')
            eq('c2.related.leaf.name', 'x')
        }.list()*.name == ['split']
        new DetachedCriteria(DepParent).build {
            createAlias('children', 'c1')
            createAlias('children', 'c2')
            createAlias('c2.related', 'r')
            eq('c1.name', 'one')
            eq('c2.name', 'two')
            eq('r.leaf.name', 'x')
        }.list()*.name == ['split']
    }

    def 'a property path that continues past the second alias of an association stays on it in the criteria builder'() {
        given:
        seed()

        expect:
        DepParent.withCriteria {
            createAlias('children', 'c1')
            createAlias('children', 'c2')
            eq('c1.name', 'one')
            eq('c2.name', 'two')
            eq('c2.related.leaf.name', 'x')
        }*.name == ['split']
    }

    def 'a chain of three aliases from the first alias of an association stays on that alias'() {
        given:
        seed()

        expect:
        new DetachedCriteria(DepParent).build {
            createAlias('children', 'c1')
            createAlias('children', 'c2')
            createAlias('c1.related', 'r')
            createAlias('r.leaf', 'rr')
            eq('c1.name', 'one')
            eq('c2.name', 'two')
            eq('rr.name', 'x')
        }.list()*.name == ['wrong']
    }

    def 'an alias may continue from an alias declared earlier in the same and block'() {
        given:
        seed()

        expect:
        new DetachedCriteria(DepParent).build {
            and {
                createAlias('children', 'c')
                createAlias('c.related', 'r')
            }
            eq('c.name', 'two')
            isNotNull('r.id')
        }.list()*.name == ['split']
    }

    def 'an alias may continue from an alias declared earlier in the same and block of the criteria builder'() {
        given:
        seed()

        expect:
        DepParent.withCriteria {
            and {
                createAlias('children', 'c')
                createAlias('c.related', 'r')
            }
            eq('c.name', 'two')
            isNotNull('r.id')
        }*.name == ['split']
    }

    def 'an alias in an or block that continues from an earlier alias of the block is found by either branch'() {
        given:
        seed()

        expect:
        new DetachedCriteria(DepParent).build {
            or {
                createAlias('children', 'c')
                createAlias('c.related', 'r')
                eq('c.name', 'nobody')
                eq('r.leaf.name', 'x')
            }
        }.list(sort: 'name')*.name == ['split', 'wrong']
    }
}

@Entity
class DepLeaf {
    String name
}

@Entity
class DepRelated {
    DepLeaf leaf
}

@Entity
class DepChild {
    String name
    DepRelated related

    static constraints = {
        related nullable: true
    }
}

@Entity
class DepParent {
    String name

    static hasMany = [children: DepChild]
}
