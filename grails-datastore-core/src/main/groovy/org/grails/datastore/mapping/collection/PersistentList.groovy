/* Copyright (C) 2010-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the 'License')
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.datastore.mapping.collection

import groovy.transform.CompileStatic

import org.grails.datastore.mapping.core.Session
import org.grails.datastore.mapping.engine.AssociationQueryExecutor
import org.grails.datastore.mapping.model.types.Association

/**
 * A lazy loaded list.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@SuppressWarnings(['rawtypes', 'unchecked'])
@CompileStatic
class PersistentList extends AbstractPersistentCollection implements List {

    private final List list

    PersistentList(Class childType, Session session, List collection) {
        super(childType, session, collection)
        this.list = collection
    }

    PersistentList(Collection keys, Class childType, Session session) {
        super(keys, childType, session, new ArrayList())
        list = (List) collection
    }

    PersistentList(Serializable associationKey, Session session, AssociationQueryExecutor indexer) {
        super(associationKey, session, indexer, new ArrayList())
        list = (List) collection
    }

    PersistentList(Association association, Serializable associationKey, Session session) {
        super(association, associationKey, session, new ArrayList())
        list = (List) collection
    }

    int indexOf(Object o) {
        initialize()
        return list.indexOf(o)
    }

    int lastIndexOf(Object o) {
        initialize()
        return list.lastIndexOf(o)
    }

    Object get(int index) {
        initialize()
        return list.get(index)
    }

    Object set(int index, Object element) {
        initialize()
        Object replaced = list.set(index, element)
        if (!replaced.is(element)) {
            markDirty()
        }
        return replaced
    }

    void add(int index, Object element) {
        initialize()
        list.add(index, element)
        markDirty()
    }

    Object remove(int index) {
        initialize()
        int size = size()
        Object removed = list.remove(index)
        if (size != this.size()) {
            markDirty()
        }
        return removed
    }

    boolean addAll(int index, Collection c) {
        initialize()
        boolean changed = list.addAll(index, c)
        if (changed) {
            markDirty()
        }
        return changed
    }

    ListIterator listIterator() {
        initialize()
        return new PersistentListIterator(list.listIterator())
    }

    ListIterator listIterator(int index) {
        initialize()
        return new PersistentListIterator(list.listIterator(index))
    }

    List subList(int fromIndex, int toIndex) {
        initialize()
        return list.subList(fromIndex, toIndex) // not modification-aware
    }

    private class PersistentListIterator implements ListIterator {

        private final ListIterator iterator

        private PersistentListIterator(ListIterator iterator) {
            this.iterator = iterator
        }

        boolean hasNext() {
            return iterator.hasNext()
        }

        Object next() {
            return iterator.next()
        }

        boolean hasPrevious() {
            return iterator.hasPrevious()
        }

        Object previous() {
            return iterator.previous()
        }

        int nextIndex() {
            return iterator.nextIndex()
        }

        int previousIndex() {
            return iterator.previousIndex()
        }

        void remove() {
            iterator.remove()
            markDirty()
        }

        void set(Object e) {
            iterator.set(e)
            markDirty() // assume changed
        }

        void add(Object e) {
            iterator.add(e)
            markDirty()
        }
    }

}
