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
package org.grails.datastore.gorm.dirty.checking

import org.grails.datastore.mapping.collection.PersistentList
import org.grails.datastore.mapping.collection.PersistentSet
import org.grails.datastore.mapping.collection.PersistentSortedSet
import org.grails.datastore.mapping.core.Session
import org.grails.datastore.mapping.dirty.checking.DirtyCheckableCollection
import org.grails.datastore.mapping.dirty.checking.DirtyCheckable
import org.grails.datastore.mapping.dirty.checking.DirtyCheckingCollection
import org.grails.datastore.mapping.dirty.checking.DirtyCheckingList
import org.grails.datastore.mapping.dirty.checking.DirtyCheckingMap
import org.grails.datastore.mapping.dirty.checking.DirtyCheckingSortedSet
import org.grails.datastore.mapping.dirty.checking.DirtyCheckingSupport

import spock.lang.Shared
import spock.lang.Specification

/**
 * Interception-based stores (MongoDB et al.) install DirtyChecking* wrappers on collection
 * properties when an entity is decoded, and rely on them exclusively — there is no flush-time
 * snapshot comparison. The generated dirty-checking setter, however, used to store whatever
 * raw value it was handed, so reassigning a collection property replaced the tracked wrapper
 * with a plain untracked collection and every later in-place mutation became invisible to
 * {@code hasChanged()}; a subsequent save() persisted nothing.
 *
 * <p>The observed real-world shape (schedule sharing): an empty tracked list is falsy in
 * Groovy, so the common defensive re-init {@code if (!entity.shares) entity.shares = []}
 * always replaced the tracked wrapper, and because {@code [] == []} the equality-suppressed
 * markDirty never even flagged the assignment. The add() that followed was silently lost.
 *
 * <p>The setter must therefore re-wrap: when the value being replaced was tracked, the
 * replacement collection is wrapped too. A property that was never tracked (a transient
 * instance, or a store like Hibernate that never installs these wrappers) is left untouched.
 */
class DirtyCheckCollectionReassignmentSpec extends Specification {

    @Shared
    Class entityClass

    @Shared
    Class keyedEntityClass

    def setupSpec() {
        def gcl = new GroovyClassLoader()
        keyedEntityClass = gcl.parseClass('''
package org.grails.datastore.gorm.dirty.checking

import grails.gorm.dirty.checking.DirtyCheck

@DirtyCheck
class KeyedScheduleLike {
    String businessKey
    List<String> shares = []

    boolean equals(Object o) {
        o instanceof KeyedScheduleLike && ((KeyedScheduleLike) o).businessKey == businessKey
    }

    int hashCode() { businessKey == null ? 0 : businessKey.hashCode() }
}
''')
        entityClass = gcl.parseClass('''
package org.grails.datastore.gorm.dirty.checking

import grails.gorm.dirty.checking.DirtyCheck

@DirtyCheck
class ScheduleLike {
    List<String> shares = []
    Set<String> tags = new HashSet<String>()
    SortedSet<String> ranked = new TreeSet<String>()
    Map<String, String> attributes = [:]
}
''')
    }

    def 'reassigning an equal plain list over a tracked list keeps tracking (falsy empty re-init)'() {
        given: 'an entity whose collection is tracked, as it is after a datastore decode'
        def entity = entityClass.newInstance()
        entity.shares = DirtyCheckingSupport.wrap([], (DirtyCheckable) entity, 'shares')
        entity.trackChanges()

        when: 'the common defensive re-init runs (true for an EMPTY tracked list — Groovy falsy)'
        if (!entity.shares) {
            entity.shares = []
        }

        and: 'an element is added in place'
        entity.shares.add('new-share')

        then: 'the replacement collection is still tracked and the mutation was recorded'
        entity.shares instanceof DirtyCheckableCollection
        ((DirtyCheckableCollection) entity.shares).isAssigned() // replacement, not decode — persisters must not diff per element
        entity.hasChanged()
        entity.hasChanged('shares')
        entity.shares.contains('new-share')
    }

