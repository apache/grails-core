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

import org.hibernate.MappingException;
import org.hibernate.boot.spi.MetadataBuildingContext;
import org.hibernate.engine.jdbc.env.spi.JdbcEnvironment;
import org.hibernate.generator.Generator;
import org.hibernate.generator.GeneratorCreationContext;
import org.hibernate.mapping.BasicValue;
import org.hibernate.mapping.Table;

import org.grails.datastore.mapping.model.DefaultPropertyMapping;
import org.grails.datastore.mapping.model.config.GormProperties;
import org.grails.orm.hibernate.cfg.Identity;
import org.grails.orm.hibernate.cfg.PersistentEntityNamingStrategy;
import org.grails.orm.hibernate.cfg.PropertyConfig;
import org.grails.orm.hibernate.cfg.domainbinding.generator.GrailsSequenceWrapper;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateIdentityProperty;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernatePersistentProperty;

/** The basic value id creator class. */
@SuppressWarnings("PMD.DataflowAnomalyAnalysis")
public class BasicValueIdCreator {

    private final MetadataBuildingContext metadataBuildingContext;
    private final JdbcEnvironment jdbcEnvironment;
    private final PersistentEntityNamingStrategy namingStrategy;
    private final GrailsSequenceWrapper grailsSequenceWrapper;

    /** Creates a new {@link BasicValueIdCreator} instance. */
    public BasicValueIdCreator(
            MetadataBuildingContext metadataBuildingContext,
            JdbcEnvironment jdbcEnvironment,
            PersistentEntityNamingStrategy namingStrategy) {
        this.metadataBuildingContext = metadataBuildingContext;
        this.jdbcEnvironment = jdbcEnvironment;
        this.namingStrategy = namingStrategy;
        this.grailsSequenceWrapper = new GrailsSequenceWrapper();
    }

    /** Creates a new {@link BasicValueIdCreator} instance. */
    protected BasicValueIdCreator(
            MetadataBuildingContext metadataBuildingContext,
            JdbcEnvironment jdbcEnvironment,
            PersistentEntityNamingStrategy namingStrategy,
            GrailsSequenceWrapper grailsSequenceWrapper) {
        this.metadataBuildingContext = metadataBuildingContext;
        this.jdbcEnvironment = jdbcEnvironment;
        this.namingStrategy = namingStrategy;
        this.grailsSequenceWrapper = grailsSequenceWrapper;
    }

    /** Gets the basic value id. */
    public BasicValue bindBasicValue(
            Table table, Identity mappedId, GrailsHibernatePersistentEntity domainClass) {
        org.grails.orm.hibernate.cfg.Mapping result = domainClass.getHibernateMappedForm();
        boolean useSequence = result != null && result.isTablePerConcreteClass();
        BasicValue basicValue = new BasicValue(metadataBuildingContext, table);
        // create a BasicValue for the specific entity table (do not reuse the prototype directly
        // because table differs)
        String generatorName = Identity.determineGeneratorName(mappedId, useSequence);
        basicValue.setCustomIdGeneratorCreator(context -> createGenerator(
                mappedId,
                domainClass,
                context.getValue() == null ? new GeneratorCreationContextWrapper(context, basicValue) : context,
                generatorName));
        return basicValue;
    }

    public HibernatePersistentProperty resolveIdentifierProperty(
            GrailsHibernatePersistentEntity domainClass, Identity mappedId) {
        var identifier = domainClass.getIdentity();
        if (identifier == null) {
            var syntheticId = new HibernateIdentityProperty(
                    domainClass, domainClass.getMappingContext(), GormProperties.IDENTITY, Long.class);
            syntheticId.setMapping(new DefaultPropertyMapping<>(domainClass.getMapping(), new PropertyConfig()));
            identifier = syntheticId;
        }
        if (mappedId != null) {
            String propertyName = mappedId.getName();
            if (propertyName != null && !propertyName.equals(domainClass.getName())) {
                var namedIdentityProp = domainClass.getHibernatePropertyByName(propertyName);
                if (namedIdentityProp == null) {
                    throw new MappingException(
                            "Mapping specifies an identifier property name that doesn't exist [" + propertyName + "]");
                }
                if (!namedIdentityProp.equals(identifier)) {
                    identifier = namedIdentityProp;
                }
            }
        }
        return identifier;
    }

    private Generator createGenerator(
            Identity mappedId,
            GrailsHibernatePersistentEntity domainClass,
            GeneratorCreationContext context,
            String generatorName) {
        return grailsSequenceWrapper.getGenerator(
                generatorName, context, mappedId, domainClass, jdbcEnvironment, namingStrategy);
    }
}
