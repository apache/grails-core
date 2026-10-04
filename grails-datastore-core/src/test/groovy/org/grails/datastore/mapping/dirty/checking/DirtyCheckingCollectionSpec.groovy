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
package org.grails.datastore.mapping.dirty.checking

import spock.lang.Specification

/**
 * The DirtyChecking* wrappers are the only change-detection interception-based stores have
 * (there is no Hibernate-style flush-time snapshot comparison), so every mutation path a
 * wrapped collection exposes must mark the parent dirty. Historically only the directly
 * overridden methods (add/remove/addAll/removeAll(Collection)/clear) did; everything that
 * removes through an iterator — including Groovy's removeAll(Closure)/retainAll(Closure)
 * DGM methods and Java's removeIf default method — silently bypassed tracking, so a
 * subsequent save() persisted nothing.
 */
class DirtyCheckingCollectionSpec extends Specification {

    def 'iterator().remove() marks the parent dirty'() {
        given:
        def owner = new CollectionOwner()
        def list = new DirtyCheckingList(['a', 'b', 'c'], owner, 'items')
        owner.trackChanges()

        when:
        Iterator i = list.iterator()
        i.next()
        i.remove()

        then:
        list.size() == 2
        owner.hasChanged()
        owner.hasChanged('items')
    }

    def "Groovy removeAll(Closure) marks the parent dirty"() {
        given:
        def owner = new CollectionOwner()
        def list = new DirtyCheckingList(['a', 'b', 'c'], owner, 'items')
        owner.trackChanges()

        when: 'elements are removed via the DGM closure variant (iterator-based)'
        list.removeAll { it == 'b' }

        then:
        list.size() == 2
        owner.hasChanged()
        owner.hasChanged('items')
    }

    def "Groovy retainAll(Closure) marks the parent dirty"() {
        given:
        def owner = new CollectionOwner()
        def list = new DirtyCheckingList(['a', 'b', 'c'], owner, 'items')
        owner.trackChanges()

        when:
        list.retainAll { it == 'a' }

        then:
        list.size() == 1
        owner.hasChanged()
        owner.hasChanged('items')
    }

    def 'retainAll(Collection) marks the parent dirty'() {
        given:
        def owner = new CollectionOwner()
        def list = new DirtyCheckingList(['a', 'b', 'c'], owner, 'items')
        owner.trackChanges()

        when:
        list.retainAll(['a'])

        then:
        list.size() == 1
        owner.hasChanged()
        owner.hasChanged('items')
    }

    def 'removeIf(Predicate) marks the parent dirty'() {
        given:
        def owner = new CollectionOwner()
        def list = new DirtyCheckingList(['a', 'b', 'c'], owner, 'items')
        owner.trackChanges()

        when:
        list.removeIf { String s -> s == 'c' }

        then:
        list.size() == 2
        owner.hasChanged()
        owner.hasChanged('items')
    }

    def 'listIterator mutations mark the parent dirty'() {
        given:
        def owner = new CollectionOwner()
        def list = new DirtyCheckingList(['a', 'b', 'c'], owner, 'items')
        owner.trackChanges()

        when:
        ListIterator li = list.listIterator()
        li.next()
        li.set('z')

        then:
        list[0] == 'z'
        owner.hasChanged()
        owner.hasChanged('items')

        when:
        owner.trackChanges()
        li = list.listIterator(1)
        li.next()
        li.remove()

        then:
        list.size() == 2
        owner.hasChanged('items')

        when:
        owner.trackChanges()
        li = list.listIterator()
        li.add('new')

        then:
        list.size() == 3
        owner.hasChanged('items')
    }

    def 'sort and replaceAll mark the parent dirty'() {
        given:
        def owner = new CollectionOwner()
        def list = new DirtyCheckingList(['c', 'a', 'b'], owner, 'items')
        owner.trackChanges()

        when:
        list.sort(Comparator.<String> naturalOrder())

        then:
        list[0] == 'a'
        owner.hasChanged('items')

        when:
        owner.trackChanges()
        list.replaceAll { String s -> s.toUpperCase() }

        then:
        list[0] == 'A'
        owner.hasChanged('items')
    }

