package org.grails.gorm.rx.api

import groovy.transform.CompileStatic
import org.grails.datastore.gorm.GormValidationApi
import org.grails.datastore.mapping.engine.event.ValidationEvent
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.rx.RxDatastoreClient
/**
 * RxGORM version of validation API
 *
 * @author Graeme Rocher
 * @since 6.0
 */
@CompileStatic
class RxGormValidationApi<D> extends GormValidationApi<D>{

    final RxDatastoreClient datastoreClient
    RxGormValidationApi(PersistentEntity entity, RxDatastoreClient datastoreClient) {
        super(entity.javaClass, datastoreClient.mappingContext, datastoreClient.eventPublisher)
        this.datastoreClient = datastoreClient
    }

    @Override
    protected ValidationEvent createValidationEvent(Object target) {
        return new ValidationEvent(datastoreClient, persistentEntity, mappingContext.createEntityAccess(persistentEntity, target) )
    }
}
