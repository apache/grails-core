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
package org.grails.datastore.mapping.collection

import spock.lang.Specification

import org.grails.datastore.mapping.config.Property
import org.grails.datastore.mapping.core.Session
import org.grails.datastore.mapping.engine.AssociationQueryExecutor
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.PropertyMapping
import org.grails.datastore.mapping.model.types.Association
import org.grails.datastore.mapping.query.Query

class PersistentCollectionsSpec extends Specification {

    Session session = Mock(Session)

    void "collections wrapping an existing collection start initialized and dirty"() {
        when:
        PersistentSet set = new PersistentSet(String, session, ['a', 'b'] as Set)
        PersistentList list = new PersistentList(String, session, ['a', 'b'])
        PersistentSortedSet sorted = new PersistentSortedSet(String, session, new TreeSet(['b', 'a']))

        then:
        [set, list, sorted].every { it.initialized && it.dirty && it.hasChanged() }
        set == ['a', 'b'] as Set
        list == ['a', 'b']
        sorted.toList() == ['a', 'b']
        set.hashCode() == (['a', 'b'] as Set).hashCode()
        list.toString() == '[a, b]'
        0 * session._
    }

    void "collections created from keys load their members lazily from the session"() {
        given:
        PersistentSet set = new PersistentSet([1L, 2L], String, session)

        expect:
        !set.initialized
        !set.dirty
        set.originalSize == 0

        when:
        int size = set.size()

        then:
        1 * session.retrieveAll(String, [1L, 2L]) >> ['one', 'two']
        size == 2
        set.initialized
        !set.dirty
        set.originalSize == 2
        !set.hasGrown()
        !set.hasShrunk()
        !set.hasChangedSize()

        when: 'initialization happens only once'
        set.initialize()
        set.contains('one')

        then:
        0 * session.retrieveAll(*_)
    }

    void "collections created from keys can proxy their members"() {
        given:
        PersistentList list = new PersistentList([1L, 2L], String, session)
        list.proxyEntities = true

        when:
        list.initialize()

        then:
        1 * session.proxy(String, 1L) >> 'p1'
        1 * session.proxy(String, 2L) >> 'p2'
        0 * session.retrieveAll(*_)
        list == ['p1', 'p2']
    }

    void "an empty key collection does not touch the session"() {
        given:
        PersistentSet set = new PersistentSet([], String, session)

        when:
        set.initialize()

        then:
        0 * session._
        set.initialized
        set.empty
    }

    void "collections backed by an indexer load keys or entities"() {
        given:
        PersistentEntity entity = Stub(PersistentEntity) { getJavaClass() >> String }
        AssociationQueryExecutor indexer = Mock(AssociationQueryExecutor) {
            getIndexedEntity() >> entity
            doesReturnKeys() >> returnsKeys
        }
        PersistentSet set = new PersistentSet(9L, session, indexer)

        when:
        List members = set.toList()

        then:
        1 * indexer.query(9L) >> results
        (returnsKeys ? 1 : 0) * session.retrieveAll(String, [1L]) >> ['one']
        members == ['one']
        !set.dirty

        where:
        returnsKeys | results
        true        | [1L]
        false       | ['one']
    }

    void "indexer collections fall back to the child type when the indexed entity disappears"() {
        given:
        PersistentEntity entity = Stub(PersistentEntity) { getJavaClass() >> String }
        AssociationQueryExecutor indexer = Stub(AssociationQueryExecutor) {
            getIndexedEntity() >>> [entity, null]
            doesReturnKeys() >> true
            query(9L) >> [1L]
        }
        PersistentSortedSet set = new PersistentSortedSet(9L, session, indexer)

        when:
        set.initialize()

        then:
        1 * session.retrieveAll(String, [1L]) >> ['one']
        set.first() == 'one'
    }

    void "association collections query the inverse side for keys"() {
        given:
        PersistentEntity child = Stub(PersistentEntity) { getJavaClass() >> String }
        Association inverse = Stub(Association) { getName() >> 'owner' }
        Association association = Stub(Association) {
            getAssociatedEntity() >> child
            getInverseSide() >> inverse
            getMapping() >> Stub(PropertyMapping) { getMappedForm() >> Stub(Property) { isLazy() >> lazy } }
        }
        Query query = Mock(Query)
        Query.ProjectionList projections = Mock(Query.ProjectionList)
        PersistentList list = new PersistentList(association, 3L, session)

        expect:
        !list.initialized

        when:
        list.initialize()

        then:
        1 * session.createQuery(String) >> query
        1 * query.eq('owner', 3L)
        1 * query.projections() >> projections
        1 * projections.id()
        1 * query.list() >> [7L]
        (lazy ? 1 : 0) * session.proxy(String, 7L) >> 'proxied'
        (lazy ? 0 : 1) * session.retrieveAll(String, [7L]) >> ['loaded']
        list == [lazy ? 'proxied' : 'loaded']

        where:
        lazy << [true, false]
    }

