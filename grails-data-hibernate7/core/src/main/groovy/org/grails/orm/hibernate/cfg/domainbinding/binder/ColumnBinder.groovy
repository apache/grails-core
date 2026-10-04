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
import org.hibernate.dialect.Dialect
import org.hibernate.mapping.Column
import org.hibernate.mapping.Table
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import org.grails.orm.hibernate.cfg.ColumnConfig
import org.grails.orm.hibernate.cfg.Mapping
import org.grails.orm.hibernate.cfg.PersistentEntityNamingStrategy
import org.grails.orm.hibernate.cfg.PropertyConfig
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateAssociation
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernatePersistentProperty
import org.grails.orm.hibernate.cfg.domainbinding.util.BackticksRemover
import org.grails.orm.hibernate.cfg.domainbinding.util.ColumnNameForPropertyAndPathFetcher
import org.grails.orm.hibernate.cfg.domainbinding.util.CreateKeyForProps
import org.grails.orm.hibernate.cfg.domainbinding.util.DefaultColumnNameFetcher

/**
 * Binds a {@link org.hibernate.mapping.Column} to the Hibernate meta-model from Grails property and column config.
 *
 * @since 8.0
 */
@CompileStatic
@SuppressWarnings(['PMD.NullAssignment', 'PMD.DataflowAnomalyAnalysis'])
class ColumnBinder {

    private static final Logger LOG = LoggerFactory.getLogger(ColumnBinder)

    private final ColumnNameForPropertyAndPathFetcher columnNameForPropertyAndPathFetcher
    private final StringColumnConstraintsBinder stringColumnConstraintsBinder
    private final NumericColumnConstraintsBinder numericColumnConstraintsBinder
    private final CreateKeyForProps createKeyForProps
    private final IndexBinder indexBinder

    /** Public constructor that accepts all collaborators. */
    ColumnBinder(
            ColumnNameForPropertyAndPathFetcher columnNameForPropertyAndPathFetcher,
            StringColumnConstraintsBinder stringColumnConstraintsBinder,
            NumericColumnConstraintsBinder numericColumnConstraintsBinder,
            CreateKeyForProps createKeyForProps,
            IndexBinder indexBinder) {
        this.columnNameForPropertyAndPathFetcher = columnNameForPropertyAndPathFetcher
        this.stringColumnConstraintsBinder = stringColumnConstraintsBinder
        this.numericColumnConstraintsBinder = numericColumnConstraintsBinder
        this.createKeyForProps = createKeyForProps
        this.indexBinder = indexBinder
    }

    ColumnBinder(PersistentEntityNamingStrategy namingStrategy, Dialect dialect) {
        this(
                new ColumnNameForPropertyAndPathFetcher(
                        namingStrategy, new DefaultColumnNameFetcher(namingStrategy), new BackticksRemover()),
                new StringColumnConstraintsBinder(),
                new NumericColumnConstraintsBinder(dialect),
                new CreateKeyForProps(new ColumnNameForPropertyAndPathFetcher(
                        namingStrategy, new DefaultColumnNameFetcher(namingStrategy), new BackticksRemover())),
                new IndexBinder())
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
    void bindColumn(
            HibernatePersistentProperty property,
            HibernatePersistentProperty parentProperty,
            Column column,
            ColumnConfig cc,
            String path,
            Table table,
            String typeName) {

        if (cc != null) {
            column.comment = cc.comment
            column.defaultValue = cc.defaultValue
            column.customRead = cc.read
            column.customWrite = cc.write
        }

        Class<?> userType = property.userType
        String columnName = columnNameForPropertyAndPathFetcher.getColumnNameForPropertyAndPath(property, path, cc)
        if ((property instanceof HibernateAssociation) && userType == null) {
            HibernateAssociation assoc = (HibernateAssociation) property
            // Only use conventional naming when the column has not been explicitly mapped.
            if (column.name == null) {
                column.name = columnName
            }
            column.nullable = assoc.isAssociationColumnNullable()
        } else {
            column.name = columnName
            column.nullable = property.isNullable() || (parentProperty != null && parentProperty.isNullable())
            // Use the constraints for this property to more accurately define
            // the column's length, precision, and scale
            Class<?> type = property.type
            if (type != null && (String.isAssignableFrom(type) || byte[].class.isAssignableFrom(type))) {
                PropertyConfig mappedForm = property.hibernateMappedForm
                stringColumnConstraintsBinder.bindStringColumnConstraints(column, mappedForm, typeName)
            } else if (type != null && Number.isAssignableFrom(type)) {
                PropertyConfig mappedForm = property.hibernateMappedForm
                numericColumnConstraintsBinder.bindNumericColumnConstraints(column, cc, mappedForm, type)
            }
        }

        createKeyForProps.createKeyForProps(property, path, table, columnName)
        indexBinder.bindIndex(columnName, column, cc, table)

        def owner = property.hibernateOwner
        if (!owner.isRoot()) {
            Mapping mapping = owner.hibernateMappedForm
            if (mapping != null && mapping.getTablePerHierarchy()) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug('Sub class property [{}] for column name [{}] set to nullable', property.name, column.name)
                }
                column.nullable = true
            } else {
                column.nullable = property.isNullable()
            }
        }

        // Apply uniqueness last to ensure it isn't overridden by downstream binders
        PropertyConfig mappedFormFinal = property.hibernateMappedForm
        column.unique = mappedFormFinal.isUnique() && !mappedFormFinal.isUniqueWithinGroup()

        if (LOG.isDebugEnabled()) {
            LOG.debug('Bound property [{}] to column name [{}] in table [{}]', property.name, column.name, table.name)
        }
    }

}
