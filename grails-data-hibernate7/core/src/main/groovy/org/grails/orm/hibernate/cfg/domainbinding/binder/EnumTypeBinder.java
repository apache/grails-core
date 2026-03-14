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

import java.util.Properties;

import jakarta.annotation.Nonnull;
import jakarta.persistence.EnumType;

import org.hibernate.boot.spi.MetadataBuildingContext;
import org.hibernate.mapping.BasicValue;
import org.hibernate.mapping.Column;
import org.hibernate.mapping.Table;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.grails.orm.hibernate.cfg.ColumnConfig;
import org.grails.orm.hibernate.cfg.IdentityEnumType;
import org.grails.orm.hibernate.cfg.PropertyConfig;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernatePersistentProperty;
import org.grails.orm.hibernate.cfg.domainbinding.util.ColumnNameForPropertyAndPathFetcher;
import org.grails.orm.hibernate.cfg.domainbinding.util.GrailsEnumType;

import static org.grails.orm.hibernate.cfg.domainbinding.binder.GrailsDomainBinder.ENUM_CLASS_PROP;

public class EnumTypeBinder {

    private final MetadataBuildingContext metadataBuildingContext;
    private final ColumnNameForPropertyAndPathFetcher columnNameForPropertyAndPathFetcher;
    private final IndexBinder indexBinder;
    private final ColumnConfigToColumnBinder columnConfigToColumnBinder;

    public EnumTypeBinder(
            MetadataBuildingContext metadataBuildingContext,
            ColumnNameForPropertyAndPathFetcher columnNameForPropertyAndPathFetcher) {
        this(
                metadataBuildingContext,
                columnNameForPropertyAndPathFetcher,
                new IndexBinder(),
                new ColumnConfigToColumnBinder());
    }

    protected EnumTypeBinder(
            MetadataBuildingContext metadataBuildingContext,
            ColumnNameForPropertyAndPathFetcher columnNameForPropertyAndPathFetcher,
            IndexBinder indexBinder,
            ColumnConfigToColumnBinder columnConfigToColumnBinder) {
        this.metadataBuildingContext = metadataBuildingContext;
        this.columnNameForPropertyAndPathFetcher = columnNameForPropertyAndPathFetcher;
        this.indexBinder = indexBinder;
        this.columnConfigToColumnBinder = columnConfigToColumnBinder;
    }

    private static final Logger LOG = LoggerFactory.getLogger(EnumTypeBinder.class);

    public BasicValue bindEnumType(
            @Nonnull HibernatePersistentProperty property, Class<?> propertyType, Table table, String path) {
        String columnName = columnNameForPropertyAndPathFetcher.getColumnNameForPropertyAndPath(property, path, null);
        return bindEnumTypeForColumn(property, propertyType, table, columnName);
    }

    public BasicValue bindEnumTypeForColumn(
            @Nonnull HibernatePersistentProperty property, Class<?> propertyType, Table table, @Nonnull String columnName) {
        BasicValue simpleValue = new BasicValue(metadataBuildingContext, table);
        bindEnumType(property, propertyType, simpleValue, columnName);
        return simpleValue;
    }

    protected void bindEnumType(
            HibernatePersistentProperty property, Class<?> propertyType, BasicValue simpleValue, String columnName) {
        PropertyConfig pc = property.getMappedForm();
        Properties enumProperties = new Properties();
        enumProperties.put(ENUM_CLASS_PROP, propertyType.getName());
        String typeName = property.getTypeName(propertyType);
        if (typeName != null) {
            simpleValue.setTypeName(typeName);
        } else {
            switch (GrailsEnumType.fromString(pc.getEnumType())) {
                case DEFAULT, STRING -> {
                    // Hibernate 7 native string enum mapping: store by Enum.name() as VARCHAR.
                    simpleValue.setImplicitJavaTypeAccess(tc -> propertyType);
                    simpleValue.setEnumerationStyle(EnumType.STRING);
                }
                case ORDINAL -> {
                    // Hibernate 7 native ordinal enum mapping: store by Enum.ordinal() as INTEGER.
                    simpleValue.setImplicitJavaTypeAccess(tc -> propertyType);
                    simpleValue.setEnumerationStyle(EnumType.ORDINAL);
                }
                case IDENTITY -> simpleValue.setTypeName(IdentityEnumType.class.getName());
            }
        }
        simpleValue.setTypeParameters(enumProperties);

        Column column = new Column();
        boolean isTablePerHierarchySubclass = property.getHibernateOwner().isTablePerHierarchySubclass();
        if (isTablePerHierarchySubclass) {
            // Properties on subclasses in a table-per-hierarchy strategy must be nullable.
            if (LOG.isDebugEnabled()) {
                LOG.debug(
                        "[GrailsDomainBinder] Sub class property [{}] for column name [{}] forced to nullable",
                        property.getName(),
                        columnName);
            }
            column.setNullable(true);
        } else {
            column.setNullable(property.isNullable());
        }

        column.setValue(simpleValue);
        column.setName(columnName);
        Table t = simpleValue.getTable();
        t.addColumn(column);
        simpleValue.addColumn(column);

        if (!pc.getColumns().isEmpty()) {
            ColumnConfig columnConfig = pc.getColumns().get(0);
            indexBinder.bindIndex(columnName, column, columnConfig, t);
            columnConfigToColumnBinder.bindColumnConfigToColumn(column, columnConfig, pc);
        }
    }
}