    def 'iterator removal on a wrapped Set marks the parent dirty'() {
        given:
        def owner = new CollectionOwner()
        def set = new DirtyCheckingSet(['a', 'b'] as Set, owner, 'tags')
        owner.trackChanges()

        when:
        set.removeAll { it == 'a' }

        then:
        set.size() == 1
        owner.hasChanged()
        owner.hasChanged('tags')
    }

    def 'directly overridden mutators still mark the parent dirty'() {
        given:
        def owner = new CollectionOwner()
        def list = new DirtyCheckingList(['a'], owner, 'items')
        owner.trackChanges()

        when:
        list.add('b')

        then:
        owner.hasChanged('items')

        when:
        owner.trackChanges()
        list.remove('a')

        then:
        owner.hasChanged('items')

        when:
        owner.trackChanges()
        list.clear()

        then:
        list.isEmpty()
        owner.hasChanged('items')
    }

    def 'listIterator navigation methods delegate without marking the parent dirty'() {
        given:
        def owner = new CollectionOwner()
        def list = new DirtyCheckingList(['a', 'b'], owner, 'items')
        owner.trackChanges()

        when:
        ListIterator li = list.listIterator()
        li.next()

        then:
        li.hasPrevious()
        li.nextIndex() == 1
        li.previousIndex() == 0
        li.previous() == 'a'
        !owner.hasChanged()
    }

    def 'a wrapped SortedSet tracks iterator removal and carries the assigned flag'() {
        given:
        def owner = new CollectionOwner()
        def sorted = new DirtyCheckingSortedSet(new TreeSet(['a', 'b', 'c']), owner, 'sorted')
        owner.trackChanges()

        expect:
        !sorted.isAssigned()
        new DirtyCheckingSortedSet(new TreeSet(), owner, 'sorted', true).isAssigned()

        when:
        sorted.removeAll { it == 'b' }

        then:
        sorted.size() == 2
        owner.hasChanged('sorted')
    }

    def 'a wrapped Map carries the assigned flag'() {
        given:
        def owner = new CollectionOwner()

        expect:
        !new DirtyCheckingMap([:], owner, 'attrs').isAssigned()
        new DirtyCheckingMap([:], owner, 'attrs', true).isAssigned()
    }

    def 'isAssigned defaults to false for implementations that do not override it'() {
        expect: 'the interface default keeps pre-existing implementations (PersistentCollection) unflagged'
        !new MinimalDirtyCheckableCollection().isAssigned()
    }

    def 'subList mutations mark the parent dirty'() {
        given:
        def owner = new CollectionOwner()
        def list = new DirtyCheckingList(['a', 'b', 'c'], owner, 'items')
        owner.trackChanges()

        when: 'an element is removed through the live subList view'
        list.subList(0, 2).remove('a')

        then:
        list.size() == 2
        owner.hasChanged('items')
    }

    def 'wrap returns the SortedSet wrapper for a SortedSet'() {
        given:
        def owner = new CollectionOwner()

        expect:
        DirtyCheckingSupport.wrap(new TreeSet(['a']), owner, 'sorted') instanceof DirtyCheckingSortedSet
    }

    def 'Map default methods mark the parent dirty'() {
        given:
        def owner = new CollectionOwner()
        def map = new DirtyCheckingMap([a: 1], owner, 'attrs')

        expect:
        marksDirty(owner) { map.putIfAbsent('b', 2) }
        marksDirty(owner) { map.merge('a', 10) { x, y -> x + y } }
        marksDirty(owner) { map.computeIfAbsent('c') { 3 } }
        marksDirty(owner) { map.computeIfPresent('a') { k, v -> v + 1 } }
        marksDirty(owner) { map.compute('a') { k, v -> 99 } }
        marksDirty(owner) { map.replace('a', 100) }
        marksDirty(owner) { map.replace('a', 100, 101) }
        marksDirty(owner) { map.replaceAll { k, v -> v } }
        marksDirty(owner) { map.remove('absent', 0) }
    }

