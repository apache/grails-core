/* Copyright (C) 2011-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.datastore.mapping.collection;

import org.grails.datastore.mapping.core.Session;
import org.grails.datastore.mapping.engine.AssociationQueryExecutor;
import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.datastore.mapping.model.types.Association;
import org.grails.datastore.mapping.query.Query;

import java.io.Serializable;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

/**
 * Abstract base class for persistent collections.
 *
 * @author Burt Beckwith
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public abstract class AbstractPersistentCollection implements PersistentCollection, Serializable {

    protected final transient Session session;
    protected final transient AssociationQueryExecutor indexer;
    protected final transient Class childType;

    protected boolean initialized;
    protected Object initializing;
    protected Serializable associationKey;
    protected Collection keys;
    protected boolean dirty = false;

    protected final Collection collection;
    protected int originalSize;
    protected boolean proxyEntities = false;

    protected AbstractPersistentCollection(Class childType, Session session, Collection collection) {
        this.childType = childType;
        this.collection = collection;
        this.session = session;
        this.initializing = Boolean.FALSE;
        this.initialized = true;
        this.indexer = null;
        markDirty();
    }

    protected AbstractPersistentCollection(final Association association, Serializable associationKey, final Session session, Collection collection) {
        this.collection = collection;
        this.session = session;
        this.associationKey = associationKey;
        this.proxyEntities = association.getMapping().getMappedForm().isLazy();
        this.childType = association.getAssociatedEntity().getJavaClass();
        this.indexer = new AssociationQueryExecutor() {

            @Override
            public boolean doesReturnKeys() {
                return true;
            }

            @Override
            public List query(Object primaryKey) {
                Association inverseSide = association.getInverseSide();
                Query query = session.createQuery(association.getAssociatedEntity().getJavaClass());
                query.eq(inverseSide.getName(), primaryKey);
                query.projections().id();
                return query.list();
            }

            @Override
            public PersistentEntity getIndexedEntity() {
                return association.getAssociatedEntity();
            }
        };
    }

    protected AbstractPersistentCollection(Collection keys, Class childType,
                                           Session session, Collection collection) {
        this.session = session;
        this.keys = keys;
        this.childType = childType;
        this.collection = collection;
        this.indexer = null;
    }

    protected AbstractPersistentCollection(Serializable associationKey, Session session,
                                           AssociationQueryExecutor indexer, Collection collection) {
        this.session = session;
        this.associationKey = associationKey;
        this.indexer = indexer;
        this.collection = collection;
        this.childType = indexer.getIndexedEntity().getJavaClass();
    }

    /**
     * Whether to proxy entities by their keys
     *
     * @param proxyEntities True if you wish to proxy entities
     */
    public void setProxyEntities(boolean proxyEntities) {
        this.proxyEntities = proxyEntities;
    }

    @Override
    public boolean hasChanged() {
        return isDirty();
    }

    @Override
    public int getOriginalSize() {
        return originalSize;
    }

    @Override
    public boolean hasGrown() {
        return isInitialized() && (size() > originalSize);
    }

    @Override
    public boolean hasShrunk() {
        return isInitialized() && (size() < originalSize);
    }

    @Override
    public boolean hasChangedSize() {
        return isInitialized() && (size() != originalSize);
    }

    /* Collection methods */

    public Iterator iterator() {
        initialize();

        final Iterator iterator = collection.iterator();
        return new Iterator() {
            public boolean hasNext() {
                return iterator.hasNext();
            }

            public Object next() {
                return iterator.next();
            }

            public void remove() {
                iterator.remove();
                markDirty();
            }
        };
    }

    public int size() {
        initialize();
        return collection.size();
    }

    public boolean isEmpty() {
        initialize();
        return collection.isEmpty();
    }

    public boolean contains(Object o) {
        initialize();
        return collection.contains(o);
    }

    public boolean add(Object o) {
        initialize();
        boolean added = collection.add(o);
        if (added) {
            markDirty();
        }
        return added;
    }

    public boolean remove(Object o) {
        initialize();
        boolean remove = collection.remove(o);
        if (remove) {
            markDirty();
        }
        return remove;
    }

    public void clear() {
        initialize();
        collection.clear();
        markDirty();
    }

    @Override
    public boolean equals(Object o) {
        initialize();
        return collection.equals(o);
    }

    @Override
    public int hashCode() {
        initialize();
        return collection.hashCode();
    }

    @Override
    public String toString() {
        initialize();
        return collection.toString();
    }

    public boolean removeAll(Collection c) {
        initialize();
        boolean changed = collection.removeAll(c);
        if (changed) {
            markDirty();
        }
        return changed;
    }

    public Object[] toArray() {
        initialize();
        return collection.toArray();
    }

    public Object[] toArray(Object[] a) {
        initialize();
        return collection.toArray(a);
    }

    public boolean containsAll(Collection c) {
        initialize();
        return collection.containsAll(c);
    }

    public boolean addAll(Collection c) {
        initialize();
        boolean changed = collection.addAll(c);
        if (changed) {
            markDirty();
        }
        return changed;
    }

    public boolean retainAll(Collection c) {
        initialize();
        boolean changed = collection.retainAll(c);
        if (changed) {
            markDirty();
        }
        return changed;
    }

    /* PersistentCollection methods */

    public boolean isInitialized() {
        return initialized;
    }

    protected void setInitializing(Boolean initializing) {
        this.initializing = initializing;
    }

    public void initialize() {
        if (initializing != null) {
            return;
        }

        setInitializing(Boolean.TRUE);

        try {
            if (isInitialized()) {
                return;
            }

            final Session session = this.session;
            if (session == null) {
                throw new IllegalStateException("PersistentCollection of type " + this.getClass().getName() + " should have been initialized before serialization.");
            }

            initialized = true;

            final Class childType = this.childType;
            if (associationKey == null) {
                final Collection keys = this.keys;
                if (keys != null) {

                    loadInverseChildKeys(session, childType, keys);
                }
            } else {
                List results = indexer.query(associationKey);
                if (indexer.doesReturnKeys()) {

                    PersistentEntity entity = indexer.getIndexedEntity();

                    // This should really only happen for unit testing since entities are
                    // mocked selectively and may not always be registered in the indexer. In this
                    // case, there can't be any results to be added to the collection.
                    if (entity != null) {
                        loadInverseChildKeys(session, entity.getJavaClass(), results);
                    } else if (childType != null) {
                        loadInverseChildKeys(session, childType, results);
                    }
                } else {
                    addAll(results);
                }
            }
            this.originalSize = size();
        } finally {
            setInitializing(Boolean.FALSE);
        }
    }

    protected void loadInverseChildKeys(Session session, Class childType, Collection keys) {
        if (!keys.isEmpty()) {
            if (proxyEntities) {
                for (Object key : keys) {
                    add(
                            session.proxy(childType, (Serializable) key)
                    );
                }
            } else {
                addAll(session.retrieveAll(childType, keys));
            }
        }
    }

    public boolean isDirty() {
        return dirty;
    }

    public void resetDirty() {
        dirty = false;
    }

    public void markDirty() {
        if (!currentlyInitializing()) {
            dirty = true;
        }
    }

    protected boolean currentlyInitializing() {
        return initializing != null && initializing.equals(Boolean.TRUE);
    }
}
