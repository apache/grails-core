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

import java.util.function.BiFunction
import java.util.function.Function

import groovy.transform.CompileStatic

/**
 * A map that can be dirty checked
 *
 * @author Graeme Rocher
 * @since 4.1
 */
@CompileStatic
class DirtyCheckingMap implements Map, DirtyCheckableCollection {

    final @Delegate Map target
    final DirtyCheckable parent
    final String property
    final int originalSize
    final boolean assigned

    DirtyCheckingMap(Map target, DirtyCheckable parent, String property) {
        this(target, parent, property, false)
    }

    DirtyCheckingMap(Map target, DirtyCheckable parent, String property, boolean assigned) {
        this.target = target
        this.parent = parent
        this.property = property
        this.originalSize = target.size()
        this.assigned = assigned
    }

    @Override
    boolean isAssigned() {
        return assigned
    }

    @Override
    boolean hasGrown() {
        return size() > originalSize
    }

    @Override
    boolean hasShrunk() {
        return size() < originalSize
    }

    @Override
    boolean hasChangedSize() {
        return size() != originalSize
    }

    boolean hasChanged() {
        parent.hasChanged(property)
    }

    // Content equality, like AbstractPersistentCollection — see DirtyCheckingCollection.
    // Identity first: a target with no content equality of its own (values() wraps an
    // AbstractCollection, which inherits Object.equals) must still equal itself, or
    // List.contains and HashSet membership break on the wrapper.
    @Override
    boolean equals(Object other) {
        other.is(this) || target.equals(other)
    }

    @Override
    int hashCode() {
        target.hashCode()
    }

    @Override
    Object put(Object key, Object value) {
        parent.markDirty(property)
        target.put(key, value)
    }

    @Override
    Object remove(Object key) {
        parent.markDirty(property)
        target.remove(key)
    }

    @Override
    void putAll(Map m) {
        parent.markDirty(property)
        target.putAll(m)
    }

    @Override
    void clear() {
        parent.markDirty(property)
        target.clear()
    }

    @Override
    Object putIfAbsent(Object key, Object value) {
        parent.markDirty(property)
        target.putIfAbsent(key, value)
    }

    @Override
    Object merge(Object key, Object value, BiFunction remappingFunction) {
        parent.markDirty(property)
        target.merge(key, value, remappingFunction)
    }

    @Override
    Object compute(Object key, BiFunction remappingFunction) {
        parent.markDirty(property)
        target.compute(key, remappingFunction)
    }

    @Override
    Object computeIfAbsent(Object key, Function mappingFunction) {
        parent.markDirty(property)
        target.computeIfAbsent(key, mappingFunction)
    }

    @Override
    Object computeIfPresent(Object key, BiFunction remappingFunction) {
        parent.markDirty(property)
        target.computeIfPresent(key, remappingFunction)
    }

    @Override
    Object replace(Object key, Object value) {
        parent.markDirty(property)
        target.replace(key, value)
    }

    @Override
    boolean replace(Object key, Object oldValue, Object newValue) {
        parent.markDirty(property)
        target.replace(key, oldValue, newValue)
    }

    @Override
    void replaceAll(BiFunction function) {
        parent.markDirty(property)
        target.replaceAll(function)
    }

    @Override
    boolean remove(Object key, Object value) {
        parent.markDirty(property)
        target.remove(key, value)
    }

    /**
     * The three views write through to the map, so they are returned wrapped in the tracking
     * collection types — Groovy's {@code Map.removeAll(Closure)}/{@code retainAll(Closure)}
     * iterate {@code entrySet()}, and {@code keySet().remove(...)} / {@code values().removeIf(...)}
     * are ordinary removal paths. Mutating an entry's value directly during iteration
     * ({@code Map.Entry.setValue}) remains untracked.
     */
    @Override
    Set entrySet() {
        return new DirtyCheckingSet(target.entrySet(), parent, property, true)
    }

    @Override
    Set keySet() {
        return new DirtyCheckingSet(target.keySet(), parent, property, true)
    }

    @Override
    Collection values() {
        return new DirtyCheckingCollection(target.values(), parent, property, true)
    }
}
