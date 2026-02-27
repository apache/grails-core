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

import jakarta.annotation.Nonnull;
import org.grails.orm.hibernate.cfg.GrailsHibernateUtil;
import org.grails.orm.hibernate.cfg.JoinTable;
import org.grails.orm.hibernate.cfg.PersistentEntityNamingStrategy;
import org.grails.orm.hibernate.cfg.PropertyConfig;
import org.grails.orm.hibernate.cfg.domainbinding.collectionType.CollectionHolder;
import org.grails.orm.hibernate.cfg.domainbinding.collectionType.CollectionType;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateOneToManyProperty;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernatePersistentProperty;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateToManyProperty;
import org.grails.orm.hibernate.cfg.domainbinding.secondpass.BidirectionalOneToManyLinker;
import org.grails.orm.hibernate.cfg.domainbinding.secondpass.CollectionKeyColumnUpdater;
import org.grails.orm.hibernate.cfg.domainbinding.secondpass.CollectionSecondPassBinder;
import org.grails.orm.hibernate.cfg.domainbinding.secondpass.CollectionWithJoinTableBinder;
import org.grails.orm.hibernate.cfg.domainbinding.secondpass.DependentKeyValueBinder;
import org.grails.orm.hibernate.cfg.domainbinding.secondpass.ListSecondPass;
import org.grails.orm.hibernate.cfg.domainbinding.secondpass.ListSecondPassBinder;
import org.grails.orm.hibernate.cfg.domainbinding.secondpass.MapSecondPass;
import org.grails.orm.hibernate.cfg.domainbinding.secondpass.MapSecondPassBinder;
import org.grails.orm.hibernate.cfg.domainbinding.secondpass.PrimaryKeyValueCreator;
import org.grails.orm.hibernate.cfg.domainbinding.secondpass.SetSecondPass;
import org.grails.orm.hibernate.cfg.domainbinding.secondpass.UnidirectionalOneToManyBinder;
import org.grails.orm.hibernate.cfg.domainbinding.secondpass.UnidirectionalOneToManyInverseValuesBinder;
import org.grails.orm.hibernate.cfg.domainbinding.util.CascadeBehavior;
import org.grails.orm.hibernate.cfg.domainbinding.util.DefaultColumnNameFetcher;
import org.grails.orm.hibernate.cfg.domainbinding.util.GrailsPropertyResolver;
import org.grails.orm.hibernate.cfg.domainbinding.util.NamespaceNameExtractor;
import org.grails.orm.hibernate.cfg.domainbinding.util.SimpleValueColumnFetcher;
import org.grails.orm.hibernate.cfg.domainbinding.util.TableForManyCalculator;
import org.hibernate.FetchMode;
import org.hibernate.boot.spi.InFlightMetadataCollector;
import org.hibernate.boot.spi.MetadataBuildingContext;
import org.hibernate.mapping.Collection;
import org.hibernate.mapping.OneToMany;
import org.hibernate.mapping.PersistentClass;
import org.hibernate.mapping.Table;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Handles the binding of collections to the Hibernate runtime meta model. */
@SuppressWarnings("PMD.DataflowAnomalyAnalysis")
public class CollectionBinder {

  private static final Logger LOG = LoggerFactory.getLogger(CollectionBinder.class);

  private final MetadataBuildingContext metadataBuildingContext;
  private final PersistentEntityNamingStrategy namingStrategy;
  private final CollectionHolder collectionHolder;
  private final ListSecondPassBinder listSecondPassBinder;
  private final CollectionSecondPassBinder collectionSecondPassBinder;
  private final MapSecondPassBinder mapSecondPassBinder;

