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
import org.hibernate.mapping.Formula;
import org.hibernate.mapping.RootClass;
import org.hibernate.mapping.SimpleValue;

import org.grails.orm.hibernate.cfg.ColumnConfig;
import org.grails.orm.hibernate.cfg.DiscriminatorConfig;

import static org.grails.orm.hibernate.cfg.domainbinding.binder.GrailsDomainBinder.JPA_DEFAULT_DISCRIMINATOR_TYPE;

public class ConfiguredDiscriminatorBinder {

    private static final String STRING_TYPE = "string";

    private final SimpleValueColumnBinder simpleValueColumnBinder;
    private final ColumnConfigToColumnBinder columnConfigToColumnBinder;

    public ConfiguredDiscriminatorBinder(
            SimpleValueColumnBinder simpleValueColumnBinder, ColumnConfigToColumnBinder columnConfigToColumnBinder) {
        this.simpleValueColumnBinder = simpleValueColumnBinder;
        this.columnConfigToColumnBinder = columnConfigToColumnBinder;
    }

    /**
     * Binds a discriminator with explicit configuration
     *
     * @param entity The root class entity
     * @param discriminator The discriminator value to configure
     * @param config The discriminator configuration
     */
    public void bindConfiguredDiscriminator(RootClass entity, SimpleValue discriminator, DiscriminatorConfig config) {
        // Set discriminator value
        entity.setDiscriminatorValue(config.getValue());

        // Configure insertable if specified
        if (config.getInsertable() != null) {
            entity.setDiscriminatorInsertable(config.getInsertable());
        }

        // Resolve type name
        String typeName = resolveTypeName(config.getType());

        // Bind based on configuration type
        if (config.getFormula() != null) {
            bindDiscriminatorWithFormula(discriminator, typeName, config.getFormula());
        } else {
            bindDiscriminatorWithColumn(discriminator, typeName, config.getColumn());
        }
    }

    private String resolveTypeName(Object type) {
        if (type == null) {
            return STRING_TYPE;
        }

        return (type instanceof Class) ? ((Class<?>) type).getName() : type.toString();
    }

    private void bindDiscriminatorWithFormula(SimpleValue discriminator, String typeName, String formula) {
        discriminator.setTypeName(typeName);
        Formula f = new Formula();
        f.setFormula(formula);
        discriminator.addFormula(f);
    }

    private void bindDiscriminatorWithColumn(SimpleValue discriminator, String typeName, ColumnConfig columnConfig) {
        simpleValueColumnBinder.bindSimpleValue(discriminator, typeName, JPA_DEFAULT_DISCRIMINATOR_TYPE, false);

        if (columnConfig != null) {
            configureDiscriminatorColumn(discriminator, columnConfig);
        }
    }

    private void configureDiscriminatorColumn(SimpleValue discriminator, ColumnConfig columnConfig) {
        Column column = discriminator.getColumns().iterator().next();

        if (columnConfig.getName() != null) {
            column.setName(columnConfig.getName());
        }

        columnConfigToColumnBinder.bindColumnConfigToColumn(column, columnConfig, null);
    }
}