    def 'reassigning a different plain list over a tracked list keeps tracking'() {
        given:
        def entity = entityClass.newInstance()
        entity.shares = DirtyCheckingSupport.wrap(['a'], (DirtyCheckable) entity, 'shares')
        entity.trackChanges()

        when:
        entity.shares = ['b']

        then: 'the assignment itself is flagged (values differ)'
        entity.hasChanged('shares')
        entity.shares instanceof DirtyCheckableCollection

        when: 'changes are reset and the list is mutated in place'
        entity.trackChanges()
        entity.shares.add('c')

        then:
        entity.hasChanged()
        entity.hasChanged('shares')
    }

    def 'reassigning a plain set over a tracked set keeps tracking'() {
        given:
        def entity = entityClass.newInstance()
        entity.tags = DirtyCheckingSupport.wrap(new HashSet(), (DirtyCheckable) entity, 'tags')
        entity.trackChanges()

        when:
        if (!entity.tags) {
            entity.tags = new HashSet()
        }
        entity.tags.add('tag')

        then:
        entity.tags instanceof DirtyCheckableCollection
        entity.hasChanged('tags')
    }

    def 'reassigning a plain map over a tracked map keeps tracking'() {
        given:
        def entity = entityClass.newInstance()
        entity.attributes = new DirtyCheckingMap([:], (DirtyCheckable) entity, 'attributes')
        entity.trackChanges()

        when:
        if (!entity.attributes) {
            entity.attributes = [:]
        }
        entity.attributes.put('k', 'v')

        then:
        entity.attributes instanceof DirtyCheckableCollection
        entity.hasChanged('attributes')
    }

    def 'reassigning a plain SortedSet over a tracked SortedSet keeps tracking and the SortedSet API'() {
        given:
        def entity = entityClass.newInstance()
        entity.ranked = DirtyCheckingSupport.wrap(new TreeSet(), (DirtyCheckable) entity, 'ranked')
        entity.trackChanges()

        when:
        if (!entity.ranked) {
            entity.ranked = new TreeSet()
        }
        entity.ranked.add('r1')

        then: 'the replacement is the SortedSet wrapper, so the property keeps its declared API'
        entity.ranked instanceof DirtyCheckingSortedSet
        entity.ranked instanceof SortedSet
        ((DirtyCheckableCollection) entity.ranked).isAssigned()
        entity.hasChanged('ranked')
    }

    def 'a store-specific wrapper subclass is never replaced by a generic rewrap'() {
        given: 'a value tracked by a store-specific subclass, as the Neo4j store installs'
        def entity = entityClass.newInstance()
        entity.shares = new StoreSpecificList([], (DirtyCheckable) entity, 'shares')
        entity.trackChanges()

        when: 'the property is reassigned wholesale'
        entity.shares = ['a']

        then: 'the raw value is stored so the store persister can install its own type on save'
        !(entity.shares instanceof DirtyCheckableCollection)
        entity.hasChanged('shares')
    }

    def "assigning another entity's tracked list re-binds it to the entity being assigned to"() {
        given: 'two entities, each holding a decode-installed wrapper of its own'
        def a = entityClass.newInstance()
        def b = entityClass.newInstance()
        a.shares = DirtyCheckingSupport.wrap(['a1'], (DirtyCheckable) a, 'shares')
        b.shares = DirtyCheckingSupport.wrap(['b1'], (DirtyCheckable) b, 'shares')
        a.trackChanges()
        b.trackChanges()

        when: "b's collection is assigned onto a"
        a.shares = b.shares

        then: 'a holds a NEW wrapper bound to a, flagged as the wholesale replacement it is'
        !a.shares.is(b.shares)
        a.shares.getParent().is(a)
        ((DirtyCheckableCollection) a.shares).isAssigned()

        when: 'changes are reset and the collection is mutated through a'
        a.trackChanges()
        b.trackChanges()
        a.shares.add('added')

        then: 'a is marked dirty and b is left alone — before, it was the other way round'
        a.hasChanged('shares')
        !b.hasChanged('shares')

        and: 'the two properties still share one underlying collection, as a plain assignment does'
        b.shares.contains('added')
    }

