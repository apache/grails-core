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

import org.grails.datastore.mapping.collection.PersistentCollection
import org.grails.datastore.mapping.collection.PersistentList
import org.grails.datastore.mapping.collection.PersistentSet
import org.grails.datastore.mapping.collection.PersistentSortedSet
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.types.Association
import org.grails.datastore.mapping.model.types.Embedded
import org.grails.datastore.mapping.model.types.ToOne
import org.grails.datastore.mapping.reflect.EntityReflector

/**
 * Support methods for dirty checking
 *
 * @author Graeme Rocher
 * @since 2.0
 */
@CompileStatic
class DirtyCheckingSupport {

    /**
     * Used internally as a marker. Do not use in user code
     */
    public static final  Map DIRTY_CLASS_MARKER = [:].asImmutable()

    /**
     * Checks whether associations are dirty
     *
     * @param session The session
     * @param entity The entity
     * @param instance The instance
     * @return True if they are
     */
    static boolean areAssociationsDirty(PersistentEntity entity, Object instance) {
        if (!instance) return false

        MappingContext mappingContext = entity.mappingContext
        final proxyFactory = mappingContext.proxyFactory
        final EntityReflector entityReflector = mappingContext.getEntityReflector(entity)

        final associations = entity.associations
        for (Association a in associations) {
            final isOwner = a.isOwningSide() || (a.bidirectional && !a.inverseSide?.owningSide)
            if (isOwner) {
                if (a instanceof ToOne) {
                    final value = entityReflector.getProperty(instance, a.name)
                    if (proxyFactory.isInitialized(value)) {
                        if (value instanceof DirtyCheckable) {
                            DirtyCheckable dirtyCheckable = (DirtyCheckable) value
                            if (dirtyCheckable.hasChanged()) {
                                return true
                            }
                        }
                    }
                }
                else {
                    final value = entityReflector.getProperty(instance, a.name)
                    if (value instanceof PersistentCollection) {
                        PersistentCollection coll = (PersistentCollection) value
                        if (coll.isInitialized()) {
                            if (coll.isDirty()) return true
                        }
                    }
                }

            }
        }
        return false
    }

    /**
     * Checks whether embedded associations are dirty
     *
     * @param session The session
     * @param entity The entity
     * @param instance The instance
     * @return True if they are
     */
    static boolean areEmbeddedDirty(PersistentEntity entity, Object instance) {
        if (instance == null) return false

        final associations = entity.getEmbedded()
        for (Embedded a in associations) {
            final value = a.reader.read(instance)
            if (value instanceof DirtyCheckable) {
                DirtyCheckable dirtyCheckable = (DirtyCheckable) value
                if (dirtyCheckable.hasChanged()) {
                    return true
                }
            }
        }
        return false
    }
    /**
     * Wraps a collection in dirty checking capability
     *
     * @param coll The collection
     * @param parent The parent
     * @param property The property
     * @return The wrapped collection
     */
    static Collection wrap(Collection coll, DirtyCheckable parent, String property) {
        if (coll instanceof DirtyCheckingCollection) {
            return coll
        }
        if (coll instanceof List) {
            return new DirtyCheckingList(coll, parent, property)
        }
        if (coll instanceof SortedSet) {
            return new DirtyCheckingSortedSet(coll, parent, property)
        }
        if (coll instanceof Set) {
            return new DirtyCheckingSet(coll, parent, property)
        }
        return new DirtyCheckingCollection(coll, parent, property)
    }

    /**
     * Wraps a map in dirty checking capability. Returns an already-wrapped map unchanged —
     * without that guard an encoder that write-backs on every save nests one wrapper per save.
     *
     * @param map The map
     * @param parent The parent
     * @param property The property
     * @return The wrapped map
     */
    static Map wrap(Map map, DirtyCheckable parent, String property) {
        if (map instanceof DirtyCheckingMap) {
            return map
        }
        return new DirtyCheckingMap(map, parent, property)
    }

