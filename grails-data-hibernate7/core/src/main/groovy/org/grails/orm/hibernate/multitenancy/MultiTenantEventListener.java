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
package org.grails.orm.hibernate.multitenancy;

import grails.gorm.multitenancy.Tenants;
import java.io.Serializable;
import org.grails.datastore.gorm.GormEnhancer;
import org.grails.datastore.mapping.core.Datastore;
import org.grails.datastore.mapping.core.connections.ConnectionSource;
import org.grails.datastore.mapping.engine.event.*;
import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.datastore.mapping.model.types.TenantId;
import org.grails.datastore.mapping.multitenancy.MultiTenantCapableDatastore;
import org.grails.datastore.mapping.multitenancy.exceptions.TenantException;
import org.grails.datastore.mapping.query.Query;
import org.grails.datastore.mapping.query.event.PreQueryEvent;
import org.grails.orm.hibernate.AbstractHibernateDatastore;
import org.springframework.context.ApplicationEvent;

/**
 * An event listener that hooks into persistence events to enable discriminator based multi tenancy
 * (ie {@link
 * org.grails.datastore.mapping.multitenancy.MultiTenancySettings.MultiTenancyMode#DISCRIMINATOR}
 *
 * @author Graeme Rocher
 * @since 6.0
 */
public class MultiTenantEventListener implements PersistenceEventListener {
  @Override
  public boolean supportsEventType(Class<? extends ApplicationEvent> eventType) {
    return org.grails.datastore.gorm.multitenancy.MultiTenantEventListener.SUPPORTED_EVENTS
        .contains(eventType);
  }

  @Override
  public boolean supportsSourceType(Class<?> sourceType) {
    return AbstractHibernateDatastore.class.isAssignableFrom(sourceType);
  }

  @SuppressWarnings("PMD.DataflowAnomalyAnalysis")
  @Override
  public void onApplicationEvent(ApplicationEvent event) {
    if (supportsEventType(event.getClass())) {
      Datastore datastore = (Datastore) event.getSource();
      if (event instanceof PreQueryEvent preQueryEvent) {
        Query query = preQueryEvent.getQuery();

        PersistentEntity entity = query.getEntity();
        if (entity.isMultiTenant()) {
          Datastore ds =
              (datastore != null) ? datastore : GormEnhancer.findDatastore(entity.getJavaClass());
          if (ds instanceof AbstractHibernateDatastore hibernateDatastore) {
            hibernateDatastore.enableMultiTenancyFilter();
          }
        }
      } else if (event instanceof AbstractPersistenceEvent persistenceEvent
          && (persistenceEvent instanceof ValidationEvent
              || persistenceEvent instanceof PreInsertEvent
              || persistenceEvent instanceof PreUpdateEvent)) {
        PersistentEntity entity = persistenceEvent.getEntity();
        if (entity.isMultiTenant()) {
          TenantId tenantId = entity.getTenantId();
          Datastore ds =
              (datastore != null) ? datastore : GormEnhancer.findDatastore(entity.getJavaClass());
          if (ds instanceof AbstractHibernateDatastore hibernateDatastore) {
            Serializable currentId;

            if (hibernateDatastore instanceof MultiTenantCapableDatastore) {
              currentId = Tenants.currentId((MultiTenantCapableDatastore) hibernateDatastore);
            } else {
              currentId = Tenants.currentId(hibernateDatastore.getClass());
            }
            if (currentId != null) {
              try {
                if (ConnectionSource.DEFAULT.equals(currentId)) {
                  currentId =
                      (Serializable)
                          persistenceEvent.getEntityAccess().getProperty(tenantId.getName());
                }
                persistenceEvent.getEntityAccess().setProperty(tenantId.getName(), currentId);
              } catch (Exception e) {
                throw new TenantException(
                    "Could not assigned tenant id ["
                        + currentId
                        + "] to property ["
                        + tenantId
                        + "], probably due to a type mismatch. You should return a type from the tenant resolver that matches the property type of the tenant id!: "
                        + e.getMessage(),
                    e);
              }
            }
          }
        }
      }
    }
  }

  @Override
  public int getOrder() {
    return DEFAULT_ORDER;
  }
}
