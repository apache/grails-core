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
import org.hibernate.Length
import org.hibernate.MappingException
import org.hibernate.boot.SessionFactoryBuilder
import org.hibernate.boot.internal.InFlightMetadataCollectorImpl
import org.hibernate.boot.internal.MetadataBuildingContextRootImpl
import org.hibernate.boot.model.relational.Database
import org.hibernate.boot.model.relational.SqlStringGenerationContext
import org.hibernate.boot.model.relational.internal.SqlStringGenerationContextImpl
import org.hibernate.boot.model.source.internal.annotations.AnnotationMetadataSourceProcessorImpl
import org.hibernate.boot.spi.AdditionalMappingContributions
import org.hibernate.boot.spi.MetadataBuildingContext
import org.hibernate.boot.spi.MetadataImplementor
import org.hibernate.boot.spi.SessionFactoryBuilderFactory
import org.hibernate.boot.spi.SessionFactoryBuilderImplementor
import org.hibernate.cfg.MappingSettings
import org.hibernate.engine.config.spi.ConfigurationService
import org.hibernate.engine.config.spi.StandardConverters
import org.hibernate.engine.jdbc.env.spi.JdbcEnvironment
import org.hibernate.engine.spi.FilterDefinition
import org.hibernate.generator.GeneratorCreationContext
import org.hibernate.mapping.BasicValue
import org.hibernate.mapping.Collection
import org.hibernate.mapping.Column
import org.hibernate.mapping.Component
import org.hibernate.mapping.ForeignKey
import org.hibernate.mapping.GeneratorSettings
import org.hibernate.mapping.IndexedCollection
import org.hibernate.mapping.ManyToOne
import org.hibernate.mapping.PersistentClass
import org.hibernate.mapping.PrimaryKey
import org.hibernate.mapping.Property
import org.hibernate.mapping.RootClass
import org.hibernate.mapping.List as HibernateList
import org.hibernate.mapping.Selectable
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
import org.grails.orm.hibernate.cfg.domainbinding.binder.ManyToManyOwnerValidator
import org.grails.orm.hibernate.cfg.domainbinding.binder.NumericColumnConstraintsBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.PropertyBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.StringColumnConstraintsBinder
import org.grails.orm.hibernate.cfg.domainbinding.generator.GrailsSequenceGeneratorEnum
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateEmbeddedProperty
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernatePersistentEntity
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernatePersistentProperty
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateBasicProperty
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateSimpleIdentityProperty
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateToManyEntityProperty
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateToOneProperty
import org.grails.orm.hibernate.cfg.domainbinding.util.BackticksRemover
import org.grails.orm.hibernate.cfg.domainbinding.util.ColumnNameForPropertyAndPathFetcher
import org.grails.orm.hibernate.cfg.domainbinding.util.DefaultColumnNameFetcher
import org.grails.orm.hibernate.cfg.domainbinding.util.GeneratorCreationContextWrapper
import org.grails.orm.hibernate.cfg.domainbinding.util.MultiTenantFilterDefinitionBinder
import org.grails.orm.hibernate.cfg.domainbinding.util.UniqueNameGenerator

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
    private final UniqueNameGenerator uniqueNameGenerator = new UniqueNameGenerator()
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
        requireTargetsOnTheDataSource(toGenerate)
        requireOwnedManyToMany(toGenerate)
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
        }
        bindNow(contributions, buildingContext, new ArrayList<Class<?>>(generated.values()))
    }

    /**
     * Hibernate binds the classes handed to {@code contributeEntity} only after every {@code AdditionalMappingContributor}
     * has run, and runs the second passes of those entities later still. A contributor that reads the bound entities, such as
     * Envers building the audit mappings of the audited ones, would then see none, or collections with no element. The
     * generated classes are therefore bound here, with the entity hierarchy processing Hibernate applies to such classes and
     * the second passes it applies to the entities of its main mapping sources, so that a contributor running after this
     * binder finds the same bound model as it finds for annotated entities.
     */
    private static void bindNow(AdditionalMappingContributions contributions, MetadataBuildingContext buildingContext, List<Class<?>> classes) {
        if (buildingContext instanceof MetadataBuildingContextRootImpl && buildingContext.metadataCollector instanceof InFlightMetadataCollectorImpl) {
            AnnotationMetadataSourceProcessorImpl.processAdditionalMappings(
                    classes, null, null, (MetadataBuildingContextRootImpl) buildingContext, buildingContext.buildingOptions)
            ((InFlightMetadataCollectorImpl) buildingContext.metadataCollector).processSecondPasses(buildingContext)
        } else {
            classes.each { Class<?> generatedClass -> contributions.contributeEntity(generatedClass) }
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
        alignCompositeForeignKeys(metadata)
        createIdentifierGenerators(metadata)
        defineTenantFilter(metadata)
        return null
    }

    /**
     * Hibernate creates the generator of every root's identifier once while it builds the metadata, to let a generator register
     * what it exports (the table of a table generator, a sequence), and again when the session factory is built. The generators
     * this binder installed are installed only after the metadata is built, so without this call they would register their table
     * only when the persisters are built, after Hibernate ordered the columns of every table (by size and name): the table of a
     * table generator would then keep the order the generator added its columns in ({@code sequence_name, next_val}) where the
     * domain binder's, created in time, is ordered ({@code next_val, sequence_name}). Hibernate itself ignores a
     * {@link MappingException} here and raises it again when it builds the session factory, so this does too.
     */
    private static void createIdentifierGenerators(MetadataImplementor metadata) {
        Database database = metadata.database
        ConfigurationService settings = metadata.metadataBuildingOptions.serviceRegistry.requireService(ConfigurationService)
        String catalog = settings.getSetting(MappingSettings.DEFAULT_CATALOG, StandardConverters.STRING)
        String schema = settings.getSetting(MappingSettings.DEFAULT_SCHEMA, StandardConverters.STRING)
        SqlStringGenerationContext context = SqlStringGenerationContextImpl.fromExplicit(database.jdbcEnvironment, database, catalog, schema)
        GeneratorSettings generatorSettings = new GeneratorSettings() {
            @Override
            String getDefaultCatalog() {
                return catalog
            }

            @Override
            String getDefaultSchema() {
                return schema
            }

            @Override
            SqlStringGenerationContext getSqlStringGenerationContext() {
                return context
            }
        }
        for (PersistentClass persistentClass : new ArrayList<PersistentClass>(metadata.entityBindings)) {
            if (persistentClass instanceof RootClass) {
                try {
                    persistentClass.identifier.createGenerator(database.dialect, (RootClass) persistentClass, persistentClass.identifierProperty, generatorSettings)
                } catch (MappingException ignored) {
                    // raised again, with the same cause, when the session factory is built
                }
            }
        }
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

    /**
     * An association can only refer to an entity of the same data source: the session factory of a data source maps its own
     * entities. The domain binder fails with a mapping exception that names the unmapped class; this one names the data
     * source too, and stops before the generator reports a target that it was not given.
     */
    private void requireTargetsOnTheDataSource(List<GrailsHibernatePersistentEntity> entities) {
        Set<Class<?>> mapped = entities*.javaClass.toSet()
        for (GrailsHibernatePersistentEntity entity : entities) {
            for (GrailsHibernatePersistentEntity target : GrailsDomainGenerator.referencedEntities(entity)) {
                if (!mapped.contains(target.javaClass)) {
                    throw new MappingException(
                            "An association from entity [${entity.name}] refers to [${target.name}], which is not mapped to " +
                                    "data source [${dataSourceName}]: an association cannot cross data sources")
                }
            }
        }
    }

    /**
     * A many-to-many that neither side owns would never be stored: the domain binder refuses it at startup, and so does this
     * binder, with the same message, before the generator reports the mapping as one it cannot express.
     */
    private static void requireOwnedManyToMany(List<GrailsHibernatePersistentEntity> entities) {
        ManyToManyOwnerValidator validator = new ManyToManyOwnerValidator()
        for (GrailsHibernatePersistentEntity entity : entities) {
            for (PersistentProperty<?> property : entity.persistentProperties) {
                if (property instanceof HibernatePersistentProperty) {
                    validator.validate((HibernatePersistentProperty) property)
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
            alignDiscriminator((RootClass) persistentClass, entity)
            alignIdentifier((RootClass) persistentClass, generated, metadata)
        }
        for (Property property : persistentClass.declaredProperties) {
            alignProperty(property, entity, real, null, '')
        }
        alignUniqueKeys(persistentClass, entity)
    }

    /**
     * The mapping can give the discriminator column a precision and a scale, which {@code @DiscriminatorColumn} cannot state. They change
     * nothing in the DDL of the string, integer or character column, but the domain binder puts them on the column of the model, so the
     * same is done here and a schema comparison finds the same column.
     */
    private void alignDiscriminator(RootClass root, GrailsHibernatePersistentEntity entity) {
        DiscriminatorFacets discriminator = generator.hierarchyFacets(entity).discriminator()
        if (discriminator == null || root.discriminator == null) {
            return
        }
        for (Selectable selectable : root.discriminator.selectables) {
            if (selectable instanceof Column) {
                if (discriminator.precision() != null) {
                    ((Column) selectable).precision = discriminator.precision()
                }
                if (discriminator.scale() != null) {
                    ((Column) selectable).scale = discriminator.scale()
                }
            }
        }
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
        alignPrimaryKeyOrder(persistentClass, entity)
        NaturalIdFacets naturalId = generator.naturalIdFacets(entity)
        if (naturalId == null) {
            return
        }
        List<Column> columns = naturalId.propertyNames().collectMany { String name ->
            naturalColumns(persistentClass, entity, name)
        }
        if (persistentClass instanceof RootClass) {
            for (UniqueKey key : persistentClass.table.uniqueKeys.values()) {
                if (key.columns*.name.toSet() == columns*.name.toSet()) {
                    List<Column> ordered = key.columns.sort(false) { Column column -> columns*.name.indexOf(column.name) }
                    key.columns.clear()
                    key.columns.addAll(ordered)
                }
            }
        } else {
            alignSubclassNaturalId(persistentClass, naturalId, columns)
        }
    }

    /**
     * The columns of a property of a natural id, in the order the domain binder gives them: the order of the properties of an
     * embedded type as the mapping declares them, where Hibernate's annotation binder sorts the properties of a component by name.
     */
    private List<Column> naturalColumns(PersistentClass persistentClass, GrailsHibernatePersistentEntity entity, String name) {
        Property property = persistentClass.getProperty(name)
        List<Column> columns = property.columns
        PersistentProperty<?> mapped = entity.getPropertyByName(name)
        if (property.value instanceof Component && mapped instanceof HibernateEmbeddedProperty) {
            List<String> declared = generator.embeddedLeaves((HibernateEmbeddedProperty) mapped).collectMany { EmbeddedLeaf leaf ->
                leaf.toOne() != null ? leaf.toOne().joinColumns()*.name() : (leaf.column() != null ? [leaf.column().name()] : [])
            }
            List<String> bound = columns*.name
            if (declared.toSet() == bound.toSet()) {
                return columns.sort(false) { Column column -> declared.indexOf(column.name) }
            }
        }
        return columns
    }

    /**
     * Hibernate refuses {@code @NaturalId} on a subclass. The domain binder binds the natural id of a subclass as it does a root's
     * ({@code NaturalId.createUniqueKey}): each property it names, found in the hierarchy, is made updatable exactly when the natural
     * id is mutable, and one unique key over their columns in the order of the mapping is added to the table of the class, which is
     * the table of the hierarchy for a single-table subclass, with the name {@link UniqueNameGenerator} gives it.
     *
     * <p>The properties are not marked as natural identifiers: with Hibernate 7 the domain binder's marking makes every load and
     * update of the subclass fail with a {@code NullPointerException}, because Hibernate builds the natural id mapping of a root
     * only.</p>
     */
    private void alignSubclassNaturalId(PersistentClass persistentClass, NaturalIdFacets naturalId, List<Column> columns) {
        for (String name : naturalId.propertyNames()) {
            persistentClass.getProperty(name).updateable = naturalId.mutable()
        }
        if (!columns.isEmpty()) {
            UniqueKey key = new UniqueKey(persistentClass.table)
            columns.each { Column column -> key.addColumn(column) }
            uniqueNameGenerator.setGeneratedUniqueName(key)
            persistentClass.table.addUniqueKey(key)
        }
    }

    /**
     * A to-one foreign key to an entity with a composite identifier names the referenced columns, in the order of the identifier's
     * parts sorted by name: the domain binder's {@code CompositeIdentifierToManyToOneBinder} creates it with the referenced columns it
     * collected after {@code Component.sortProperties()}. The key Hibernate derives refers to the primary key without naming its
     * columns, and before the schema is built Hibernate puts the columns of such a key in the order it gives the primary key
     * (by size and name); a key that names its referenced columns keeps the order it was created in, which is the sorted order of
     * the parts, as the binder's does. The columns of the key already pair with the columns of the primary key in that order.
     */
    private void alignCompositeForeignKeys(MetadataImplementor metadata) {
        for (PersistentClass persistentClass : new ArrayList<PersistentClass>(metadata.entityBindings)) {
            if (generatedByName.containsKey(persistentClass.entityName)) {
                List<Value> values = new ArrayList<Value>()
                collectValues(persistentClass, values)
                for (Value value : values) {
                    if (value instanceof ManyToOne) {
                        alignCompositeForeignKey((ManyToOne) value)
                    }
                }
            }
        }
    }

    private static void collectValues(PersistentClass persistentClass, List<Value> into) {
        if (persistentClass instanceof RootClass && persistentClass.identifier instanceof Component) {
            collectValues((Component) persistentClass.identifier, into)
        }
        for (Property property : persistentClass.declaredProperties) {
            into << property.value
            if (property.value instanceof Component) {
                collectValues((Component) property.value, into)
            }
        }
    }

    private static void collectValues(Component component, List<Value> into) {
        for (Property property : component.properties) {
            into << property.value
            if (property.value instanceof Component) {
                collectValues((Component) property.value, into)
            }
        }
    }

    private void alignCompositeForeignKey(ManyToOne value) {
        Generated target = generatedByName.get(value.referencedEntityName)
        if (target == null || target.entity().compositeIdentity == null) {
            return
        }
        ForeignKey key = value.table.foreignKeys.values().find { ForeignKey candidate ->
            candidate.referencedEntityName == value.referencedEntityName && candidate.columns == value.columns
        }
        if (key != null && key.referencedColumns.isEmpty() && key.referencedTable.primaryKey != null) {
            key.addReferencedColumns(new ArrayList<Column>(key.referencedTable.primaryKey.columns))
        }
    }

    /**
     * A unique key over exactly the columns of the primary key orders the primary key's columns: the domain binder creates the
     * primary key after the key, and Hibernate takes the order of the key for the primary key and drops the key
     * ({@code Table.setPrimaryKey}). Hibernate's annotation binder adds the key after the primary key and drops it without telling
     * the primary key, so the order the generator decided (the binder's) is stated here with a key that is not part of the table.
     */
    private void alignPrimaryKeyOrder(PersistentClass persistentClass, GrailsHibernatePersistentEntity entity) {
        PrimaryKey primaryKey = persistentClass.table.primaryKey
        if (!(persistentClass instanceof RootClass) || primaryKey == null) {
            return
        }
        List<String> order = generator.constraintFacets(entity).primaryKeyOrder()
        if (order == null || order.toSet() != primaryKey.columns*.name.toSet()) {
            return
        }
        UniqueKey ordering = new UniqueKey(persistentClass.table)
        for (String name : order) {
            ordering.addColumn(primaryKey.columns.find { Column column -> column.name == name })
        }
        primaryKey.orderingUniqueKey = ordering
    }

    private void alignIdentifier(RootClass root, Generated generated, MetadataImplementor metadata) {
        GrailsHibernatePersistentEntity entity = generated.entity()
        if (!(entity.identity instanceof HibernateSimpleIdentityProperty)) {
            alignCompositeIdentifier(root, entity, metadata)
            return
        }
        alignProperty(root.identifierProperty, entity, entity.javaClass, null, '')
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
                if (property.value instanceof ManyToOne && part instanceof HibernateToOneProperty) {
                    alignJoinColumns(property.value, generator.toOneFacets((HibernateToOneProperty) part).joinColumns())
                }
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

    /**
     * @param holder the entity that declares the embedded property the property is inside, or {@code null} for a property of the entity
     * @param path the dotted names of the embedded properties from {@code holder} down to the owner of the property
     */
    private void alignProperty(
            Property property, GrailsHibernatePersistentEntity owner, Class<?> ownerClass, GrailsHibernatePersistentEntity holder, String path) {
        PersistentProperty<?> persistentProperty = owner.identity?.name == property.name ?
                owner.identity : owner.getPropertyByName(property.name)
        if (persistentProperty instanceof HibernatePersistentProperty) {
            property.propertyAccessorName = propertyBinder.accessorName((HibernatePersistentProperty) persistentProperty)
        }
        if (property.value instanceof ManyToOne && persistentProperty instanceof HibernateToOneProperty) {
            alignJoinColumns(property.value, generator.toOneFacets((HibernateToOneProperty) persistentProperty).joinColumns())
        }
        if (property.value instanceof Collection && persistentProperty instanceof HibernatePersistentProperty) {
            // a collection of an embedded type that several embedded properties reach has a table of its own, named after its owner
            String qualifier = holder == null ? null : generator.embeddedCollectionQualifier(holder, path, persistentProperty.name)
            alignJoinColumns((Collection) property.value, (HibernatePersistentProperty) persistentProperty, qualifier)
            alignExtraLazy((Collection) property.value, (HibernatePersistentProperty) persistentProperty, qualifier)
            alignCollectionTable((Collection) property.value, (HibernatePersistentProperty) persistentProperty, qualifier)
            alignListIndexLength((Collection) property.value, (HibernatePersistentProperty) persistentProperty, qualifier)
            alignCollectionIndexes((Collection) property.value, (HibernatePersistentProperty) persistentProperty, qualifier)
        }
        if (property.value instanceof Component && persistentProperty instanceof Embedded) {
            // PropertyBinder marks the property lazy when the mapping says lazy: true; @Basic(fetch = LAZY) on an @Embedded is ignored
            // by Hibernate's annotation binder, so the mapping's flag is set on the bound property
            if (persistentProperty instanceof HibernatePersistentProperty && ((HibernatePersistentProperty) persistentProperty).isLazy()) {
                property.lazy = true
            }
            PersistentEntity embedded = ((Embedded<?>) persistentProperty).associatedEntity
            if (embedded instanceof GrailsHibernatePersistentEntity) {
                alignComponent(
                        (Component) property.value,
                        (GrailsHibernatePersistentEntity) embedded,
                        BeanUtils.findPropertyType(property.name, ownerClass),
                        holder == null ? owner : holder,
                        path.isEmpty() ? property.name : "${path}.${property.name}".toString())
            }
        }
    }

    /**
     * The domain binder puts the {@code defaultValue}, {@code comment}, {@code read} and {@code write} of a column config on the foreign
     * key column of a to-one association, on the key column of a collection and on the element column of a many-to-many (the {@code length},
     * {@code precision} and {@code scale} change nothing, as the type of a foreign key column is the one of the column it references). A
     * {@code @JoinColumn} states none of them, so they are set on the columns of the bound model.
     */
    private void alignJoinColumns(Collection collection, HibernatePersistentProperty property, String qualifier) {
        if (property instanceof HibernateToManyEntityProperty) {
            ToManyFacets facets = generator.toManyFacets((HibernateToManyEntityProperty) property, qualifier)
            alignJoinColumns(collection.key, facets.keys())
            if (facets.manyToMany()) {
                alignJoinColumns(collection.element, [facets.element()])
            }
        } else if (property instanceof HibernateBasicProperty) {
            alignJoinColumns(collection.key, generator.collectionFacets((HibernateBasicProperty) property, qualifier).keys())
        }
    }

    private static void alignJoinColumns(Value value, List<ColumnFacets> facets) {
        List<Column> columns = value.selectables.findAll { it instanceof Column }.collect { (Column) it }
        for (int i = 0; i < facets.size(); i++) {
            ColumnFacets facet = facets[i]
            Column column = columns.size() == facets.size() ? columns[i] :
                    columns.find { Column candidate -> candidate.name == facet.name().replace('`', '') }
            if (column == null) {
                continue
            }
            if (facet.defaultValue() != null) {
                column.defaultValue = facet.defaultValue()
            }
            if (facet.comment() != null) {
                column.comment = facet.comment()
            }
            if (facet.read() != null) {
                column.customRead = facet.read()
            }
            if (facet.write() != null) {
                column.customWrite = facet.write()
            }
        }
    }

    /**
     * An explicit {@code lazy: true} on a collection makes the domain binder bind an extra-lazy collection ({@code size()},
     * {@code contains()} and {@code isEmpty()} do not initialize it). Hibernate 7's annotation binder always binds an ordinary
     * lazy collection and has no annotation for the extra-lazy kind, so the flag is set on the bound collection.
     */
    private void alignExtraLazy(Collection collection, HibernatePersistentProperty property, String qualifier) {
        boolean extraLazy
        if (property instanceof HibernateToManyEntityProperty) {
            extraLazy = generator.toManyFacets((HibernateToManyEntityProperty) property, qualifier).extraLazy()
        } else if (property instanceof HibernateBasicProperty) {
            extraLazy = generator.collectionFacets((HibernateBasicProperty) property, qualifier).extraLazy()
        } else {
            return
        }
        if (extraLazy) {
            collection.extraLazy = true
        }
    }

    /**
     * The domain binder indexes the key column of a collection (and the element column of a collection of enums) when the mapping puts
     * {@code index:} on the collection property. {@code @CollectionTable} and {@code @JoinTable} could state the index of the owning side
     * only: the inverse side of a many-to-many has no table annotation of its own, and the binder indexes the key of both sides in the one
     * join table. The indexes the generator decided are created here on the table Hibernate bound for the collection, with the names the
     * mapping gives them.
     */
    private void alignCollectionIndexes(Collection collection, HibernatePersistentProperty property, String qualifier) {
        List<IndexFacets> indexes
        if (property instanceof HibernateToManyEntityProperty) {
            indexes = generator.toManyFacets((HibernateToManyEntityProperty) property, qualifier).indexes()
        } else if (property instanceof HibernateBasicProperty) {
            indexes = generator.collectionFacets((HibernateBasicProperty) property, qualifier).indexes()
        } else {
            return
        }
        Table table = collection.collectionTable
        if (table == null) {
            return
        }
        for (IndexFacets facets : indexes) {
            org.hibernate.mapping.Index index = table.getOrCreateIndex(facets.name())
            for (String name : facets.columns()) {
                Column column = table.columns.find { Column candidate -> candidate.name.equalsIgnoreCase(name.replace('`', '')) }
                if (column != null) {
                    index.addColumn(column)
                }
            }
        }
    }

    /**
     * Hibernate's annotation binder gives the index column of a list no length, which it sizes as a long string when the mapping types
     * the index as a string (a CLOB, which cannot be a key). The domain binder's column has the default length of every column it creates.
     */
    private void alignListIndexLength(Collection collection, HibernatePersistentProperty property, String qualifier) {
        TypeFacets indexType
        if (property instanceof HibernateToManyEntityProperty) {
            indexType = generator.toManyFacets((HibernateToManyEntityProperty) property, qualifier).indexType()
        } else if (property instanceof HibernateBasicProperty) {
            indexType = generator.collectionFacets((HibernateBasicProperty) property, qualifier).indexType()
        } else {
            return
        }
        if (indexType != null && collection instanceof HibernateList) {
            for (Selectable selectable : ((IndexedCollection) collection).index.selectables) {
                if (selectable instanceof Column) {
                    ((Column) selectable).length = (long) Length.DEFAULT
                }
            }
        }
    }

    /**
     * Hibernate's annotation binder forces the key and element columns of a collection table not null ("I break the spec, but it's
     * for good"), where the domain binder leaves them nullable unless they are part of the primary key, and a set whose element
     * column is nullable gets a unique key over its columns instead of a primary key. The nullability the generator decided
     * (the binder's) is restored on the columns, and for a set the key Hibernate derived from them is derived again.
     */
    private void alignCollectionTable(Collection collection, HibernatePersistentProperty property, String qualifier) {
        if (collection.inverse || collection.oneToMany) {
            return
        }
        List<ColumnFacets> keys
        ColumnFacets element
        if (property instanceof HibernateToManyEntityProperty) {
            ToManyFacets facets = generator.toManyFacets((HibernateToManyEntityProperty) property, qualifier)
            keys = facets.keys()
            element = facets.element()
        } else if (property instanceof HibernateBasicProperty) {
            CollectionFacets facets = generator.collectionFacets((HibernateBasicProperty) property, qualifier)
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

    private void alignComponent(
            Component component, GrailsHibernatePersistentEntity embedded, Class<?> type, GrailsHibernatePersistentEntity holder, String path) {
        component.componentClassName = type.name
        for (Property property : component.getProperties()) {
            alignProperty(property, embedded, type, holder, path)
        }
    }

    private static record Generated(GrailsHibernatePersistentEntity entity, Class<?> generatedClass) { }

}