    void "association sets and sorted sets are created the same way"() {
        given:
        PersistentEntity child = Stub(PersistentEntity) { getJavaClass() >> String }
        Association association = Stub(Association) {
            getAssociatedEntity() >> child
            getInverseSide() >> Stub(Association) { getName() >> 'owner' }
            getMapping() >> Stub(PropertyMapping) { getMappedForm() >> Stub(Property) }
        }
        Query query = Stub(Query) {
            projections() >> Stub(Query.ProjectionList)
            list() >> [2L, 1L]
        }
        session.createQuery(String) >> query
        session.retrieveAll(String, [2L, 1L]) >> ['b', 'a']

        when:
        PersistentSet set = new PersistentSet(association, 1L, session)
        PersistentSet explicit = new PersistentSet(association, 1L, session, new LinkedHashSet())
        PersistentSortedSet sorted = new PersistentSortedSet(association, 1L, session)

        then:
        set == ['a', 'b'] as Set
        explicit.toList() == ['b', 'a']
        sorted.toList() == ['a', 'b']
        sorted.comparator() == null
        sorted.first() == 'a'
        sorted.last() == 'b'
        sorted.headSet('b').toList() == ['a']
        sorted.tailSet('b').toList() == ['b']
        sorted.subSet('a', 'b').toList() == ['a']
    }

    void "an uninitialized collection without a session cannot be initialized"() {
        given:
        PersistentSet set = new PersistentSet([1L], String, null)

        when:
        set.size()

        then:
        IllegalStateException e = thrown()
        e.message == 'PersistentCollection of type ' + PersistentSet.name + ' should have been initialized before serialization.'
    }

    void "mutations mark the collection dirty only when something changed"() {
        given:
        session.retrieveAll(String, [1L]) >> ['a']
        PersistentSet set = new PersistentSet([1L], String, session)

        when:
        set.initialize()
        set.resetDirty()

        then:
        !set.dirty

        expect:
        !set.add('a') && !set.dirty
        set.add('b') && set.dirty
        set.hasGrown() && set.hasChangedSize()
        set.resetDirty() == null && !set.dirty
        !set.remove('zzz') && !set.dirty
        set.remove('b') && set.dirty
        set.resetDirty() == null
        !set.addAll(['a']) && !set.dirty
        set.addAll(['c']) && set.dirty
        set.resetDirty() == null
        !set.removeAll(['zzz']) && !set.dirty
        set.removeAll(['c']) && set.dirty
        set.resetDirty() == null
        !set.retainAll(['a']) && !set.dirty
        set.retainAll([]) && set.dirty
        set.hasShrunk()
        set.resetDirty() == null

        when:
        set.clear()

        then:
        set.dirty
        set.empty
        set.containsAll([])
        set.toArray().length == 0
        set.toArray(new String[0]).length == 0

        when:
        set.resetDirty()
        set.markDirty()

        then:
        set.dirty
    }

    void "iterators mark the collection dirty on remove"() {
        given:
        session.retrieveAll(String, [1L, 2L]) >> ['a', 'b']
        PersistentSet set = new PersistentSet([1L, 2L], String, session)
        set.initialize()

        when:
        Iterator iterator = set.iterator()
        List seen = []
        while (iterator.hasNext()) {
            seen << iterator.next()
            iterator.remove()
        }

        then:
        seen == ['a', 'b']
        set.empty
        set.dirty
    }

    void "persistent lists delegate positional operations and track changes"() {
        given:
        session.retrieveAll(String, [1L, 2L]) >> ['a', 'b']
        PersistentList list = new PersistentList([1L, 2L], String, session)

        expect:
        list.get(0) == 'a'
        list.indexOf('b') == 1
        list.lastIndexOf('a') == 0
        list.subList(0, 1) == ['a']
        !list.dirty

        when:
        String replaced = list.set(0, 'a')

        then:
        replaced == 'a'
        !list.dirty

        when:
        replaced = list.set(0, 'z')

        then:
        replaced == 'a'
        list.dirty

        when:
        list.resetDirty()
        list.add(1, 'y')

        then:
        list == ['z', 'y', 'b']
        list.dirty

        when:
        list.resetDirty()
        String removed = list.remove(1)

        then:
        removed == 'y'
        list.dirty

        when:
        list.resetDirty()
        boolean changed = list.addAll(0, [])

        then:
        !changed
        !list.dirty

        when:
        changed = list.addAll(0, ['x'])

        then:
        changed
        list.dirty
        list == ['x', 'z', 'b']
    }

    void "persistent list iterators track modifications"() {
        given:
        session.retrieveAll(String, [1L, 2L]) >> ['a', 'b']
        PersistentList list = new PersistentList([1L, 2L], String, session)
        list.initialize()

        when:
        ListIterator iterator = list.listIterator()

        then:
        !list.dirty
        iterator.hasNext()
        !iterator.hasPrevious()
        iterator.nextIndex() == 0
        iterator.next() == 'a'
        iterator.previousIndex() == 0
        iterator.hasPrevious()

        when:
        iterator.set('A')

        then:
        list.dirty
        list[0] == 'A'

        when:
        list.resetDirty()
        iterator.add('inserted')

        then:
        list.dirty
        list == ['A', 'inserted', 'b']

        when:
        list.resetDirty()
        ListIterator fromIndex = list.listIterator(2)
        fromIndex.next() == 'b'
        fromIndex.remove()

        then:
        list.dirty
        list == ['A', 'inserted']
        fromIndex.previous() == 'inserted'
    }

}
