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
import org.hibernate.id.enhanced.SequenceStyleGenerator;

import org.grails.orm.hibernate.cfg.Identity;

@SuppressWarnings("PMD.ConstructorCallsOverridableMethod")
public class GrailsSequenceStyleGenerator extends SequenceStyleGenerator {

    @Serial
    private static final long serialVersionUID = 1L;

    public GrailsSequenceStyleGenerator(
            GeneratorCreationContext context, Identity mappedId, JdbcEnvironment jdbcEnvironment) {
        Properties generatorProps =
                Optional.ofNullable(mappedId).map(Identity::getProperties).orElse(new Properties());

        generatorProps.putIfAbsent(INCREMENT_PARAM, "50");
        generatorProps.putIfAbsent(OPT_PARAM, "pooled-lo");

        super.configure(context, generatorProps);

        if (jdbcEnvironment != null) {
            var database = context.getDatabase();
            if (getDatabaseStructure() != null) {
                this.registerExportables(database);
            }

            var physicalName = database.getDefaultNamespace().getPhysicalName();

            String catalog =
                    (physicalName.catalog() != null) ? physicalName.catalog().getCanonicalName() : null;

            String schema =
                    (physicalName.schema() != null) ? physicalName.schema().getCanonicalName() : null;

            if (getDatabaseStructure() != null) {
                SqlStringGenerationContext sqlContext =
                        SqlStringGenerationContextImpl.fromExplicit(jdbcEnvironment, database, catalog, schema);
                this.initialize(sqlContext);
            }
        }
    }

    @Override
    public void initialize(SqlStringGenerationContext context) {
        super.initialize(context);
    }
}