    def "assigning another entity's tracked map re-binds it too"() {
        given: 'the Map wrapper shares no supertype with the collection wrappers'
        def a = entityClass.newInstance()
        def b = entityClass.newInstance()
        a.attributes = new DirtyCheckingMap([x: '1'], (DirtyCheckable) a, 'attributes')
        b.attributes = new DirtyCheckingMap([y: '2'], (DirtyCheckable) b, 'attributes')
        a.trackChanges()
        b.trackChanges()

        when:
        a.attributes = b.attributes

        then:
        !a.attributes.is(b.attributes)
        a.attributes.getParent().is(a)
        ((DirtyCheckableCollection) a.attributes).isAssigned()

        when:
        a.trackChanges()
        b.trackChanges()
        a.attributes.put('k', 'v')

        then:
        a.hasChanged('attributes')
        !b.hasChanged('attributes')
    }

    def "a store-specific wrapper borrowed from another entity is stored untouched"() {
        given: 'the value being replaced is tracked, so the re-binding branch is reached'
        def a = entityClass.newInstance()
        def b = entityClass.newInstance()
        a.shares = DirtyCheckingSupport.wrap([], (DirtyCheckable) a, 'shares')
        def storeSpecific = new StoreSpecificList(['x'], (DirtyCheckable) b, 'shares')
        a.trackChanges()

        when:
        a.shares = storeSpecific

        then: 'never swapped for a generic wrapper — the store re-wraps it on save'
        a.shares.is(storeSpecific)
    }

    def "the same-owner check compares identity, not equals"() {
        given: 'two distinct entities that are equal by business key'
        def a1 = keyedEntityClass.newInstance()
        def a2 = keyedEntityClass.newInstance()
        a1.businessKey = 'same'
        a2.businessKey = 'same'
        assert a1 == a2
        assert !a1.is(a2)
        a1.shares = DirtyCheckingSupport.wrap(['x'], (DirtyCheckable) a1, 'shares')
        a1.trackChanges()

        when: "a wrapper owned by the equal-but-distinct a2 is assigned to a1"
        a1.shares = DirtyCheckingSupport.wrap(['y'], (DirtyCheckable) a2, 'shares')

        then: "it is re-bound to a1 rather than mistaken for a1's own"
        a1.shares.getParent().is(a1)
        ((DirtyCheckableCollection) a1.shares).isAssigned()
    }

    def "a wrapper nested inside another is unwrapped all the way to the raw collection"() {
        given: "an encoder can leave a wrapper wrapping a wrapper (BasicCollectionTypeEncoder does)"
        def a = entityClass.newInstance()
        def b = entityClass.newInstance()
        def raw = ['x']
        def nested = new DirtyCheckingList(
                new DirtyCheckingList(raw, (DirtyCheckable) b, 'shares'), (DirtyCheckable) b, 'shares')
        a.shares = DirtyCheckingSupport.wrap([], (DirtyCheckable) a, 'shares')
        a.trackChanges()
        b.trackChanges()

        when:
        a.shares = nested

        then: "re-bound onto the raw collection, not onto b's inner wrapper"
        a.shares.getTarget().is(raw)

        when:
        a.shares.add('added')

        then: "so a mutation marks only a — an inner wrapper would have marked b as well"
        a.hasChanged('shares')
        !b.hasChanged('shares')
    }

    def "a collection borrowed onto a property that was never tracked is still re-bound"() {
        given: "the shape of new Entity(shares: other.shares) — the field starts out null"
        def a = entityClass.newInstance()
        def b = entityClass.newInstance()
        b.shares = DirtyCheckingSupport.wrap(['b1'], (DirtyCheckable) b, 'shares')
        a.shares = null
        a.trackChanges()
        b.trackChanges()

        when:
        a.shares = b.shares

        then: "a wrapper's existence proves a store already tracks it, just for the wrong owner"
        a.shares.getParent().is(a)

        when:
        a.shares.add('added')

        then:
        a.hasChanged('shares')
        !b.hasChanged('shares')
    }

