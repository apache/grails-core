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
import jakarta.annotation.Nonnull
import org.hibernate.boot.spi.InFlightMetadataCollector
import org.hibernate.boot.spi.MetadataBuildingContext
import org.hibernate.mapping.Backref
import org.hibernate.mapping.BasicValue
import org.hibernate.mapping.DependantValue
import org.hibernate.mapping.IndexBackref
import org.hibernate.mapping.PersistentClass
import org.hibernate.mapping.Table

import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.orm.hibernate.cfg.PersistentEntityNamingStrategy
import org.grails.orm.hibernate.cfg.domainbinding.binder.SimpleValueColumnBinder
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateAssociation
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateToManyProperty
import org.grails.orm.hibernate.cfg.domainbinding.util.BackticksRemover

import static org.grails.orm.hibernate.cfg.domainbinding.binder.GrailsDomainBinder.UNDERSCORE

/** Refactored from CollectionBinder to handle list second pass binding. */
@CompileStatic
@SuppressWarnings('PMD.DataflowAnomalyAnalysis')
class ListSecondPassBinder {

    private static final String DEFAULT_INDEX_TYPE = 'integer'

    private final MetadataBuildingContext metadataBuildingContext
    private final CollectionSecondPassBinder collectionSecondPassBinder
    private final PersistentEntityNamingStrategy namingStrategy
    private final SimpleValueColumnBinder simpleValueColumnBinder
    private final InFlightMetadataCollector mappings
    private final BackticksRemover backticksRemover = new BackticksRemover()

    ListSecondPassBinder(
            MetadataBuildingContext metadataBuildingContext,
            PersistentEntityNamingStrategy namingStrategy,
            CollectionSecondPassBinder collectionSecondPassBinder,
            SimpleValueColumnBinder simpleValueColumnBinder,
            InFlightMetadataCollector mappings) {
        this.metadataBuildingContext = metadataBuildingContext
        this.collectionSecondPassBinder = collectionSecondPassBinder
        this.namingStrategy = namingStrategy
        this.simpleValueColumnBinder = simpleValueColumnBinder
        this.mappings = mappings
    }

    void bindListSecondPass(@Nonnull HibernateToManyProperty property) {
        property.validateOwningSide()
        collectionSecondPassBinder.bindCollectionSecondPass(property)
        org.hibernate.mapping.List list = (org.hibernate.mapping.List) property.collection
        bindIndexColumn(property)
        list.baseIndex = 0
        list.inverse = false
        list.element.createForeignKey()
        bindBackReferences(property, list)
    }

    private void bindIndexColumn(HibernateToManyProperty property) {
        org.hibernate.mapping.List list = (org.hibernate.mapping.List) property.collection
        Table collectionTable = list.collectionTable
        String columnName = property.getIndexColumnName(namingStrategy)
        String type = property.getIndexColumnType(DEFAULT_INDEX_TYPE)

        BasicValue indexValue = simpleValueColumnBinder.bindSimpleValue(
                metadataBuildingContext, collectionTable, type, columnName, true)
        list.index = indexValue
    }

    private void bindBackReferences(HibernateToManyProperty property, org.hibernate.mapping.List list) {
        if (!property.isBidirectional()) {
            return
        }

        HibernateAssociation inverseSide = property.hibernateInverseSide
        String entityName = inverseSide.hibernateOwner.name
        PersistentClass referenced = mappings.getEntityBinding(entityName)

        if (referenced != null) {
            boolean compositeIdProperty = inverseSide.isCompositeIdProperty()

            if (!compositeIdProperty) {
                addBackref(property, list, referenced)
            }

            if (shouldAddIndexBackref(list, compositeIdProperty)) {
                addIndexBackref(property, list, referenced)
            }
        }
    }

    private void addBackref(HibernateToManyProperty property, org.hibernate.mapping.List list, PersistentClass referenced) {
        Backref prop = new Backref()
        final PersistentEntity owner = property.owner
        prop.entityName = owner.name

        String name = UNDERSCORE.toString() +
                backticksRemover.apply(owner.javaClass.simpleName) +
                UNDERSCORE +
                backticksRemover.apply(property.name) +
                'Backref'

        prop.name = name
        prop.selectable = false
        prop.updatable = false
        prop.insertable = false
        prop.collectionRole = list.role
        prop.value = list.key

        DependantValue value = (DependantValue) prop.value
        if (!property.isCircular()) {
            value.nullable = false
        }
        value.updateable = true
        prop.optional = false

        referenced.addProperty(prop)
    }

    private boolean shouldAddIndexBackref(org.hibernate.mapping.List list, boolean compositeIdProperty) {
        return (!list.key.isNullable() && !list.isInverse()) || compositeIdProperty
    }

    private void addIndexBackref(HibernateToManyProperty property, org.hibernate.mapping.List list, PersistentClass referenced) {
        IndexBackref ib = new IndexBackref()
        ib.name = UNDERSCORE.toString() + property.name + 'IndexBackref'
        ib.updatable = false
        ib.selectable = false
        ib.insertable = false
        ib.collectionRole = list.role
        ib.entityName = list.owner.entityName
        ib.value = list.index
        referenced.addProperty(ib)
    }

}