    def 'Map view removals mark the parent dirty'() {
        given:
        def owner = new CollectionOwner()
        def map = new DirtyCheckingMap([a: 1, b: 2, c: 3, d: 4], owner, 'attrs')
        owner.trackChanges()

        when: 'an entry is removed through the entrySet iterator (the path Groovy DGM removal methods use)'
        Iterator entries = map.entrySet().iterator()
        entries.next()
        entries.remove()

        then:
        map.size() == 3
        owner.hasChanged('attrs')

        when:
        owner.trackChanges()
        map.keySet().remove('b')

        then:
        map.size() == 2
        owner.hasChanged('attrs')

        when:
        owner.trackChanges()
        map.values().removeIf { it == 3 }

        then:
        map.size() == 1
        owner.hasChanged('attrs')

        when:
        owner.trackChanges()
        map.entrySet().removeAll { Map.Entry e -> e.key == 'd' }

        then:
        map.isEmpty()
        owner.hasChanged('attrs')
    }

    private static boolean marksDirty(CollectionOwner owner, Closure mutation) {
        owner.trackChanges()
        mutation()
        owner.hasChanged('attrs')
    }

    def 'wrappers are content-equal to plain collections in both directions'() {
        given:
        def owner = new CollectionOwner()
        def list = new DirtyCheckingList(['a', 'b'], owner, 'items')
        def set = new DirtyCheckingSet(['a'] as Set, owner, 'tags')
        def map = new DirtyCheckingMap([a: 1, b: 2], owner, 'attrs')

        expect: 'Groovy == and Java equals both hold, both ways (AbstractPersistentCollection precedent)'
        list == ['a', 'b']
        ['a', 'b'] == list
        list.hashCode() == ['a', 'b'].hashCode()
        set == (['a'] as Set)
        map == [a: 1, b: 2]
        [a: 1, b: 2] == map
        map.hashCode() == [a: 1, b: 2].hashCode()

        and: 'the map views are content-equal to the raw views (what Map equality iterates)'
        map.keySet() == (['a', 'b'] as Set)
        map.entrySet() == [a: 1, b: 2].entrySet()
        map.values() as List == [1, 2]
    }

    def 'SequencedCollection mutators mark the parent dirty'() {
        given: 'without overrides these are @Delegate-generated straight through to the target'
        def owner = new CollectionOwner()
        def list = new DirtyCheckingList(['a', 'b', 'c'], owner, 'items')

        expect:
        marksDirtyOn(owner, 'items') { list.addFirst('z') }
        marksDirtyOn(owner, 'items') { list.addLast('y') }
        marksDirtyOn(owner, 'items') { list.removeFirst() }
        marksDirtyOn(owner, 'items') { list.removeLast() }
    }

    def 'mutating the reversed view marks the parent dirty'() {
        given:
        def owner = new CollectionOwner()
        def list = new DirtyCheckingList(['a', 'b'], owner, 'items')
        owner.trackChanges()

        when: 'the live reverse-ordered view is mutated'
        list.reversed().remove('a')

        then:
        list.size() == 1
        owner.hasChanged('items')
    }

    def 'SortedSet sequenced removals and its reversed view mark the parent dirty'() {
        given:
        def owner = new CollectionOwner()
        def sorted = new DirtyCheckingSortedSet(new TreeSet(['a', 'b', 'c']), owner, 'sorted')

        expect:
        marksDirtyOn(owner, 'sorted') { sorted.removeFirst() }
        marksDirtyOn(owner, 'sorted') { sorted.removeLast() }
        marksDirtyOn(owner, 'sorted') { sorted.reversed().remove('b') }
    }

