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
package org.grails.orm.hibernate.cfg.domainbinding.generator

import groovy.transform.CompileStatic
import org.hibernate.boot.model.relational.SqlStringGenerationContext
import org.hibernate.boot.model.relational.internal.SqlStringGenerationContextImpl
import org.hibernate.engine.jdbc.env.spi.JdbcEnvironment
import org.hibernate.generator.GeneratorCreationContext
import org.hibernate.id.PersistentIdentifierGenerator
import org.hibernate.id.enhanced.SequenceStyleGenerator

import org.grails.orm.hibernate.cfg.HibernateSimpleIdentity

@CompileStatic
@SuppressWarnings('PMD.ConstructorCallsOverridableMethod')
class GrailsSequenceStyleGenerator extends SequenceStyleGenerator {

    private static final long serialVersionUID = 1L

    GrailsSequenceStyleGenerator(
            GeneratorCreationContext context, HibernateSimpleIdentity mappedId, JdbcEnvironment jdbcEnvironment) {
        Properties generatorProps = mappedId?.properties ?: new Properties()

        generatorProps.putIfAbsent(INCREMENT_PARAM, '50')
        generatorProps.putIfAbsent(OPT_PARAM, 'pooled-lo')
        // Without an explicit 'sequence' mapping param, Hibernate's own naming strategy falls back to
        // deriving an implicit sequence name from the target table - but only if it is told what that
        // table is. Hibernate's standard generator-creation path supplies this itself; the Grails-specific
        // path that constructs this generator directly does not, so without it every implicit sequence
        // mapping fails session factory bootstrap with "Unable to determine implicit sequence name for
        // target table 'null'". Only resolved when actually needed, since getRootClass() is not always
        // available (e.g. a component/embedded identifier's generator).
        if (!hasExplicitSequenceName(generatorProps) && context.rootClass != null) {
            generatorProps.putIfAbsent(PersistentIdentifierGenerator.TABLE, context.rootClass.table.name)
        }

        this.configure(context, generatorProps)

        if (jdbcEnvironment != null) {
            def database = context.database
            if (databaseStructure != null) {
                this.registerExportables(database)
            }

            def physicalName = database.defaultNamespace.physicalName

            String catalog = physicalName.catalog() != null ? physicalName.catalog().canonicalName : null
            String schema = physicalName.schema() != null ? physicalName.schema().canonicalName : null

            if (databaseStructure != null) {
                SqlStringGenerationContext sqlContext =
                        SqlStringGenerationContextImpl.fromExplicit(jdbcEnvironment, database, catalog, schema)
                this.initialize(sqlContext)
            }
        }
    }

    /**
     * Whether the mapping named the sequence itself, resolved the way {@link SequenceStyleGenerator} resolves
     * it: {@code sequence_name} when present, otherwise {@code sequence}, and a blank name counts as no name
     * at all. Anything the generator would treat as unnamed still needs the target table to derive one.
     */
    private static boolean hasExplicitSequenceName(Properties generatorProps) {
        Object sequenceName = generatorProps.get(SEQUENCE_PARAM)
        if (sequenceName == null) {
            sequenceName = generatorProps.get(ALT_SEQUENCE_PARAM)
        }
        return sequenceName != null && !sequenceName.toString().isEmpty()
    }

}
