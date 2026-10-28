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
package org.grails.orm.hibernate.cfg.domainbinding.util;

import java.util.Optional;

import org.hibernate.boot.spi.MetadataBuildingContext;
import org.hibernate.engine.jdbc.env.spi.JdbcEnvironment;
import org.hibernate.generator.Generator;
import org.hibernate.generator.GeneratorCreationContext;
import org.hibernate.mapping.BasicValue;

import org.grails.orm.hibernate.cfg.HibernateSimpleIdentity;
import org.grails.orm.hibernate.cfg.PersistentEntityNamingStrategy;
import org.grails.orm.hibernate.cfg.domainbinding.generator.GrailsSequenceWrapper;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernatePersistentProperty;

/** The basic value creator class. */
@SuppressWarnings("PMD.DataflowAnomalyAnalysis")
public class BasicValueCreator {

    private final MetadataBuildingContext metadataBuildingContext;
    private final JdbcEnvironment jdbcEnvironment;
    private final PersistentEntityNamingStrategy namingStrategy;
    private final GrailsSequenceWrapper grailsSequenceWrapper;

    /** Creates a new {@link BasicValueCreator} instance. */
    public BasicValueCreator(
            MetadataBuildingContext metadataBuildingContext,
            JdbcEnvironment jdbcEnvironment,
            PersistentEntityNamingStrategy namingStrategy) {
        this.metadataBuildingContext = metadataBuildingContext;
        this.jdbcEnvironment = jdbcEnvironment;
        this.namingStrategy = namingStrategy;
        this.grailsSequenceWrapper = new GrailsSequenceWrapper();
    }

    /** Creates a new {@link BasicValueCreator} instance. */
    protected BasicValueCreator(
            MetadataBuildingContext metadataBuildingContext,
            JdbcEnvironment jdbcEnvironment,
            PersistentEntityNamingStrategy namingStrategy,
            GrailsSequenceWrapper grailsSequenceWrapper) {
        this.metadataBuildingContext = metadataBuildingContext;
        this.jdbcEnvironment = jdbcEnvironment;
        this.namingStrategy = namingStrategy;
        this.grailsSequenceWrapper = grailsSequenceWrapper;
    }

    /** Creates and configures a {@link BasicValue} for the given persistent property. */
    public BasicValue bindBasicValue(HibernatePersistentProperty property) {
        BasicValue basicValue = new BasicValue(metadataBuildingContext, property.getTable());
        Optional.ofNullable(property.getGeneratorName()).ifPresent(generator ->
                basicValue.setCustomIdGeneratorCreator(context -> createGenerator(
                        property,
                        property.getHibernateOwner(),
                        new GeneratorCreationContextWrapper(context, basicValue),
                        generator)));
        return basicValue;
    }

    private Generator createGenerator(
            HibernatePersistentProperty property,
            GrailsHibernatePersistentEntity domainClass,
            GeneratorCreationContext context,
            String generatorName) {
        HibernateSimpleIdentity mappedId = domainClass.getHibernateIdentity() instanceof HibernateSimpleIdentity id ? id : null;
        if (mappedId == null) {
            mappedId = property.buildPropertyIdentity().orElse(null);
        }
        return grailsSequenceWrapper.getGenerator(
                generatorName, context, mappedId, domainClass, jdbcEnvironment, namingStrategy);
    }
}
