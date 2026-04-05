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
package org.grails.orm.hibernate.cfg.domainbinding.secondpass;

import java.util.Collections;

import org.hibernate.mapping.Collection;

import org.grails.datastore.mapping.model.config.GormProperties;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateToManyEntityProperty;
import org.grails.orm.hibernate.cfg.domainbinding.util.DefaultColumnNameFetcher;

/** Applies multi-tenant filters to a collection based on the associated entity's tenancy. */
public class ToManyEntityMultiTenantFilterBinder {

    private final DefaultColumnNameFetcher defaultColumnNameFetcher;

    /** Creates a new {@link ToManyEntityMultiTenantFilterBinder} instance. */
    public ToManyEntityMultiTenantFilterBinder(DefaultColumnNameFetcher defaultColumnNameFetcher) {
        this.defaultColumnNameFetcher = defaultColumnNameFetcher;
    }

    /** Applies the multi-tenant filter to the collection if the associated entity is multi-tenant. */
    public void bind(HibernateToManyEntityProperty entityProperty) {
        var referenced = entityProperty.getHibernateAssociatedEntity();
        if (entityProperty.isOneToMany() && referenced.isMultiTenant()) {
            String filterCondition = referenced.getMultiTenantFilterCondition(defaultColumnNameFetcher);
            if (filterCondition != null) {
                Collection collection = entityProperty.getCollection();
                if (entityProperty.isUnidirectionalOneToMany()) {
                    collection.addManyToManyFilter(
                            GormProperties.TENANT_IDENTITY,
                            filterCondition,
                            true,
                            Collections.emptyMap(),
                            Collections.emptyMap());
                } else {
                    collection.addFilter(
                            GormProperties.TENANT_IDENTITY,
                            filterCondition,
                            true,
                            Collections.emptyMap(),
                            Collections.emptyMap());
                }
            }
        }
    }
}
