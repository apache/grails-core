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

import java.lang.reflect.Field

import groovy.transform.CompileStatic
import org.hibernate.boot.SessionFactoryBuilder
import org.hibernate.boot.spi.AdditionalMappingContributions
import org.hibernate.boot.spi.MetadataBuildingContext
import org.hibernate.boot.spi.MetadataImplementor
import org.hibernate.boot.spi.SessionFactoryBuilderFactory
import org.hibernate.boot.spi.SessionFactoryBuilderImplementor
import org.hibernate.engine.jdbc.env.spi.JdbcEnvironment
import org.hibernate.engine.spi.FilterDefinition
import org.hibernate.generator.GeneratorCreationContext
import org.hibernate.mapping.BasicValue
import org.hibernate.mapping.Collection
import org.hibernate.mapping.Column
import org.hibernate.mapping.Component
import org.hibernate.mapping.PersistentClass
import org.hibernate.mapping.Property
import org.hibernate.mapping.RootClass
import org.hibernate.mapping.Set as HibernateSet
import org.hibernate.mapping.Table
import org.hibernate.mapping.UniqueKey
import org.hibernate.mapping.Value
import org.springframework.beans.BeanUtils

import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.datastore.mapping.model.types.Embedded
import org.grails.orm.hibernate.cfg.HibernateSimpleIdentity
import org.grails.orm.hibernate.cfg.PersistentEntityNamingStrategy
import org.grails.orm.hibernate.cfg.domainbinding.binder.ColumnConfigToColumnBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.NumericColumnConstraintsBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.PropertyBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.StringColumnConstraintsBinder
import org.grails.orm.hibernate.cfg.domainbinding.generator.GrailsSequenceGeneratorEnum
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernatePersistentEntity
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernatePersistentProperty
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateBasicProperty
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateSimpleIdentityProperty
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateToManyEntityProperty
import org.grails.orm.hibernate.cfg.domainbinding.util.BackticksRemover
import org.grails.orm.hibernate.cfg.domainbinding.util.ColumnNameForPropertyAndPathFetcher
import org.grails.orm.hibernate.cfg.domainbinding.util.DefaultColumnNameFetcher
import org.grails.orm.hibernate.cfg.domainbinding.util.GeneratorCreationContextWrapper
import org.grails.orm.hibernate.cfg.domainbinding.util.MultiTenantFilterDefinitionBinder

/**
 * Lets Hibernate's own annotation binder bind the GORM domain classes, while the application's real instances stay what
 * Hibernate persists and loads.
 *
 * <p>The generated class of an entity has the name of the domain class, so Hibernate's entity name, which is the name of
 * the annotated class, is the real name: statistics, entity graphs, collection roles, second-level cache regions and
 * exception messages all use it, as they do when the domain binder binds the entity. The two classes live in different class
 * loaders. The binder works in two steps, one on each side of Hibernate's metadata build:</p>
 * <ol>
 *   <li>{@link #contribute}, called by the domain binder while Hibernate collects mappings, generates an annotated class
 *   for every entity ({@link GrailsDomainGenerator}) and hands the generated classes to Hibernate, which binds them as it
 *   binds any annotated entity; Hibernate's class loader service resolves a domain class name to the generated class
 *   meanwhile ({@link GeneratedDomainClassLoader}).</li>
 *   <li>{@link #getSessionFactoryBuilder}, called by Hibernate once the metadata is complete and before the session
 *   factory is built, switches the class loader service so that the same names resolve to the real classes
 *   ({@link GeneratedDomainClassLoaderService}) and points the bound entities at them: the mapped class and proxy interface
 *   become the domain class, the property accessors are the ones the domain binder would choose, the identifier gets GORM's
 *   generator, and embedded types become the real embedded classes. Hibernate registers a persister under the entity name
 *   and the class name of its mapped class, so {@code session.get(Book, id)}, HQL, criteria queries and the entity name of a
 *   real {@code Book} instance all find it; the generated class is never instantiated.</li>
 * </ol>
 *
 * @since 9.0
 */
@CompileStatic
class GeneratedDomainClassBinder implements SessionFactoryBuilderFactory {

