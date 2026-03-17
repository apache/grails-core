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

    /** Convenience constructor for backward compatibility. */
    public ColumnBinder(PersistentEntityNamingStrategy namingStrategy) {
        this(
                new ColumnNameForPropertyAndPathFetcher(
                        namingStrategy, new DefaultColumnNameFetcher(namingStrategy), new BackticksRemover()),
                new StringColumnConstraintsBinder(),
                new NumericColumnConstraintsBinder(),
                new CreateKeyForProps(new ColumnNameForPropertyAndPathFetcher(
                        namingStrategy, new DefaultColumnNameFetcher(namingStrategy), new BackticksRemover())),
                new IndexBinder());
    }

    /**
     * Binds a Column instance to the Hibernate meta model
     *
     * @param property The Grails domain class property
     * @param parentProperty parent property
     * @param column The column to bind
     * @param path the path
     * @param table The table name
     */
    public void bindColumn(
            HibernatePersistentProperty property,
            HibernatePersistentProperty parentProperty,
            Column column,
            ColumnConfig cc,
            String path,
            Table table) {

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
            // Use the constraints for this property to more accurately define
            // the column's length, precision, and scale
            Class<?> type = property.getType();
            if (type != null && (String.class.isAssignableFrom(type) || byte[].class.isAssignableFrom(type))) {
                PropertyConfig mappedForm = property.getMappedForm();
                stringColumnConstraintsBinder.bindStringColumnConstraints(column, mappedForm);
            } else if (type != null && Number.class.isAssignableFrom(type)) {
                PropertyConfig mappedForm = property.getMappedForm();
                numericColumnConstraintsBinder.bindNumericColumnConstraints(column, cc, mappedForm);
            }
        }

        createKeyForProps.createKeyForProps(property, path, table, columnName);
        indexBinder.bindIndex(columnName, column, cc, table);

        var owner = property.getHibernateOwner();
        if (!owner.isRoot()) {
            Mapping mapping = owner.getMappedForm();
            if (mapping != null && mapping.getTablePerHierarchy()) {
                if (LOG.isDebugEnabled())
                    LOG.debug("[GrailsDomainBinder] Sub class property ["
                            + property.getName()
                            + "] for column name ["
                            + column.getName()
                            + "] set to nullable");
                column.setNullable(true);
            } else {
                column.setNullable(property.isNullable());
            }
        }

        // Apply uniqueness last to ensure it isn't overridden by downstream binders
        PropertyConfig mappedFormFinal = property.getMappedForm();
        column.setUnique(mappedFormFinal.isUnique() && !mappedFormFinal.isUniqueWithinGroup());

        if (LOG.isDebugEnabled())
            LOG.debug("[GrailsDomainBinder] bound property ["
                    + property.getName()
                    + "] to column name ["
                    + column.getName()
                    + "] in table ["
                    + table.getName()
                    + "]");
    }
}