    /**
     * Re-establishes change tracking when a tracked collection or map value is replaced
     * through a generated dirty-checking setter.
     *
     * <p>Interception-based stores (MongoDB et al.) install the DirtyChecking* wrappers when an
     * entity is decoded and rely on them exclusively — there is no flush-time snapshot
     * comparison. The generated setter used to store whatever raw value it was handed, so
     * reassigning a collection property replaced the tracked wrapper with a plain, untracked
     * collection and every later in-place mutation became invisible to change tracking. The
     * common defensive re-init {@code if (!entity.items) entity.items = []} triggered this on
     * every load (an empty tracked collection is falsy in Groovy), and because the new empty
     * collection equals the old one the assignment itself was never flagged either.
     *
     * <p>Tracking is only re-established, never introduced: the value being replaced must
     * already be one of the exact classes a datastore decode installs — a generic wrapper, or
     * the {@code PersistentList}/{@code PersistentSet}/{@code PersistentSortedSet} a to-many
     * association decodes into. Anything else is returned untouched: a transient instance, a
     * store like Hibernate that performs its own snapshot-based dirty checking, or a
     * store-specific subclass such as the Neo4j collection types. Store-specific wrappers in
     * particular must NOT be replaced by a generic one:
     * the store's persister recognises its own types (and treats anything already
     * dirty-checkable as such), so a generic replacement would permanently disable that store's
     * relationship handling for the property. Leaving the raw value lets the persister wrap it
     * in its own type on save, exactly as it did before this method existed.
     *
     * @param parent The dirty-checkable owner
     * @param property The property being assigned
     * @param oldValue The value being replaced
     * @param newValue The value being assigned
     * @return The value to store: {@code newValue}, wrapped if it replaces a tracked value
     */
    static Object rewrap(DirtyCheckable parent, String property, Object oldValue, Object newValue) {
        if (newValue == null) {
            return newValue
        }
        if (newValue instanceof DirtyCheckableCollection) {
            // Already tracking this same owner: the wrapper a datastore decode or an encode
            // write-back installed. Pass it through untouched — re-wrapping would restart the
            // originalSize baseline and flag an assignment that never happened.
            if (tracksSameOwner(newValue, parent, property)) {
                return newValue
            }
            // Anything that is not one of this package's own generic wrappers is left entirely
            // alone, exactly as the oldValue gate does — see isGenericWrapper.
            Object borrowed = genericWrapperTarget(newValue)
            if (borrowed == null) {
                return newValue
            }
            // Another entity's wrapper. Storing it verbatim binds this property to that entity:
            // mutating it would mark THAT entity dirty and leave this one clean, and it would
            // keep assigned=false, so a persister would diff a wholesale replacement element by
            // element. Re-bind by wrapping its raw target below — never the wrapper itself,
            // which would mark both entities dirty. The two properties go on sharing the
            // underlying collection, as any plain assignment would.
            newValue = borrowed
        }
        else if (!wasTracked(oldValue)) {
            // A plain collection replacing a value this package was not already tracking.
            // Tracking is introduced by the datastore at decode, never here — a transient
            // instance, or a store like Hibernate that does its own snapshot-based dirty
            // checking, must keep storing exactly what it was given. (A wrapper arriving from
            // another entity is handled above and re-bound whatever it replaces: its existence
            // means a store already tracks that collection, just for the wrong owner.)
            return newValue
        }
        // Constructed with assigned=true: a replacement is a wholesale rewrite, so persisters
        // must not diff it element-by-element against stored state (see DirtyCheckableCollection
        // .isAssigned()).
        if (newValue instanceof List) {
            return new DirtyCheckingList((List) newValue, parent, property, true)
        }
        if (newValue instanceof SortedSet) {
            return new DirtyCheckingSortedSet((SortedSet) newValue, parent, property, true)
        }
        if (newValue instanceof Set) {
            return new DirtyCheckingSet((Set) newValue, parent, property, true)
        }
        if (newValue instanceof Collection) {
            return new DirtyCheckingCollection((Collection) newValue, parent, property, true)
        }
        if (newValue instanceof Map) {
            return new DirtyCheckingMap((Map) newValue, parent, property, true)
        }
        return newValue
    }

    /**
     * True when the wrapper already tracks changes for exactly this owner and property, rather
     * than being a value borrowed from another entity.
     *
     * <p>The owner is compared by identity on purpose: a domain class may implement
     * {@code equals()} on a business key, and on an association comparing it can initialise a
     * proxy and issue another query.
     */
    private static boolean tracksSameOwner(Object value, DirtyCheckable parent, String property) {
        if (value instanceof DirtyCheckingCollection) {
            DirtyCheckingCollection wrapper = (DirtyCheckingCollection) value
            return wrapper.parent.is(parent) && property == wrapper.property
        }
        if (value instanceof DirtyCheckingMap) {
            DirtyCheckingMap wrapper = (DirtyCheckingMap) value
            return wrapper.parent.is(parent) && property == wrapper.property
        }
        return false
    }

    /**
     * The raw collection or map behind one of this package's own generic wrappers, or
     * {@code null} for anything else — a store-specific subclass such as the Neo4j collection
     * types, or a {@code PersistentCollection}. Mirrors {@link #isGenericWrapper}.
     */
    private static Object genericWrapperTarget(Object value) {
        if (!isGenericWrapper(value)) {
            return null
        }
        Object target = value
        // Loop rather than unwrap once: an encoder can leave a wrapper nested inside another
        // (BasicCollectionTypeEncoder builds a DirtyCheckingMap unconditionally), and re-binding
        // onto an inner wrapper would leave every mutation marking the other entity too.
        while (isGenericWrapper(target)) {
            target = target instanceof DirtyCheckingMap
                    ? ((DirtyCheckingMap) target).target
                    : ((DirtyCheckingCollection) target).target
        }
        return target
    }

    /**
     * True when the property was already held in a collection the datastore tracks changes
     * through: one of this package's own generic wrappers, or the {@code PersistentCollection}
     * a to-many association decodes into. Replacing either with a plain collection would drop
     * that tracking, so the replacement is wrapped.
     *
     * <p>Deliberately does not read the value — a {@code PersistentCollection} may be an
     * uninitialised lazy association, and touching it would issue a query.
     */
    private static boolean wasTracked(Object value) {
        return isGenericWrapper(value) || isPersistentCollection(value)
    }

    /**
     * True only for the datastore's own to-many collection classes, by exact class. A
     * store-specific subclass (the Neo4j {@code Neo4jPersistentList} and friends) fails this
     * check and stays with its store, exactly as {@link #isGenericWrapper} keeps the Neo4j
     * wrapper subclasses out.
     */
    private static boolean isPersistentCollection(Object value) {
        Class<?> valueClass = value?.getClass()
        return valueClass == PersistentList || valueClass == PersistentSet ||
                valueClass == PersistentSortedSet
    }

    /**
     * True only for the plugin's own generic wrapper classes — the exact types installed by a
     * datastore decode or a previous {@link #rewrap}. Subclasses (Neo4jList, Neo4jSet, …) and
     * {@code PersistentCollection} implementations deliberately fail this check.
     */
    private static boolean isGenericWrapper(Object value) {
        Class<?> valueClass = value?.getClass()
        return valueClass == DirtyCheckingCollection || valueClass == DirtyCheckingList ||
                valueClass == DirtyCheckingSet || valueClass == DirtyCheckingSortedSet ||
                valueClass == DirtyCheckingMap
    }
}
