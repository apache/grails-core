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
import org.hibernate.mapping.OneToMany;
import org.hibernate.mapping.PersistentClass;

import org.grails.orm.hibernate.cfg.domainbinding.binder.CollectionForPropertyConfigBinder;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateCollectionProperty;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernatePersistentProperty;
import org.grails.orm.hibernate.cfg.domainbinding.util.OrderByClauseBuilder;

/** Binds the order-by clause and discriminator where condition to a collection. */
public class CollectionOrderByBinder {

    private final OrderByClauseBuilder orderByClauseBuilder;
    private final CollectionForPropertyConfigBinder collectionForPropertyConfigBinder;

    /** Creates a new {@link CollectionOrderByBinder} instance. */
    public CollectionOrderByBinder() {
        this.orderByClauseBuilder = new OrderByClauseBuilder();
        this.collectionForPropertyConfigBinder = new CollectionForPropertyConfigBinder();
    }

    /** Binds the order-by clause and discriminator where condition to the given collection. */
    public void bind(HibernateCollectionProperty property) {
        Collection collection = property.getCollection();
        PersistentClass associatedClass = property.getAssociatedClass();
        GrailsHibernatePersistentEntity referenced = property.getHibernateAssociatedEntity();

        if (referenced.isTablePerHierarchySubclass()) {
            String discriminatorColumnName = referenced.getDiscriminatorColumnName();
            Set<String> discSet = referenced.buildDiscriminatorSet();
            String clause = String.join(",", discSet);
            collection.setWhere(discriminatorColumnName + " in (" + clause + ")");
        }
        if (property.hasSort()) {
            HibernatePersistentProperty sortBy = referenced.getHibernatePropertyByName(property.getSort());
            String order = Optional.ofNullable(property.getOrder()).orElse("asc");
            collection.setOrderBy(orderByClauseBuilder.buildOrderByClause(
                    sortBy.getName(), associatedClass, collection.getRole(), order));
        }

        if (!collection.isOneToMany()) {
            return;
        }
        OneToMany oneToMany = (OneToMany) collection.getElement();
        oneToMany.setAssociatedClass(associatedClass);
        if (property.shouldBindWithForeignKey()) {
            collection.setCollectionTable(associatedClass.getTable());
        }
        collectionForPropertyConfigBinder.bindCollectionForPropertyConfig(property);
    }

}
