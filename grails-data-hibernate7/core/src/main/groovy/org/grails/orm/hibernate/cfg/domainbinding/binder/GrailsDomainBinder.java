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

import org.grails.datastore.mapping.core.connections.ConnectionSource;
import org.hibernate.boot.ResourceStreamLocator;
import org.hibernate.boot.internal.MetadataBuildingContextRootImpl;
import org.hibernate.boot.model.TypeContributions;
import org.hibernate.boot.model.TypeContributor;
import org.hibernate.boot.spi.AdditionalMappingContributions;
import org.hibernate.boot.spi.AdditionalMappingContributor;
import org.hibernate.boot.spi.InFlightMetadataCollector;
import org.hibernate.boot.spi.MetadataBuildingContext;
import org.hibernate.engine.jdbc.env.spi.JdbcEnvironment;
import org.hibernate.mapping.BasicValue;
import org.hibernate.service.ServiceRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.grails.orm.hibernate.cfg.HibernateMappingContext;
import org.grails.orm.hibernate.cfg.MappingCacheHolder;
import org.grails.orm.hibernate.cfg.PersistentEntityNamingStrategy;
import org.grails.orm.hibernate.cfg.domainbinding.collectionType.CollectionHolder;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernatePersistentEntity;
import org.grails.orm.hibernate.cfg.domainbinding.util.BackticksRemover;
import org.grails.orm.hibernate.cfg.domainbinding.util.BasicValueIdCreator;
import org.grails.orm.hibernate.cfg.domainbinding.util.ColumnNameForPropertyAndPathFetcher;
import org.grails.orm.hibernate.cfg.domainbinding.util.DefaultColumnNameFetcher;
import org.grails.orm.hibernate.cfg.domainbinding.util.GrailsPropertyResolver;
import org.grails.orm.hibernate.cfg.domainbinding.util.MultiTenantFilterBinder;
import org.grails.orm.hibernate.cfg.domainbinding.util.MultiTenantFilterDefinitionBinder;
import org.grails.orm.hibernate.cfg.domainbinding.util.NamingStrategyProvider;
import org.grails.orm.hibernate.cfg.domainbinding.util.NamingStrategyWrapper;
import org.grails.orm.hibernate.cfg.domainbinding.util.PropertyFromValueCreator;
import org.grails.orm.hibernate.cfg.domainbinding.util.SimpleValueColumnFetcher;

/**
 * Handles the binding Grails domain classes and properties to the Hibernate runtime meta model.
 * Based on the HbmBinder code in Hibernate core and influenced by AnnotationsBinder.
 *
 * @author Graeme Rocher
 * @since 0.1
 */
public class GrailsDomainBinder implements AdditionalMappingContributor, TypeContributor {

    public static final String FOREIGN_KEY_SUFFIX = "_id";
    public static final String EMPTY_PATH = "";
    public static final char UNDERSCORE = '_';

    public static final String ENUM_CLASS_PROP = "enumClass";
    public static final Logger LOG = LoggerFactory.getLogger(GrailsDomainBinder.class);

    public static final String JPA_DEFAULT_DISCRIMINATOR_TYPE = "DTYPE";

    private final String sessionFactoryName;
    private final String dataSourceName;
    private final HibernateMappingContext hibernateMappingContext;
    private final NamingStrategyProvider namingStrategyProvider;
    private PersistentEntityNamingStrategy namingStrategy;
    private MetadataBuildingContext metadataBuildingContext;
    private final MappingCacheHolder mappingCacheHolder;

    public JdbcEnvironment getJdbcEnvironment() {
        return metadataBuildingContext.getMetadataCollector().getDatabase().getJdbcEnvironment();
    }

    public GrailsDomainBinder(
            String dataSourceName, String sessionFactoryName, HibernateMappingContext hibernateMappingContext) {
        this(
                dataSourceName,
                sessionFactoryName,
                hibernateMappingContext,
                new NamingStrategyProvider(),
                new MappingCacheHolder());
    }

    public GrailsDomainBinder(
            String dataSourceName,
            String sessionFactoryName,
            HibernateMappingContext hibernateMappingContext,
            NamingStrategyProvider namingStrategyProvider,
            MappingCacheHolder mappingCacheHolder) {
        this.sessionFactoryName = sessionFactoryName;
        this.dataSourceName = dataSourceName;
        this.hibernateMappingContext = hibernateMappingContext;
        this.namingStrategyProvider = namingStrategyProvider;
        this.mappingCacheHolder = mappingCacheHolder;

        // pre-build mappings
        for (HibernatePersistentEntity persistentEntity :
                hibernateMappingContext.getHibernatePersistentEntities(dataSourceName)) {
            mappingCacheHolder.cacheMapping(persistentEntity);
        }
    }

