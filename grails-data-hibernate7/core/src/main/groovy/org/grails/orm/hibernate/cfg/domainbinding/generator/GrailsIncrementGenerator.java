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
package org.grails.orm.hibernate.cfg.domainbinding.generator;

import java.io.Serial;
import java.util.Properties;

import org.hibernate.boot.model.relational.SqlStringGenerationContext;
import org.hibernate.boot.model.relational.internal.SqlStringGenerationContextImpl;
import org.hibernate.engine.jdbc.env.spi.JdbcEnvironment;
import org.hibernate.generator.GeneratorCreationContext;
import org.hibernate.id.IncrementGenerator;

import org.grails.orm.hibernate.cfg.Identity;
import org.grails.orm.hibernate.cfg.PersistentEntityNamingStrategy;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity;

import static org.hibernate.id.PersistentIdentifierGenerator.CATALOG;
import static org.hibernate.id.PersistentIdentifierGenerator.SCHEMA;

/**
 * Grails-aware increment ID generator. Builds the standard {@link IncrementGenerator} parameters
 * from GORM mapping metadata and delegates entirely to the parent class — no reflection required.
 */
public class GrailsIncrementGenerator extends IncrementGenerator {

    @Serial
    private static final long serialVersionUID = 1L;

    public GrailsIncrementGenerator(
            GeneratorCreationContext context,
            Identity mappedId,
            GrailsHibernatePersistentEntity domainClass,
            PersistentEntityNamingStrategy namingStrategy) {

        Properties params = new Properties();
        if (mappedId != null && mappedId.getProperties() != null) {
            params.putAll(mappedId.getProperties());
        }

        // Use the entity's naming-strategy-aware table resolution:
        // handles explicit mapping, table-per-hierarchy root, and PhysicalNamingStrategy fallback.
        params.put(TABLES, domainClass.getTableName(namingStrategy));

        org.grails.orm.hibernate.cfg.Mapping mapping = domainClass.getMappedForm();
        if (mapping != null && mapping.getTable() != null) {
            if (mapping.getTable().getCatalog() != null)
                params.put(CATALOG, mapping.getTable().getCatalog());
            if (mapping.getTable().getSchema() != null)
                params.put(SCHEMA, mapping.getTable().getSchema());
        }

        // Resolve column name — fall back to "id" if the property path is dotted (composite)
        String columnName = context.getProperty().getName();
        if (columnName == null || columnName.contains(".")) {
            columnName = (mappedId != null
                            && mappedId.getName() != null
                            && !mappedId.getName().contains("."))
                    ? mappedId.getName()
                    : "id";
        }
        params.put(COLUMN, columnName);

        // Delegate to the standard configure() — sets returnClass, column, physicalTableNames
        configure(context, params);

        // Build SqlStringGenerationContext and initialize the SQL query
        JdbcEnvironment jdbcEnvironment = context.getDatabase().getJdbcEnvironment();
        var physicalName = context.getDatabase().getDefaultNamespace().getPhysicalName();
        String catalog = physicalName.catalog() != null ? physicalName.catalog().getCanonicalName() : null;
        String schema = physicalName.schema() != null ? physicalName.schema().getCanonicalName() : null;
        SqlStringGenerationContext sqlContext =
                SqlStringGenerationContextImpl.fromExplicit(jdbcEnvironment, context.getDatabase(), catalog, schema);
        initialize(sqlContext);
    }
}
