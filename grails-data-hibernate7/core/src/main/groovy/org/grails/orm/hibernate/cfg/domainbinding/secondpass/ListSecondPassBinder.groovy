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

import jakarta.annotation.Nonnull;

import org.hibernate.boot.spi.InFlightMetadataCollector;
import org.hibernate.boot.spi.MetadataBuildingContext;
import org.hibernate.mapping.Backref;
import org.hibernate.mapping.BasicValue;
import org.hibernate.mapping.DependantValue;
import org.hibernate.mapping.IndexBackref;
import org.hibernate.mapping.List;
import org.hibernate.mapping.PersistentClass;
import org.hibernate.mapping.Table;

import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.orm.hibernate.cfg.PersistentEntityNamingStrategy;
import org.grails.orm.hibernate.cfg.domainbinding.binder.SimpleValueColumnBinder;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateAssociation;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateToManyProperty;
import org.grails.orm.hibernate.cfg.domainbinding.util.BackticksRemover;

import static org.grails.orm.hibernate.cfg.domainbinding.binder.GrailsDomainBinder.UNDERSCORE;

/** Refactored from CollectionBinder to handle list second pass binding. */
@SuppressWarnings("PMD.DataflowAnomalyAnalysis")
public class ListSecondPassBinder {

    private static final String DEFAULT_INDEX_TYPE = "integer";

    private final MetadataBuildingContext metadataBuildingContext;
    private final CollectionSecondPassBinder collectionSecondPassBinder;
    private final PersistentEntityNamingStrategy namingStrategy;
    private final SimpleValueColumnBinder simpleValueColumnBinder;
    private final InFlightMetadataCollector mappings;
    private final BackticksRemover backticksRemover = new BackticksRemover();

    public ListSecondPassBinder(
            MetadataBuildingContext metadataBuildingContext,
            PersistentEntityNamingStrategy namingStrategy,
            CollectionSecondPassBinder collectionSecondPassBinder,
            SimpleValueColumnBinder simpleValueColumnBinder,
            InFlightMetadataCollector mappings) {
        this.metadataBuildingContext = metadataBuildingContext;
        this.collectionSecondPassBinder = collectionSecondPassBinder;
        this.namingStrategy = namingStrategy;
        this.simpleValueColumnBinder = simpleValueColumnBinder;
        this.mappings = mappings;
    }

    public void bindListSecondPass(@Nonnull HibernateToManyProperty property) {
        property.validateOwningSide();
        collectionSecondPassBinder.bindCollectionSecondPass(property);
        List list = (List) property.getCollection();
        bindIndexColumn(property);
        list.setBaseIndex(0);
        list.setInverse(false);
        list.getElement().createForeignKey();
        bindBackReferences(property, list);
    }

    private void bindIndexColumn(HibernateToManyProperty property) {
        List list = (List) property.getCollection();
        Table collectionTable = list.getCollectionTable();
        String columnName = property.getIndexColumnName(namingStrategy);
        String type = property.getIndexColumnType(DEFAULT_INDEX_TYPE);

        BasicValue indexValue = simpleValueColumnBinder.bindSimpleValue(
                metadataBuildingContext, collectionTable, type, columnName, true);
        list.setIndex(indexValue);
    }

    private void bindBackReferences(HibernateToManyProperty property, List list) {
        if (!property.isBidirectional()) {
            return;
        }

        HibernateAssociation inverseSide = property.getHibernateInverseSide();
        String entityName = inverseSide.getHibernateOwner().getName();
        PersistentClass referenced = mappings.getEntityBinding(entityName);

        if (referenced != null) {
            boolean compositeIdProperty = inverseSide.isCompositeIdProperty();

            if (!compositeIdProperty) {
                addBackref(property, list, referenced);
            }

            if (shouldAddIndexBackref(list, compositeIdProperty)) {
                addIndexBackref(property, list, referenced);
            }
        }
    }

    private void addBackref(HibernateToManyProperty property, List list, PersistentClass referenced) {
        Backref prop = new Backref();
        final PersistentEntity owner = property.getOwner();
        prop.setEntityName(owner.getName());

        String name = UNDERSCORE +
                backticksRemover.apply(owner.getJavaClass().getSimpleName()) +
                UNDERSCORE +
                backticksRemover.apply(property.getName()) +
                "Backref";

        prop.setName(name);
        prop.setSelectable(false);
        prop.setUpdatable(false);
        prop.setInsertable(false);
        prop.setCollectionRole(list.getRole());
        prop.setValue(list.getKey());

        DependantValue value = (DependantValue) prop.getValue();
        if (!property.isCircular()) {
            value.setNullable(false);
        }
        value.setUpdateable(true);
        prop.setOptional(false);

        referenced.addProperty(prop);
    }

    private boolean shouldAddIndexBackref(List list, boolean compositeIdProperty) {
        return (!list.getKey().isNullable() && !list.isInverse()) || compositeIdProperty;
    }

    private void addIndexBackref(HibernateToManyProperty property, List list, PersistentClass referenced) {
        IndexBackref ib = new IndexBackref();
        ib.setName(UNDERSCORE + property.getName() + "IndexBackref");
        ib.setUpdatable(false);
        ib.setSelectable(false);
        ib.setInsertable(false);
        ib.setCollectionRole(list.getRole());
        ib.setEntityName(list.getOwner().getEntityName());
        ib.setValue(list.getIndex());
        referenced.addProperty(ib);
    }
}
