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
package org.grails.datastore.gorm.query.criteria

import grails.gorm.DetachedCriteria
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.datastore.mapping.model.types.Association
import org.grails.datastore.mapping.query.Query
import spock.lang.Specification

class DetachedCriteriaCloneSpec extends Specification {

    void "clone preserves connectionName"() {
        given:
        def criteria = new DetachedCriteria(TestEntity)
        criteria.@connectionName = 'secondary'

        when:
        def cloned = criteria.clone()

        then:
        cloned.@connectionName == 'secondary'
    }

    void "clone preserves lazyQuery"() {
        given:
        def lazy = { -> }
        def criteria = new DetachedCriteria(TestEntity)
        criteria.@lazyQuery = lazy

        when:
        def cloned = criteria.clone()

        then:
        cloned.@lazyQuery.is(lazy)
    }

    void "clone preserves associationCriteriaMap"() {
        given:
        def criteria = new DetachedCriteria(TestEntity)
        def node = associationNode('books', 'b')
        criteria.@associationCriteriaMap['books'] = node

        when:
        def cloned = criteria.clone()

        then:
        cloned.@associationCriteriaMap.size() == 1
        cloned.@associationCriteriaMap['books'].associationPath == 'books'
        cloned.@associationCriteriaMap['books'].alias == 'b'
    }

    void "clone creates independent copy of associationCriteriaMap"() {
        given:
        def criteria = new DetachedCriteria(TestEntity)
        criteria.@associationCriteriaMap['books'] = associationNode('books', 'b')

        when:
        def cloned = criteria.clone()
        cloned.@associationCriteriaMap['authors'] = associationNode('authors', 'a')

        then:
        criteria.@associationCriteriaMap.size() == 1
        cloned.@associationCriteriaMap.size() == 2
    }

    void "withConnection followed by max preserves connectionName"() {
        given:
        def criteria = new DetachedCriteria(TestEntity)

        when:
        def withConn = criteria.withConnection('secondary')
        def withMax = withConn.max(10)

        then:
        withConn.@connectionName == 'secondary'
        withMax.@connectionName == 'secondary'
        withMax.defaultMax == 10
    }

    void "clone preserves default connectionName"() {
        given:
        def criteria = new DetachedCriteria(TestEntity)

        when:
        def cloned = criteria.clone()

        then:
        cloned.@connectionName == ConnectionSource.DEFAULT
    }

    void "clone preserves existing fields"() {
        given:
        def criteria = new DetachedCriteria(TestEntity)
        criteria.@defaultMax = 50
        criteria.@defaultOffset = 10

        when:
        def cloned = criteria.clone()

        then:
        cloned.defaultMax == 50
        cloned.defaultOffset == 10
    }

    void "clone preserves alias"() {
        given:
        def criteria = new DetachedCriteria(TestEntity, 't')

        when:
        def cloned = criteria.clone()

        then:
        cloned.alias == 't'
    }

    void "clone copies association criteria so the original nodes are never shared"() {
        given:
        def node = associationNode('books', 'b')
        def criteria = new DetachedCriteria(TestEntity)
        criteria.@criteria << node
        criteria.@associationCriteriaMap['books'] = node

        when:
        def cloned = criteria.clone()
        DetachedAssociationCriteria copy = cloned.@associationCriteriaMap['books']

        then: 'the copy is a distinct association node that keeps its identity'
        !copy.is(node)
        copy instanceof DetachedAssociationCriteria
        copy.association.is(node.association)
        copy.associationPath == 'books'
        copy.alias == 'b'

        and: 'the criteria list and the association map of the copy refer to the same node'
        cloned.@criteria.size() == 1
        cloned.@criteria[0].is(copy)
    }

    void "changing a copied association node does not reach the original"() {
        given:
        def node = associationNode('books', 'b')
        def criteria = new DetachedCriteria(TestEntity)
        criteria.@criteria << node
        criteria.@associationCriteriaMap['books'] = node

        when:
        def cloned = criteria.clone()
        DetachedAssociationCriteria copy = cloned.@associationCriteriaMap['books']
        copy.setAlias('changed')
        copy.add(new Query.Equals('title', 'x'))

        then:
        node.alias == 'b'
        node.criteria.isEmpty()
        copy.alias == 'changed'
        copy.criteria.size() == 1
    }

    void "clone copies nested association nodes"() {
        given:
        def inner = associationNode('author', 'a')
        def outer = associationNode('books', 'b')
        outer.@criteria << inner
        outer.@associationCriteriaMap['author'] = inner
        def criteria = new DetachedCriteria(TestEntity)
        criteria.@criteria << outer
        criteria.@associationCriteriaMap['books'] = outer

        when:
        def cloned = criteria.clone()
        DetachedAssociationCriteria outerCopy = cloned.@associationCriteriaMap['books']
        DetachedAssociationCriteria innerCopy = outerCopy.@associationCriteriaMap['author']

        then:
        !outerCopy.is(outer)
        !innerCopy.is(inner)
        outerCopy.@criteria[0].is(innerCopy)
        innerCopy.alias == 'a'
    }

