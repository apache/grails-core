package org.grails.datastore.rx.batch

import groovy.transform.Canonical
import groovy.transform.CompileStatic
import org.grails.datastore.mapping.model.PersistentEntity

/**
 * Represents a batch operation
 *
 * @author Graeme Rocher
 * @since 6.0
 */

@CompileStatic
class BatchOperation {
    final Map<PersistentEntity, Map<Serializable, EntityOperation>> deletes = [:].withDefault { [:] }
    final Map<PersistentEntity, Map<Serializable, EntityOperation>> updates = [:].withDefault { [:] }
    final Map<PersistentEntity, Map<Serializable, EntityOperation>> inserts = [:].withDefault { [:] }

    /**
     * Arguments to the operation
     */
    Map<String, Object> arguments

    BatchOperation(Map<String, Object> arguments = Collections.emptyMap()) {
        this.arguments = arguments
    }

    /**
     * Adds a delete operation for the given entity, id and object
     *
     * @param entity The entity type
     * @param id The id of the entity
     * @param object The object
     */
    void addDelete(PersistentEntity entity, Serializable id, Object object) {
        deletes.get(entity).put(id, new EntityOperation(id, object))
    }

    /**
     * Adds an update operation for the given entity, id and object
     *
     * @param entity The entity type
     * @param id The id of the entity
     * @param object The object
     */
    void addUpdate(PersistentEntity entity, Serializable id, Object object) {
        updates.get(entity).put(id, new EntityOperation(id, object))
    }

    /**
     * Adds an insert operation for the given entity and object
     *
     * @param entity The entity type
     * @param object The object
     */
    void addInsert(PersistentEntity entity, Serializable id, Object object) {
        inserts.get(entity).put(id, new EntityOperation(id, object))
    }

    /**
     * @return Whether there are any pending operations
     */
    boolean hasPendingOperations() {
        !inserts.isEmpty() || !updates.isEmpty() || !deletes.isEmpty()
    }

    boolean isAlreadyPending(PersistentEntity entity, Serializable id, Object o) {
        (inserts.containsKey(entity) ? inserts.get(entity).get(id) != null : false) || (updates.containsKey(entity) ? updates.get(entity).get(id) != null : false)
    }

    @Canonical
    static class EntityOperation {
        final Serializable identity
        final Object object
    }
}
