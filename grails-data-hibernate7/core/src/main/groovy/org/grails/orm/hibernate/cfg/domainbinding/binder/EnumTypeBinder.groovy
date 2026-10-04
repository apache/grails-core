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
package org.grails.orm.hibernate.cfg.domainbinding.binder

import groovy.transform.CompileStatic
import jakarta.annotation.Nonnull
import org.hibernate.boot.spi.MetadataBuildingContext
import org.hibernate.mapping.BasicValue
import org.hibernate.mapping.Column
import org.hibernate.mapping.Table
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import org.grails.orm.hibernate.cfg.ColumnConfig
import org.grails.orm.hibernate.cfg.PersistentEntityNamingStrategy
import org.grails.orm.hibernate.cfg.PropertyConfig
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateEnumProperty
import org.grails.orm.hibernate.cfg.domainbinding.util.ColumnNameForPropertyAndPathFetcher
import org.grails.orm.hibernate.cfg.domainbinding.util.GrailsEnumType

import static org.grails.orm.hibernate.cfg.domainbinding.binder.GrailsDomainBinder.ENUM_CLASS_PROP

@CompileStatic
class EnumTypeBinder {

    private static final Logger LOG = LoggerFactory.getLogger(EnumTypeBinder)

    private final MetadataBuildingContext metadataBuildingContext
    private final ColumnNameForPropertyAndPathFetcher columnNameForPropertyAndPathFetcher
    private final IndexBinder indexBinder
    private final ColumnConfigToColumnBinder columnConfigToColumnBinder
    private final PersistentEntityNamingStrategy namingStrategy

    EnumTypeBinder(
            MetadataBuildingContext metadataBuildingContext,
            ColumnNameForPropertyAndPathFetcher columnNameForPropertyAndPathFetcher,
            PersistentEntityNamingStrategy namingStrategy) {
        this(
                metadataBuildingContext,
                columnNameForPropertyAndPathFetcher,
                new IndexBinder(),
                new ColumnConfigToColumnBinder(),
                namingStrategy)
    }

    protected EnumTypeBinder(
            MetadataBuildingContext metadataBuildingContext,
            ColumnNameForPropertyAndPathFetcher columnNameForPropertyAndPathFetcher,
            IndexBinder indexBinder,
            ColumnConfigToColumnBinder columnConfigToColumnBinder,
            PersistentEntityNamingStrategy namingStrategy) {
        this.metadataBuildingContext = metadataBuildingContext
        this.columnNameForPropertyAndPathFetcher = columnNameForPropertyAndPathFetcher
        this.indexBinder = indexBinder
        this.columnConfigToColumnBinder = columnConfigToColumnBinder
        this.namingStrategy = namingStrategy
    }

    BasicValue bindEnumType(@Nonnull HibernateEnumProperty property, String path) {
        String columnName = property.resolveEnumColumnName(namingStrategy, columnNameForPropertyAndPathFetcher, path)
        BasicValue simpleValue = new BasicValue(metadataBuildingContext, property.table)
        Class<?> propertyType = property.enumType
        PropertyConfig pc = property.hibernateMappedForm
        String typeName = property.getTypeName(propertyType)
        if (typeName != null) {
            simpleValue.typeName = typeName
        } else {
            GrailsEnumType.fromString(pc.enumType).configure(simpleValue, propertyType)
        }
        Properties enumProperties = new Properties()
        enumProperties.put(ENUM_CLASS_PROP, propertyType.name)
        simpleValue.setTypeParameters(enumProperties)

        Column column = new Column()
        if (property.hibernateOwner.isTablePerHierarchySubclass() && LOG.isDebugEnabled()) {
            LOG.debug('[GrailsDomainBinder] Sub class property [{}] for column name [{}] forced to nullable',
                    property.name, columnName)
        }
        column.nullable = property.isEnumColumnNullable()
        column.value = simpleValue
        column.name = columnName
        Table t = simpleValue.table
        t.addColumn(column)
        simpleValue.addColumn(column)

        if (!pc.columns.isEmpty()) {
            ColumnConfig columnConfig = pc.columns.get(0)
            indexBinder.bindIndex(columnName, column, columnConfig, t)
            columnConfigToColumnBinder.bindColumnConfigToColumn(column, columnConfig, pc)
        }
        return simpleValue
    }

}
