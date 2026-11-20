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

import org.hibernate.boot.spi.MetadataBuildingContext;
import org.hibernate.dialect.Dialect;
import org.hibernate.mapping.Column;
import org.hibernate.mapping.Table;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.grails.orm.hibernate.cfg.ColumnConfig;
import org.grails.orm.hibernate.cfg.Mapping;
import org.grails.orm.hibernate.cfg.PersistentEntityNamingStrategy;
import org.grails.orm.hibernate.cfg.PropertyConfig;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateAssociation;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernatePersistentProperty;
import org.grails.orm.hibernate.cfg.domainbinding.util.BackticksRemover;
import org.grails.orm.hibernate.cfg.domainbinding.util.ColumnNameForPropertyAndPathFetcher;
import org.grails.orm.hibernate.cfg.domainbinding.util.CreateKeyForProps;
import org.grails.orm.hibernate.cfg.domainbinding.util.DefaultColumnNameFetcher;

/**
 * Binds a {@link org.hibernate.mapping.Column} to the Hibernate meta-model from Grails property and column config.
 *
 * @since 8.0
 */
@SuppressWarnings({"PMD.NullAssignment", "PMD.DataflowAnomalyAnalysis"})
public class ColumnBinder {

    private static final Logger LOG = LoggerFactory.getLogger(ColumnBinder.class);

    private final ColumnNameForPropertyAndPathFetcher columnNameForPropertyAndPathFetcher;
    private final StringColumnConstraintsBinder stringColumnConstraintsBinder;
    private final NumericColumnConstraintsBinder numericColumnConstraintsBinder;
    private final CreateKeyForProps createKeyForProps;
    private final IndexBinder indexBinder;

    /** Public constructor that accepts all collaborators. */
    public ColumnBinder(
            ColumnNameForPropertyAndPathFetcher columnNameForPropertyAndPathFetcher,
            StringColumnConstraintsBinder stringColumnConstraintsBinder,
            NumericColumnConstraintsBinder numericColumnConstraintsBinder,
            CreateKeyForProps createKeyForProps,
            IndexBinder indexBinder) {
        this.columnNameForPropertyAndPathFetcher = columnNameForPropertyAndPathFetcher;
        this.stringColumnConstraintsBinder = stringColumnConstraintsBinder;
        this.numericColumnConstraintsBinder = numericColumnConstraintsBinder;
        this.createKeyForProps = createKeyForProps;
        this.indexBinder = indexBinder;
    }

    /**
     * Creates a binder whose uniqueness groups instantiate a composite user type through its default constructor,
     * as Hibernate does outside a CDI container, when a group names a property mapped with one.
     *
     * @param namingStrategy the naming strategy of the column names
     * @param dialect the dialect of the numeric defaults
     * @see #ColumnBinder(MetadataBuildingContext, PersistentEntityNamingStrategy, Dialect)
     */
    public ColumnBinder(PersistentEntityNamingStrategy namingStrategy, Dialect dialect) {
        this(null, namingStrategy, dialect);
    }

    /**
     * Creates a binder whose uniqueness groups instantiate a composite user type as the binder of its property
     * does when a group names a property mapped with one: as a managed bean when the building options allow
     * extensions in CDI, through its default constructor otherwise.
     *
     * @param metadataBuildingContext the context of the binding, or {@code null} to instantiate every composite
     * user type through its default constructor
     * @param namingStrategy the naming strategy of the column names
     * @param dialect the dialect of the numeric defaults
     * @since 8.0.1
     */
    public ColumnBinder(
            MetadataBuildingContext metadataBuildingContext,
            PersistentEntityNamingStrategy namingStrategy,
            Dialect dialect) {
        this(
                columnNameFetcher(metadataBuildingContext, namingStrategy),
                new StringColumnConstraintsBinder(),
                new NumericColumnConstraintsBinder(dialect),
                new CreateKeyForProps(columnNameFetcher(metadataBuildingContext, namingStrategy),
                        metadataBuildingContext == null ? null : metadataBuildingContext.getMetadataCollector()),
                new IndexBinder());
    }

    private static ColumnNameForPropertyAndPathFetcher columnNameFetcher(
            MetadataBuildingContext metadataBuildingContext, PersistentEntityNamingStrategy namingStrategy) {
        return new ColumnNameForPropertyAndPathFetcher(
                namingStrategy, new DefaultColumnNameFetcher(namingStrategy), new BackticksRemover(),
                metadataBuildingContext);
    }

    /**
     * Binds a Column instance to the Hibernate meta model
     *
     * @param property The Grails domain class property
     * @param parentProperty parent property
     * @param column The column to bind
     * @param path the path
     * @param table The table name
     * @param typeName the property's resolved Hibernate type name
     */
    public void bindColumn(
            HibernatePersistentProperty property,
            HibernatePersistentProperty parentProperty,
            Column column,
            ColumnConfig cc,
            String path,
            Table table,
            String typeName) {

        String columnName = bindColumnDefinition(property, parentProperty, column, cc, path, table, typeName);
        createKeyForProps.createKeyForProps(property, path, table, columnName);

        // Apply uniqueness last to ensure it isn't overridden by downstream binders
        PropertyConfig mappedFormFinal = property.getHibernateMappedForm();
        column.setUnique(mappedFormFinal.isUnique() && !mappedFormFinal.isUniqueWithinGroup());
    }