    private final String dataSourceName
    private final GeneratedDomainClassLoader classLoader
    private final PropertyBinder propertyBinder = new PropertyBinder()
    private final Map<String, Generated> generatedByName = new HashMap<String, Generated>()
    private final MultiTenantFilterDefinitionBinder filterDefinitionBinder = new MultiTenantFilterDefinitionBinder()
    private PersistentEntityNamingStrategy namingStrategy
    private JdbcEnvironment jdbcEnvironment
    private GrailsDomainGenerator generator
    private GeneratedDomainClassLoaderService classLoaderService

    /**
     * @param dataSourceName the data source whose entities are generated
     * @param applicationClassLoader the loader the domain classes are loaded through; the generated classes are loaded in a
     *     child of it
     */
    GeneratedDomainClassBinder(String dataSourceName, ClassLoader applicationClassLoader) {
        this.dataSourceName = dataSourceName
        this.classLoader = new GeneratedDomainClassLoader(applicationClassLoader)
    }

    /**
     * @return the loader Hibernate's class loader service must use, so that it can load the generated classes by name
     */
    GeneratedDomainClassLoader getClassLoader() {
        return classLoader
    }

    /**
     * @param classLoaderService the class loader service of the registry the mappings are bound in; the binder switches it to
     *     the real classes once the mappings are bound
     */
    void setClassLoaderService(GeneratedDomainClassLoaderService classLoaderService) {
        this.classLoaderService = classLoaderService
    }

    /**
     * Generates the classes of every entity bound for the data source, whole hierarchies at a time, and contributes them
     * to Hibernate.
     */
    void contribute(
            AdditionalMappingContributions contributions,
            MetadataBuildingContext buildingContext,
            List<HibernatePersistentEntity> entities,
            PersistentEntityNamingStrategy namingStrategy,
            JdbcEnvironment jdbcEnvironment) {
        this.namingStrategy = namingStrategy
        this.jdbcEnvironment = jdbcEnvironment
        List<GrailsHibernatePersistentEntity> toGenerate = hierarchies(entities)
        if (toGenerate.isEmpty()) {
            return
        }
        // GORM's dirty checking reads the 'derived' flag the domain binder sets on the mapping of a formula property
        for (GrailsHibernatePersistentEntity entity : toGenerate) {
            entity.configureDerivedProperties()
        }
        BackticksRemover backticksRemover = new BackticksRemover()
        generator = new GrailsDomainGenerator(
                namingStrategy,
                new ColumnNameForPropertyAndPathFetcher(
                        namingStrategy, new DefaultColumnNameFetcher(namingStrategy, backticksRemover), backticksRemover),
                new ColumnConfigToColumnBinder(),
                new StringColumnConstraintsBinder(),
                new NumericColumnConstraintsBinder(jdbcEnvironment.dialect),
                buildingContext.bootstrapContext.typeConfiguration)
        Map<GrailsHibernatePersistentEntity, Class<?>> generated = generator.generateAll(toGenerate, classLoader.parent)
        classLoader.register(generated.values().first().classLoader)
        for (Map.Entry<GrailsHibernatePersistentEntity, Class<?>> entry : generated.entrySet()) {
            generatedByName.put(entry.value.name, new Generated(entry.key, entry.value))
            contributions.contributeEntity(entry.value)
        }
    }

    /**
     * Called by Hibernate when the session factory is about to be built: points the bound entities at the real classes. The
     * binder never takes over the building of the session factory, so it returns no builder.
     */
    @Override
    SessionFactoryBuilder getSessionFactoryBuilder(MetadataImplementor metadata, SessionFactoryBuilderImplementor defaultBuilder) {
        // the mappings are bound: from here on the domain class names resolve to the real classes
        classLoaderService?.useRealClasses()
        for (PersistentClass persistentClass : new ArrayList<PersistentClass>(metadata.entityBindings)) {
            Generated generated = generatedByName.get(persistentClass.className)
            if (generated != null) {
                align(persistentClass, generated, metadata)
            }
        }
        defineTenantFilter(metadata)
        return null
    }