    def "a plain collection replacing a datastore #kind is re-wrapped"() {
        given: "the class a stored to-many decodes into, rather than one of the generic wrappers"
        def entity = entityClass.newInstance()
        entity[property] = decoded(Mock(Session))
        entity.trackChanges()

        when: "the defensive re-init replaces it and the result is mutated in place"
        entity[property] = replacement()
        entity[property].add('a')

        then:
        entity[property] instanceof DirtyCheckableCollection
        ((DirtyCheckableCollection) entity[property]).isAssigned()
        entity.hasChanged(property)

        where:
        kind                  | property | decoded                                                  | replacement
        'PersistentList'      | 'shares' | { Session s -> new PersistentList([], String, s) }       | { -> [] }
        'PersistentSet'       | 'tags'   | { Session s -> new PersistentSet([], String, s) }        | { -> new HashSet() }
        'PersistentSortedSet' | 'ranked' | { Session s -> new PersistentSortedSet([], String, s) }  | { -> new TreeSet() }
    }

    def "a store-specific PersistentCollection subclass is left to its store"() {
        given: "the shape of the Neo4j and Rx to-many types, which subclass the datastore ones"
        def entity = entityClass.newInstance()
        entity.shares = new StoreSpecificPersistentList([], String, Mock(Session))
        entity.trackChanges()

        when:
        entity.shares = ['a']

        then: "the exact-class rule leaves it raw, so the store wraps it in its own type on save"
        !(entity.shares instanceof DirtyCheckableCollection)

        and: "the assignment is still flagged, which the store persister relies on to re-wrap it"
        entity.hasChanged('shares')
    }

    def 'a property that was never tracked is left untouched by the setter'() {
        given: 'a transient instance whose initializer collections were never wrapped'
        def entity = entityClass.newInstance()
        entity.trackChanges()

        when:
        entity.shares = ['a']

        then: 'the raw value is stored as-is (Hibernate and transient behaviour unchanged)'
        !(entity.shares instanceof DirtyCheckableCollection)
        entity.hasChanged('shares')
    }

    def 'assigning an already-tracked wrapper over a tracked value passes it through unchanged'() {
        given:
        def entity = entityClass.newInstance()
        entity.shares = DirtyCheckingSupport.wrap([], (DirtyCheckable) entity, 'shares')
        entity.trackChanges()

        when: 'a decode-style wrapper is assigned, as a datastore decoder does'
        def decoded = DirtyCheckingSupport.wrap(['x'], (DirtyCheckable) entity, 'shares')
        entity.shares = decoded

        then: 'it is stored as-is and NOT re-flagged as an assignment'
        entity.shares.is(decoded)
        !((DirtyCheckableCollection) entity.shares).isAssigned()
    }

    def 'rewrap wraps a plain non-List non-Set collection replacing a tracked value'() {
        given:
        def entity = entityClass.newInstance()
        def tracked = DirtyCheckingSupport.wrap([], (DirtyCheckable) entity, 'shares')

        when:
        def result = DirtyCheckingSupport.rewrap((DirtyCheckable) entity, 'shares', tracked, new ArrayDeque<String>(['q']))

        then:
        result instanceof DirtyCheckingCollection
        ((DirtyCheckableCollection) result).isAssigned()
    }

    def 'rewrap leaves a non-collection value untouched'() {
        given:
        def entity = entityClass.newInstance()
        def tracked = DirtyCheckingSupport.wrap([], (DirtyCheckable) entity, 'shares')

        expect: 'defensive tail — a value that is neither Collection nor Map is returned as-is'
        DirtyCheckingSupport.rewrap((DirtyCheckable) entity, 'shares', tracked, 'not-a-collection') == 'not-a-collection'
    }

    def 'assigning null over a tracked collection stores null'() {
        given:
        def entity = entityClass.newInstance()
        entity.shares = DirtyCheckingSupport.wrap(['a'], (DirtyCheckable) entity, 'shares')
        entity.trackChanges()

        when:
        entity.shares = null

        then:
        entity.shares == null
        entity.hasChanged('shares')
    }
}

class StoreSpecificList extends DirtyCheckingList {
    StoreSpecificList(List target, DirtyCheckable parent, String property) {
        super(target, parent, property)
    }
}

class StoreSpecificPersistentList extends PersistentList {
    StoreSpecificPersistentList(Collection keys, Class childType, Session session) {
        super(keys, childType, session)
    }
}