    void "materializeLazyCriteria evaluates lazy alias on cloned instance without mutating original"() {
        given:
        def criteria = new DetachedCriteria(TestEntity).buildLazy {
            setAlias('lazyAlias')
        }

        when:
        def materialized = criteria.materializeLazyCriteria()

        then:
        materialized.alias == 'lazyAlias'
        criteria.alias == null
    }

    void "materializing a deferred closure that changes an association node leaves the original untouched"() {
        given:
        def node = associationNode('books', 'b')
        def base = new DetachedCriteria(TestEntity)
        base.@criteria << node
        base.@associationCriteriaMap['books'] = node
        def lazy = base.buildLazy {
            def books = associationCriteriaMap['books']
            books.setAlias('deferred')
            books.add(new Query.Equals('title', 'x'))
        }

        when:
        def first = lazy.materializeLazyCriteria()
        def second = lazy.materializeLazyCriteria()

        then: 'each materialization applies the deferred closure once to its own copy'
        first.@associationCriteriaMap['books'].criteria.size() == 1
        second.@associationCriteriaMap['books'].criteria.size() == 1
        !first.@associationCriteriaMap['books'].is(second.@associationCriteriaMap['books'])

        and: 'the original node is never changed'
        node.alias == 'b'
        node.criteria.isEmpty()
    }

    void "clone copies an association node inside #junctionClass.simpleName through the same map as the association map"() {
        given:
        def node = associationNode('books', 'b')
        Query.Junction junction = junctionClass.getDeclaredConstructor().newInstance()
        Query.Criterion plain = new Query.Equals('title', 'x')
        junction.add(node)
        junction.add(plain)
        def criteria = new DetachedCriteria(TestEntity)
        criteria.@criteria << junction
        criteria.@associationCriteriaMap['books'] = node

        when:
        def cloned = criteria.clone()
        Query.Junction junctionCopy = cloned.@criteria[0]
        DetachedAssociationCriteria copy = cloned.@associationCriteriaMap['books']

        then: 'the junction is a copy of the same kind and holds the copied node, the one the association map holds'
        !junctionCopy.is(junction)
        junctionCopy.class == junctionClass
        junctionCopy.criteria[0].is(copy)
        !copy.is(node)

        and: 'a criterion that cannot change is shared'
        junctionCopy.criteria[1].is(plain)

        and: 'the original junction still holds the original node'
        junction.criteria[0].is(node)

        where:
        junctionClass << [Query.Conjunction, Query.Disjunction, Query.Negation]
    }

    void "a restriction added to the association map copy reaches the node inside the junction copy"() {
        given:
        def node = associationNode('books', 'b')
        def criteria = new DetachedCriteria(TestEntity)
        criteria.@criteria << new Query.Disjunction().add(node)
        criteria.@associationCriteriaMap['books'] = node

        when:
        def cloned = criteria.clone()
        cloned.@associationCriteriaMap['books'].add(new Query.Equals('title', 'wanted'))
        DetachedAssociationCriteria inTree = ((Query.Junction) cloned.@criteria[0]).criteria[0]

        then:
        inTree.criteria.size() == 1
        node.criteria.isEmpty()
    }

    void "clone copies junctions nested in junctions"() {
        given:
        def node = associationNode('books', 'b')
        def inner = new Query.Negation().add(node)
        def outer = new Query.Conjunction().add(inner)
        def criteria = new DetachedCriteria(TestEntity)
        criteria.@criteria << outer
        criteria.@associationCriteriaMap['books'] = node

        when:
        def cloned = criteria.clone()
        Query.Junction outerCopy = cloned.@criteria[0]
        Query.Junction innerCopy = outerCopy.criteria[0]

        then:
        !outerCopy.is(outer)
        !innerCopy.is(inner)
        innerCopy instanceof Query.Negation
        innerCopy.criteria[0].is(cloned.@associationCriteriaMap['books'])
    }

    void "the deferred closure runs once and each materialization is a distinct copy"() {
        given:
        int runs = 0
        def criteria = new DetachedCriteria(TestEntity).buildLazy {
            runs++
            setAlias('deferred')
        }

        when:
        def first = criteria.materializeLazyCriteria()
        def second = criteria.materializeLazyCriteria()

        then:
        runs == 1
        !first.is(second)
        first.alias == 'deferred'
        second.alias == 'deferred'

        and: 'the original keeps its deferred query and is not changed'
        criteria.alias == null
        criteria.@lazyQuery != null
    }

    void "changing a materialized copy does not change the next materialization"() {
        given:
        def criteria = new DetachedCriteria(TestEntity).buildLazy {
            setAlias('deferred')
        }

        when:
        def first = criteria.materializeLazyCriteria()
        first.@criteria << new Query.Equals('title', 'x')
        first.setAlias('changed')
        def second = criteria.materializeLazyCriteria()

        then:
        second.alias == 'deferred'
        second.@criteria.isEmpty()
    }

    private DetachedAssociationCriteria associationNode(String path, String alias) {
        Association association = Stub(Association) {
            getName() >> path
        }
        new DetachedAssociationCriteria(TestEntity, association, path, alias)
    }
}

class TestEntity {
    Long id
    String name
}
