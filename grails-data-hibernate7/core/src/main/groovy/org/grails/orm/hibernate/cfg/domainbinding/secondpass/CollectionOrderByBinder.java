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

import java.util.Optional;
import java.util.Set;

import org.hibernate.mapping.Collection;
import org.hibernate.mapping.PersistentClass;

import org.grails.datastore.mapping.model.DatastoreConfigurationException;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateOneToManyProperty;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernatePersistentProperty;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateToManyProperty;
import org.grails.orm.hibernate.cfg.domainbinding.util.OrderByClauseBuilder;

/** Binds the order-by clause and discriminator where condition to a collection. */
public class CollectionOrderByBinder {

    private final OrderByClauseBuilder orderByClauseBuilder;

    /** Creates a new {@link CollectionOrderByBinder} instance. */
    public CollectionOrderByBinder() {
        this.orderByClauseBuilder = new OrderByClauseBuilder();
    }

    /** Binds the order-by clause and discriminator where condition to the given collection. */
    public void bind(HibernateToManyProperty property, Collection collection, PersistentClass associatedClass) {
        GrailsHibernatePersistentEntity referenced = property.getHibernateAssociatedEntity();

        if (referenced.isTablePerHierarchySubclass()) {
            String discriminatorColumnName = referenced.getDiscriminatorColumnName();
            Set<String> discSet = referenced.buildDiscriminatorSet();
            String clause = String.join(",", discSet);
            collection.setWhere(discriminatorColumnName + " in (" + clause + ")");
        }

        if (property.hasSort()) {
            if (!property.isBidirectional() && property instanceof HibernateOneToManyProperty) {
                throw new DatastoreConfigurationException("Default sort for associations [" +
                        property.getHibernateOwner().getName() +
                        "->" +
                        property.getName() +
                        "] are not supported with unidirectional one to many relationships.");
            }
            HibernatePersistentProperty sortBy = referenced.getHibernatePropertyByName(property.getSort());
            String order = Optional.ofNullable(property.getOrder()).orElse("asc");
            collection.setOrderBy(orderByClauseBuilder.buildOrderByClause(
                    sortBy.getName(), associatedClass, collection.getRole(), order));
        }
    }
}
