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

import java.util.Optional;

import org.hibernate.boot.spi.MetadataBuildingContext;
import org.hibernate.engine.jdbc.env.spi.JdbcEnvironment;
import org.hibernate.mapping.BasicValue;
import org.hibernate.mapping.Column;
import org.hibernate.mapping.DependantValue;
import org.hibernate.mapping.Formula;
import org.hibernate.mapping.SimpleValue;
import org.hibernate.mapping.Table;

import org.grails.datastore.mapping.model.types.TenantId;
import org.grails.orm.hibernate.cfg.ColumnConfig;
import org.grails.orm.hibernate.cfg.PersistentEntityNamingStrategy;
import org.grails.orm.hibernate.cfg.PropertyConfig;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernatePersistentProperty;
import org.grails.orm.hibernate.cfg.domainbinding.util.BasicValueCreator;

@SuppressWarnings("PMD.NullAssignment")
public class SimpleValueBinder {

    private final MetadataBuildingContext metadataBuildingContext;
    private final ColumnConfigToColumnBinder columnConfigToColumnBinder;
    private final ColumnBinder columnBinder;
    private final BasicValueCreator basicValueCreator;

    /** Private constructor that accepts all collaborators. */
    private SimpleValueBinder(
            MetadataBuildingContext metadataBuildingContext,
            ColumnConfigToColumnBinder columnConfigToColumnBinder,
            ColumnBinder columnBinder,
            BasicValueCreator basicValueCreator) {
        this.metadataBuildingContext = metadataBuildingContext;
        this.columnConfigToColumnBinder = columnConfigToColumnBinder;
        this.columnBinder = columnBinder;
        this.basicValueCreator = basicValueCreator;
    }

    /** Convenience constructor for namingStrategy. */
    public SimpleValueBinder(
            MetadataBuildingContext metadataBuildingContext,
            PersistentEntityNamingStrategy namingStrategy,
            JdbcEnvironment jdbcEnvironment) {
        this(
                metadataBuildingContext,
                new ColumnConfigToColumnBinder(),
                new ColumnBinder(namingStrategy),
                new BasicValueCreator(metadataBuildingContext, jdbcEnvironment, namingStrategy));
    }

    public BasicValue bindSimpleValue(
            @jakarta.annotation.Nonnull HibernatePersistentProperty property,
            HibernatePersistentProperty parentProperty,
            Table table,
            String path) {
        BasicValue basicValue = basicValueCreator.bindBasicValue(table, property);
        bindSimpleValue(property, parentProperty, basicValue, path);
        return basicValue;
    }

    public SimpleValue bindSimpleValue(
            @jakarta.annotation.Nonnull HibernatePersistentProperty property,
            HibernatePersistentProperty parentProperty,
            SimpleValue simpleValue,
            String path) {

        PropertyConfig propertyConfig = property.getHibernateMappedForm();
        simpleValue.setTypeName(property.getTypeName(simpleValue));
        simpleValue.setTypeParameters(property.getTypeParameters(simpleValue));

        if (propertyConfig.isDerived() && !(property instanceof TenantId)) {
            Formula formula = new Formula();
            formula.setFormula(propertyConfig.getFormula());
            simpleValue.addFormula(formula);
        } else {
            Table table = simpleValue.getTable();

            Optional.ofNullable(propertyConfig.getColumns())
                    .filter(list -> !list.isEmpty())
                    .orElse(java.util.Arrays.asList(new ColumnConfig[] {null}))
                    .forEach(cc -> {
                        Column column = new Column();
                        columnConfigToColumnBinder.bindColumnConfigToColumn(column, cc, propertyConfig);
                        columnBinder.bindColumn(property, parentProperty, column, cc, path, table);
                        if (simpleValue instanceof DependantValue) {
                            column.setNullable(true);
                        }
                        if (table != null) {
                            table.addColumn(column);
                        }
                        simpleValue.addColumn(column);
                    });
        }
        return simpleValue;
    }
}