    /**
     * Binds one of the columns of a property that spans several columns, such as one mapped with a composite
     * user type, as {@link #bindColumn} does, except for what describes the whole value of the property rather
     * than the column. The column is not unique on its own and no unique key is created for it, since two
     * different values of the property can share the value of one column; the caller applies the uniqueness once,
     * across all the columns of the property, through {@link CreateKeyForProps#createKeyForColumns}. The
     * constraints of the property, such as its size, its bounds and its scale, are not applied to the column
     * either: it takes its length, precision and scale from its own configuration, and a numeric column without a
     * configured precision gets the default precision of the dialect, as the column of a numeric property without
     * constraints does.
     *
     * @param property The Grails domain class property
     * @param parentProperty parent property
     * @param column The column to bind
     * @param cc The configuration of the column
     * @param path the path
     * @param table The table name
     * @param typeName the property's resolved Hibernate type name
     * @param columnType the type of the value the column holds, such as the type of the attribute of the
     * embeddable class of a composite user type
     */
    public void bindColumnWithoutUniqueness(
            HibernatePersistentProperty property,
            HibernatePersistentProperty parentProperty,
            Column column,
            ColumnConfig cc,
            String path,
            Table table,
            String typeName,
            Class<?> columnType) {
        bindColumnDefinition(property, parentProperty, column, cc, path, table, typeName, columnType);
        column.setUnique(false);
    }

    /**
     * Binds everything but the uniqueness of the one column of a property, whose constraints apply to the column.
     *
     * @return the name of the column of the property
     */
    private String bindColumnDefinition(
            HibernatePersistentProperty property,
            HibernatePersistentProperty parentProperty,
            Column column,
            ColumnConfig cc,
            String path,
            Table table,
            String typeName) {
        return bindColumnDefinition(property, parentProperty, column, cc, path, table, typeName, null);
    }

    /**
     * Binds everything but the uniqueness of the column.
     *
     * @param columnType the type of the value the column holds when it is one of several columns of the property,
     * which is then defined from its own configuration; {@code null} for the one column of a property, which is
     * defined from the constraints of the property
     * @return the name of the column of the property
     */
    private String bindColumnDefinition(
            HibernatePersistentProperty property,
            HibernatePersistentProperty parentProperty,
            Column column,
            ColumnConfig cc,
            String path,
            Table table,
            String typeName,
            Class<?> columnType) {

        if (cc != null) {
            column.setComment(cc.getComment());
            column.setDefaultValue(cc.getDefaultValue());
            column.setCustomRead(cc.getRead());
            column.setCustomWrite(cc.getWrite());
        }

        Class<?> userType = property.getUserType();
        String columnName = columnNameForPropertyAndPathFetcher.getColumnNameForPropertyAndPath(property, path, cc);
        if ((property instanceof HibernateAssociation assoc) && userType == null) {
            // Only use conventional naming when the column has not been explicitly mapped.
            if (column.getName() == null) {
                column.setName(columnName);
            }
            column.setNullable(assoc.isAssociationColumnNullable());
        } else {
            column.setName(columnName);
            column.setNullable(property.isNullable() || (parentProperty != null && parentProperty.isNullable()));
            if (columnType != null) {
                bindAttributeColumnConstraints(column, cc, columnType);
            } else {
                bindPropertyColumnConstraints(property, column, cc, typeName);
            }
        }

        indexBinder.bindIndex(columnName, column, cc, table);

        var owner = property.getHibernateOwner();
        if (!owner.isRoot()) {
            Mapping mapping = owner.getHibernateMappedForm();
            if (mapping != null && mapping.getTablePerHierarchy()) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Sub class property [{}] for column name [{}] set to nullable", property.getName(), column.getName());
                }
                column.setNullable(true);
            } else {
                column.setNullable(property.isNullable());
            }
        }

        if (LOG.isDebugEnabled()) {
            LOG.debug("Bound property [{}] to column name [{}] in table [{}]", property.getName(), column.getName(), table.getName());
        }
        return columnName;
    }

    /**
     * Uses the constraints of the property to define the length, the precision and the scale of its one column
     * more accurately.
     */
    private void bindPropertyColumnConstraints(
            HibernatePersistentProperty property, Column column, ColumnConfig cc, String typeName) {
        Class<?> type = property.getType();
        if (type != null && (String.class.isAssignableFrom(type) || byte[].class.isAssignableFrom(type))) {
            PropertyConfig mappedForm = property.getHibernateMappedForm();
            stringColumnConstraintsBinder.bindStringColumnConstraints(column, mappedForm, typeName);
        } else if (type != null && Number.class.isAssignableFrom(type)) {
            PropertyConfig mappedForm = property.getHibernateMappedForm();
            numericColumnConstraintsBinder.bindNumericColumnConstraints(column, cc, mappedForm, type);
        }
    }

    /**
     * Defines one of several columns of a property from its own configuration alone: the constraints of the
     * property, such as its size, its bounds and its scale, describe the whole value, not the column. A numeric
     * column without a configured precision gets the default precision of the dialect, as the column of a numeric
     * property without constraints does.
     */
    private void bindAttributeColumnConstraints(Column column, ColumnConfig cc, Class<?> columnType) {
        if (Number.class.isAssignableFrom(columnType)) {
            numericColumnConstraintsBinder.bindNumericColumnConstraints(column, cc, null, columnType);
        }
    }
}
