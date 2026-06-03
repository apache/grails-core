/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  'License'); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  'AS IS' BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.grails.datastore.gorm

import java.util.concurrent.ConcurrentHashMap
import groovy.transform.CompileStatic
import org.grails.datastore.mapping.core.Datastore
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.datastore.mapping.core.connections.MultipleConnectionSourceCapableDatastore
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.reflect.NameUtils

/**
 * Manages the registration, tracking, indexing, and lookup of Datastore instances.
 * Extracted from GormRegistry to improve single responsibility and unit testability.
 *
 * @author Walter Duque de Estrada
 * @since 8.0.0
 */
@CompileStatic
@SuppressWarnings('unused')
class DatastoreDiscovery {

    final Map<String, Datastore> datastoresByQualifier = new ConcurrentHashMap<>()
    final Map<String, Map<String, Datastore>> entityDatastores = new ConcurrentHashMap<>()
    final Map<Class, Datastore> datastoresByType = new ConcurrentHashMap<>()
    final Set<Datastore> allDatastores = Collections.newSetFromMap(new ConcurrentHashMap<Datastore, Boolean>())

    private final Map<Class, String> normalizedEntityKeysByClass = new ConcurrentHashMap<>()
    private final Map<String, String> normalizedEntityKeysByName = new ConcurrentHashMap<>()
    private final Map<String, String> normalizedQualifiers = new ConcurrentHashMap<>()

    /**
     * @return The default datastore
     */
    Datastore getDefaultDatastore() {
        return datastoresByQualifier.get(ConnectionSource.DEFAULT)
    }

    /**
     * Finds a datastore for a specific qualifier (connection name).
     */
    Datastore getDatastore(String qualifier) {
        return getDatastoreByString((String) null, qualifier)
    }

    /**
     * Finds a datastore for a specific entity class.
     * Part of the public API for external integrations and manual datastore lookup.
     */
    Datastore getDatastore(Class entityClass) {
        return getDatastore(entityClass, ConnectionSource.DEFAULT)
    }

    /**
     * Finds a datastore for a specific entity class and qualifier.
     */
    Datastore getDatastore(Class entityClass, String qualifier) {
        return getDatastoreByString(entityClass != null ? normalizeEntityKey(entityClass) : (String) null, qualifier)
    }

    /**
     * Finds a datastore for an entity class name and qualifier.
     */
    Datastore getDatastore(String className, String qualifier) {
        return getDatastoreByString(className, qualifier)
    }

    /**
     * Internal method to avoid redundant normalization.
     */
    Datastore getDatastoreDirect(String normalizedClassName, String normalizedQualifier) {
        if (normalizedClassName != null) {
            Map<String, Datastore> mappedDatastores = entityDatastores.get(normalizedClassName)
            if (mappedDatastores != null) {
                Datastore ds = mappedDatastores.get(normalizedQualifier)
                if (ds != null) {
                    return ds
                }
                if (normalizedQualifier == ConnectionSource.DEFAULT && !mappedDatastores.isEmpty()) {
                    return mappedDatastores.values().iterator().next()
                }
                Datastore qualifierDs = datastoresByQualifier.get(normalizedQualifier)
                if (qualifierDs != null && qualifierDs.getMappingContext()?.getPersistentEntity(normalizedClassName) != null) {
                    return qualifierDs
                }
                return null
            }
        }

        Datastore ds = datastoresByQualifier.get(normalizedQualifier)
        if (ds == null && normalizedQualifier == ConnectionSource.DEFAULT) {
            if (allDatastores.size() == 1) {
                return allDatastores.iterator().next()
            }
        }
        return ds
    }

    /**
     * Internal method to avoid ambiguity.
     */
    Datastore getDatastoreByString(String className, String qualifier) {
        return getDatastoreDirect(className != null ? normalizeEntityKey(className) : null, normalizeQualifier(qualifier))
    }

    /**
     * Registers a datastore for a qualifier. (O(N) part)
     */
    void registerDatastore(String qualifier, Datastore datastore) {
        if (datastore == null) return
        String normalizedQualifier = normalizeQualifier(qualifier)
        datastoresByQualifier.put(normalizedQualifier, datastore)
        allDatastores.add(datastore)
    }

    /**
     * Initializes a datastore, registering its type and default qualifier.
     */
    void initializeDatastore(Datastore datastore) {
        if (datastore == null) return
        registerDatastore(ConnectionSource.DEFAULT, datastore)
        datastoresByType.put(datastore.getClass(), datastore)
    }