    /**
     * Hibernate's contributed-class path binds the entity hierarchies only and ignores the global annotations on them, so the
     * {@code @FilterDef} the generator writes is never read: the filter definition is registered here, as the domain binder
     * registers it, from the type of the bound tenant id.
     */
    private void defineTenantFilter(MetadataImplementor metadata) {
        for (Generated generated : generatedByName.values()) {
            GrailsHibernatePersistentEntity entity = generated.entity()
            TenantFacets tenant = generator.tenantFacets(entity)
            if (tenant != null && !metadata.filterDefinitions.containsKey(tenant.filterName())) {
                Property tenantId = entity.persistentClass.getRecursiveProperty(entity.hibernateTenantId.name)
                filterDefinitionBinder.create(tenant.filterName(), tenantId).ifPresent { FilterDefinition definition ->
                    metadata.filterDefinitions.put(tenant.filterName(), definition)
                }
            }
        }
    }

    private List<GrailsHibernatePersistentEntity> hierarchies(List<HibernatePersistentEntity> entities) {
        List<GrailsHibernatePersistentEntity> result = []
        Deque<GrailsHibernatePersistentEntity> pending = new ArrayDeque<GrailsHibernatePersistentEntity>()
        for (HibernatePersistentEntity entity : entities) {
            if (entity.forGrailsDomainMapping(dataSourceName)) {
                pending.add(entity)
            }
        }
        while (!pending.isEmpty()) {
            GrailsHibernatePersistentEntity entity = pending.poll()
            if (!result.contains(entity)) {
                result.add(entity)
                pending.addAll(entity.childEntities)
            }
        }
        return result
    }

    private void align(PersistentClass persistentClass, Generated generated, MetadataImplementor metadata) {
        GrailsHibernatePersistentEntity entity = generated.entity()
        Class<?> real = entity.javaClass
        persistentClass.className = real.name
        if (persistentClass.proxyInterfaceName != null) {
            persistentClass.proxyInterfaceName = real.name
        }
        entity.persistentClass = persistentClass
        if (persistentClass instanceof RootClass) {
            // the generated root forces its discriminator so that Hibernate adds no check constraint; the binder never forces it
            ((RootClass) persistentClass).forceDiscriminator = false
            alignIdentifier((RootClass) persistentClass, generated, metadata)
        }
        for (Property property : persistentClass.declaredProperties) {
            alignProperty(property, entity, real)
        }
        alignUniqueKeys(persistentClass, entity)
    }

    /**
     * Hibernate's annotation binder names the unique keys of a table and marks them as stated by the user: the schema then has
     * them as named constraints, and the key an {@code orderingUniqueKey} gives the primary key is named too. The domain binder's keys
     * are neither, and are created unnamed with the database's own name. The natural id's key takes its columns in the order the
     * mapping names the properties, as the binder's does, where Hibernate's follow the order of the fields.
     */
    private void alignUniqueKeys(PersistentClass persistentClass, GrailsHibernatePersistentEntity entity) {
        for (UniqueKey key : persistentClass.table.uniqueKeys.values()) {
            key.nameExplicit = false
            key.explicit = false
        }
        NaturalIdFacets naturalId = persistentClass instanceof RootClass ? generator.naturalIdFacets(entity) : null
        if (naturalId != null) {
            List<String> columns = naturalId.propertyNames().collectMany { String name ->
                persistentClass.getProperty(name).columns*.name
            }
            for (UniqueKey key : persistentClass.table.uniqueKeys.values()) {
                if (key.columns*.name.toSet() == columns.toSet()) {
                    List<Column> ordered = key.columns.sort(false) { Column column -> columns.indexOf(column.name) }
                    key.columns.clear()
                    key.columns.addAll(ordered)
                }
            }
        }
    }

    private void alignIdentifier(RootClass root, Generated generated, MetadataImplementor metadata) {
        GrailsHibernatePersistentEntity entity = generated.entity()
        if (!(entity.identity instanceof HibernateSimpleIdentityProperty)) {
            alignCompositeIdentifier(root, entity, metadata)
            return
        }
        alignProperty(root.identifierProperty, entity, entity.javaClass)
        String name = entity.identity.name
        Field field = generated.generatedClass().getDeclaredField(name)
        GrailsIdGenerator marker = field.getAnnotation(GrailsIdGenerator)
        if (marker != null) {
            installGenerator((BasicValue) root.identifier, entity, marker.strategy())
        }
    }

