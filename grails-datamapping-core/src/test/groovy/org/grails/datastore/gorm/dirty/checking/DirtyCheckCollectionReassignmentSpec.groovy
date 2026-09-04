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

import org.grails.datastore.mapping.dirty.checking.DirtyCheckableCollection
import org.grails.datastore.mapping.dirty.checking.DirtyCheckable
import org.grails.datastore.mapping.dirty.checking.DirtyCheckingCollection
import org.grails.datastore.mapping.dirty.checking.DirtyCheckingMap
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

    def setupSpec() {
        def gcl = new GroovyClassLoader()
        entityClass = gcl.parseClass('''
package org.grails.datastore.gorm.dirty.checking

import grails.gorm.dirty.checking.DirtyCheck

@DirtyCheck
class ScheduleLike {
    List<String> shares = []
    Set<String> tags = new HashSet<String>()
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
