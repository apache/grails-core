/* Copyright (C) 2011-2025 the original author or authors.
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
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.types.Association
import org.grails.datastore.mapping.query.Query

/**
 * Abstract base class for persistent collections.
 *
 * @author Burt Beckwith
 */
@SuppressWarnings(['rawtypes', 'unchecked'])
@CompileStatic
abstract class AbstractPersistentCollection implements PersistentCollection, Serializable {

    protected final transient Session session
    protected final transient AssociationQueryExecutor indexer
    protected final transient Class childType

    protected boolean initialized
    protected Object initializing
    protected Serializable associationKey
    protected Collection keys
    protected boolean dirty = false

    protected final Collection collection
    protected int originalSize
    protected boolean proxyEntities = false

    protected AbstractPersistentCollection(Class childType, Session session, Collection collection) {
        this.childType = childType
        this.collection = collection
        this.session = session
        this.initializing = Boolean.FALSE
        this.initialized = true
        this.indexer = null
        markDirty()
    }

    protected AbstractPersistentCollection(final Association association, Serializable associationKey, final Session session, Collection collection) {
        this.collection = collection
        this.session = session
        this.associationKey = associationKey
        this.proxyEntities = association.getMapping().getMappedForm().isLazy()
        this.childType = association.getAssociatedEntity().getJavaClass()
        this.indexer = new AssociationQueryExecutor() {

            @Override
            boolean doesReturnKeys() {
                return true
            }

            @Override
            List query(Object primaryKey) {
                Association inverseSide = association.getInverseSide()
                Query query = session.createQuery(association.getAssociatedEntity().getJavaClass())
                query.eq(inverseSide.getName(), primaryKey)
                query.projections().id()
                return query.list()
            }

            @Override
            PersistentEntity getIndexedEntity() {
                return association.getAssociatedEntity()
            }
        }
    }

    protected AbstractPersistentCollection(Collection keys, Class childType,
                                           Session session, Collection collection) {
        this.session = session
        this.keys = keys
        this.childType = childType
        this.collection = collection
        this.indexer = null
    }

    protected AbstractPersistentCollection(Serializable associationKey, Session session,
                                           AssociationQueryExecutor indexer, Collection collection) {
        this.session = session
        this.associationKey = associationKey
        this.indexer = indexer
        this.collection = collection
        this.childType = indexer.getIndexedEntity().getJavaClass()
    }

    /**
     * Whether to proxy entities by their keys
     *
     * @param proxyEntities True if you wish to proxy entities
     */
    void setProxyEntities(boolean proxyEntities) {
        this.proxyEntities = proxyEntities
    }

    @Override
    boolean hasChanged() {
        return isDirty()
    }

    @Override
    int getOriginalSize() {
        return originalSize
    }

    @Override
    boolean hasGrown() {
        return isInitialized() && (size() > originalSize)
    }

    @Override
    boolean hasShrunk() {
        return isInitialized() && (size() < originalSize)
    }

    @Override
    boolean hasChangedSize() {
        return isInitialized() && (size() != originalSize)
    }

    /* Collection methods */

    Iterator iterator() {
        initialize()

        final Iterator iterator = collection.iterator()
        return new Iterator() {
            boolean hasNext() {
                return iterator.hasNext()
            }

            Object next() {
                return iterator.next()
            }

            void remove() {
                iterator.remove()
                markDirty()
            }
        }
    }

    int size() {
        initialize()
        return collection.size()
    }

    boolean isEmpty() {
        initialize()
        return collection.isEmpty()
    }

    boolean contains(Object o) {
        initialize()
        return collection.contains(o)
    }

    boolean add(Object o) {
        initialize()
        boolean added = collection.add(o)
        if (added) {
            markDirty()
        }
        return added
    }

    boolean remove(Object o) {
        initialize()
        boolean remove = collection.remove(o)
        if (remove) {
            markDirty()
        }
        return remove
    }

    void clear() {
        initialize()
        collection.clear()
        markDirty()
    }

    @Override
    boolean equals(Object o) {
        initialize()
        return collection.equals(o)
    }

    @Override
    int hashCode() {
        initialize()
        return collection.hashCode()
    }

    @Override
    String toString() {
        initialize()
        return collection.toString()
    }

    boolean removeAll(Collection c) {
        initialize()
        boolean changed = collection.removeAll(c)
        if (changed) {
            markDirty()
        }
        return changed
    }

    Object[] toArray() {
        initialize()
        return collection.toArray()
    }

    Object[] toArray(Object[] a) {
        initialize()
        return collection.toArray(a)
    }

    boolean containsAll(Collection c) {
        initialize()
        return collection.containsAll(c)
    }

    boolean addAll(Collection c) {
        initialize()
        boolean changed = collection.addAll(c)
        if (changed) {
            markDirty()
        }
        return changed
    }

    boolean retainAll(Collection c) {
        initialize()
        boolean changed = collection.retainAll(c)
        if (changed) {
            markDirty()
        }
        return changed
    }

    /* PersistentCollection methods */

    boolean isInitialized() {
        return initialized
    }

    protected void setInitializing(Boolean initializing) {
        this.initializing = initializing
    }

    void initialize() {
        if (initializing != null) return

        setInitializing(Boolean.TRUE)

        try {
            if (isInitialized()) {
                return
            }

            final Session session = this.session
            if (session == null) {
                throw new IllegalStateException('PersistentCollection of type ' + this.getClass().getName() + ' should have been initialized before serialization.')
            }

            initialized = true

            final Class childType = this.childType
            if (associationKey == null) {
                final Collection keys = this.keys
                if (keys != null) {

                    loadInverseChildKeys(session, childType, keys)
                }
            }
            else {
                List results = indexer.query(associationKey)
                if (indexer.doesReturnKeys()) {

                    PersistentEntity entity = indexer.getIndexedEntity()

                    // This should really only happen for unit testing since entities are
                    // mocked selectively and may not always be registered in the indexer. In this
                    // case, there can't be any results to be added to the collection.
                    if (entity != null) {
                        loadInverseChildKeys(session, entity.getJavaClass(), results)
                    }
                    else if (childType != null) {
                        loadInverseChildKeys(session, childType, results)
                    }
                }
                else {
                    addAll(results)
                }
            }
            this.originalSize = size()
        } finally {
            setInitializing(Boolean.FALSE)
        }
    }

    protected void loadInverseChildKeys(Session session, Class childType, Collection keys) {
        if (!keys.isEmpty()) {
            if (proxyEntities) {
                for (Object key in keys) {
                    add(
                            session.proxy(childType, (Serializable) key)
                    )
                }
            }
            else {
                addAll(session.retrieveAll(childType, keys))
            }
        }
    }

    boolean isDirty() {
        return dirty
    }

    void resetDirty() {
        dirty = false
    }

    void markDirty() {
        if (!currentlyInitializing()) {
            dirty = true
        }
    }

    protected boolean currentlyInitializing() {
        return initializing != null && initializing.equals(Boolean.TRUE)
    }

}