    def 'SortedSet range views mark the parent dirty'() {
        given: 'headSet/subSet/tailSet are live views that write through to the backing set'
        def owner = new CollectionOwner()
        def sorted = new DirtyCheckingSortedSet(new TreeSet(['a', 'b', 'c', 'd']), owner, 'sorted')

        expect:
        marksDirtyOn(owner, 'sorted') { sorted.headSet('b').remove('a') }
        marksDirtyOn(owner, 'sorted') { sorted.tailSet('d').remove('d') }
        marksDirtyOn(owner, 'sorted') { sorted.subSet('b', 'c').clear() }

        and:
        sorted.size() == 1
    }

    def 'a view stored back onto a property counts as a wholesale replacement'() {
        given: 'the flag decides whether a persister re-encodes or diffs element by element'
        def owner = new CollectionOwner()
        def list = new DirtyCheckingList(['a', 'b', 'c'], owner, 'items')
        def sorted = new DirtyCheckingSortedSet(new TreeSet(['a', 'b']), owner, 'sorted')
        def map = new DirtyCheckingMap([a: 1], owner, 'attrs')

        expect: 'every live view reports itself as assigned; the backing wrapper does not'
        !((DirtyCheckableCollection) list).isAssigned()
        ((DirtyCheckableCollection) list.reversed()).isAssigned()
        ((DirtyCheckableCollection) list.subList(0, 2)).isAssigned()
        ((DirtyCheckableCollection) sorted.reversed()).isAssigned()
        ((DirtyCheckableCollection) sorted.headSet('b')).isAssigned()
        ((DirtyCheckableCollection) map.keySet()).isAssigned()
        ((DirtyCheckableCollection) map.values()).isAssigned()
    }

    def 'a wrapper equals itself even when its target has only identity equality'() {
        given: "values() wraps an AbstractCollection, which inherits Object.equals"
        def owner = new CollectionOwner()
        def values = new DirtyCheckingMap([a: 1], owner, 'attrs').values()
        def overDeque = new DirtyCheckingCollection(new ArrayDeque(['x']), owner, 'items')

        expect: 'reflexive, so List.contains and HashSet membership work on the wrapper'
        values == values
        values.equals(values)
        [values].contains(values)
        overDeque.equals(overDeque)
        [overDeque].contains(overDeque)

        and: 'content equality against the target is unaffected'
        new DirtyCheckingList(['a'], owner, 'items') == ['a']
    }

    def 'wrap returns an already-wrapped map unchanged'() {
        given: "an encoder that write-backs on every save would otherwise nest one wrapper per save"
        def owner = new CollectionOwner()
        def wrapped = DirtyCheckingSupport.wrap([a: 1], owner, 'attrs')

        expect:
        wrapped instanceof DirtyCheckingMap
        DirtyCheckingSupport.wrap(wrapped, owner, 'attrs').is(wrapped)
    }

    private static boolean marksDirtyOn(CollectionOwner owner, String property, Closure mutation) {
        owner.trackChanges()
        mutation()
        owner.hasChanged(property)
    }

    def 'iteration without mutation does not mark the parent dirty'() {
        given:
        def owner = new CollectionOwner()
        def list = new DirtyCheckingList(['a', 'b'], owner, 'items')
        owner.trackChanges()

        when: 'the collection is only read'
        def joined = list.collect { it }.join(',')
        for (def ignored : list) {
            // consume
        }

        then:
        joined == 'a,b'
        !owner.hasChanged()
    }
}

class CollectionOwner implements DirtyCheckable {
    List<String> items
    Set<String> tags
    SortedSet<String> sorted
    Map<String, Object> attrs
}

class MinimalDirtyCheckableCollection implements DirtyCheckableCollection {
    boolean hasChanged() { false }
    int getOriginalSize() { 0 }
    boolean hasGrown() { false }
    boolean hasShrunk() { false }
    boolean hasChangedSize() { false }
}
