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

import org.hibernate.boot.model.relational.SqlStringGenerationContext;
import org.hibernate.boot.model.relational.internal.SqlStringGenerationContextImpl;
import org.hibernate.engine.jdbc.env.spi.JdbcEnvironment;
import org.hibernate.generator.GeneratorCreationContext;
import org.hibernate.id.enhanced.TableGenerator;

import org.grails.orm.hibernate.cfg.Identity;

public class GrailsTableGenerator extends TableGenerator {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final String DEFAULT_ENTITY_NAME = "default";

    public GrailsTableGenerator(GeneratorCreationContext context, Identity mappedId, JdbcEnvironment jdbcEnvironment) {
        Properties generatorProps =
                Optional.ofNullable(mappedId).map(Identity::getProperties).orElse(new Properties());

        if (!generatorProps.containsKey(SEGMENT_VALUE_PARAM)) {
            String propertyName = context.getProperty().getName();

            // Use the name we just ensured exists in BasicValueIdCreator
            String entityName = (mappedId != null && mappedId.getName() != null) ? mappedId.getName() : DEFAULT_ENTITY_NAME;

            generatorProps.put(SEGMENT_VALUE_PARAM, entityName + "." + propertyName);
        }

        // Standard Pooled-lo defaults
        if (!generatorProps.containsKey(INCREMENT_PARAM)) {
            generatorProps.put(INCREMENT_PARAM, "50");
        }
        if (!generatorProps.containsKey(OPT_PARAM)) {
            generatorProps.put(OPT_PARAM, "pooled-lo");
        }

        // Fixes the "SQL to format should not be null" error
        this.configure(context, generatorProps);
        var database = context.getDatabase();

        this.registerExportables(database);
        // Get the Name record from the physical name
        var physicalName = database.getDefaultNamespace().getPhysicalName();

        // Use the record component accessors (catalog() and schema())
        // instead of the deprecated getCatalog()/getSchema()
        String catalog =
                (physicalName.catalog() != null) ? physicalName.catalog().getCanonicalName() : null;

        String schema = (physicalName.schema() != null) ? physicalName.schema().getCanonicalName() : null;

        // Build the context and initialize templates
        SqlStringGenerationContext context1 =
                SqlStringGenerationContextImpl.fromExplicit(jdbcEnvironment, database, catalog, schema);
        this.initialize(context1);
    }
}
