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
package org.grails.orm.hibernate.cfg.domainbinding.binder;

import org.hibernate.boot.spi.InFlightMetadataCollector;
import org.hibernate.boot.spi.MetadataBuildingContext;
import org.hibernate.mapping.Collection;
import org.hibernate.mapping.OneToMany;

import org.grails.orm.hibernate.cfg.PersistentEntityNamingStrategy;
import org.grails.orm.hibernate.cfg.domainbinding.collectionType.CollectionHolder;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateToManyEntityProperty;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateToManyProperty;
import org.grails.orm.hibernate.cfg.domainbinding.secondpass.BasicCollectionElementBinder;
import org.grails.orm.hibernate.cfg.domainbinding.secondpass.BidirectionalMapElementBinder;
import org.grails.orm.hibernate.cfg.domainbinding.secondpass.BidirectionalOneToManyLinker;
import org.grails.orm.hibernate.cfg.domainbinding.secondpass.CollectionKeyBinder;
import org.grails.orm.hibernate.cfg.domainbinding.secondpass.CollectionKeyColumnUpdater;
import org.grails.orm.hibernate.cfg.domainbinding.secondpass.CollectionSecondPassBinder;
import org.grails.orm.hibernate.cfg.domainbinding.secondpass.CollectionWithJoinTableBinder;
import org.grails.orm.hibernate.cfg.domainbinding.secondpass.DependentKeyValueBinder;
import org.grails.orm.hibernate.cfg.domainbinding.secondpass.HibernateToManyEntityOrderByBinder;
import org.grails.orm.hibernate.cfg.domainbinding.secondpass.ListSecondPass;
import org.grails.orm.hibernate.cfg.domainbinding.secondpass.ListSecondPassBinder;
import org.grails.orm.hibernate.cfg.domainbinding.secondpass.ManyToOneElementBinder;
import org.grails.orm.hibernate.cfg.domainbinding.secondpass.MapSecondPass;
import org.grails.orm.hibernate.cfg.domainbinding.secondpass.MapSecondPassBinder;
import org.grails.orm.hibernate.cfg.domainbinding.secondpass.PrimaryKeyValueCreator;
import org.grails.orm.hibernate.cfg.domainbinding.secondpass.SetSecondPass;
import org.grails.orm.hibernate.cfg.domainbinding.secondpass.ToManyEntityMultiTenantFilterBinder;
import org.grails.orm.hibernate.cfg.domainbinding.secondpass.UnidirectionalOneToManyBinder;
import org.grails.orm.hibernate.cfg.domainbinding.secondpass.UnidirectionalOneToManyInverseValuesBinder;
import org.grails.orm.hibernate.cfg.domainbinding.util.DefaultColumnNameFetcher;
import org.grails.orm.hibernate.cfg.domainbinding.util.GrailsPropertyResolver;
import org.grails.orm.hibernate.cfg.domainbinding.util.SimpleValueColumnFetcher;
import org.grails.orm.hibernate.cfg.domainbinding.util.TableForManyCalculator;

import static org.grails.orm.hibernate.cfg.domainbinding.util.CascadeBehavior.ALL_DELETE_ORPHAN;

/** Handles the binding of collections to the Hibernate runtime meta model. */
@SuppressWarnings("PMD.DataflowAnomalyAnalysis")
public class CollectionBinder {

    private final MetadataBuildingContext metadataBuildingContext;
    private final CollectionHolder collectionHolder;
    private final ListSecondPassBinder listSecondPassBinder;
    private final CollectionSecondPassBinder collectionSecondPassBinder;
    private final MapSecondPassBinder mapSecondPassBinder;
    private final InFlightMetadataCollector mappings;
    private final TableForManyCalculator tableForManyCalculator;

