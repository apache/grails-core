package org.grails.gorm.rx.events

import groovy.transform.CompileStatic
import org.grails.datastore.gorm.GormValidateable
import org.grails.datastore.mapping.engine.event.AbstractPersistenceEvent
import org.grails.datastore.mapping.engine.event.PersistenceEventListener
import org.grails.datastore.mapping.engine.event.PreInsertEvent
import org.grails.datastore.mapping.engine.event.PreUpdateEvent
import org.grails.datastore.rx.RxDatastoreClient
import org.springframework.context.ApplicationEvent
/**
 * A validation event listener for RxGORM
 *
 * @author Graeme Rocher
 * @since 6.0
 */
@CompileStatic
class ValidationEventListener implements PersistenceEventListener {
    @Override
    boolean supportsEventType(Class<? extends ApplicationEvent> eventType) {
        return PreInsertEvent.isAssignableFrom(eventType) || PreUpdateEvent.isAssignableFrom(eventType)
    }

    @Override
    boolean supportsSourceType(Class<?> sourceType) {
        return RxDatastoreClient.isAssignableFrom(sourceType)
    }

    @Override
    void onApplicationEvent(ApplicationEvent event) {
        def persistenceEvent = (AbstractPersistenceEvent) event
        def entityObject = persistenceEvent.getEntityObject()
        if(entityObject instanceof GormValidateable) {
            GormValidateable gormValidateable = (GormValidateable) entityObject
            if(gormValidateable.shouldSkipValidation()) {
                if( gormValidateable.getErrors()?.hasErrors() )  {
                    persistenceEvent.cancel()
                }
            }
            else {
                if( !gormValidateable.validate() ) {
                    persistenceEvent.cancel()
                }
            }
        }
    }

    @Override
    int getOrder() {
        return 0
    }
}