    /**
     * The generated entity names an {@code @IdClass}, so Hibernate bound a non-aggregated identifier: the entity has no
     * identifier property, the identifier component is embedded, and an {@code _identifierMapper} component maps the same parts
     * from the entity. GORM's identifier for such an entity is the entity instance itself, so both components are re-pointed at the
     * real entity class, and read its parts the way the domain binder would.
     *
     * <p>Hibernate's JPA metamodel registers an embeddable for the class of the identifier component and an entity for the
     * class of the entity, and when both are the same class the one whose Java type descriptor was resolved first wins the
     * class. The entity descriptor is therefore resolved here, before the metamodel is built.</p>
     */
    private void alignCompositeIdentifier(RootClass root, GrailsHibernatePersistentEntity entity, MetadataImplementor metadata) {
        Class<?> real = entity.javaClass
        metadata.typeConfiguration.javaTypeRegistry.resolveEntityTypeDescriptor(real)
        Component identifier = (Component) root.identifier
        alignIdentifierComponent(identifier, entity, real)
        installPartGenerators(identifier, entity)
        if (root.identifierMapper != null) {
            alignIdentifierComponent(root.identifierMapper, entity, real)
        }
    }

    /**
     * The domain binder gives the value of every property that maps a {@code generator} a generator creator, and Hibernate
     * consults the creators of the parts of a non-aggregated identifier when it builds the identifier generator (it generates the
     * parts that are not assigned). The same creator is set on the identifier's part, with the generator and the parameters the
     * mapping names; the {@code @IdClass} Hibernate bound has none, as it has no annotation for one.
     */
    private void installPartGenerators(Component identifier, GrailsHibernatePersistentEntity entity) {
        for (Property property : identifier.getProperties()) {
            HibernatePersistentProperty part = entity.compositeIdentity.find { HibernatePersistentProperty candidate -> candidate.name == property.name }
            String strategy = part?.generatorName
            if (strategy != null && property.value instanceof BasicValue) {
                BasicValue value = (BasicValue) property.value
                value.setCustomIdGeneratorCreator({ GeneratorCreationContext context ->
                    HibernateSimpleIdentity mappedId = entity.hibernateIdentity instanceof HibernateSimpleIdentity ?
                            (HibernateSimpleIdentity) entity.hibernateIdentity : part.buildPropertyIdentity().orElse(null)
                    return GrailsSequenceGeneratorEnum.getGenerator(
                            GrailsSequenceGeneratorEnum.fromName(strategy).orElse(GrailsSequenceGeneratorEnum.NATIVE),
                            new GeneratorCreationContextWrapper(context, value),
                            mappedId,
                            entity,
                            jdbcEnvironment,
                            namingStrategy)
                })
            }
        }
    }

    private void alignIdentifierComponent(Component component, GrailsHibernatePersistentEntity entity, Class<?> real) {
        component.componentClassName = real.name
        for (Property property : component.getProperties()) {
            HibernatePersistentProperty part = entity.compositeIdentity.find { HibernatePersistentProperty candidate -> candidate.name == property.name }
            if (part != null) {
                property.propertyAccessorName = propertyBinder.accessorName(part)
            }
        }
    }

    private void installGenerator(BasicValue identifier, GrailsHibernatePersistentEntity entity, String strategy) {
        identifier.setCustomIdGeneratorCreator({ GeneratorCreationContext context ->
            HibernateSimpleIdentity mappedId = entity.hibernateIdentity instanceof HibernateSimpleIdentity ?
                    (HibernateSimpleIdentity) entity.hibernateIdentity :
                    ((HibernateSimpleIdentityProperty) entity.identity).buildPropertyIdentity().orElse(null)
            return GrailsSequenceGeneratorEnum.getGenerator(
                    GrailsSequenceGeneratorEnum.fromName(strategy).orElse(GrailsSequenceGeneratorEnum.NATIVE),
                    new GeneratorCreationContextWrapper(context, identifier),
                    mappedId,
                    entity,
                    jdbcEnvironment,
                    namingStrategy)
        })
    }