    /**
     * Registers a datastore.
     */
    void registerDatastore(Datastore datastore) {
        initializeDatastore(datastore)
    }

    /**
     * Registers a datastore by its type.
     * Nominally unused in core mapping runtime code, but used by test suites and external integrations.
     */
    void registerDatastoreByType(Datastore datastore) {
        if (datastore == null) return
        datastoresByType.put(datastore.getClass(), datastore)
        allDatastores.add(datastore)
    }

    /**
     * Registers a datastore by qualifier only, without adding it to the global type-based discovery.
     */
    void registerDatastoreByQualifier(String qualifier, Datastore datastore) {
        if (qualifier != null && datastore != null) {
            datastoresByQualifier.put(normalizeQualifier(qualifier), datastore)
        }
    }

    /**
     * Removes a datastore from discovery by its class type.
     * Nominally unused in core mapping runtime code, but used by testing frameworks to clean up dynamic datastores.
     */
    void removeDatastoreByType(Class datastoreType) {
        if (datastoreType == null) return
        datastoresByType.remove(datastoreType)
    }

    /**
     * Removes a datastore from discovery by its instance type.
     * Nominally unused in core mapping runtime code, but used by testing frameworks to clean up dynamic datastores.
     */
    void removeDatastoreByType(Datastore datastore) {
        if (datastore == null) return
        removeDatastoreByType(datastore.getClass())
    }

    /**
     * Removes a datastore from global discovery (allDatastores and datastoresByType)
     * but keeps it in datastoresByQualifier.
     * Nominally unused in core mapping runtime code, but used by test suites to verify multi-datastore isolation.
     */
    void removeDatastoreFromDiscovery(Datastore datastore) {
        if (datastore == null) return
        allDatastores.remove(datastore)
        datastoresByType.remove(datastore.getClass())
    }

    /**
     * Completely removes a datastore from the registry.
     */
    void removeDatastore(Datastore datastore) {
        if (datastore == null) return
        allDatastores.remove(datastore)
        datastoresByType.remove(datastore.getClass())

        Iterator<Map.Entry<String, Datastore>> it = datastoresByQualifier.entrySet().iterator()
        while (it.hasNext()) {
            if (it.next().value == datastore) it.remove()
        }

        for (Map<String, Datastore> entityMap in entityDatastores.values()) {
            Iterator<Map.Entry<String, Datastore>> eit = entityMap.entrySet().iterator()
            while (eit.hasNext()) {
                if (eit.next().value == datastore) eit.remove()
            }
        }
    }

    /**
     * Removes a datastore for a specific entity.
     */
    void removeEntityDatastore(String className, Datastore datastore) {
        if (className != null && datastore != null) {
            Map<String, Datastore> entityMap = entityDatastores.get(className)
            if (entityMap != null) {
                Iterator<Map.Entry<String, Datastore>> eit = entityMap.entrySet().iterator()
                while (eit.hasNext()) {
                    if (eit.next().value == datastore) eit.remove()
                }
            }
        }
    }

    /**
     * Checks if a specific datastore is explicitly registered for an entity.
     */
    boolean isDatastoreRegisteredForEntity(String className, Datastore datastore) {
        if (className != null && datastore != null) {
            Map<String, Datastore> entityMap = entityDatastores.get(normalizeEntityKey(className))
            if (entityMap != null && entityMap.values().contains(datastore)) {
                return true
            }
        }
        return false
    }