  /** Creates a new {@link CollectionBinder} instance. */
  public CollectionBinder(
      MetadataBuildingContext metadataBuildingContext,
      PersistentEntityNamingStrategy namingStrategy,
      SimpleValueBinder simpleValueBinder,
      EnumTypeBinder enumTypeBinder,
      ManyToOneBinder manyToOneBinder,
      CompositeIdentifierToManyToOneBinder compositeIdentifierToManyToOneBinder,
      SimpleValueColumnFetcher simpleValueColumnFetcher,
      CollectionHolder collectionHolder) {
    this.metadataBuildingContext = metadataBuildingContext;
    this.namingStrategy = namingStrategy;
    this.collectionHolder = collectionHolder;
    GrailsPropertyResolver grailsPropertyResolver = new GrailsPropertyResolver();
    CollectionForPropertyConfigBinder collectionForPropertyConfigBinder =
        new CollectionForPropertyConfigBinder();
    UnidirectionalOneToManyInverseValuesBinder unidirectionalOneToManyInverseValuesBinder =
        new UnidirectionalOneToManyInverseValuesBinder();
    SimpleValueColumnBinder simpleValueColumnBinder = new SimpleValueColumnBinder();
    CollectionWithJoinTableBinder collectionWithJoinTableBinder =
        new CollectionWithJoinTableBinder(
            metadataBuildingContext,
            namingStrategy,
            unidirectionalOneToManyInverseValuesBinder,
            enumTypeBinder,
            compositeIdentifierToManyToOneBinder,
            simpleValueColumnFetcher,
            collectionForPropertyConfigBinder,
            simpleValueColumnBinder,
            new ColumnConfigToColumnBinder());
    this.collectionSecondPassBinder =
        new CollectionSecondPassBinder(
            manyToOneBinder,
            new PrimaryKeyValueCreator(metadataBuildingContext),
            new CollectionKeyColumnUpdater(),
            new BidirectionalOneToManyLinker(grailsPropertyResolver),
            new DependentKeyValueBinder(simpleValueBinder, compositeIdentifierToManyToOneBinder),
            new UnidirectionalOneToManyBinder(collectionWithJoinTableBinder),
            collectionWithJoinTableBinder,
            collectionForPropertyConfigBinder,
            new DefaultColumnNameFetcher(namingStrategy),
            simpleValueColumnBinder);
    this.listSecondPassBinder =
        new ListSecondPassBinder(
            metadataBuildingContext,
            namingStrategy,
            collectionSecondPassBinder,
            simpleValueColumnBinder);
    this.mapSecondPassBinder =
        new MapSecondPassBinder(
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
   * @param owner The owning persistent class
   * @param mappings The Hibernate mappings instance
   * @param path The property path
   * @return the result
   */
  public Collection bindCollection(
      HibernateToManyProperty property,
      PersistentClass owner,
      @Nonnull InFlightMetadataCollector mappings,
      String path) {
    CollectionType collectionType = collectionHolder.get(property.getType());
    Collection collection = collectionType.create(property, owner);

    // set role
    String propertyName = getNameForPropertyAndPath(property, path);
    collection.setRole(GrailsHibernateUtil.qualify(property.getOwner().getName(), propertyName));

    PropertyConfig pc = property.getMappedForm();
    // configure eager fetching
    final FetchMode fetchMode = pc.getFetchMode();
    if (fetchMode == FetchMode.JOIN) {
      collection.setFetchMode(FetchMode.JOIN);
    } else if (pc.getFetchMode() != null) {
      collection.setFetchMode(pc.getFetchMode());
    } else {
      collection.setFetchMode(FetchMode.DEFAULT);
    }

    if (pc.getCascade() != null) {
      collection.setOrphanDelete(
          pc.getCascade().equals(CascadeBehavior.ALL_DELETE_ORPHAN.getValue()));
    }
    // if it's a one-to-many mapping
    if (property.shouldBindWithForeignKey()) {
      OneToMany oneToMany = new OneToMany(metadataBuildingContext, collection.getOwner());
      collection.setElement(oneToMany);
      bindOneToMany((HibernateOneToManyProperty) property, oneToMany);
    } else {
      bindCollectionTable(property, mappings, collection, owner.getTable());

      if (property.isBidirectional()) {
        if (!property.isOwningSide()) {
          collection.setInverse(true);
        }
      } else {
        collection.setInverse(false);
      }
    }

    if (pc.getBatchSize() != null) {
      collection.setBatchSize(pc.getBatchSize());
    }

    // set up second pass
    if (collection instanceof org.hibernate.mapping.List) {
      mappings.addSecondPass(
          new ListSecondPass(listSecondPassBinder, property, mappings, collection));
    } else if (collection instanceof org.hibernate.mapping.Map) {
      mappings.addSecondPass(
          new MapSecondPass(mapSecondPassBinder, property, mappings, collection));
    } else { // Collection -> Bag
      mappings.addSecondPass(
          new SetSecondPass(collectionSecondPassBinder, property, mappings, collection));
    }
    mappings.addCollectionBinding(collection);
    return collection;
  }

  private String getNameForPropertyAndPath(HibernatePersistentProperty property, String path) {
    if (GrailsHibernateUtil.isNotEmpty(path)) {
      return GrailsHibernateUtil.qualify(path, property.getName());
    }
    return property.getName();
  }

  private void bindOneToMany(HibernateOneToManyProperty currentGrailsProp, OneToMany one) {
    one.setReferencedEntityName(currentGrailsProp.getHibernateAssociatedEntity().getName());
    one.setIgnoreNotFound(true);
  }

  private void bindCollectionTable(
      HibernateToManyProperty property,
      @Nonnull InFlightMetadataCollector mappings,
      Collection collection,
      Table ownerTable) {

    String owningTableSchema = ownerTable.getSchema();
    PropertyConfig config = property.getMappedForm();
    JoinTable jt = config.getJoinTable();

    String s = new TableForManyCalculator(namingStrategy).calculateTableForMany(property);
    String tableName =
        (jt != null && jt.getName() != null ? jt.getName() : namingStrategy.resolveTableName(s));

    String schemaName = NamespaceNameExtractor.getSchemaName(mappings);
    String catalogName = NamespaceNameExtractor.getCatalogName(mappings);
    if (jt != null) {
      if (jt.getSchema() != null) {
        schemaName = jt.getSchema();
      }
      if (jt.getCatalog() != null) {
        catalogName = jt.getCatalog();
      }
    }

    if (schemaName == null && owningTableSchema != null) {
      schemaName = owningTableSchema;
    }

    collection.setCollectionTable(
        mappings.addTable(
            schemaName, catalogName, tableName, null, false, metadataBuildingContext));
  }
}