    private void alignProperty(Property property, GrailsHibernatePersistentEntity owner, Class<?> ownerClass) {
        PersistentProperty<?> persistentProperty = owner.identity?.name == property.name ?
                owner.identity : owner.getPropertyByName(property.name)
        if (persistentProperty instanceof HibernatePersistentProperty) {
            property.propertyAccessorName = propertyBinder.accessorName((HibernatePersistentProperty) persistentProperty)
        }
        if (property.value instanceof Collection && persistentProperty instanceof HibernatePersistentProperty) {
            alignExtraLazy((Collection) property.value, (HibernatePersistentProperty) persistentProperty)
            alignCollectionTable((Collection) property.value, (HibernatePersistentProperty) persistentProperty)
        }
        if (property.value instanceof Component && persistentProperty instanceof Embedded) {
            PersistentEntity embedded = ((Embedded<?>) persistentProperty).associatedEntity
            if (embedded instanceof GrailsHibernatePersistentEntity) {
                alignComponent(
                        (Component) property.value,
                        (GrailsHibernatePersistentEntity) embedded,
                        BeanUtils.findPropertyType(property.name, ownerClass))
            }
        }
    }

    /**
     * An explicit {@code lazy: true} on a collection makes the domain binder bind an extra-lazy collection ({@code size()},
     * {@code contains()} and {@code isEmpty()} do not initialize it). Hibernate 7's annotation binder always binds an ordinary
     * lazy collection and has no annotation for the extra-lazy kind, so the flag is set on the bound collection.
     */
    private void alignExtraLazy(Collection collection, HibernatePersistentProperty property) {
        boolean extraLazy
        if (property instanceof HibernateToManyEntityProperty) {
            extraLazy = generator.toManyFacets((HibernateToManyEntityProperty) property).extraLazy()
        } else if (property instanceof HibernateBasicProperty) {
            extraLazy = generator.collectionFacets((HibernateBasicProperty) property).extraLazy()
        } else {
            return
        }
        if (extraLazy) {
            collection.extraLazy = true
        }
    }

    /**
     * Hibernate's annotation binder forces the key and element columns of a collection table not null ("I break the spec, but it's
     * for good"), where the domain binder leaves them nullable unless they are part of the primary key, and a set whose element
     * column is nullable gets a unique key over its columns instead of a primary key. The nullability the generator decided
     * (the binder's) is restored on the columns, and for a set the key Hibernate derived from them is derived again.
     */
    private void alignCollectionTable(Collection collection, HibernatePersistentProperty property) {
        if (collection.inverse || collection.oneToMany) {
            return
        }
        List<ColumnFacets> keys
        ColumnFacets element
        if (property instanceof HibernateToManyEntityProperty) {
            ToManyFacets facets = generator.toManyFacets((HibernateToManyEntityProperty) property)
            keys = facets.keys()
            element = facets.element()
        } else if (property instanceof HibernateBasicProperty) {
            CollectionFacets facets = generator.collectionFacets((HibernateBasicProperty) property)
            keys = facets.keys()
            element = facets.element()
        } else {
            return
        }
        Table table = collection.collectionTable
        boolean rederive = collection instanceof HibernateSet && table.primaryKey != null
        if (rederive) {
            table.primaryKey = null
        }
        restoreNullability(collection.key, keys)
        restoreNullability(collection.element, [element])
        if (rederive) {
            collection.createAllKeys()
        }
    }

    private static void restoreNullability(Value value, List<ColumnFacets> facets) {
        List<Column> columns = value.selectables.findAll { it instanceof Column }.collect { (Column) it }
        for (ColumnFacets facet : facets) {
            Column column = columns.size() == 1 ? columns[0] : columns.find { Column c -> c.name == facet.name().replace('`', '') }
            if (column != null) {
                column.nullable = facet.nullable()
            }
        }
    }

    private void alignComponent(Component component, GrailsHibernatePersistentEntity embedded, Class<?> type) {
        component.componentClassName = type.name
        for (Property property : component.getProperties()) {
            alignProperty(property, embedded, type)
        }
    }

    private static record Generated(GrailsHibernatePersistentEntity entity, Class<?> generatedClass) { }

}
