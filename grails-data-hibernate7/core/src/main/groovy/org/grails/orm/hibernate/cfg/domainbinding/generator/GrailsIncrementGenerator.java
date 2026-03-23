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
import java.util.Optional;
import java.util.Properties;

import org.hibernate.boot.model.naming.Identifier;
import org.hibernate.boot.model.relational.SqlStringGenerationContext;
import org.hibernate.boot.model.relational.internal.SqlStringGenerationContextImpl;
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

        configure(context, buildParams(context, mappedId, domainClass, namingStrategy));
        initialize(buildSqlContext(context));
    }

    private Properties buildParams(
            GeneratorCreationContext context,
            Identity mappedId,
            GrailsHibernatePersistentEntity domainClass,
            PersistentEntityNamingStrategy namingStrategy) {

        Properties params = new Properties();
        Optional.ofNullable(mappedId).map(Identity::getProperties).ifPresent(params::putAll);

        params.put(TABLES, domainClass.getTableName(namingStrategy));
        params.put(COLUMN, resolveColumnName(context, mappedId));

        Optional.ofNullable(domainClass.getHibernateMappedForm())
                .map(org.grails.orm.hibernate.cfg.Mapping::getTable)
                .ifPresent(table -> {
                    if (table.getCatalog() != null) params.put(CATALOG, table.getCatalog());
                    if (table.getSchema() != null) params.put(SCHEMA, table.getSchema());
                });

        return params;
    }

    private String resolveColumnName(GeneratorCreationContext context, Identity mappedId) {
        String propertyName = context.getProperty().getName();
        if (propertyName != null && !propertyName.contains(".")) {
            return propertyName;
        }
        return Optional.ofNullable(mappedId)
                .map(Identity::getName)
                .filter(name -> !name.contains("."))
                .orElse("id");
    }

    private SqlStringGenerationContext buildSqlContext(GeneratorCreationContext context) {
        var database = context.getDatabase();
        var physicalName = database.getDefaultNamespace().getPhysicalName();

        return SqlStringGenerationContextImpl.fromExplicit(
                database.getJdbcEnvironment(),
                database,
                Optional.ofNullable(physicalName.catalog())
                        .map(Identifier::getCanonicalName)
                        .orElse(null),
                Optional.ofNullable(physicalName.schema())
                        .map(Identifier::getCanonicalName)
                        .orElse(null));
    }
}
