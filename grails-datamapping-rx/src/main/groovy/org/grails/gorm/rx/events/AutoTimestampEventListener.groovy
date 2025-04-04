package org.grails.gorm.rx.events

import groovy.transform.CompileStatic
import org.grails.datastore.mapping.engine.event.AbstractPersistenceEvent
import org.grails.datastore.rx.RxDatastoreClient
/**
 * An auto timestamp event listener for RxGORM
 *
 * @author Graeme Rocher
 * @since 6.0
 */
@CompileStatic
class AutoTimestampEventListener extends org.grails.datastore.gorm.events.AutoTimestampEventListener {
    final RxDatastoreClient datastoreClient

    AutoTimestampEventListener(RxDatastoreClient datastoreClient) {
        super(datastoreClient.mappingContext)
        this.datastoreClient = datastoreClient
    }

    @Override
    protected boolean isValidSource(AbstractPersistenceEvent event) {
        Object source = event.getSource();
        return (source instanceof RxDatastoreClient) && source.equals(datastoreClient);
    }

    @Override
    boolean supportsSourceType(Class<?> sourceType) {
        datastoreClient.getClass().equals(sourceType)
    }
}
