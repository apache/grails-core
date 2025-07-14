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
import org.grails.datastore.mapping.core.Session
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
    public static final Map DIRTY_CLASS_MARKER = [:].asImmutable()
    /**
     * Checks whether associations are dirty
     *
     * @param session The session
     * @param entity The entity
     * @param instance The instance
     * @return True if they are
     * @deprecated Use {@link #areAssociationsDirty(org.grails.datastore.mapping.model.PersistentEntity, java.lang.Object)} instead
     */
    @Deprecated
    static boolean areAssociationsDirty(Session session, PersistentEntity entity, Object instance) {
        areAssociationsDirty(entity, instance)
    }

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
                } else {
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
        if (coll instanceof Set) {
            return new DirtyCheckingSet(coll, parent, property)
        }
        return new DirtyCheckingCollection(coll, parent, property)
    }
}
