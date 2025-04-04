package org.grails.gorm.rx.api

import grails.gorm.rx.api.RxGormInstanceOperations
import groovy.transform.CompileStatic
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.reflect.EntityReflector
import org.grails.datastore.rx.RxDatastoreClient
import rx.Observable
import rx.Single

/**
 * Bridge to the implementation of the instance method level operations
 *
 * @author Graeme Rocher
 * @since 6.0
 */
@CompileStatic
class RxGormInstanceApi<D> implements RxGormInstanceOperations<D> {

    final PersistentEntity entity
    final RxDatastoreClient datastoreClient
    final EntityReflector entityReflector

    RxGormInstanceApi(PersistentEntity entity, RxDatastoreClient datastoreClient) {
        this.entity = entity
        this.datastoreClient = datastoreClient
        this.entityReflector = datastoreClient.mappingContext.getEntityReflector(entity)
    }

    @Override
    Observable<D> save(D instance, Map arguments = Collections.emptyMap()) {
        datastoreClient.persist(instance, arguments)
    }

    @Override
    Observable<D> insert(D instance, Map arguments = Collections.emptyMap()) {
        datastoreClient.insert(instance, arguments)
    }

    @Override
    Serializable ident(D instance) {
        entityReflector.getIdentifier(instance)
    }

    @Override
    Observable<Boolean> delete(D instance) {
        delete(instance, Collections.emptyMap())
    }

    @Override
    Observable<Boolean> delete(D instance, Map arguments) {
        datastoreClient.delete(instance, arguments)
    }
}
