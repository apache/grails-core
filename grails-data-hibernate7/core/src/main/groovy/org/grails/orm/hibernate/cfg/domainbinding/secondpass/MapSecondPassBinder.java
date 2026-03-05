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

import java.util.List;
import java.util.Map;
import org.grails.orm.hibernate.cfg.ColumnConfig;
import org.grails.orm.hibernate.cfg.PersistentEntityNamingStrategy;
import org.grails.orm.hibernate.cfg.PropertyConfig;
import org.grails.orm.hibernate.cfg.domainbinding.binder.ColumnConfigToColumnBinder;
import org.grails.orm.hibernate.cfg.domainbinding.binder.SimpleValueColumnBinder;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateManyToManyProperty;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateOneToManyProperty;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateToManyProperty;
import org.grails.orm.hibernate.cfg.domainbinding.util.SimpleValueColumnFetcher;
import org.hibernate.MappingException;
import org.hibernate.boot.spi.InFlightMetadataCollector;
import org.hibernate.boot.spi.MetadataBuildingContext;
import org.hibernate.mapping.BasicValue;
import org.hibernate.mapping.Column;
import org.hibernate.type.StandardBasicTypes;

/** Refactored from CollectionBinder to handle map second pass binding. */
@SuppressWarnings("PMD.DataflowAnomalyAnalysis")
public class MapSecondPassBinder {
    private final MetadataBuildingContext metadataBuildingContext;
    private final PersistentEntityNamingStrategy namingStrategy;
    private final CollectionSecondPassBinder collectionSecondPassBinder;
    private final SimpleValueColumnBinder simpleValueColumnBinder;
    private final ColumnConfigToColumnBinder columnConfigToColumnBinder;
    private final SimpleValueColumnFetcher simpleValueColumnFetcher;

  public MapSecondPassBinder(
      MetadataBuildingContext metadataBuildingContext,
      PersistentEntityNamingStrategy namingStrategy,
      CollectionSecondPassBinder collectionSecondPassBinder,
      SimpleValueColumnBinder simpleValueColumnBinder,
      ColumnConfigToColumnBinder columnConfigToColumnBinder,
      SimpleValueColumnFetcher simpleValueColumnFetcher) {
    this.metadataBuildingContext = metadataBuildingContext;
    this.namingStrategy = namingStrategy;
    this.collectionSecondPassBinder = collectionSecondPassBinder;
    this.simpleValueColumnBinder = simpleValueColumnBinder;
    this.columnConfigToColumnBinder = columnConfigToColumnBinder;
    this.simpleValueColumnFetcher = simpleValueColumnFetcher;
  }

  public void bindMapSecondPass(
      @Nonnull HibernateToManyProperty property,
      @Nonnull InFlightMetadataCollector mappings,
      Map<?, ?> persistentClasses,
      @Nonnull org.hibernate.mapping.Map map) {
    collectionSecondPassBinder.bindCollectionSecondPass(property, mappings, persistentClasses, map);

    String type = property.getIndexColumnType("string");
    String columnName1 = property.getIndexColumnName(namingStrategy);
    BasicValue value = simpleValueColumnBinder.bindSimpleValue(metadataBuildingContext, map.getCollectionTable(), type, columnName1, true);
    PropertyConfig mappedForm = property.getMappedForm();
    if (mappedForm.getIndexColumn() != null) {
      Column column = simpleValueColumnFetcher.getColumnForSimpleValue(value);
      ColumnConfig columnConfig = getSingleColumnConfig(mappedForm.getIndexColumn());
      columnConfigToColumnBinder.bindColumnConfigToColumn(column, columnConfig, mappedForm);
    }

    public void bindMapSecondPass(
            @Nonnull HibernateToManyProperty property,
            Map<?, ?> persistentClasses,
            @Nonnull org.hibernate.mapping.Map map) {
        collectionSecondPassBinder.bindCollectionSecondPass(property, persistentClasses, map);

        String type = property.getIndexColumnType("string");
        String columnName1 = property.getIndexColumnName(namingStrategy);
        BasicValue value = simpleValueColumnBinder.bindSimpleValue(
                metadataBuildingContext, map.getCollectionTable(), type, columnName1, true);
        PropertyConfig mappedForm = property.getMappedForm();
        if (mappedForm.getIndexColumn() != null) {
            Column column = simpleValueColumnFetcher.getColumnForSimpleValue(value);
            ColumnConfig columnConfig = getSingleColumnConfig(mappedForm.getIndexColumn());
            columnConfigToColumnBinder.bindColumnConfigToColumn(column, columnConfig, mappedForm);
        }

      String typeName = null;
      Class<?> componentType = property.getComponentType();
      if (componentType != null) {
        typeName = property.getTypeName(componentType);
      }

      if (typeName == null) {
        typeName = property.getTypeName();
      }
      if (typeName == null || typeName.equals(Object.class.getName())) {
        typeName = StandardBasicTypes.STRING.getName();
      }
      String columnName = property.getMapElementName(namingStrategy);
      BasicValue elt = simpleValueColumnBinder.bindSimpleValue(metadataBuildingContext, map.getCollectionTable(), typeName, columnName, false);
      map.setElement(elt);
    }

    private ColumnConfig getSingleColumnConfig(PropertyConfig propertyConfig) {
        if (propertyConfig != null) {
            List<ColumnConfig> columns = propertyConfig.getColumns();
            if (columns != null && !columns.isEmpty()) {
                return columns.get(0);
            }
        }
        return null;
    }
}
