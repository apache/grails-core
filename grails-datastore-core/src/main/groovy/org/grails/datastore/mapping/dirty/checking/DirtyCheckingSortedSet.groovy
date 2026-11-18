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

import groovy.transform.CompileStatic

/**
 * Dirty checks sorted sets
 *
 * @author Graeme Rocher
 * @since 5.0
 */
@CompileStatic
class DirtyCheckingSortedSet extends DirtyCheckingCollection implements SortedSet {

    // See the note on DirtyCheckingList: reversed() must not be generated over the override.
    @Delegate(excludes = 'reversed') SortedSet target

    DirtyCheckingSortedSet(SortedSet target, DirtyCheckable parent, String property) {
        this(target, parent, property, false)
    }

    DirtyCheckingSortedSet(SortedSet target, DirtyCheckable parent, String property, boolean assigned) {
        super(target, parent, property, assigned)
        this.target = target
    }

    // SequencedCollection (Java 21) removals; addFirst/addLast are not overridden because
    // SortedSet rejects them outright, so they never reach a mutation to track.
    @Override
    Object removeFirst() {
        parent.markDirty(property)
        target.removeFirst()
    }

    @Override
    Object removeLast() {
        parent.markDirty(property)
        target.removeLast()
    }

    @Override
    SortedSet reversed() {
        return trackingView(target.reversed())
    }

    // headSet/subSet/tailSet are @Delegate-generated live views onto the backing set, so a
    // removal through one writes through while bypassing change tracking entirely.
    @Override
    SortedSet headSet(Object toElement) {
        return trackingView(target.headSet(toElement))
    }

    @Override
    SortedSet tailSet(Object fromElement) {
        return trackingView(target.tailSet(fromElement))
    }

    @Override
    SortedSet subSet(Object fromElement, Object toElement) {
        return trackingView(target.subSet(fromElement, toElement))
    }

    /** A live view that writes through to this set, tracking the same parent. */
    private SortedSet trackingView(SortedSet view) {
        // Flagged as assigned: a view stored on a property IS a wholesale replacement, and a
        // persister must re-encode it rather than diff it element-by-element against what the
        // property held before. The flag is inert for a view that is only traversed.
        return new DirtyCheckingSortedSet(view, parent, property, true)
    }
}
