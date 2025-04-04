package org.grails.gorm.rx.events

import grails.gorm.rx.multitenancy.Tenants
import groovy.transform.CompileStatic
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.datastore.mapping.engine.event.PersistenceEventListener
import org.grails.datastore.mapping.engine.event.PreInsertEvent
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.types.TenantId
import org.grails.datastore.mapping.multitenancy.exceptions.TenantException
import org.grails.datastore.mapping.query.Query
import org.grails.datastore.mapping.query.event.PostQueryEvent
import org.grails.datastore.mapping.query.event.PreQueryEvent
import org.grails.datastore.mapping.reflect.EntityReflector
import org.grails.datastore.rx.RxDatastoreClient
import org.springframework.context.ApplicationEvent
import org.springframework.util.Assert

/**
 * Multi tenant event listener for RxGORM
 *
 * @author Graeme Rocher
 * @since 6.0
 */
@CompileStatic
class MultiTenantEventListener implements PersistenceEventListener {
    protected final RxDatastoreClient datastoreClient

    MultiTenantEventListener(RxDatastoreClient datastoreClient) {
        this.datastoreClient = datastoreClient
    }

    @Override
    boolean supportsEventType(Class<? extends ApplicationEvent> eventType) {
        return PreQueryEvent.class.isAssignableFrom(eventType) || PostQueryEvent.class.isAssignableFrom(eventType) || PreInsertEvent.class.isAssignableFrom(eventType)
    }

    @Override
    boolean supportsSourceType(Class<?> sourceType) {
        return RxDatastoreClient.class.isAssignableFrom(sourceType)
    }

    @Override
    void onApplicationEvent(ApplicationEvent event) {
        if(supportsEventType(event.getClass())) {
            RxDatastoreClient datastoreClient = (RxDatastoreClient) event.getSource()
            Assert.notNull(datastoreClient, "Datastore client should never be null from source event")
            if(event instanceof PreQueryEvent) {
                PreQueryEvent preQueryEvent = (PreQueryEvent) event
                Query query = preQueryEvent.getQuery()

                PersistentEntity entity = query.getEntity()
                if(entity.isMultiTenant()) {
                    if(supportsSourceType(datastoreClient.getClass()) && this.datastoreClient.equals(datastoreClient)) {
                        TenantId tenantId = entity.getTenantId()
                        if(tenantId != null) {
                            Serializable currentId = Tenants.currentId(datastoreClient.getClass())
                            query.eq(tenantId.getName(), currentId )
                        }
                    }
                }
            }
            else if(event instanceof PreInsertEvent) {
                PreInsertEvent preInsertEvent = (PreInsertEvent) event
                PersistentEntity entity = preInsertEvent.getEntity()
                if(entity.isMultiTenant()) {
                    TenantId tenantId = entity.getTenantId()
                    EntityReflector reflector = entity.getReflector()
                    if(supportsSourceType(datastoreClient.getClass()) && this.datastoreClient.equals(datastoreClient)) {
                        Serializable currentId = Tenants.currentId(datastoreClient.getClass())
                        if(currentId != null) {
                            try {
                                if(currentId == ConnectionSource.DEFAULT) {
                                    currentId = (Serializable) preInsertEvent.getEntityAccess().getProperty(tenantId.getName())
                                }
                                reflector.setProperty(preInsertEvent.getEntityObject(), tenantId.getName(), currentId)
                            } catch (Exception e) {
                                throw new TenantException("Could not assigned tenant id ["+currentId+"] to property ["+tenantId+"], probably due to a type mismatch. You should return a type from the tenant resolver that matches the property type of the tenant id!: " + e.getMessage(), e);
                            }
                        }
                    }
                }
            }
        }
    }

    @Override
    int getOrder() {
        return DEFAULT_ORDER
    }
}