    /** Creates a new {@link CollectionBinder} instance. */
    public CollectionBinder(
            MetadataBuildingContext metadataBuildingContext,
            PersistentEntityNamingStrategy namingStrategy,
            SimpleValueBinder simpleValueBinder,
            EnumTypeBinder enumTypeBinder,
            ManyToOneBinder manyToOneBinder,
            CompositeIdentifierToManyToOneBinder compositeIdentifierToManyToOneBinder,
            SimpleValueColumnFetcher simpleValueColumnFetcher,
            CollectionHolder collectionHolder,
            InFlightMetadataCollector mappings,
            TableForManyCalculator tableForManyCalculator) {
        this.metadataBuildingContext = metadataBuildingContext;
        this.collectionHolder = collectionHolder;
        this.mappings = mappings;
        this.tableForManyCalculator = tableForManyCalculator;
        GrailsPropertyResolver grailsPropertyResolver = new GrailsPropertyResolver();
        CollectionForPropertyConfigBinder collectionForPropertyConfigBinder = new CollectionForPropertyConfigBinder();
        UnidirectionalOneToManyInverseValuesBinder unidirectionalOneToManyInverseValuesBinder =
                new UnidirectionalOneToManyInverseValuesBinder(metadataBuildingContext);
        SimpleValueColumnBinder simpleValueColumnBinder = new SimpleValueColumnBinder();
        CollectionWithJoinTableBinder collectionWithJoinTableBinder = new CollectionWithJoinTableBinder(
                namingStrategy,
                unidirectionalOneToManyInverseValuesBinder,
                compositeIdentifierToManyToOneBinder,
                collectionForPropertyConfigBinder,
                simpleValueColumnBinder,
                new BasicCollectionElementBinder(
                        metadataBuildingContext,
                        namingStrategy,
                        enumTypeBinder,
                        simpleValueColumnBinder,
                        simpleValueColumnFetcher,
                        new ColumnConfigToColumnBinder()));
        this.collectionSecondPassBinder = new CollectionSecondPassBinder(
                new CollectionKeyColumnUpdater(new CollectionKeyBinder(
                        new BidirectionalOneToManyLinker(grailsPropertyResolver),
                        new DependentKeyValueBinder(simpleValueBinder, compositeIdentifierToManyToOneBinder),
                        simpleValueColumnBinder,
                        new PrimaryKeyValueCreator(metadataBuildingContext))),
                new UnidirectionalOneToManyBinder(collectionWithJoinTableBinder, mappings),
                collectionWithJoinTableBinder,
                new BidirectionalMapElementBinder(manyToOneBinder, collectionForPropertyConfigBinder),
                new ManyToOneElementBinder(manyToOneBinder, collectionForPropertyConfigBinder),
                new HibernateToManyEntityOrderByBinder(),
                new ToManyEntityMultiTenantFilterBinder(new DefaultColumnNameFetcher(namingStrategy))
        );
        this.listSecondPassBinder = new ListSecondPassBinder(
                metadataBuildingContext, namingStrategy, collectionSecondPassBinder, simpleValueColumnBinder, mappings);
        this.mapSecondPassBinder = new MapSecondPassBinder(
                metadataBuildingContext,
                namingStrategy,
                collectionSecondPassBinder,
                simpleValueColumnBinder,
                new ColumnConfigToColumnBinder(),
                simpleValueColumnFetcher);
    }

    /**
     * First pass to bind collection to Hibernate metamodel, sets up second pass
     *
     * @param property The GrailsDomainClassProperty instance
     * @param path     The property path
     * @return the result
     */
    public Collection bindCollection(HibernateToManyProperty property, String path) {
        Collection collection = initializeCollection(property, path);

        configureCollectionMetadata(property, collection);

        if (property.shouldBindWithForeignKey()) {
            bindOneToManyElement((HibernateToManyEntityProperty) property, collection);
        } else {
            bindCollectionTable(property, collection);
        }

        registerSecondPass(property, collection);

        mappings.addCollectionBinding(collection);

        return collection;
    }

    private Collection initializeCollection(HibernateToManyProperty property, String path) {
        Collection collection = collectionHolder.create(property);
        property.setCollection(collection);
        collection.setRole(property.getRole(path));
        return collection;
    }

    private void configureCollectionMetadata(HibernateToManyProperty property, Collection collection) {
        collection.setFetchMode(property.getFetchMode());
        collection.setOrphanDelete(ALL_DELETE_ORPHAN.getValue().equals(property.getCascade()));
        collection.setBatchSize(property.getBatchSize());
    }

    private void bindOneToManyElement(HibernateToManyEntityProperty property, Collection collection) {
        OneToMany oneToMany = new OneToMany(metadataBuildingContext, collection.getOwner());
        oneToMany.setReferencedEntityName(property.getHibernateAssociatedEntity().getName());
        oneToMany.setIgnoreNotFound(true);
        collection.setElement(oneToMany);
    }

    private void bindCollectionTable(HibernateToManyProperty property, Collection collection) {
        String tableName = tableForManyCalculator.getTableName(property);
        String schemaName = tableForManyCalculator.getJoinTableSchema(property);
        String catalogName = tableForManyCalculator.getJoinTableCatalog(property);

        collection.setCollectionTable(
                mappings.addTable(schemaName, catalogName, tableName, null, false, metadataBuildingContext));
        collection.setInverse(property.isBidirectional() && !property.isOwningSide());
    }

    private void registerSecondPass(HibernateToManyProperty property, Collection collection) {
        if (collection instanceof org.hibernate.mapping.List) {
            mappings.addSecondPass(new ListSecondPass(listSecondPassBinder, property));
        } else if (collection instanceof org.hibernate.mapping.Map) {
            mappings.addSecondPass(new MapSecondPass(mapSecondPassBinder, property));
        } else {
            mappings.addSecondPass(new SetSecondPass(collectionSecondPassBinder, property));
        }
    }
}
