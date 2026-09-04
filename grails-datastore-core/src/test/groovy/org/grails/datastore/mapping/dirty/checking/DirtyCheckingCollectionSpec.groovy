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
}

class MinimalDirtyCheckableCollection implements DirtyCheckableCollection {
    boolean hasChanged() { false }
    int getOriginalSize() { 0 }
    boolean hasGrown() { false }
    boolean hasShrunk() { false }
    boolean hasChangedSize() { false }
}
