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

import java.util.function.UnaryOperator

import groovy.transform.CompileStatic

/**
 * Wrapper list to dirty check a list and mark a parent as dirty
 *
 * @author Graeme Rocher
 * @since 4.1
 */
@CompileStatic
class DirtyCheckingList extends DirtyCheckingCollection implements List {

    @Delegate List target

    DirtyCheckingList(List target, DirtyCheckable parent, String property) {
        this(target, parent, property, false)
    }

    DirtyCheckingList(List target, DirtyCheckable parent, String property, boolean assigned) {
        super(target, parent, property, assigned)
        this.target = target
    }

    @Override
    boolean addAll(int index, Collection c) {
        parent.markDirty(property)
        target.addAll(index, c)
    }

    @Override
    Object set(int index, Object element) {
        parent.markDirty(property)
        target.set(index, element)
    }

    @Override
    void add(int index, Object element) {
        parent.markDirty(property)
        target.add(index, element)
    }

    @Override
    Object remove(int index) {
        parent.markDirty(property)
        target.remove((int) index)
    }

    @Override
    void sort(Comparator c) {
        parent.markDirty(property)
        target.sort(c)
    }

    @Override
    void replaceAll(UnaryOperator operator) {
        parent.markDirty(property)
        target.replaceAll(operator)
    }

    @Override
    Iterator iterator() {
        // Route through the dirty-marking iterator (Groovy DGM removal methods iterate)
        return super.iterator()
    }

    @Override
    ListIterator listIterator() {
        trackingListIterator(target.listIterator())
    }

    @Override
    ListIterator listIterator(int index) {
        trackingListIterator(target.listIterator(index))
    }

    /** A list iterator whose mutating operations mark the parent dirty (see {@link DirtyCheckingCollection#iterator()}). */
    private ListIterator trackingListIterator(final ListIterator underlying) {
        return new ListIterator() {
            @Override
            boolean hasNext() { underlying.hasNext() }

            @Override
            Object next() { underlying.next() }

            @Override
            boolean hasPrevious() { underlying.hasPrevious() }

            @Override
            Object previous() { underlying.previous() }

            @Override
            int nextIndex() { underlying.nextIndex() }

            @Override
            int previousIndex() { underlying.previousIndex() }

            @Override
            void remove() {
                parent.markDirty(property)
                underlying.remove()
            }

            @Override
            void set(Object o) {
                parent.markDirty(property)
                underlying.set(o)
            }

            @Override
            void add(Object o) {
                parent.markDirty(property)
                underlying.add(o)
            }
        }
    }
}
