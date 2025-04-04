package org.grails.datastore.rx.query

import groovy.transform.CompileStatic

import java.util.concurrent.ConcurrentHashMap

/**
 *
 * Used to maintain query state and avoid hitting the database again when loading associations
 *
 * @author Graeme Rocher
 * @since 6.0
 */
@CompileStatic
class QueryState {

    private final Map<Class, Map<Serializable, Object>> loadedEntities = new ConcurrentHashMap<>()

    QueryState() {
    }

    void addLoadedEntity(Class type, Serializable id, Object object) {
        def loadedByType = loadedEntities.get(type)
        if(loadedByType == null) {
            loadedByType = new ConcurrentHashMap<Serializable, Object>()
            loadedByType.put(id, object)
            loadedEntities.put(type, loadedByType)
        }
        else {
            loadedByType.put(id, object)
        }
    }

    public <T> T getLoadedEntity(Class<T> type, Serializable id) {
        def loadedByType = loadedEntities.get(type)
        if(loadedByType == null) {
            return null
        }
        else {
            return (T) loadedByType.get(id)
        }
    }
}
