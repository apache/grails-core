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

import org.hibernate.event.spi.InitializeCollectionEvent;
import org.hibernate.event.spi.InitializeCollectionEventListener;
import org.hibernate.persister.collection.CollectionPersister;
import org.hibernate.type.EntityType;
import org.hibernate.type.Type;

import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.orm.hibernate.HibernateDatastore;

/**
 * Sets the tenant filter for the current tenant before Hibernate initializes a collection of a multi-tenant entity,
 * as {@link MultiTenantEventListener} does before a query. Without it, the collection would be loaded with whatever
 * filter state the session has, which is none until the first query on a multi-tenant entity, and none after
 * {@link grails.gorm.multitenancy.Tenants#withoutId(groovy.lang.Closure)} has run in the session.
 *
 * @since 8.0.1
 */
public class MultiTenantCollectionEventListener implements InitializeCollectionEventListener {

    private final HibernateDatastore datastore;

    public MultiTenantCollectionEventListener(HibernateDatastore datastore) {
        this.datastore = datastore;
    }

    @Override
    public void onInitializeCollection(InitializeCollectionEvent event) {
        CollectionPersister persister = event.getCollectionPersister();
        if (persister == null) {
            return;
        }
        Type elementType = persister.getElementType();
        if (elementType.isEntityType()) {
            PersistentEntity entity = datastore.getMappingContext()
                    .getPersistentEntity(((EntityType) elementType).getAssociatedEntityName());
            if (entity != null && entity.isMultiTenant()) {
                datastore.enableMultiTenancyFilter(event.getSession());
            }
        }
    }
}
