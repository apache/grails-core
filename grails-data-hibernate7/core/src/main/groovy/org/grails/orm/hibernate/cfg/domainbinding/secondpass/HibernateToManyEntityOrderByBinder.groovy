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
package org.grails.orm.hibernate.cfg.domainbinding.secondpass

import groovy.transform.CompileStatic
import org.hibernate.mapping.Collection
import org.hibernate.mapping.OneToMany
import org.hibernate.mapping.PersistentClass

import org.grails.orm.hibernate.cfg.domainbinding.binder.CollectionForPropertyConfigBinder
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernatePersistentProperty
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateToManyEntityProperty
import org.grails.orm.hibernate.cfg.domainbinding.util.OrderByClauseBuilder

/**
 * Binds the order-by clause to a collection, and the discriminator where condition to a one-to-many
 * collection of a table-per-hierarchy subclass.
 */
@CompileStatic
class HibernateToManyEntityOrderByBinder {

    private final OrderByClauseBuilder orderByClauseBuilder
    private final CollectionForPropertyConfigBinder collectionForPropertyConfigBinder

    /** Creates a new {@link HibernateToManyEntityOrderByBinder} instance. */
    HibernateToManyEntityOrderByBinder() {
        this.orderByClauseBuilder = new OrderByClauseBuilder()
        this.collectionForPropertyConfigBinder = new CollectionForPropertyConfigBinder()
    }

    /**
     * Binds the order-by clause and, for a one-to-many collection of a table-per-hierarchy subclass,
     * the discriminator where condition to the given collection.
     */
    void bind(HibernateToManyEntityProperty property) {
        Collection collection = property.collection
        PersistentClass associatedClass = property.associatedClass
        GrailsHibernatePersistentEntity referenced = property.hibernateAssociatedEntity

        if (property.hasSort()) {
            HibernatePersistentProperty sortBy = referenced.getHibernatePropertyByName(property.sort)
            String order = property.order != null ? property.order : 'asc'
            collection.orderBy = orderByClauseBuilder.buildOrderByClause(
                    sortBy.name, associatedClass, collection.role, order)
        }

        if (!collection.isOneToMany()) {
            return
        }
        // the where condition applies to the collection table, which only holds the discriminator
        // column when the collection is mapped by a foreign key in the element's table
        if (referenced.isTablePerHierarchySubclass()) {
            String discriminatorColumnName = referenced.discriminatorColumnName
            Set<String> discSet = referenced.buildDiscriminatorSet()
            String clause = discSet.join(',')
            collection.where = discriminatorColumnName + ' in (' + clause + ')'
        }
        OneToMany oneToMany = (OneToMany) collection.element
        oneToMany.associatedClass = associatedClass
        if (property.shouldBindWithForeignKey()) {
            collection.collectionTable = associatedClass.table
        }
        collectionForPropertyConfigBinder.bindCollectionForPropertyConfig(property)
    }

}
