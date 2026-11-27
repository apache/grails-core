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
package org.grails.orm.hibernate.cfg.domainbinding.jpa

import groovy.transform.CompileStatic
import org.hibernate.boot.ResourceStreamLocator
import org.hibernate.boot.spi.AdditionalMappingContributions
import org.hibernate.boot.spi.AdditionalMappingContributor
import org.hibernate.boot.spi.InFlightMetadataCollector
import org.hibernate.boot.spi.MetadataBuildingContext
import org.hibernate.engine.jdbc.env.spi.JdbcEnvironment

import org.grails.orm.hibernate.cfg.MappingCacheHolder
import org.grails.orm.hibernate.cfg.PersistentEntityNamingStrategy
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernatePersistentEntity
import org.grails.orm.hibernate.cfg.domainbinding.util.NamingStrategyProvider
import org.grails.orm.hibernate.cfg.domainbinding.util.NamingStrategyWrapper

/**
 * The {@link AdditionalMappingContributor} through which GORM hands its domain classes to Hibernate: when Hibernate asks for
 * the additional mappings, it resolves the naming strategy of the session factory against the JDBC environment and lets
 * {@link GeneratedDomainClassBinder} generate the classes of the entities and bind them. It also caches the mapping of every
 * entity it is given when it is created, which is what the rest of the datastore reads.
 *
 * @since 9.0
 */
@CompileStatic
class GormMappingContributor implements AdditionalMappingContributor {

    static final String CONTRIBUTOR_NAME = 'GORM'

    private final String sessionFactoryName
    private final List<HibernatePersistentEntity> persistentEntities
    private final NamingStrategyProvider namingStrategyProvider
    private final GeneratedDomainClassBinder binder
    private PersistentEntityNamingStrategy namingStrategy
    private MetadataBuildingContext metadataBuildingContext

    /**
     * @param sessionFactoryName the name the physical naming strategy of the session factory is registered under
     * @param persistentEntities the entities to bind; each must already carry the name of the data source
     * @param mappingCacheHolder the holder the mapping of every entity is cached in
     * @param binder the binder that generates the classes of the entities and binds them
     */
    GormMappingContributor(
            String sessionFactoryName,
            List<HibernatePersistentEntity> persistentEntities,
            NamingStrategyProvider namingStrategyProvider,
            MappingCacheHolder mappingCacheHolder,
            GeneratedDomainClassBinder binder) {
        this.sessionFactoryName = sessionFactoryName
        this.persistentEntities = persistentEntities
        this.namingStrategyProvider = namingStrategyProvider
        this.binder = binder
        for (HibernatePersistentEntity persistentEntity : persistentEntities) {
            mappingCacheHolder.cacheMapping(persistentEntity)
        }
    }

    @Override
    String getContributorName() {
        return CONTRIBUTOR_NAME
    }

    @Override
    void contribute(
            AdditionalMappingContributions contributions,
            InFlightMetadataCollector metadataCollector,
            ResourceStreamLocator resourceStreamLocator,
            MetadataBuildingContext buildingContext) {
        this.metadataBuildingContext = buildingContext
        binder.contribute(contributions, buildingContext, persistentEntities, getNamingStrategy(), getJdbcEnvironment())
    }

    /**
     * The naming strategy of the session factory, wrapped for the JDBC environment of the last bootstrap; available once
     * Hibernate has asked for the contributions.
     */
    PersistentEntityNamingStrategy getNamingStrategy() {
        if (namingStrategy == null) {
            namingStrategy = new NamingStrategyWrapper(
                    namingStrategyProvider.getPhysicalNamingStrategy(sessionFactoryName), getJdbcEnvironment())
        }
        return namingStrategy
    }

    /**
     * The building context of the last bootstrap, {@code null} before Hibernate has asked for the contributions.
     */
    MetadataBuildingContext getMetadataBuildingContext() {
        return metadataBuildingContext
    }

    JdbcEnvironment getJdbcEnvironment() {
        return metadataBuildingContext.metadataCollector.database.jdbcEnvironment
    }

}
