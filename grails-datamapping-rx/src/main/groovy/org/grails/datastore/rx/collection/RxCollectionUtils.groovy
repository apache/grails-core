package org.grails.datastore.rx.collection

import groovy.transform.CompileStatic
import org.grails.datastore.mapping.model.types.Association
import org.grails.datastore.mapping.query.Query
import org.grails.datastore.rx.query.QueryState
import org.grails.gorm.rx.api.RxGormEnhancer

/**
 * Utility methods for RxCollections
 *
 * @author Graeme Rocher
 * @since 6.0
 */
@CompileStatic
class RxCollectionUtils {

    /**
     * Creates a concrete collection for the given association
     *
     * @param association The association
     * @param foreignKey The foreign key
     * @param queryState
     * @return
     */
    static Collection createConcreteCollection(Association association, Serializable foreignKey, QueryState queryState) {
        switch(association.type) {
            case SortedSet:
                return new RxPersistentSortedSet(RxGormEnhancer.findInstanceApi(association.associatedEntity.javaClass).datastoreClient, association, foreignKey, queryState)
            case List:
                return new RxPersistentList(RxGormEnhancer.findInstanceApi(association.associatedEntity.javaClass).datastoreClient, association, foreignKey, queryState)
            default:
                return new RxPersistentSet(RxGormEnhancer.findInstanceApi(association.associatedEntity.javaClass).datastoreClient, association, foreignKey, queryState)
        }
    }

    /**
     * Creates a concrete collection for the given association
     *
     * @param association The association
     * @param initializerQuery The query that initializes the collection
     * @param queryState
     * @return
     */
    static Collection createConcreteCollection(Association association, Query initializerQuery, QueryState queryState) {
        switch(association.type) {
            case SortedSet:
                return new RxPersistentSortedSet(RxGormEnhancer.findInstanceApi(association.associatedEntity.javaClass).datastoreClient, association, initializerQuery, queryState)
            case List:
                return new RxPersistentList(RxGormEnhancer.findInstanceApi(association.associatedEntity.javaClass).datastoreClient, association, initializerQuery, queryState)
            default:
                return new RxPersistentSet(RxGormEnhancer.findInstanceApi(association.associatedEntity.javaClass).datastoreClient, association, initializerQuery, queryState)
        }
    }
}