    /**
     * Register datastores for a persistent entity across multiple connection sources.
     * Handles entity-specific datastore mappings for multi-tenant and multi-datasource scenarios.
     *
     * @param className The entity class name
     * @param datastore The datastore to register
     * @param connectionSourceNames The connection source names to register the datastore for
     * @param entity The persistent entity (for entity-specific qualifier resolution)
     */
    void registerEntityDatastores(String className, Object datastore, List<String> connectionSourceNames, Object entity) {
        if (datastore == null) return
        String normalizedClassName = normalizeEntityKey(className)
        if (normalizedClassName == null) {
            return
        }

        Datastore defaultDatastore = (Datastore) datastore
        List<String> qualifiers = connectionSourceNames ?: Collections.singletonList(ConnectionSource.DEFAULT)
        boolean multiTenantEntity = entity instanceof PersistentEntity && ((PersistentEntity) entity).isMultiTenant()

        entityDatastores.remove(normalizedClassName)

        Datastore primaryDatastore = defaultDatastore

        for (String connectionSourceName in qualifiers) {
            String normalizedQualifier = normalizeQualifier(connectionSourceName)
            Datastore qualifierDatastore = defaultDatastore
            if (defaultDatastore instanceof MultipleConnectionSourceCapableDatastore &&
                    normalizedQualifier != ConnectionSource.DEFAULT) {
                try {
                    Datastore resolved = ((MultipleConnectionSourceCapableDatastore) defaultDatastore)
                            .getDatastoreForConnection(normalizedQualifier)
                    if (resolved != null) {
                        qualifierDatastore = resolved
                    }
                } catch (Throwable ignored) {
                    // qualifier is not a datasource connection name; keep defaultDatastore
                }
            }
            if (multiTenantEntity && normalizedQualifier != ConnectionSource.DEFAULT &&
                    qualifierDatastore == defaultDatastore) {
                continue
            }
            if (normalizedQualifier != ConnectionSource.DEFAULT && primaryDatastore == defaultDatastore) {
                primaryDatastore = qualifierDatastore
            }
            registerDatastoreByQualifier(normalizedQualifier, qualifierDatastore)
            registerEntityDatastore(normalizedClassName, normalizedQualifier, qualifierDatastore)
        }

        if (!qualifiers.collect { String it -> normalizeQualifier(it) }.contains(ConnectionSource.DEFAULT)) {
            registerEntityDatastore(normalizedClassName, ConnectionSource.DEFAULT, primaryDatastore)
        }
    }

    /**
     * Registers an entity-specific datastore override.
     */
    void registerEntityDatastore(String className, String qualifier, Datastore datastore) {
        if (datastore != null) {
            String normalizedClassName = normalizeEntityKey(className)
            if (normalizedClassName == null) {
                return
            }
            String normalizedQualifier = normalizeQualifier(qualifier)
            getInternalMap(entityDatastores, normalizedClassName).put(normalizedQualifier, datastore)
        }
    }

    String normalizeEntityKey(Object entityKey) {
        if (entityKey == null) {
            return null
        }
        if (entityKey instanceof Class) {
            Class entityClass = (Class) entityKey
            String existing = normalizedEntityKeysByClass.get(entityClass)
            if (existing != null) {
                return existing
            }
            String computed = NameUtils.getClassName(entityClass)
            String normalized = normalizeEntityKey(computed)
            if (normalized == null) {
                return null
            }
            String prior = normalizedEntityKeysByClass.putIfAbsent(entityClass, normalized)
            return prior != null ? prior : normalized
        } else {
            String className = entityKey.toString()
            String existing = normalizedEntityKeysByName.get(className)
            if (existing != null) {
                return existing
            }
            String normalized = className.trim()
            if (normalized.isEmpty()) {
                return null
            }
            String prior = normalizedEntityKeysByName.putIfAbsent(className, normalized)
            return prior != null ? prior : normalized
        }
    }

    String normalizeEntityKey(Class cls) {
        normalizeEntityKey((Object) cls)
    }

    String normalizeEntityKey(String className) {
        normalizeEntityKey((Object) className)
    }

    String normalizeQualifier(String qualifier) {
        if (qualifier == null) {
            return ConnectionSource.DEFAULT
        }
        String existing = normalizedQualifiers.get(qualifier)
        if (existing != null) {
            return existing
        }
        String normalized = qualifier.trim()
        if (normalized.isEmpty() || ConnectionSource.DEFAULT.equalsIgnoreCase(normalized)) {
            normalized = ConnectionSource.DEFAULT
        }
        String prior = normalizedQualifiers.putIfAbsent(qualifier, normalized)
        return prior != null ? prior : normalized
    }

    void clear() {
        datastoresByQualifier.clear()
        entityDatastores.clear()
        normalizedEntityKeysByClass.clear()
        normalizedEntityKeysByName.clear()
        normalizedQualifiers.clear()
        datastoresByType.clear()
        allDatastores.clear()
    }

    private static Map<String, Datastore> getInternalMap(Map<String, Map<String, Datastore>> rootMap, String key) {
        Map<String, Datastore> map = rootMap.get(key)
        if (map == null) {
            map = new ConcurrentHashMap<String, Datastore>()
            rootMap.put(key, map)
        }
        return map
    }
}
