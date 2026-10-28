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

import jakarta.persistence.GenerationType;

import org.hibernate.boot.model.relational.Database;
import org.hibernate.boot.model.relational.SqlStringGenerationContext;
import org.hibernate.boot.model.relational.internal.SqlStringGenerationContextImpl;
import org.hibernate.engine.jdbc.env.spi.JdbcEnvironment;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.generator.EventType;
import org.hibernate.generator.GeneratorCreationContext;
import org.hibernate.id.NativeGenerator;

/**
 * A native generator that supports Grails assigned identifiers and fixes Hibernate 7 ClassCastException.
 *
 * <p>Hibernate drives {@link NativeGenerator} through {@code initialize} (which picks the delegate for the
 * dialect), {@code configure} (which configures a sequence or table delegate), {@code registerExportables} (which
 * adds the delegate's sequence or table to the schema export) and {@code initialize(SqlStringGenerationContext)}.
 * Grails creates this generator itself instead of through Hibernate's generator binding, so it has to run every one
 * of those steps. Skipping the middle ones leaves the delegate of a sequence based dialect without its sequence.
 *
 * @since 8.0
 */
public class GrailsNativeGenerator extends NativeGenerator {

    @Serial
    private static final long serialVersionUID = 1L;

    /** Carries the default {@code @NativeGenerator} the way Hibernate reads it from an unannotated identifier. */
    @org.hibernate.annotations.NativeGenerator
    private static final class DefaultNativeGenerator {}

    private static final org.hibernate.annotations.NativeGenerator DEFAULT_ANNOTATION =
            DefaultNativeGenerator.class.getAnnotation(org.hibernate.annotations.NativeGenerator.class);

    public GrailsNativeGenerator(GeneratorCreationContext context, JdbcEnvironment jdbcEnvironment) {
        // Picks the delegate for the dialect; for IDENTITY it also calls setIdentity(true) on the column.
        this.initialize(DEFAULT_ANNOTATION, null, context);
        this.configure(context, new Properties());

        Database database = context.getDatabase();
        this.registerExportables(database);

        var physicalName = database.getDefaultNamespace().getPhysicalName();
        String catalog = (physicalName.catalog() != null) ? physicalName.catalog().getCanonicalName() : null;
        String schema = (physicalName.schema() != null) ? physicalName.schema().getCanonicalName() : null;
        SqlStringGenerationContext sqlContext =
                SqlStringGenerationContextImpl.fromExplicit(jdbcEnvironment, database, catalog, schema);
        this.initialize(sqlContext);
    }

    @Override
    public Object generate(
            SharedSessionContractImplementor session, Object entity, Object currentValue, EventType eventType) {
        // 1. Support Grails assigned identifiers
        if (currentValue != null) {
            return currentValue;
        }

        // 2. Fix the Hibernate 7 ClassCastException
        // NativeGenerator.generate() tries to cast the delegate to BeforeExecutionGenerator.
        // If the dialect chose IDENTITY, that cast fails. We bypass it by returning null.
        if (this.getGenerationType() == GenerationType.IDENTITY) {
            return null;
        }

        // 3. For Sequences/UUIDs, delegate to the standard logic
        return super.generate(session, entity, null, eventType);
    }
}