    @Override
    @SuppressWarnings("PMD.DataflowAnomalyAnalysis")
    public void contribute(
            AdditionalMappingContributions contributions,
            InFlightMetadataCollector metadataCollector,
            ResourceStreamLocator resourceStreamLocator,
            MetadataBuildingContext buildingContext) {
        this.metadataBuildingContext = new MetadataBuildingContextRootImpl(
                ConnectionSource.DEFAULT,
                metadataCollector.getBootstrapContext(),
                metadataCollector.getMetadataBuildingOptions(),
                metadataCollector,
                null);
        CollectionHolder collectionHolder = new CollectionHolder(metadataBuildingContext);
        BackticksRemover backticksRemover = new BackticksRemover();
        PersistentEntityNamingStrategy namingStrategy = getNamingStrategy();
        JdbcEnvironment jdbcEnvironment = getJdbcEnvironment();
        DefaultColumnNameFetcher defaultColumnNameFetcher =
                new DefaultColumnNameFetcher(namingStrategy, backticksRemover);
        ColumnNameForPropertyAndPathFetcher columnNameForPropertyAndPathFetcher =
                new ColumnNameForPropertyAndPathFetcher(namingStrategy, defaultColumnNameFetcher, backticksRemover);
        SimpleValueBinder simpleValueBinder =
                new SimpleValueBinder(metadataBuildingContext, namingStrategy, jdbcEnvironment);
        EnumTypeBinder enumTypeBinder =
                new EnumTypeBinder(metadataBuildingContext, columnNameForPropertyAndPathFetcher, namingStrategy);
        PropertyFromValueCreator propertyFromValueCreator = new PropertyFromValueCreator();
        ClassBinder classBinder = new ClassBinder(metadataCollector);
        SimpleValueColumnFetcher simpleValueColumnFetcher = new SimpleValueColumnFetcher();
        CompositeIdentifierToManyToOneBinder compositeIdentifierToManyToOneBinder =
                new CompositeIdentifierToManyToOneBinder(
                        new org.grails.orm.hibernate.cfg.domainbinding.util.ForeignKeyColumnCountCalculator(),
                        namingStrategy,
                        defaultColumnNameFetcher,
                        backticksRemover,
                        simpleValueBinder);
        OneToOneBinder oneToOneBinder = new OneToOneBinder(metadataBuildingContext, simpleValueBinder);
        ManyToOneBinder manyToOneBinder = new ManyToOneBinder(
                metadataBuildingContext,
                namingStrategy,
                simpleValueBinder,
                new ManyToOneValuesBinder(),
                compositeIdentifierToManyToOneBinder);
        ForeignKeyOneToOneBinder foreignKeyOneToOneBinder =
                new ForeignKeyOneToOneBinder(manyToOneBinder, simpleValueColumnFetcher);

        CollectionBinder collectionBinder = new CollectionBinder(
                metadataBuildingContext,
                namingStrategy,
                simpleValueBinder,
                enumTypeBinder,
                manyToOneBinder,
                compositeIdentifierToManyToOneBinder,
                simpleValueColumnFetcher,
                collectionHolder,
                metadataCollector);
        ComponentUpdater componentUpdater = new ComponentUpdater(propertyFromValueCreator);
        ComponentBinder componentBinder =
                new ComponentBinder(metadataBuildingContext, getMappingCacheHolder(), componentUpdater);

        GrailsPropertyBinder grailsPropertyBinder = new GrailsPropertyBinder(
                enumTypeBinder,
                componentBinder,
                collectionBinder,
                simpleValueBinder,
                oneToOneBinder,
                manyToOneBinder,
                foreignKeyOneToOneBinder);
        componentBinder.setGrailsPropertyBinder(grailsPropertyBinder);
        CompositeIdBinder compositeIdBinder =
                new CompositeIdBinder(metadataBuildingContext, componentUpdater, grailsPropertyBinder);
        PropertyBinder propertyBinder = new PropertyBinder();
        SimpleIdBinder simpleIdBinder = new SimpleIdBinder(
                metadataBuildingContext,
                new BasicValueIdCreator(jdbcEnvironment, namingStrategy),
                simpleValueBinder,
                propertyBinder);
        IdentityBinder identityBinder = new IdentityBinder(simpleIdBinder, compositeIdBinder);
        VersionBinder versionBinder =
                new VersionBinder(metadataBuildingContext, simpleValueBinder, propertyBinder, BasicValue::new);
        NaturalIdentifierBinder naturalIdentifierBinder = new NaturalIdentifierBinder();
        ClassPropertiesBinder classPropertiesBinder =
                new ClassPropertiesBinder(grailsPropertyBinder, propertyFromValueCreator, naturalIdentifierBinder);
        MultiTenantFilterBinder multiTenantFilterBinder = new MultiTenantFilterBinder(
                new GrailsPropertyResolver(),
                new MultiTenantFilterDefinitionBinder(),
                metadataCollector,
                defaultColumnNameFetcher);
        JoinedSubClassBinder joinedSubClassBinder = new JoinedSubClassBinder(
                metadataBuildingContext,
                namingStrategy,
                new SimpleValueColumnBinder(),
                columnNameForPropertyAndPathFetcher,
                classBinder,
                metadataCollector);
        UnionSubclassBinder unionSubclassBinder =
                new UnionSubclassBinder(metadataBuildingContext, namingStrategy, classBinder, metadataCollector);
        SingleTableSubclassBinder singleTableSubclassBinder =
                new SingleTableSubclassBinder(classBinder, metadataBuildingContext);

        SubclassMappingBinder subclassMappingBinder = new SubclassMappingBinder(
                joinedSubClassBinder, unionSubclassBinder, singleTableSubclassBinder, classPropertiesBinder);
        SubClassBinder subClassBinder =
                new SubClassBinder( subclassMappingBinder, multiTenantFilterBinder, dataSourceName);
        RootPersistentClassCommonValuesBinder rootPersistentClassCommonValuesBinder =
                new RootPersistentClassCommonValuesBinder(
                        metadataBuildingContext,
                        getNamingStrategy(),
                        identityBinder,
                        versionBinder,
                        classBinder,
                        classPropertiesBinder,
                        metadataCollector);
        DiscriminatorPropertyBinder discriminatorPropertyBinder = new DiscriminatorPropertyBinder(
                metadataBuildingContext,
                mappingCacheHolder,
                new ConfiguredDiscriminatorBinder(new SimpleValueColumnBinder(), new ColumnConfigToColumnBinder()),
                new DefaultDiscriminatorBinder(new SimpleValueColumnBinder()));
        RootBinder rootBinder = new RootBinder(
                dataSourceName,
                multiTenantFilterBinder,
                subClassBinder,
                rootPersistentClassCommonValuesBinder,
                discriminatorPropertyBinder,
                metadataCollector,
                mappingCacheHolder);

        hibernateMappingContext.getHibernatePersistentEntities(dataSourceName).stream()
                .filter(persistentEntity -> persistentEntity.forGrailsDomainMapping(dataSourceName))
                .forEach(rootBinder::bindRoot);
    }

