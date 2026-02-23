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

import static org.grails.orm.hibernate.cfg.domainbinding.binder.GrailsDomainBinder.*;

import jakarta.annotation.Nonnull;
import java.util.Optional;
import org.grails.datastore.mapping.model.types.Basic;
import org.grails.orm.hibernate.cfg.*;
import org.grails.orm.hibernate.cfg.domainbinding.binder.CollectionForPropertyConfigBinder;
import org.grails.orm.hibernate.cfg.domainbinding.binder.ColumnConfigToColumnBinder;
import org.grails.orm.hibernate.cfg.domainbinding.binder.CompositeIdentifierToManyToOneBinder;
import org.grails.orm.hibernate.cfg.domainbinding.binder.EnumTypeBinder;
import org.grails.orm.hibernate.cfg.domainbinding.binder.SimpleValueColumnBinder;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateToManyProperty;
import org.grails.orm.hibernate.cfg.domainbinding.util.BackticksRemover;
import org.grails.orm.hibernate.cfg.domainbinding.util.SimpleValueColumnFetcher;
import org.hibernate.boot.spi.InFlightMetadataCollector;
import org.hibernate.boot.spi.MetadataBuildingContext;
import org.hibernate.mapping.*;
import org.hibernate.mapping.Collection;

/** Binds a collection with a join table. */
public class CollectionWithJoinTableBinder {

  private final MetadataBuildingContext metadataBuildingContext;
  private final PersistentEntityNamingStrategy namingStrategy;
  private final UnidirectionalOneToManyInverseValuesBinder
      unidirectionalOneToManyInverseValuesBinder;
  private final EnumTypeBinder enumTypeBinder;
  private final CompositeIdentifierToManyToOneBinder compositeIdentifierToManyToOneBinder;
  private final SimpleValueColumnFetcher simpleValueColumnFetcher;
  private final CollectionForPropertyConfigBinder collectionForPropertyConfigBinder;
  private final SimpleValueColumnBinder simpleValueColumnBinder;
  private final ColumnConfigToColumnBinder columnConfigToColumnBinder;

  /** Creates a new {@link CollectionWithJoinTableBinder} instance. */
  public CollectionWithJoinTableBinder(
      MetadataBuildingContext metadataBuildingContext,
      PersistentEntityNamingStrategy namingStrategy,
      UnidirectionalOneToManyInverseValuesBinder unidirectionalOneToManyInverseValuesBinder,
      EnumTypeBinder enumTypeBinder,
      CompositeIdentifierToManyToOneBinder compositeIdentifierToManyToOneBinder,
      SimpleValueColumnFetcher simpleValueColumnFetcher,
      CollectionForPropertyConfigBinder collectionForPropertyConfigBinder,
      SimpleValueColumnBinder simpleValueColumnBinder,
      ColumnConfigToColumnBinder columnConfigToColumnBinder) {
    this.metadataBuildingContext = metadataBuildingContext;
    this.namingStrategy = namingStrategy;
    this.unidirectionalOneToManyInverseValuesBinder = unidirectionalOneToManyInverseValuesBinder;
    this.enumTypeBinder = enumTypeBinder;
    this.compositeIdentifierToManyToOneBinder = compositeIdentifierToManyToOneBinder;
    this.simpleValueColumnFetcher = simpleValueColumnFetcher;
    this.collectionForPropertyConfigBinder = collectionForPropertyConfigBinder;
    this.simpleValueColumnBinder = simpleValueColumnBinder;
    this.columnConfigToColumnBinder = columnConfigToColumnBinder;
  }

  /** Bind collection with join table. */
  public void bindCollectionWithJoinTable(
      @Nonnull HibernateToManyProperty property,
      @Nonnull InFlightMetadataCollector mappings,
      @Nonnull Collection collection) {

    collection.setInverse(false);
    SimpleValue element;
    final boolean isBasicCollectionType = property instanceof Basic;
    if (isBasicCollectionType) {
      element = new BasicValue(metadataBuildingContext, collection.getCollectionTable());
    } else {
      // for a normal unidirectional one-to-many we use a join column
      element = new ManyToOne(metadataBuildingContext, collection.getCollectionTable());
      unidirectionalOneToManyInverseValuesBinder.bindUnidirectionalOneToManyInverseValues(
          property, (ManyToOne) element);
    }

    String columnName;

    var joinColumnMappingOptional =
        Optional.ofNullable(property.getMappedForm()).map(PropertyConfig::getJoinTableColumnConfig);
    if (isBasicCollectionType) {
      final Class<?> referencedType = ((Basic) property).getComponentType();
      final boolean isEnum = referencedType.isEnum();
      if (joinColumnMappingOptional.isPresent()) {
        columnName = joinColumnMappingOptional.get().getName();
      } else {
        var clazz = namingStrategy.resolveColumnName(referencedType.getName());
        var prop = namingStrategy.resolveTableName(property.getName());
        columnName =
            isEnum
                ? clazz
                : new BackticksRemover().apply(prop)
                    + UNDERSCORE
                    + new BackticksRemover().apply(clazz);
      }

      if (isEnum) {
        enumTypeBinder.bindEnumType(property, referencedType, (BasicValue) element, columnName);
      } else {

        String typeName = property.getTypeName(referencedType);

        simpleValueColumnBinder.bindSimpleValue(element, typeName, columnName, true);
        if (joinColumnMappingOptional.isPresent()) {
          Column column = simpleValueColumnFetcher.getColumnForSimpleValue(element);
          ColumnConfig columnConfig = joinColumnMappingOptional.get();
          final PropertyConfig mappedForm = property.getMappedForm();
          columnConfigToColumnBinder.bindColumnConfigToColumn(column, columnConfig, mappedForm);
        }
      }
    } else {
      final var domainClass = property.getHibernateAssociatedEntity();

      if (domainClass != null) {
        if (domainClass.getHibernateCompositeIdentity().isPresent()) {
          CompositeIdentity ci = domainClass.getHibernateCompositeIdentity().get();
          compositeIdentifierToManyToOneBinder.bindCompositeIdentifierToManyToOne(
              property, element, ci, domainClass, EMPTY_PATH);
        } else {
          if (joinColumnMappingOptional.isPresent()) {
            columnName = joinColumnMappingOptional.get().getName();
          } else {
            var decapitalize = domainClass.getHibernateRootEntity().getJavaClass().getSimpleName();
            columnName = namingStrategy.resolveColumnName(decapitalize) + FOREIGN_KEY_SUFFIX;
          }

          simpleValueColumnBinder.bindSimpleValue(element, "long", columnName, true);
        }
      }
    }

    collection.setElement(element);

    collectionForPropertyConfigBinder.bindCollectionForPropertyConfig(collection, property);
  }
}
