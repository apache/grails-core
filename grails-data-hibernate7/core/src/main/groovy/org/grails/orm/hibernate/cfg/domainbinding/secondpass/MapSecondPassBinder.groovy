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
import org.hibernate.MappingException
import org.hibernate.boot.spi.MetadataBuildingContext
import org.hibernate.mapping.BasicValue
import org.hibernate.mapping.Column

import org.grails.orm.hibernate.cfg.ColumnConfig
import org.grails.orm.hibernate.cfg.PersistentEntityNamingStrategy
import org.grails.orm.hibernate.cfg.PropertyConfig
import org.grails.orm.hibernate.cfg.domainbinding.binder.ColumnConfigToColumnBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.SimpleValueColumnBinder
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateToManyCollectionProperty
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateToManyProperty
import org.grails.orm.hibernate.cfg.domainbinding.util.SimpleValueColumnFetcher

/** Refactored from CollectionBinder to handle map second pass binding. */
@CompileStatic
@SuppressWarnings('PMD.DataflowAnomalyAnalysis')
class MapSecondPassBinder {

    private final MetadataBuildingContext metadataBuildingContext
    private final PersistentEntityNamingStrategy namingStrategy
    private final CollectionSecondPassBinder collectionSecondPassBinder
    private final SimpleValueColumnBinder simpleValueColumnBinder
    private final ColumnConfigToColumnBinder columnConfigToColumnBinder
    private final SimpleValueColumnFetcher simpleValueColumnFetcher

    MapSecondPassBinder(
            MetadataBuildingContext metadataBuildingContext,
            PersistentEntityNamingStrategy namingStrategy,
            CollectionSecondPassBinder collectionSecondPassBinder,
            SimpleValueColumnBinder simpleValueColumnBinder,
            ColumnConfigToColumnBinder columnConfigToColumnBinder,
            SimpleValueColumnFetcher simpleValueColumnFetcher) {
        this.metadataBuildingContext = metadataBuildingContext
        this.namingStrategy = namingStrategy
        this.collectionSecondPassBinder = collectionSecondPassBinder
        this.simpleValueColumnBinder = simpleValueColumnBinder
        this.columnConfigToColumnBinder = columnConfigToColumnBinder
        this.simpleValueColumnFetcher = simpleValueColumnFetcher
    }

    void bindMapSecondPass(@Nonnull HibernateToManyProperty property) {
        org.hibernate.mapping.Map map = (org.hibernate.mapping.Map) property.collection
        collectionSecondPassBinder.bindCollectionSecondPass(property)

        String type = property.getIndexColumnType('string')
        String columnName1 = property.getIndexColumnName(namingStrategy)
        BasicValue value = simpleValueColumnBinder.bindSimpleValue(
                metadataBuildingContext, map.collectionTable, type, columnName1, true)
        PropertyConfig mappedForm = property.hibernateMappedForm
        if (mappedForm.indexColumn != null) {
            Column column = simpleValueColumnFetcher.getColumnForSimpleValue(value)
            ColumnConfig columnConfig = getSingleColumnConfig(mappedForm.indexColumn)
            columnConfigToColumnBinder.bindColumnConfigToColumn(column, columnConfig, mappedForm)
        }

        if (!value.isTypeSpecified()) {
            throw new MappingException('map index element must specify a type: ' + map.role)
        }
        map.index = value

        if (property instanceof HibernateToManyCollectionProperty) {
            HibernateToManyCollectionProperty collectionProperty = (HibernateToManyCollectionProperty) property
            String typeName = collectionProperty.elementTypeName
            String columnName = collectionProperty.getMapElementName(namingStrategy)
            BasicValue elt = simpleValueColumnBinder.bindSimpleValue(
                    metadataBuildingContext, map.collectionTable, typeName, columnName, false)
            map.element = elt
        }

        map.inverse = false
    }

    ColumnConfig getSingleColumnConfig(PropertyConfig propertyConfig) {
        if (propertyConfig != null) {
            List<ColumnConfig> columns = propertyConfig.columns
            if (columns != null && !columns.isEmpty()) {
                return columns.get(0)
            }
        }
        return null
    }

}