    /**
     * Override the default naming strategy given a Class or a full class name, or an instance of a
     * PhysicalNamingStrategy.
     *
     * @param datasourceName the datasource name
     * @param strategy the class, name, or instance
     * @throws ClassNotFoundException When the class was not found for specified strategy
     * @throws InstantiationException When an error occurred instantiating the strategy
     * @throws IllegalAccessException When an error occurred instantiating the strategy
     */
    public void configureNamingStrategy(final String datasourceName, final Object strategy)
            throws ClassNotFoundException, InstantiationException, IllegalAccessException {
        namingStrategyProvider.configureNamingStrategy(datasourceName, strategy);
    }

    public PersistentEntityNamingStrategy getNamingStrategy() {
        if (namingStrategy == null) {
            namingStrategy = new NamingStrategyWrapper(
                    namingStrategyProvider.getPhysicalNamingStrategy(sessionFactoryName), getJdbcEnvironment());
        }
        return namingStrategy;
    }

    public MetadataBuildingContext getMetadataBuildingContext() {
        return metadataBuildingContext;
    }

    public MappingCacheHolder getMappingCacheHolder() {
        return mappingCacheHolder;
    }

    @Override
    public String getContributorName() {
        return "GORM";
    }

    @Override
    public void contribute(TypeContributions typeContributions, ServiceRegistry serviceRegistry) {}

    /**
     * Manually triggers the contribution process. Useful for unit testing
     * where the full Hibernate bootstrap is not invoked.
     */
    public void contribute(InFlightMetadataCollector metadataCollector, HibernateMappingContext mappingContext) {
        contribute(null, metadataCollector, null, getMetadataBuildingContext());
    }
}
