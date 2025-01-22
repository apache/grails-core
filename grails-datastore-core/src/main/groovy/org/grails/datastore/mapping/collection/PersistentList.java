/* Copyright (C) 2010 SpringSource
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.datastore.mapping.collection;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.ListIterator;

import org.grails.datastore.mapping.core.Session;
import org.grails.datastore.mapping.engine.AssociationQueryExecutor;
import org.grails.datastore.mapping.model.types.Association;

/**
 * A lazy loaded list.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public class PersistentList extends AbstractPersistentCollection implements List {

    private final List list;

    public PersistentList(Class childType, Session session, List collection) {
        super(childType, session, collection);
        this.list = collection;
    }

    public PersistentList(Collection keys, Class childType, Session session) {
        super(keys, childType, session, new ArrayList());
        list = (List)collection;
    }

    public PersistentList(Serializable associationKey, Session session, AssociationQueryExecutor indexer) {
        super(associationKey, session, indexer, new ArrayList());
        list = (List)collection;
    }

    public PersistentList(Association association, Serializable associationKey, Session session) {
        super(association, associationKey, session, new ArrayList());
        list = (List)collection;
    }

    public int indexOf(Object o) {
        initialize();
        return list.indexOf(o);
    }

    public int lastIndexOf(Object o) {
        initialize();
        return list.lastIndexOf(o);
    }

    public Object get(int index) {
        initialize();
        return list.get(index);
    }

    public Object set(int index, Object element) {
        initialize();
        Object replaced = list.set(index, element);
        if (replaced != element) {
            markDirty();
        }
        return replaced;
    }

    public void add(int index, Object element) {
        initialize();
        list.add(index, element);
        markDirty();
    }

    public Object remove(int index) {
        initialize();
        int size = size();
        Object removed = list.remove(index);
        if (size != size()) {
            markDirty();
        }
        return removed;
    }

    public boolean addAll(int index, Collection c) {
        initialize();
        boolean changed = list.addAll(index, c);
        if (changed) {
            markDirty();
        }
        return changed;
    }

    public ListIterator listIterator() {
        initialize();
        return new PersistentListIterator(list.listIterator());
    }

    public ListIterator listIterator(int index) {
        initialize();
        return new PersistentListIterator(list.listIterator(index));
    }

    public List subList(int fromIndex, int toIndex) {
        initialize();
        return list.subList(fromIndex, toIndex); // not modification-aware
    }

    private class PersistentListIterator implements ListIterator {

        private final ListIterator iterator;

        private PersistentListIterator(ListIterator iterator) {
            this.iterator = iterator;
        }

        public boolean hasNext() {
            return iterator.hasNext();
        }

        public Object next() {
            return iterator.next();
        }

        public boolean hasPrevious() {
            return iterator.hasPrevious();
        }

        public Object previous() {
            return iterator.previous();
        }

        public int nextIndex() {
            return iterator.nextIndex();
        }

        public int previousIndex() {
            return iterator.previousIndex();
        }

        public void remove() {
            iterator.remove();
            markDirty();
        }

        public void set(Object e) {
            iterator.set(e);
            markDirty(); // assume changed
        }

        public void add(Object e) {
            iterator.add(e);
            markDirty();
        }
    }
}
