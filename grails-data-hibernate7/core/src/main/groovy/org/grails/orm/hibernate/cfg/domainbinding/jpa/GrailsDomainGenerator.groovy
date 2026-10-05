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

import java.lang.annotation.Annotation
import java.lang.reflect.Method
import java.lang.reflect.Modifier

import groovy.transform.CompileStatic
import jakarta.persistence.AttributeOverride
import jakarta.persistence.AttributeOverrides
import jakarta.persistence.CascadeType
import jakarta.persistence.CollectionTable
import jakarta.persistence.Column as JpaColumn
import jakarta.persistence.DiscriminatorColumn
import jakarta.persistence.DiscriminatorType
import jakarta.persistence.DiscriminatorValue
import jakarta.persistence.ElementCollection
import jakarta.persistence.Embeddable
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.Inheritance
import jakarta.persistence.InheritanceType
import jakarta.persistence.JoinColumn
import jakarta.persistence.JoinTable as JpaJoinTable
import jakarta.persistence.ManyToMany
import jakarta.persistence.ManyToOne
import jakarta.persistence.MapKeyColumn
import jakarta.persistence.OneToMany
import jakarta.persistence.OneToOne
import jakarta.persistence.OrderBy
import jakarta.persistence.OrderColumn
import jakarta.persistence.PrimaryKeyJoinColumn
import jakarta.persistence.Table
import jakarta.persistence.Version
import net.bytebuddy.ByteBuddy
import net.bytebuddy.description.annotation.AnnotationDescription
import net.bytebuddy.description.method.MethodDescription
import net.bytebuddy.description.modifier.TypeManifestation
import net.bytebuddy.description.modifier.Visibility
import net.bytebuddy.description.type.TypeDescription
import net.bytebuddy.description.type.TypeList
import net.bytebuddy.dynamic.DynamicType
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy
import org.hibernate.FetchMode
import org.hibernate.Length
import org.hibernate.annotations.BatchSize
import org.hibernate.annotations.Cache
import org.hibernate.annotations.CacheConcurrencyStrategy
import org.hibernate.annotations.Cascade
import org.hibernate.annotations.ColumnDefault
import org.hibernate.annotations.ColumnTransformer
import org.hibernate.annotations.Comment
import org.hibernate.annotations.DiscriminatorFormula
import org.hibernate.annotations.DiscriminatorOptions
import org.hibernate.annotations.DynamicInsert
import org.hibernate.annotations.DynamicUpdate
import org.hibernate.annotations.Fetch
import org.hibernate.annotations.Filter
import org.hibernate.annotations.FilterDef
import org.hibernate.annotations.FetchMode as AnnotationFetchMode
import org.hibernate.annotations.Formula
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.annotations.NotFound
import org.hibernate.annotations.NotFoundAction
import org.hibernate.annotations.ParamDef
import org.hibernate.annotations.Parameter
import org.hibernate.annotations.SortNatural
import org.hibernate.annotations.Type
import org.hibernate.annotations.UuidGenerator
import org.hibernate.id.uuid.UuidGenerator as HibernateUuidGenerator
import org.hibernate.mapping.Column
import org.hibernate.generator.Assigned
import org.hibernate.generator.Generator
import org.hibernate.type.BasicType
import org.hibernate.type.spi.TypeConfiguration
import org.hibernate.usertype.UserType

import org.grails.datastore.mapping.model.config.GormProperties
import org.grails.datastore.mapping.model.types.Association
import org.grails.orm.hibernate.cfg.ColumnConfig
import org.grails.orm.hibernate.cfg.DiscriminatorConfig
import org.grails.orm.hibernate.cfg.HibernateSimpleIdentity
import org.grails.orm.hibernate.cfg.IdentityEnumType
import org.grails.orm.hibernate.cfg.JoinTable
import org.grails.orm.hibernate.cfg.Mapping
import org.grails.orm.hibernate.cfg.PersistentEntityNamingStrategy
import org.grails.orm.hibernate.cfg.PropertyConfig
import org.grails.orm.hibernate.cfg.domainbinding.binder.ColumnConfigToColumnBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.GrailsDomainBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.NumericColumnConstraintsBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.StringColumnConstraintsBinder
import org.grails.orm.hibernate.cfg.domainbinding.generator.GrailsIdentityGenerator
import org.grails.orm.hibernate.cfg.domainbinding.generator.GrailsIncrementGenerator
import org.grails.orm.hibernate.cfg.domainbinding.generator.GrailsNativeGenerator
import org.grails.orm.hibernate.cfg.domainbinding.generator.GrailsSequenceGeneratorEnum
import org.grails.orm.hibernate.cfg.domainbinding.generator.GrailsSequenceStyleGenerator
import org.grails.orm.hibernate.cfg.domainbinding.generator.GrailsTableGenerator
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateAssociation
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateBasicProperty
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateEmbeddedCollectionProperty
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateEmbeddedProperty
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateEnumProperty
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateManyToOneProperty
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateOneToManyProperty
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateOneToOneProperty
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernatePersistentProperty
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateSimpleIdentityProperty
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateSimpleProperty
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateTenantIdProperty
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateToManyEntityProperty
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateToManyProperty
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateToOneProperty
import org.grails.orm.hibernate.cfg.domainbinding.util.CascadeBehavior
import org.grails.orm.hibernate.cfg.domainbinding.util.CascadeBehaviorFetcher
import org.grails.orm.hibernate.cfg.domainbinding.util.ColumnNameForPropertyAndPathFetcher
import org.grails.orm.hibernate.cfg.domainbinding.util.DefaultColumnNameFetcher
import org.grails.orm.hibernate.cfg.domainbinding.util.GrailsEnumType
import org.grails.orm.hibernate.cfg.domainbinding.util.TableForManyCalculator

/**
 * Describes a GORM domain class to Hibernate as an annotated JPA entity.
 *
 * <p>GORM domain classes carry no JPA metadata, so Hibernate's annotation binder cannot read them. This class
 * turns an already-built {@link GrailsHibernatePersistentEntity} (its properties and its evaluated
 * {@code mapping} and {@code constraints}) into a generated class whose fields carry the annotations Hibernate
 * understands. The field set is exactly the entity's persistent properties, so Hibernate never reflects over the
 * Groovy class and sees none of its injected members.</p>
 *
 * <p>Column facets (length, precision, scale, column definition, uniqueness) are not derived a second time: the
 * same constraint binders the domain binder uses are run on a scratch {@link Column} and the result is read back,
 * so there is one set of rules.</p>
 *
 * @since 9.0
 */
@CompileStatic
class GrailsDomainGenerator {

    static final String GENERATED_PACKAGE = 'org.grails.orm.hibernate.generated'

    private final PersistentEntityNamingStrategy namingStrategy
    private final ColumnNameForPropertyAndPathFetcher columnNames
    private final ColumnConfigToColumnBinder columnConfigBinder
    private final StringColumnConstraintsBinder stringConstraints
    private final NumericColumnConstraintsBinder numericConstraints
    private final TypeConfiguration typeConfiguration
    private final TableForManyCalculator tableForMany
    private final DefaultColumnNameFetcher defaultColumnNames
    private final CascadeBehaviorFetcher cascadeFetcher = new CascadeBehaviorFetcher()

    GrailsDomainGenerator(
            PersistentEntityNamingStrategy namingStrategy,
            ColumnNameForPropertyAndPathFetcher columnNames,
            ColumnConfigToColumnBinder columnConfigBinder,
            StringColumnConstraintsBinder stringConstraints,
            NumericColumnConstraintsBinder numericConstraints,
            TypeConfiguration typeConfiguration) {
        this.namingStrategy = namingStrategy
        this.columnNames = columnNames
        this.columnConfigBinder = columnConfigBinder
        this.stringConstraints = stringConstraints
        this.numericConstraints = numericConstraints
        this.typeConfiguration = typeConfiguration
        // the table name rules never touch the metadata collector; only the schema and catalog defaults do, and those are mirrored here
        this.tableForMany = new TableForManyCalculator(namingStrategy, null)
        this.defaultColumnNames = new DefaultColumnNameFetcher(namingStrategy)
    }

    /**
     * Generates the class for an entity that is not part of an inheritance hierarchy.
     *
     * @param entity the entity to describe; it must be a root with no subclasses
     * @param parent the class loader the generated class is loaded under; the class lives as long as it does
     * @return a new class whose annotations describe the entity
     * @throws IllegalArgumentException when the entity is part of a hierarchy, which {@link #generateAll} describes
     */
    Class<?> generate(GrailsHibernatePersistentEntity entity, ClassLoader parent) {
        return generateAll([entity], parent).get(entity)
    }

    /**
     * Generates one class for each entity, so that a hierarchy is described whole. Hibernate's annotation binder
     * derives the hierarchy from {@code extends}, so the class generated for a subclass extends the class
     * generated for its direct superclass.
     *
     * <p>A multi-tenant entity gets the tenant {@code @Filter}; the one {@code @FilterDef} that declares the filter is
     * placed on the first such entity of the call. Hibernate refuses a second definition of the same filter, so every
     * multi-tenant entity of a Hibernate registry must be generated in one call.</p>
     *
     * <p>A GORM embedded property becomes an {@code @Embedded} field whose type is a generated {@code @Embeddable}
     * class. Owners that embed the same type with the same properties share one embeddable class, and each owner
     * states its own column names and nullability with {@code @AttributeOverride}s.</p>
     *
     * <p>All the classes of one call are loaded together into a single new class loader whose parent is
     * {@code parent}: a subclass resolves its superclass by plain delegation inside that loader, and a Hibernate
     * registry that must load the classes by name needs only that one loader
     * ({@code BootstrapServiceRegistryBuilder.applyClassLoader(generated.classLoader)}). Nothing is injected into an
     * existing loader, so no reflective access to {@code ClassLoader.defineClass} is needed, and the classes are
     * collected together with the loader.</p>
     *
     * @param entities the entities to describe; every direct superclass and every direct subclass of an entity must
     *     be in the collection
     * @param parent the class loader the generated classes are loaded under
     * @return the generated class of each entity, in the order of {@code entities}
     * @throws IllegalArgumentException when a hierarchy is not complete
     * @throws UnsupportedOperationException when an entity uses something the generator cannot describe yet
     */
    Map<GrailsHibernatePersistentEntity, Class<?>> generateAll(
            Collection<? extends GrailsHibernatePersistentEntity> entities, ClassLoader parent) {
        Set<GrailsHibernatePersistentEntity> given = new LinkedHashSet<GrailsHibernatePersistentEntity>(entities)
        for (GrailsHibernatePersistentEntity entity : given) {
            requireWholeHierarchy(entity, given)
        }
        for (GrailsHibernatePersistentEntity entity : given) {
            String problem = generationProblem(entity)
            if (problem != null) {
                throw new UnsupportedOperationException(problem)
            }
        }
        for (GrailsHibernatePersistentEntity entity : given) {
            requireReferencedEntities(entity, given)
        }
        List<GrailsHibernatePersistentEntity> ordered = new ArrayList<GrailsHibernatePersistentEntity>(given)
        ordered.sort { GrailsHibernatePersistentEntity a, GrailsHibernatePersistentEntity b -> depth(a) <=> depth(b) }

        // Hibernate refuses a second @FilterDef of the same name, so exactly one class of the call carries the definition
        GrailsHibernatePersistentEntity filterDefinition = given.find { GrailsHibernatePersistentEntity entity -> tenantFacets(entity) != null }
        Map<GrailsHibernatePersistentEntity, DynamicType.Unloaded<?>> made = [:]
        Map<String, DynamicType.Unloaded<?>> embeddables = [:]
        Map<TypeDescription, byte[]> types = [:]
        for (GrailsHibernatePersistentEntity entity : ordered) {
            TypeDescription superType = entity.isRoot() ?
                    TypeDescription.ForLoadedType.of(Object) : made.get(superEntity(entity, given)).typeDescription
            DynamicType.Unloaded<?> unloaded = make(entity, superType, embeddables, entity.is(filterDefinition))
            made.put(entity, unloaded)
            types.putAll(unloaded.allTypes)
        }
        for (DynamicType.Unloaded<?> embeddable : embeddables.values()) {
            types.putAll(embeddable.allTypes)
        }
        Map<TypeDescription, Class<?>> loaded = ClassLoadingStrategy.Default.WRAPPER.load(parent, types)
        Map<GrailsHibernatePersistentEntity, Class<?>> result = [:]
        for (GrailsHibernatePersistentEntity entity : given) {
            result.put(entity, loaded.get(made.get(entity).typeDescription))
        }
        return result
    }

    private static void requireWholeHierarchy(GrailsHibernatePersistentEntity entity, Set<GrailsHibernatePersistentEntity> given) {
        if (!entity.isRoot() && superEntity(entity, given) == null) {
            throw new IllegalArgumentException(
                    "Entity [${entity.name}] is part of an inheritance hierarchy: generate it together with its " +
                            'superclass and subclasses (generateAll)')
        }
        for (GrailsHibernatePersistentEntity child : entity.childEntities) {
            if (!given.any { GrailsHibernatePersistentEntity other -> other.javaClass == child.javaClass }) {
                throw new IllegalArgumentException(
                        "Entity [${entity.name}] is part of an inheritance hierarchy: generate it together with its " +
                                "subclass [${child.name}] (generateAll)")
            }
        }
    }

    private static void requireReferencedEntities(GrailsHibernatePersistentEntity entity, Set<GrailsHibernatePersistentEntity> given) {
        for (GrailsHibernatePersistentEntity target : referencedEntities(entity)) {
            if (!given.any { GrailsHibernatePersistentEntity other -> other.javaClass == target.javaClass }) {
                throw new IllegalArgumentException(
                        "Entity [${entity.name}] refers to [${target.name}], which is not part of the call: the generated field " +
                                'is typed with the class generated for its target, so every entity of a connected group must be ' +
                                'generated together (generateAll)')
            }
        }
    }

    /**
     * @return the entities the generated class of the entity refers to through its associations, in property order and
     *     without duplicates: the classes generated for them must be part of the same call
     */
    static List<GrailsHibernatePersistentEntity> referencedEntities(GrailsHibernatePersistentEntity entity) {
        List<GrailsHibernatePersistentEntity> found = []
        for (HibernatePersistentProperty property : entity.persistentPropertiesToBind) {
            if (property instanceof HibernateToOneProperty || property instanceof HibernateToManyEntityProperty) {
                GrailsHibernatePersistentEntity target = ((HibernateAssociation) property).hibernateAssociatedEntity
                if (target != null && !found.any { GrailsHibernatePersistentEntity other -> other.javaClass == target.javaClass }) {
                    found << target
                }
            }
        }
        return found
    }

    /**
     * @return why {@link #generateAll} cannot describe the entity, or {@code null} when it can: a hierarchy that mixes
     *     strategies, a tenant id with a mapped type, an entity with no simple identifier, or a property the generator
     *     does not support. It does not look at the other entities of the call.
     */
    String generationProblem(GrailsHibernatePersistentEntity entity) {
        try {
            hierarchyFacets(entity)
            tenantFacets(entity)
        } catch (UnsupportedOperationException e) {
            return e.message
        }
        if (entity.isRoot() && !(entity.identity instanceof HibernateSimpleIdentityProperty)) {
            return "Entity [${entity.name}] has no simple identifier (a composite identifier, for example), " +
                    'which the generator does not support yet'
        }
        for (HibernatePersistentProperty property : entity.persistentPropertiesToBind) {
            if (!supports(property)) {
                return unsupportedReason(entity, property)
            }
        }
        return null
    }

    private static GrailsHibernatePersistentEntity superEntity(
            GrailsHibernatePersistentEntity entity, Set<GrailsHibernatePersistentEntity> given) {
        return given.find { GrailsHibernatePersistentEntity other -> other.javaClass == entity.javaClass.superclass }
    }

    private static int depth(GrailsHibernatePersistentEntity entity) {
        int depth = 0
        for (Class<?> type = entity.javaClass.superclass; type != null && type != Object; type = type.superclass) {
            depth++
        }
        return depth
    }

    private DynamicType.Unloaded<?> make(
            GrailsHibernatePersistentEntity entity, TypeDescription superType, Map<String, DynamicType.Unloaded<?>> embeddables,
            boolean definesFilter) {
        HierarchyFacets hierarchy = hierarchyFacets(entity)
        DynamicType.Builder<Object> builder = (DynamicType.Builder<Object>) new ByteBuddy()
                .subclass(superType)
                .name(generatedClassName(entity))
                .annotateType(classAnnotations(entity, hierarchy, definesFilter) as AnnotationDescription[])
        if (hierarchy.abstractClass()) {
            builder = builder.modifiers(Visibility.PUBLIC, TypeManifestation.ABSTRACT)
        }
        if (entity.isRoot()) {
            builder = defineIdentityAndVersion(builder, entity, embeddables)
        }
        for (HibernatePersistentProperty property : entity.persistentPropertiesToBind) {
            builder = defineField(builder, property, [], embeddables)
        }
        return builder.make()
    }

    private DynamicType.Builder<Object> defineIdentityAndVersion(
            DynamicType.Builder<Object> builder, GrailsHibernatePersistentEntity entity,
            Map<String, DynamicType.Unloaded<?>> embeddables) {
        HibernatePersistentProperty identity = (HibernatePersistentProperty) entity.identity
        List<AnnotationDescription> idAnnotations = [AnnotationDescription.Builder.ofType(Id).build()]
        idAnnotations.addAll(idGeneratorAnnotations(idFacets(entity)))
        DynamicType.Builder<Object> result = defineField(builder, identity, idAnnotations, embeddables)
        HibernatePersistentProperty version = entity.version
        if (version != null) {
            result = defineField(result, version, [AnnotationDescription.Builder.ofType(Version).build()], embeddables)
        }
        return result
    }

    /**
     * @return why {@link #generate} rejects the property, as the {@link UnsupportedOperationException} message names it
     */
    String unsupportedReason(GrailsHibernatePersistentEntity entity, HibernatePersistentProperty property) {
        if (property instanceof HibernateEmbeddedProperty) {
            String problem = embeddedProblem((HibernateEmbeddedProperty) property, [])
            if (problem != null) {
                return "Embedded property [${property.name}] of [${entity.name}]: ${problem}"
            }
        }
        if (property instanceof HibernateEmbeddedCollectionProperty) {
            return "Collection property [${property.name}] of [${entity.name}]: a collection of embedded objects, which the binder " +
                    'cannot bind: without a join table key it creates a key with no column and Hibernate fails to boot ' +
                    "('Foreign key must have the same number of columns as the referenced primary key'), and with one the boot " +
                    'overflows the stack while Hibernate builds the collection tables'
        }
        if (property instanceof HibernateBasicProperty) {
            String problem = collectionProblem((HibernateBasicProperty) property)
            if (problem != null) {
                return "Collection property [${property.name}] of [${entity.name}]: ${problem}"
            }
        }
        if (property instanceof HibernateToOneProperty) {
            String problem = toOneProblem((HibernateToOneProperty) property)
            if (problem != null) {
                return "Association property [${property.name}] of [${entity.name}]: ${problem}"
            }
        }
        if (property instanceof HibernateOneToManyProperty) {
            String problem = toManyProblem((HibernateOneToManyProperty) property)
            if (problem != null) {
                return "Association property [${property.name}] of [${entity.name}]: ${problem}"
            }
        }
        if ((property instanceof HibernateSimpleProperty || property instanceof HibernateTenantIdProperty) && !decideType(property).supported) {
            return typeNotSupported(property, decideType(property).name)
        }
        return "Property [${property.name}] of [${entity.name}] is a ${property.getClass().simpleName}, " +
                'which the generator does not support yet'
    }

    private static String typeNotSupported(HibernatePersistentProperty property, String name) {
        return "Type [${name}] of property [${property.name}] of [${property.hibernateOwner.name}] " +
                'is not a UserType or a registered type for the property class, which the generator does not support yet'
    }

    static String generatedClassName(GrailsHibernatePersistentEntity entity) {
        return GENERATED_PACKAGE + '.' + entity.javaClass.name.replace('.', '_')
    }

    /** The name of the {@code @Embeddable} generated for an embedded type; distinct from an entity's generated name. */
    static String generatedEmbeddableName(GrailsHibernatePersistentEntity type) {
        return generatedClassName(type) + '_Embeddable'
    }

    /**
     * Decides the class-level facets of an entity the way the domain binder does. A single-table subclass has no
     * table of its own: it reports the table of its hierarchy and no comment, which belongs to the table and so to
     * the root. A subclass's JPA
     * name is always its simple name, whatever {@code autoImport} says: that is what the binder's subclass mapping
     * does.
     */
    EntityFacets entityFacets(GrailsHibernatePersistentEntity entity) {
        Mapping mapping = entity.mappedForm
        boolean sharesTable = entity.isTablePerHierarchySubclass()
        GrailsHibernatePersistentEntity tableOwner = sharesTable ? entity.hibernateRootEntity : entity
        Mapping tableMapping = tableOwner.mappedForm
        boolean autoImport = mapping == null || mapping.autoImport
        return new EntityFacets(
                autoImport || !entity.isRoot() ? entity.javaClass.simpleName : entity.javaClass.name,
                tableOwner.getTableName(namingStrategy),
                tableMapping?.table?.schema ?: null,
                tableMapping?.table?.catalog ?: null,
                mapping != null && mapping.dynamicInsert,
                mapping != null && mapping.dynamicUpdate,
                mapping?.batchSize != null ? mapping.batchSize : 0,
                sharesTable ? null : entity.comment)
    }

    /**
     * Decides where the entity sits in its inheritance hierarchy the way the domain binder does: the strategy is the
     * one the entity's own accessors report ({@code isUnionSubclass}, {@code isJoinedSubclass}, else single table),
     * the discriminator is the one {@code DiscriminatorPropertyBinder} binds on the root of a single-table
     * hierarchy that has subclasses, a subclass's discriminator value is the entity's own
     * {@code getDiscriminatorValue}, and a joined subclass's key column is named like its identifier column.
     *
     * @throws UnsupportedOperationException for a hierarchy that mixes strategies (annotations state the strategy
     *     once, on the root) or whose discriminator has no annotation equivalent
     */
    HierarchyFacets hierarchyFacets(GrailsHibernatePersistentEntity entity) {
        boolean root = entity.isRoot()
        if (root && entity.childEntities.isEmpty()) {
            return new HierarchyFacets(null, null, entity.isAbstract(), entity.isTableAbstract(), true, null, null, null)
        }
        InheritanceType strategy = inheritanceType(entity)
        InheritanceType hierarchyStrategy = inheritanceType(entity.hibernateRootEntity)
        if (strategy != hierarchyStrategy) {
            throw new UnsupportedOperationException(
                    "Entity [${entity.name}] uses ${strategy} but the root of its hierarchy uses ${hierarchyStrategy}: " +
                            'a hierarchy that mixes inheritance strategies is not supported')
        }
        boolean singleTable = strategy == InheritanceType.SINGLE_TABLE
        String discriminatorValue = null
        if (singleTable) {
            DiscriminatorConfig config = entity.hibernateMappedForm?.discriminator
            discriminatorValue = root ? (config?.value != null ? config.value : entity.name) : entity.discriminatorValue
        }
        return new HierarchyFacets(
                strategy,
                root ? null : entity.parentEntity.name,
                entity.isAbstract(),
                root ? entity.isTableAbstract() : strategy == InheritanceType.TABLE_PER_CLASS && entity.isAbstract(),
                root || !singleTable,
                discriminatorValue,
                root && singleTable ? discriminatorFacets(entity) : null,
                !root && strategy == InheritanceType.JOINED ?
                        columnNames.getColumnNameForPropertyAndPath((HibernatePersistentProperty) entity.identity, '', null) : null)
    }

    private static InheritanceType inheritanceType(GrailsHibernatePersistentEntity entity) {
        if (entity.isUnionSubclass()) {
            return InheritanceType.TABLE_PER_CLASS
        }
        return entity.isJoinedSubclass() ? InheritanceType.JOINED : InheritanceType.SINGLE_TABLE
    }

    /**
     * Mirrors {@code ConfiguredDiscriminatorBinder} and {@code DefaultDiscriminatorBinder}: a formula, else a column
     * that is named {@code class} unless configured and takes its length and SQL type from the column config.
     */
    private DiscriminatorFacets discriminatorFacets(GrailsHibernatePersistentEntity entity) {
        DiscriminatorConfig config = entity.hibernateMappedForm?.discriminator
        String typeName = config?.type == null ? 'string' :
                (config.type instanceof Class ? ((Class<?>) config.type).name : config.type.toString())
        DiscriminatorType type = discriminatorType(entity, typeName)
        boolean insertable = config?.insertable == null || config.insertable
        if (config?.formula != null) {
            return new DiscriminatorFacets(null, config.formula, typeName, type, null, null, insertable)
        }
        ColumnConfig columnConfig = config?.column
        Column column = new Column()
        columnConfigBinder.bindColumnConfigToColumn(column, columnConfig, null)
        if (column.precision != null || column.scale != null) {
            throw new UnsupportedOperationException(
                    "The discriminator column of [${entity.name}] sets a precision or a scale, " +
                            'which @DiscriminatorColumn cannot state')
        }
        return new DiscriminatorFacets(
                columnConfig?.name != null ? columnConfig.name : GrailsDomainBinder.DEFAULT_DISCRIMINATOR_COLUMN_NAME,
                null, typeName, type, column.length?.intValue(), column.sqlType, insertable)
    }

    private static DiscriminatorType discriminatorType(GrailsHibernatePersistentEntity entity, String typeName) {
        switch (typeName) {
            case 'string':
            case 'java.lang.String':
                return DiscriminatorType.STRING
            case 'integer':
            case 'int':
            case 'java.lang.Integer':
                return DiscriminatorType.INTEGER
            case 'character':
            case 'char':
            case 'java.lang.Character':
                return DiscriminatorType.CHAR
            default:
                throw new UnsupportedOperationException(
                        "The discriminator type [${typeName}] of [${entity.name}] is not one of string, integer " +
                                'or character, which the generator does not support yet')
        }
    }

    /**
     * @return whether the generator can describe the property today: a plain single-column basic property, a
     *     derived (formula) property, the tenant id (an ordinary column), the version, an enum, an embedded object whose own properties are all
     *     supported, a collection of basic values or enums, a many-to-one association, or the simple identifier. Custom types,
     *     multi-column properties and the other associations are not supported yet.
     */
    boolean supports(HibernatePersistentProperty property) {
        if (property instanceof HibernateSimpleIdentityProperty) {
            return property.hibernateOwner.isRoot()
        }
        if (property instanceof HibernateEmbeddedProperty) {
            return embeddedProblem((HibernateEmbeddedProperty) property, []) == null
        }
        if (property instanceof HibernateBasicProperty) {
            return collectionProblem((HibernateBasicProperty) property) == null
        }
        if (property instanceof HibernateToOneProperty) {
            return toOneProblem((HibernateToOneProperty) property) == null
        }
        if (property instanceof HibernateOneToManyProperty) {
            return toManyProblem((HibernateOneToManyProperty) property) == null
        }
        if (!(property instanceof HibernateSimpleProperty) && !(property instanceof HibernateTenantIdProperty)) {
            return false
        }
        PropertyConfig mappedForm = property.hibernateMappedForm
        if (mappedForm.columns != null && mappedForm.columns.size() > 1) {
            return false
        }
        if (property instanceof HibernateEnumProperty && mappedForm.derived) {
            // the enum binder never reads the formula, it always binds a column
            return false
        }
        return decideType(property).supported
    }

    /**
     * Decides the explicit Hibernate type of a supported property the way the domain binder resolves it:
     * {@code PropertyConfig.type}, else the mapping's {@code userTypes} entry for the property's class, else
     * nothing (Hibernate derives the type from the field). The resolved name is a {@code UserType} class
     * ({@code @Type}), or a type name registered with Hibernate that maps the property's own Java type
     * ({@code @JdbcTypeCode}).
     *
     * @return the facets, or {@code null} when the property has no explicit type
     * @throws UnsupportedOperationException when the type is one the generator cannot state yet
     */
    TypeFacets typeFacets(HibernatePersistentProperty property) {
        TypeDecision decision = decideType(property)
        if (!decision.supported) {
            throw new UnsupportedOperationException(typeNotSupported(property, decision.name))
        }
        return decision.facets
    }

    private static final class TypeDecision {

        final boolean supported
        final String name
        final TypeFacets facets

        TypeDecision(boolean supported, String name, TypeFacets facets) {
            this.supported = supported
            this.name = name
            this.facets = facets
        }
    }

    private TypeDecision decideType(HibernatePersistentProperty property) {
        boolean isEnum = property instanceof HibernateEnumProperty
        // the type of a collection property is the collection's; the binder types the element with the component type
        boolean element = property instanceof HibernateBasicProperty
        Class<?> type = isEnum ? ((HibernateEnumProperty) property).enumType :
                (element ? ((HibernateBasicProperty) property).componentType : property.type)
        String name = property.getTypeName(type)
        // a non-enum property is bound with its own class name when nothing says otherwise
        boolean explicit = name != null && (isEnum || type == null || name != type.name)
        Map<String, String> parameters = [:]
        if (isEnum) {
            // EnumTypeBinder replaces the configured type parameters with the enum class
            parameters[GrailsDomainBinder.ENUM_CLASS_PROP] = type.name
        } else if (!element) {
            // the binder gives a collection element its type name only, never the type parameters
            Properties typeParams = property.hibernateMappedForm.typeParams
            if (typeParams != null) {
                for (String key : new TreeSet<String>(typeParams.stringPropertyNames())) {
                    parameters[key] = typeParams.getProperty(key)
                }
            }
        }
        if (!explicit) {
            return new TypeDecision(isEnum || parameters.isEmpty(), name, null)
        }
        Class<?> named = loadClass(name, property)
        if (named != null) {
            return UserType.isAssignableFrom(named) ?
                    new TypeDecision(true, name, new TypeFacets(named, null, parameters)) :
                    new TypeDecision(false, name, null)
        }
        BasicType<?> registered = typeConfiguration.basicTypeRegistry.getRegisteredType(name)
        if (registered != null && registered.valueConverter == null && parameters.isEmpty() && !isEnum &&
                registered.javaTypeDescriptor.javaTypeClass == boxed(type)) {
            return new TypeDecision(true, name, new TypeFacets(null, registered.jdbcType.defaultSqlTypeCode, parameters))
        }
        return new TypeDecision(false, name, null)
    }

    private static Class<?> loadClass(String name, HibernatePersistentProperty property) {
        for (ClassLoader loader : [property.hibernateOwner.javaClass.classLoader, Thread.currentThread().contextClassLoader]) {
            try {
                return Class.forName(name, false, loader)
            } catch (ClassNotFoundException | LinkageError ignored) {
                // try the next loader
            }
        }
        return null
    }

    private static Class<?> boxed(Class<?> type) {
        if (type == null || !type.primitive) {
            return type
        }
        return [(int): Integer, (long): Long, (boolean): Boolean, (double): Double, (float): Float, (short): Short,
                (byte): Byte, (char): Character].get(type)
    }

    /**
     * Decides the multi-tenant filter the domain binder gives the entity, as {@code MultiTenantFilterBinder} does: only an
     * entity whose multi-tenancy is active (it has a tenant id, which GORM only sets in discriminator mode) is filtered,
     * and only a root, or a joined or table-per-class subclass that declares the tenant id itself; the single-table
     * subclasses share the root's filter. The condition compares the filter parameter with the tenant id property's
     * DEFAULT column name (the binder never uses a mapped column name there), and the parameter has the tenant id's type.
     *
     * <p>Hibernate's own {@code @TenantId} is not used: it also sets the tenant on insert and filters every query through
     * a {@code CurrentTenantIdentifierResolver}, while GORM enables the filter itself per session and lets the tenant id be
     * written and queried like any other property (plan decision on item 6a).</p>
     *
     * @return the filter, or {@code null} when the binder adds none to the entity
     * @throws UnsupportedOperationException when the tenant id has a mapped type, which the filter parameter would need too
     */
    TenantFacets tenantFacets(GrailsHibernatePersistentEntity entity) {
        HibernatePersistentProperty tenantId = entity.isMultiTenant() ? entity.hibernateTenantId : null
        if (tenantId == null || !(entity.isRoot() || (!entity.isTablePerHierarchySubclass() && !tenantId.isInherited()))) {
            return null
        }
        TypeDecision type = decideType(tenantId)
        if (!type.supported || type.facets != null) {
            throw new UnsupportedOperationException(
                    "The tenant id [${tenantId.name}] of [${entity.name}] has a mapped type, which the filter parameter " +
                            'would need too and the generator cannot state yet')
        }
        return new TenantFacets(GormProperties.TENANT_IDENTITY, entity.getMultiTenantFilterCondition(defaultColumnNames), boxed(tenantId.type))
    }

    /**
     * Decides the identifier generation of a root entity the way the domain binder does: the strategy name is the
     * one the binder asks the entity for, and the parameters are the ones it hands the generator. The generator
     * class is the one {@code GrailsSequenceGeneratorEnum.getGenerator} instantiates for the strategy.
     */
    IdFacets idFacets(GrailsHibernatePersistentEntity entity) {
        HibernatePersistentProperty identity = (HibernatePersistentProperty) entity.identity
        GrailsSequenceGeneratorEnum strategy =
                GrailsSequenceGeneratorEnum.fromName(identity.generatorName).orElse(GrailsSequenceGeneratorEnum.NATIVE)
        // BasicValueCreator: the entity's simple identity, else one built from the identifier property's type params
        HibernateSimpleIdentity mappedId = entity.hibernateIdentity instanceof HibernateSimpleIdentity ?
                (HibernateSimpleIdentity) entity.hibernateIdentity : identity.buildPropertyIdentity().orElse(null)
        Map<String, String> parameters = [:]
        Properties properties = mappedId?.properties
        if (properties != null) {
            for (String key : new TreeSet<String>(properties.stringPropertyNames())) {
                parameters[key] = properties.getProperty(key)
            }
        }
        return new IdFacets(strategy, generatorClass(strategy), parameters)
    }

    /** Mirrors the {@code switch} in {@code GrailsSequenceGeneratorEnum.getGenerator}. */
    private static Class<? extends Generator> generatorClass(GrailsSequenceGeneratorEnum strategy) {
        switch (strategy) {
            case GrailsSequenceGeneratorEnum.IDENTITY:
                return GrailsIdentityGenerator
            case GrailsSequenceGeneratorEnum.SEQUENCE:
            case GrailsSequenceGeneratorEnum.SEQUENCE_IDENTITY:
            case GrailsSequenceGeneratorEnum.HILO:
                return GrailsSequenceStyleGenerator
            case GrailsSequenceGeneratorEnum.INCREMENT:
                return GrailsIncrementGenerator
            case GrailsSequenceGeneratorEnum.UUID:
            case GrailsSequenceGeneratorEnum.UUID2:
                return HibernateUuidGenerator
            case GrailsSequenceGeneratorEnum.ASSIGNED:
                return Assigned
            case GrailsSequenceGeneratorEnum.TABLE:
            case GrailsSequenceGeneratorEnum.ENHANCED_TABLE:
                return GrailsTableGenerator
            default:
                return GrailsNativeGenerator
        }
    }

    /**
     * @return why the generator cannot describe the collection of basic values, or {@code null} when it can
     */
    private String collectionProblem(HibernateBasicProperty property) {
        PropertyConfig mapped = property.hibernateMappedForm
        CollectionKind kind = CollectionKind.of(property.type)
        if (property.type == SortedSet) {
            return 'a SortedSet: the binder names java.util.SortedSet as the collection\'s custom type, which Hibernate rejects when it boots'
        }
        if (kind == null) {
            return "the declared type [${property.type?.name}] is not one of Set, List, Collection or Map, " +
                    'the only ones the binder creates a collection for'
        }
        if (mapped.type != null) {
            return 'a type is mapped on the collection property itself, which the binder applies to the collection and its element alike'
        }
        Class<?> elementType = property.componentType
        if (elementType == null || elementType == Object) {
            return 'the element type is not known'
        }
        if (mapped.lazy == Boolean.TRUE) {
            return 'an explicit lazy: true makes the binder bind an extra-lazy collection, which Hibernate 7 annotations cannot state'
        }
        if (mapped.joinTable.keys != null && mapped.joinTable.keys.size() > 1) {
            return 'the join table has a composite key'
        }
        boolean isEnum = property instanceof HibernateEnumProperty
        if (isEnum && kind == CollectionKind.MAP) {
            return 'a map of enums: the binder types the element from the map class (java.util.Map, JAVA_OBJECT) and the schema export fails'
        }
        TypeDecision type = decideType(property)
        if (!type.supported) {
            return "the element type [${type.name}] is not a UserType or a registered type for the element class"
        }
        if (kind == CollectionKind.MAP && type.facets != null) {
            return 'a mapped element type on a map'
        }
        if (kind == CollectionKind.LIST && property.getIndexColumnType('integer') != 'integer') {
            return "the index column type [${property.getIndexColumnType('integer')}] is not integer"
        }
        if (kind == CollectionKind.MAP && property.getIndexColumnType('string') != 'string') {
            return "the map key type [${property.getIndexColumnType('string')}] is not string"
        }
        ColumnFacets key = collectionKeyFacets(property)
        if (key.length() != null || key.precision() != null || key.scale() != null || key.defaultValue() != null ||
                key.read() != null || key.write() != null || key.comment() != null) {
            return 'the column config of the collection property sets a length, a precision, a scale, a default, a read or ' +
                    'write expression or a comment, which a join column cannot state'
        }
        return null
    }

    /**
     * Decides how the binder binds a collection of basic values or enums: its table, the key column pointing at the
     * owner, the element column, the index (list) or key (map) column, and the fetching.
     *
     * @throws UnsupportedOperationException when something about the collection cannot be stated yet
     */
    CollectionFacets collectionFacets(HibernateBasicProperty property) {
        String problem = collectionProblem(property)
        if (problem != null) {
            throw new UnsupportedOperationException(unsupportedReason(property.hibernateOwner, property))
        }
        PropertyConfig mapped = property.hibernateMappedForm
        CollectionKind kind = CollectionKind.of(property.type)
        JoinTable joinTable = mapped.joinTable
        // TableForManyCalculator: the join table's own schema, else the owner's table schema; the catalog is never the owner's
        String schema = joinTable?.schema != null ? joinTable.schema : entityFacets(property.hibernateOwner).schema()
        return new CollectionFacets(
                kind,
                tableForMany.getTableName(property),
                schema,
                joinTable?.catalog,
                collectionKeyFacets(property),
                collectionElementFacets(property, kind),
                collectionIndexFacets(property, kind),
                property.isLazy(),
                FetchMode.JOIN == mapped.fetchMode ? FetchMode.JOIN : FetchMode.SELECT,
                Math.max(property.batchSize, 0),
                property.cacheUsage)
    }

    /**
     * Mirrors {@code CollectionKeyBinder}: a single join table key is a plain column named by the key; otherwise the key
     * is bound like the property's own column (name from the column config or the naming strategy, the column config's
     * facets, uniqueness). It is nullable, as {@code CollectionKeyColumnUpdater} makes it, and always updatable.
     *
     * <p>That updater also makes the key NOT updatable when the owner has more than one unidirectional to-many property,
     * which is a defect rather than a rule to copy: Hibernate's collection persister disables inserting and deleting the
     * rows of a collection whose key is not updatable, so such an owner silently loses the elements of all its
     * collections (probed: two collections on one owner saved and reloaded empty, one collection persists). Hibernate's
     * annotation binder cannot state it either, a join column must be insertable and updatable alike.</p>
     */
    private ColumnFacets collectionKeyFacets(HibernateToManyProperty property) {
        PropertyConfig mapped = property.hibernateMappedForm
        boolean updatable = true
        if (mapped.hasJoinKeyMapping()) {
            return new ColumnFacets(
                    mapped.joinTable.keys.get(0).name, true, false, true, updatable, null, null, null, null, null, null, null, null)
        }
        ColumnConfig columnConfig = firstColumnConfig(mapped)
        Column column = new Column()
        columnConfigBinder.bindColumnConfigToColumn(column, columnConfig, mapped)
        if (columnConfig != null) {
            column.comment = columnConfig.comment
            column.defaultValue = columnConfig.defaultValue
            column.customRead = columnConfig.read
            column.customWrite = columnConfig.write
        }
        return new ColumnFacets(
                columnNames.getColumnNameForPropertyAndPath(property, '', columnConfig),
                true,
                mapped.isUnique() && !mapped.isUniqueWithinGroup(),
                true,
                updatable,
                column.length?.intValue(),
                column.precision?.intValue(),
                column.scale?.intValue(),
                column.sqlType,
                column.defaultValue,
                column.customRead,
                column.customWrite,
                column.comment)
    }

    /**
     * Mirrors {@code BasicCollectionElementBinder} for a set, a bag and a list (the element is named after the property
     * and the element class, nullable, with the join table column config's facets), and {@code MapSecondPassBinder} for a
     * map (a not-null column named {@code <property>_elt} unless the join table names it).
     */
    private ColumnFacets collectionElementFacets(HibernateBasicProperty property, CollectionKind kind) {
        PropertyConfig mapped = property.hibernateMappedForm
        if (kind == CollectionKind.MAP) {
            return new ColumnFacets(
                    property.getMapElementName(namingStrategy), false, false, true, true, null, null, null, null, null, null, null, null)
        }
        if (property instanceof HibernateEnumProperty) {
            ColumnFacets facets = enumColumnFacets((HibernateEnumProperty) property, null)
            return new ColumnFacets(
                    facets.name(), facets.nullable(), facets.unique(), true, true, facets.length(), facets.precision(), facets.scale(),
                    facets.sqlType(), null, null, null, null)
        }
        Column column = new Column()
        columnConfigBinder.bindColumnConfigToColumn(column, mapped.joinTableColumnConfig, mapped)
        return new ColumnFacets(
                property.joinTableColumName(namingStrategy), true, column.unique, true, true, column.length?.intValue(),
                column.precision?.intValue(), column.scale?.intValue(), column.sqlType, null, null, null, null)
    }

    /**
     * Mirrors {@code ListSecondPassBinder} (a nullable index column named by {@code getIndexColumnName}) and
     * {@code MapSecondPassBinder} (the same name, plus the index column config's facets); {@code null} for a set or a bag.
     */
    private ColumnFacets collectionIndexFacets(HibernateToManyProperty property, CollectionKind kind) {
        if (!kind.indexed) {
            return null
        }
        String name = property.getIndexColumnName(namingStrategy)
        Column column = new Column()
        if (kind == CollectionKind.MAP && property.hibernateMappedForm.indexColumn != null) {
            columnConfigBinder.bindColumnConfigToColumn(
                    column, firstColumnConfig(property.hibernateMappedForm.indexColumn), property.hibernateMappedForm)
        }
        return new ColumnFacets(
                name, true, column.unique, true, true, column.length?.intValue(), column.precision?.intValue(),
                column.scale?.intValue(), column.sqlType, null, null, null, null)
    }

    /**
     * @return why the generator cannot describe the collection of entities, or {@code null} when it can
     */
    private String toManyProblem(HibernateOneToManyProperty property) {
        PropertyConfig mapped = property.hibernateMappedForm
        if (Map.isAssignableFrom(property.type)) {
            return 'a map of entities, which the generator does not support yet'
        }
        CollectionKind kind = CollectionKind.of(property.type)
        if (kind == null) {
            return "the declared type [${property.type?.name}] is not one of Set, SortedSet, List or Collection, " +
                    'the only ones the binder creates a collection for'
        }
        GrailsHibernatePersistentEntity target = property.hibernateAssociatedEntity
        if (target == null) {
            return 'the associated entity is unknown'
        }
        if (compositeIdentifier(target) || compositeIdentifier(property.hibernateOwner)) {
            return 'the owner or the associated entity has a composite identifier: the key and element columns have one column for ' +
                    'each identifier property, named and ordered from the identifier'
        }
        if (mapped.type != null) {
            return 'a type is mapped on the collection property itself, which the binder applies to the collection and its element alike'
        }
        if (mapped.lazy == Boolean.TRUE) {
            return 'an explicit lazy: true makes the binder bind an extra-lazy collection, which Hibernate 7 annotations cannot state'
        }
        if (property.bidirectional) {
            if (!(property.hibernateInverseSide instanceof HibernateManyToOneProperty)) {
                return "the other side [${property.hibernateInverseSide?.name}] is not a many-to-one"
            }
        } else {
            if (mapped.joinTable.keys != null && mapped.joinTable.keys.size() > 1) {
                return 'the join table has a composite key'
            }
            ColumnFacets key = collectionKeyFacets(property)
            if (key.length() != null || key.precision() != null || key.scale() != null || key.defaultValue() != null ||
                    key.read() != null || key.write() != null || key.comment() != null) {
                return 'the column config of the collection property sets a length, a precision, a scale, a default, a read or ' +
                        'write expression or a comment, which a join column cannot state'
            }
        }
        if (kind == CollectionKind.LIST && property.getIndexColumnType('integer') != 'integer') {
            return "the index column type [${property.getIndexColumnType('integer')}] is not integer"
        }
        if (property.hasSort()) {
            if (kind == CollectionKind.LIST) {
                return 'a list with a default sort: an indexed list is ordered by its index column'
            }
            HibernatePersistentProperty sortBy = target.getHibernatePropertyByName(property.sort)
            if (!(sortBy instanceof HibernateSimpleProperty) && !(sortBy instanceof HibernateEnumProperty) || isDerived(sortBy)) {
                return "the sort property [${property.sort}] of [${target.name}] is not a plain column"
            }
        }
        return null
    }

    private static boolean compositeIdentifier(GrailsHibernatePersistentEntity entity) {
        GrailsHibernatePersistentEntity root = entity.hibernateRootEntity
        return root.hibernateCompositeIdentity.isPresent() || (root.compositeIdentity?.length ?: 0) > 1
    }

    /**
     * Decides how the binder binds a collection of entities. A bidirectional collection is mapped by the foreign key of the
     * other side ({@code CollectionKeyBinder} copies that column into the key) and is inverse, except an indexed list, which
     * the binder keeps owned (its index is a column of the target's table that only the collection writes). A
     * unidirectional collection is a join table whose element is a many-to-one: the binder binds it as one whatever the
     * name says, so it is a {@code @ManyToMany}. Using {@code @OneToMany} with a join table would add a unique constraint on the
     * element column, which the binder does not.
     *
     * @throws UnsupportedOperationException when something about the collection cannot be stated yet
     */
    ToManyFacets toManyFacets(HibernateOneToManyProperty property) {
        if (toManyProblem(property) != null) {
            throw new UnsupportedOperationException(unsupportedReason(property.hibernateOwner, property))
        }
        PropertyConfig mapped = property.hibernateMappedForm
        CollectionKind kind = CollectionKind.of(property.type)
        GrailsHibernatePersistentEntity target = property.hibernateAssociatedEntity
        String mappedBy = null
        String table = null
        String schema = null
        String catalog = null
        ColumnFacets key
        ColumnFacets element = null
        if (property.shouldBindWithForeignKey()) {
            HibernateToOneProperty inverse = (HibernateToOneProperty) property.hibernateInverseSide
            // CollectionKeyBinder copies the other side's foreign key column into the key; the key updater makes it nullable
            key = new ColumnFacets(
                    toOneColumnFacets(inverse).name(), true, false, true, true, null, null, null, null, null, null, null, null)
            mappedBy = kind == CollectionKind.LIST ? null : inverse.name
        } else {
            JoinTable joinTable = mapped.joinTable
            table = tableForMany.getTableName(property)
            schema = joinTable?.schema != null ? joinTable.schema : entityFacets(property.hibernateOwner).schema()
            catalog = joinTable?.catalog
            key = collectionKeyFacets(property)
            element = new ColumnFacets(
                    property.resolveJoinTableForeignKeyColumnName(namingStrategy), true, false, true, true, null, null, null,
                    null, null, null, null, null)
        }
        String condition = target.isMultiTenant() ? target.getMultiTenantFilterCondition(defaultColumnNames) : null
        return new ToManyFacets(
                kind,
                target.name,
                mappedBy,
                table,
                schema,
                catalog,
                key,
                element,
                collectionIndexFacets(property, kind),
                property.isLazy(),
                FetchMode.JOIN == mapped.fetchMode ? FetchMode.JOIN : FetchMode.SELECT,
                Math.max(property.batchSize, 0),
                property.cacheUsage,
                cascadeFacets(property),
                property.hasSort() ? property.sort : null,
                property.hasSort() ? (property.order != null ? property.order : 'asc') : null,
                condition)
    }

    /**
     * @return why the generator cannot describe the to-one association, or {@code null} when it can
     */
    private String toOneProblem(HibernateToOneProperty property) {
        boolean inverseOneToOne = boundAsOneToOne(property)
        if (inverseOneToOne && ((HibernateOneToOneProperty) property).needsSimpleValueBinding()) {
            return 'the binder binds this one-to-one as a Hibernate OneToOne with a column of its own (constrained), ' +
                    'which the generator does not support yet'
        }
        GrailsHibernatePersistentEntity target = property.hibernateAssociatedEntity
        if (target == null) {
            return 'the associated entity is unknown'
        }
        GrailsHibernatePersistentEntity root = target.hibernateRootEntity
        if (root.hibernateCompositeIdentity.isPresent() || (root.compositeIdentity?.length ?: 0) > 1) {
            return "the associated entity [${target.name}] has a composite identifier: the foreign key has one column for each " +
                    'identifier property, named and ordered from the identifier'
        }
        PropertyConfig mapped = property.hibernateMappedForm
        if (inverseOneToOne) {
            return null
        }
        if (mapped.columns != null && mapped.columns.size() > 1) {
            return 'the mapping states several columns, which the binder binds as a composite foreign key'
        }
        if (mapped.derived) {
            return 'the association is mapped with a formula'
        }
        ColumnFacets key = toOneColumnFacets(property)
        if (key.length() != null || key.precision() != null || key.scale() != null || key.defaultValue() != null ||
                key.read() != null || key.write() != null || key.comment() != null) {
            return 'the column config sets a length, a precision, a scale, a default, a read or write expression or a comment, ' +
                    'which a join column cannot state'
        }
        return null
    }

    /**
     * The binder binds a many-to-one as a {@code ManyToOne} value, and so it does a one-to-one that is not a valid Hibernate
     * one-to-one ({@code ForeignKeyOneToOneBinder}): the foreign key is a column of the owner's table and only the
     * uniqueness differs.
     */
    private static boolean boundAsManyToOne(HibernateToOneProperty property) {
        return property instanceof HibernateManyToOneProperty ||
                (property instanceof HibernateOneToOneProperty && !((HibernateOneToOneProperty) property).isValidHibernateOneToOne())
    }

    /**
     * The binder binds a valid one-to-one ({@code OneToOneBinder}) as a Hibernate {@code OneToOne}. Its foreign key lives on
     * the other side (which is bound as a many-to-one above), so this side has no column and only names the property that
     * holds the key.
     */
    private static boolean boundAsOneToOne(HibernateToOneProperty property) {
        return property instanceof HibernateOneToOneProperty && ((HibernateOneToOneProperty) property).isValidHibernateOneToOne()
    }

    /**
     * Decides how the binder binds a to-one association whose foreign key is a column of the owner's table: the associated
     * entity, whether it is lazy, how it is fetched, whether the foreign key may be null, what happens when the row it
     * points at is missing, the cascade and the join column.
     *
     * <p>{@code ColumnBinder} makes the foreign key column nullable as {@code isAssociationColumnNullable} says (always, for
     * the associations handled here) and only then lets a subclass override it: a table-per-hierarchy subclass is always
     * nullable, any other subclass follows the property's {@code nullable}. A root entity's association with
     * {@code nullable: false} therefore keeps a nullable column, and only its {@code Property.optional} is false, which
     * annotations cannot say (a non-optional association is a NOT NULL column), so the generated field is optional.</p>
     *
     * @throws UnsupportedOperationException when something about the association cannot be stated yet
     */
    ToOneFacets toOneFacets(HibernateToOneProperty property) {
        if (toOneProblem(property) != null) {
            throw new UnsupportedOperationException(unsupportedReason(property.hibernateOwner, property))
        }
        PropertyConfig mapped = property.hibernateMappedForm
        if (boundAsOneToOne(property)) {
            HibernateOneToOneProperty oneToOne = (HibernateOneToOneProperty) property
            // OneToOneBinder never sets the value's lazy flag (it stays true) nor an ignore-not-found, and constrained is false
            return new ToOneFacets(
                    property.hibernateAssociatedEntity.name,
                    true,
                    FetchMode.JOIN == oneToOne.hibernateFetchMode ? FetchMode.JOIN : FetchMode.SELECT,
                    true,
                    false,
                    cascadeFacets(property),
                    null,
                    oneToOne.hibernateReferencedPropertyName,
                    oneToOne.hibernateReferencedEntityName)
        }
        ColumnFacets column = toOneColumnFacets(property)
        return new ToOneFacets(
                property.hibernateAssociatedEntity.name,
                property.isLazy(),
                FetchMode.JOIN == mapped.fetchMode ? FetchMode.JOIN : FetchMode.SELECT,
                column.nullable(),
                mapped.ignoreNotFound,
                cascadeFacets(property),
                column,
                null,
                null)
    }

    private ColumnFacets toOneColumnFacets(HibernateToOneProperty property) {
        PropertyConfig mapped = property.hibernateMappedForm
        ColumnConfig columnConfig = firstColumnConfig(mapped)
        Column column = new Column()
        columnConfigBinder.bindColumnConfigToColumn(column, columnConfig, mapped)
        if (columnConfig != null) {
            column.comment = columnConfig.comment
            column.defaultValue = columnConfig.defaultValue
            column.customRead = columnConfig.read
            column.customWrite = columnConfig.write
        }
        String name = columnNames.getColumnNameForPropertyAndPath(property, '', columnConfig)
        boolean nullable = property.isAssociationColumnNullable()
        if (!property.hibernateOwner.isRoot()) {
            Mapping mapping = property.hibernateOwner.hibernateMappedForm
            nullable = mapping != null && mapping.tablePerHierarchy ? true : property.nullable
        }
        boolean unique = mapped.isUnique() && !mapped.isUniqueWithinGroup()
        if (property instanceof HibernateOneToOneProperty && mapped.isUniqueWithinGroup()) {
            // ForeignKeyOneToOneBinder: a column in a unique group is unique on its own only when the other side is a valid one-to-one
            HibernateOneToOneProperty inverse = ((HibernateOneToOneProperty) property).hibernateInverseSide
            unique = property.isBidirectional() && inverse != null && inverse.isValidHibernateOneToOne()
        }
        return facets(property, column, name, nullable, unique, true)
    }

    /**
     * Decides the cascade of an association with the binder's own {@code CascadeBehaviorFetcher} (an explicit
     * {@code cascade} mapping, else the behavior implied by the kind of association and who owns it) and splits it into what
     * the annotations can say: JPA's {@code cascade}, Hibernate's {@code @Cascade} for what JPA cannot name, and
     * {@code orphanRemoval}.
     */
    CascadeFacets cascadeFacets(HibernateAssociation property) {
        CascadeBehavior behavior = CascadeBehavior.fromString(cascadeFetcher.getCascadeBehaviour((Association<?>) property))
        switch (behavior) {
            case CascadeBehavior.ALL:
                return new CascadeFacets([CascadeType.ALL], [], false)
            case CascadeBehavior.ALL_DELETE_ORPHAN:
                return new CascadeFacets([CascadeType.ALL], [], true)
            case CascadeBehavior.SAVE_UPDATE:
                return new CascadeFacets([CascadeType.PERSIST, CascadeType.MERGE], [], false)
            case CascadeBehavior.MERGE:
                return new CascadeFacets([CascadeType.MERGE], [], false)
            case CascadeBehavior.PERSIST:
                return new CascadeFacets([CascadeType.PERSIST], [], false)
            case CascadeBehavior.DELETE:
                return new CascadeFacets([CascadeType.REMOVE], [], false)
            case CascadeBehavior.EVICT:
                return new CascadeFacets([CascadeType.DETACH], [], false)
            case CascadeBehavior.LOCK:
                return new CascadeFacets([], [org.hibernate.annotations.CascadeType.LOCK], false)
            case CascadeBehavior.REPLICATE:
                return new CascadeFacets([], [org.hibernate.annotations.CascadeType.REPLICATE], false)
            default:
                return new CascadeFacets([], [], false)
        }
    }

    /**
     * Decides the column facets for a supported property by running the domain binder's own rules on a scratch
     * {@link Column}, in the order the binder applies them.
     */
    ColumnFacets columnFacets(HibernatePersistentProperty property) {
        if (property instanceof HibernateBasicProperty) {
            throw new IllegalArgumentException(
                    "Property [${property.name}] is a collection: it has a key, an element and perhaps an index column, see collectionFacets")
        }
        if (property instanceof HibernateToManyEntityProperty) {
            throw new IllegalArgumentException(
                    "Property [${property.name}] is a collection of entities: it has a key, an element and perhaps an index column, see toManyFacets")
        }
        if (property instanceof HibernateToOneProperty) {
            if (boundAsOneToOne((HibernateToOneProperty) property)) {
                throw new IllegalArgumentException(
                        "Property [${property.name}] is the inverse side of a one-to-one: it has no column, see toOneFacets")
            }
            return toOneColumnFacets((HibernateToOneProperty) property)
        }
        if (property instanceof HibernateEnumProperty) {
            return enumColumnFacets((HibernateEnumProperty) property, null)
        }
        return basicColumnFacets(property, null, null)
    }

    /**
     * Decides the columns of an embedded property for the owner that declares it, the way {@code ComponentBinder}
     * binds them: one leaf for every property of the embedded type, nested embedded types included, each named from
     * the property path and nullable when the binder makes it so. A derived (formula) leaf has no column facets.
     *
     * @return the leaves in the order the binder binds them, each with its path relative to the embedded property
     *     ({@code street}, or {@code zip.code} for a nested embedded type)
     */
    List<EmbeddedLeaf> embeddedLeaves(HibernateEmbeddedProperty property) {
        List<EmbeddedLeaf> leaves = []
        collectLeaves(property, '', '', [], leaves)
        return leaves
    }

    private void collectLeaves(
            HibernateEmbeddedProperty embedded, String binderPath, String relative,
            List<HibernateEmbeddedProperty> chain, List<EmbeddedLeaf> leaves) {
        String currentPath = binderPath.isEmpty() ? embedded.name : "${binderPath}.${embedded.name}".toString()
        List<HibernateEmbeddedProperty> enclosing = new ArrayList<HibernateEmbeddedProperty>(chain)
        enclosing << embedded
        for (HibernatePersistentProperty peer : embeddedPeers(embedded)) {
            String path = relative.isEmpty() ? peer.name : "${relative}.${peer.name}".toString()
            if (peer instanceof HibernateEmbeddedProperty) {
                collectLeaves((HibernateEmbeddedProperty) peer, currentPath, path, enclosing, leaves)
            } else if (isDerived(peer)) {
                leaves << new EmbeddedLeaf(path, peer, null)
            } else {
                leaves << new EmbeddedLeaf(path, peer, componentColumnFacets(peer, embedded, currentPath, enclosing))
            }
        }
    }

    /**
     * The facets of one column of a component: the binder's own column rules with the component path in the name and
     * the parent property's nullability, then {@code ComponentUpdater}'s rule that makes the columns of every
     * enclosing component nullable when that component is.
     */
    private ColumnFacets componentColumnFacets(
            HibernatePersistentProperty peer, HibernateEmbeddedProperty parent, String path,
            List<HibernateEmbeddedProperty> enclosing) {
        ColumnFacets facets = peer instanceof HibernateEnumProperty ?
                enumColumnFacets((HibernateEnumProperty) peer, path) : basicColumnFacets(peer, path, parent)
        boolean nullable = facets.nullable() || enclosing.any { HibernateEmbeddedProperty e ->
            e.hibernateOwner.isComponentPropertyNullable(e)
        }
        return new ColumnFacets(
                facets.name(), nullable, facets.unique(), facets.insertable(), facets.updatable(), facets.length(),
                facets.precision(), facets.scale(), facets.sqlType(), facets.defaultValue(), facets.read(),
                facets.write(), facets.comment())
    }

    /** The properties {@code ComponentBinder} binds for an embedded property: the embedded type's, minus the owner's. */
    private static List<HibernatePersistentProperty> embeddedPeers(HibernateEmbeddedProperty property) {
        GrailsHibernatePersistentEntity type = (GrailsHibernatePersistentEntity) property.associatedEntity
        return type.getHibernatePersistentProperties(property.owner.javaClass)
    }

    /**
     * @return why the generator cannot describe the embedded property, or {@code null} when it can: every property of
     *     the embedded type, nested embedded types included, must be supported, the embedded type must not extend
     *     another persistent class, and embedded types must not contain each other
     */
    private String embeddedProblem(HibernateEmbeddedProperty property, List<Class<?>> visiting) {
        GrailsHibernatePersistentEntity type = (GrailsHibernatePersistentEntity) property.associatedEntity
        if (type == null) {
            return 'the embedded type is unknown'
        }
        if (!type.isRoot()) {
            return "the embedded type [${type.name}] extends another persistent class"
        }
        if (visiting.contains(type.javaClass)) {
            return "the embedded type [${type.name}] contains itself"
        }
        List<Class<?>> path = new ArrayList<Class<?>>(visiting)
        path << type.javaClass
        for (HibernatePersistentProperty peer : embeddedPeers(property)) {
            if (peer instanceof HibernateEmbeddedProperty) {
                String problem = embeddedProblem((HibernateEmbeddedProperty) peer, path)
                if (problem != null) {
                    return problem
                }
            } else if (peer instanceof HibernateToManyProperty) {
                return "the property [${peer.name}] of [${type.name}] is a collection inside an embedded type, which the generator does not support yet"
            } else if (peer instanceof HibernateAssociation) {
                return "the property [${peer.name}] of [${type.name}] is an association inside an embedded type, which the generator does not support yet"
            } else if (!supports(peer)) {
                return unsupportedReason(type, peer)
            }
        }
        return null
    }

    /**
     * @return the enum mapping the binder chooses for the property: {@code STRING}, {@code ORDINAL} or
     *     {@code IDENTITY}
     */
    String enumStyle(HibernateEnumProperty property) {
        switch (GrailsEnumType.fromString(property.hibernateMappedForm.enumType)) {
            case GrailsEnumType.ORDINAL:
                return 'ORDINAL'
            case GrailsEnumType.IDENTITY:
                return 'IDENTITY'
            default:
                return 'STRING'
        }
    }

    private ColumnFacets basicColumnFacets(HibernatePersistentProperty property, String path, HibernatePersistentProperty parent) {
        PropertyConfig mappedForm = property.hibernateMappedForm
        ColumnConfig columnConfig = firstColumnConfig(mappedForm)

        Column column = new Column()
        columnConfigBinder.bindColumnConfigToColumn(column, columnConfig, mappedForm)
        if (columnConfig != null) {
            column.comment = columnConfig.comment
            column.defaultValue = columnConfig.defaultValue
            column.customRead = columnConfig.read
            column.customWrite = columnConfig.write
        }
        String name = columnNames.getColumnNameForPropertyAndPath(property, path, columnConfig)
        Class<?> type = property.type
        if (type != null && (String.isAssignableFrom(type) || byte[].isAssignableFrom(type))) {
            stringConstraints.bindStringColumnConstraints(column, mappedForm, property.typeName)
        } else if (type != null && Number.isAssignableFrom(type)) {
            numericConstraints.bindNumericColumnConstraints(column, columnConfig, mappedForm, type)
        }
        return facets(property, column, name, isNullable(property, parent),
                mappedForm.isUnique() && !mappedForm.isUniqueWithinGroup(), true)
    }

    /** Mirrors {@code EnumTypeBinder}: only the column config rules apply, and the column config's own uniqueness. */
    private ColumnFacets enumColumnFacets(HibernateEnumProperty property, String path) {
        PropertyConfig mappedForm = property.hibernateMappedForm
        Column column = new Column()
        ColumnConfig columnConfig = firstColumnConfig(mappedForm)
        if (columnConfig != null) {
            columnConfigBinder.bindColumnConfigToColumn(column, columnConfig, mappedForm)
        }
        String name = property.resolveEnumColumnName(namingStrategy, columnNames, path)
        return facets(property, column, name, property.isEnumColumnNullable(), column.unique, false)
    }

    private static ColumnFacets facets(
            HibernatePersistentProperty property, Column column, String name, boolean nullable, boolean unique,
            boolean withExtras) {
        PropertyConfig mappedForm = property.hibernateMappedForm
        return new ColumnFacets(
                name,
                nullable,
                unique,
                mappedForm.insertable,
                mappedForm.updatable,
                column.length?.intValue(),
                column.precision?.intValue(),
                column.scale?.intValue(),
                column.sqlType,
                withExtras ? column.defaultValue : null,
                withExtras ? column.customRead : null,
                withExtras ? column.customWrite : null,
                withExtras ? column.comment : null)
    }

    private static ColumnConfig firstColumnConfig(PropertyConfig mappedForm) {
        List<ColumnConfig> columns = mappedForm.columns
        return columns == null || columns.isEmpty() ? null : columns[0]
    }

    private List<AnnotationDescription> classAnnotations(
            GrailsHibernatePersistentEntity entity, HierarchyFacets hierarchy, boolean definesFilter) {
        EntityFacets facets = entityFacets(entity)
        List<AnnotationDescription> annotations = []
        annotations << AnnotationDescription.Builder.ofType(Entity).define('name', facets.jpaName()).build()

        if (hierarchy.ownsTable()) {
            AnnotationDescription.Builder table = AnnotationDescription.Builder.ofType(Table).define('name', facets.tableName())
            if (facets.schema()) {
                table = table.define('schema', facets.schema())
            }
            if (facets.catalog()) {
                table = table.define('catalog', facets.catalog())
            }
            annotations << table.build()
        }
        annotations.addAll(hierarchyAnnotations(entity, hierarchy))

        if (facets.dynamicInsert()) {
            annotations << AnnotationDescription.Builder.ofType(DynamicInsert).build()
        }
        if (facets.dynamicUpdate()) {
            annotations << AnnotationDescription.Builder.ofType(DynamicUpdate).build()
        }
        if (facets.batchSize() > 0) {
            annotations << AnnotationDescription.Builder.ofType(BatchSize).define('size', facets.batchSize()).build()
        }
        if (facets.comment()) {
            annotations << AnnotationDescription.Builder.ofType(Comment).define('value', facets.comment()).build()
        }
        TenantFacets tenant = tenantFacets(entity)
        if (tenant != null) {
            if (definesFilter) {
                annotations << AnnotationDescription.Builder.ofType(FilterDef)
                        .define('name', tenant.filterName())
                        .defineAnnotationArray('parameters', TypeDescription.ForLoadedType.of(ParamDef),
                                AnnotationDescription.Builder.ofType(ParamDef)
                                        .define('name', tenant.filterName())
                                        .define('type', TypeDescription.ForLoadedType.of(tenant.parameterType()))
                                        .build())
                        .build()
            }
            annotations << AnnotationDescription.Builder.ofType(Filter)
                    .define('name', tenant.filterName())
                    .define('condition', tenant.condition())
                    .build()
        }
        return annotations
    }

    private static List<AnnotationDescription> hierarchyAnnotations(GrailsHibernatePersistentEntity entity, HierarchyFacets hierarchy) {
        List<AnnotationDescription> annotations = []
        if (hierarchy.strategy() == null) {
            return annotations
        }
        if (entity.isRoot()) {
            annotations << AnnotationDescription.Builder.ofType(Inheritance).define('strategy', hierarchy.strategy()).build()
        }
        DiscriminatorFacets discriminator = hierarchy.discriminator()
        if (discriminator != null) {
            if (discriminator.formula() != null) {
                annotations << AnnotationDescription.Builder.ofType(DiscriminatorFormula)
                        .define('value', discriminator.formula())
                        .define('discriminatorType', discriminator.type())
                        .build()
            } else {
                AnnotationDescription.Builder column = AnnotationDescription.Builder.ofType(DiscriminatorColumn)
                        .define('name', discriminator.column())
                        .define('discriminatorType', discriminator.type())
                        // the binder's discriminator column has Hibernate's default length unless the mapping says otherwise
                        .define('length', discriminator.length() != null ? discriminator.length() : (int) Length.DEFAULT)
                if (discriminator.sqlType()) {
                    column = column.define('columnDefinition', discriminator.sqlType())
                }
                annotations << column.build()
            }
            if (!discriminator.insertable()) {
                annotations << AnnotationDescription.Builder.ofType(DiscriminatorOptions).define('insert', false).build()
            }
        }
        if (hierarchy.discriminatorValue() != null) {
            annotations << AnnotationDescription.Builder.ofType(DiscriminatorValue).define('value', hierarchy.discriminatorValue()).build()
        }
        if (hierarchy.keyColumn() != null) {
            annotations << AnnotationDescription.Builder.ofType(PrimaryKeyJoinColumn).define('name', hierarchy.keyColumn()).build()
        }
        return annotations
    }

    private DynamicType.Builder<Object> defineField(
            DynamicType.Builder<Object> builder, HibernatePersistentProperty property, List<AnnotationDescription> extra,
            Map<String, DynamicType.Unloaded<?>> embeddables) {
        if (property instanceof HibernateEmbeddedProperty) {
            return defineEmbeddedField(builder, (HibernateEmbeddedProperty) property, embeddables, true)
        }
        if (property instanceof HibernateBasicProperty) {
            return defineCollectionField(builder, (HibernateBasicProperty) property)
        }
        if (property instanceof HibernateToOneProperty) {
            return defineToOneField(builder, (HibernateToOneProperty) property)
        }
        if (property instanceof HibernateOneToManyProperty) {
            return defineToManyField(builder, (HibernateOneToManyProperty) property)
        }
        List<AnnotationDescription> annotations = new ArrayList<>(extra)
        if (isDerived(property)) {
            annotations << AnnotationDescription.Builder.ofType(Formula).define('value', property.hibernateMappedForm.formula).build()
        } else {
            annotations << columnAnnotation(property)
            annotations.addAll(extraColumnAnnotations(property))
        }
        // an identifier's type is decided with its generator, which the generator does not describe yet
        TypeFacets type = property instanceof HibernateSimpleIdentityProperty ? null : typeFacets(property)
        if (type != null) {
            annotations << typeAnnotation(type)
        } else if (property instanceof HibernateEnumProperty) {
            annotations << enumAnnotation((HibernateEnumProperty) property)
        }
        for (Annotation constraint : validationAnnotations(property)) {
            annotations << AnnotationDescription.ForLoadedAnnotation.of(constraint)
        }
        return builder.defineField(property.name, property.type, Visibility.PRIVATE)
                .annotateField(annotations as AnnotationDescription[])
    }

    /**
     * A many-to-one association is a field typed with the class generated for its target (which is why the target must be
     * part of the same call), with {@code @ManyToOne} stating the fetch type, the optionality and the cascade, a
     * {@code @JoinColumn}, an explicit {@code @Fetch} (without it an eager association would be a join fetch, which the
     * binder's default is not), and {@code @NotFound} when the mapping says to ignore a missing row.
     */
    private DynamicType.Builder<Object> defineToOneField(DynamicType.Builder<Object> builder, HibernateToOneProperty property) {
        ToOneFacets facets = toOneFacets(property)
        List<AnnotationDescription> annotations = []
        if (facets.mappedBy() != null) {
            annotations << AnnotationDescription.Builder.ofType(OneToOne)
                    .define('mappedBy', facets.mappedBy())
                    .define('fetch', facets.lazy() ? FetchType.LAZY : FetchType.EAGER)
                    .define('optional', facets.optional())
                    .define('orphanRemoval', facets.cascade().orphanRemoval())
                    .defineEnumerationArray('cascade', CascadeType, facets.cascade().jpa() as CascadeType[])
                    .build()
        } else {
            annotations << AnnotationDescription.Builder.ofType(ManyToOne)
                    .define('fetch', facets.lazy() ? FetchType.LAZY : FetchType.EAGER)
                    .define('optional', facets.optional())
                    .defineEnumerationArray('cascade', CascadeType, facets.cascade().jpa() as CascadeType[])
                    .build()
            annotations << joinColumnAnnotation(facets.joinColumn())
        }
        annotations << AnnotationDescription.Builder.ofType(Fetch)
                .define('value', facets.fetchMode() == FetchMode.JOIN ? AnnotationFetchMode.JOIN : AnnotationFetchMode.SELECT).build()
        if (facets.ignoreNotFound()) {
            annotations << AnnotationDescription.Builder.ofType(NotFound).define('action', NotFoundAction.IGNORE).build()
        }
        List<org.hibernate.annotations.CascadeType> hibernateCascade = new ArrayList<org.hibernate.annotations.CascadeType>(facets.cascade().hibernate())
        if (facets.cascade().orphanRemoval() && facets.mappedBy() == null) {
            // @ManyToOne has no orphanRemoval attribute
            hibernateCascade << org.hibernate.annotations.CascadeType.DELETE_ORPHAN
        }
        if (!hibernateCascade.isEmpty()) {
            annotations << AnnotationDescription.Builder.ofType(Cascade)
                    .defineEnumerationArray('value', org.hibernate.annotations.CascadeType,
                            hibernateCascade as org.hibernate.annotations.CascadeType[])
                    .build()
        }
        for (Annotation constraint : validationAnnotations(property)) {
            annotations << AnnotationDescription.ForLoadedAnnotation.of(constraint)
        }
        return builder.defineField(property.name, generatedType(property.hibernateAssociatedEntity), Visibility.PRIVATE)
                .annotateField(annotations as AnnotationDescription[])
    }

    /**
     * A collection of entities is a field whose generic signature names the generated class of its target. It is
     * {@code @OneToMany(mappedBy)} when the other side holds the foreign key, {@code @OneToMany} with a {@code @JoinColumn}
     * (and no {@code mappedBy}) for the indexed list the owner manages, and {@code @ManyToMany} with a {@code @JoinTable}
     * for a unidirectional collection.
     */
    private DynamicType.Builder<Object> defineToManyField(DynamicType.Builder<Object> builder, HibernateOneToManyProperty property) {
        ToManyFacets facets = toManyFacets(property)
        List<AnnotationDescription> annotations = []
        FetchType fetchType = facets.lazy() ? FetchType.LAZY : FetchType.EAGER
        List<org.hibernate.annotations.CascadeType> hibernateCascade = new ArrayList<org.hibernate.annotations.CascadeType>(facets.cascade().hibernate())
        if (facets.tableName() != null) {
            annotations << AnnotationDescription.Builder.ofType(ManyToMany)
                    .define('fetch', fetchType)
                    .defineEnumerationArray('cascade', CascadeType, facets.cascade().jpa() as CascadeType[])
                    .build()
            AnnotationDescription.Builder joinTable = AnnotationDescription.Builder.ofType(JpaJoinTable)
                    .define('name', facets.tableName())
                    .defineAnnotationArray('joinColumns', TypeDescription.ForLoadedType.of(JoinColumn), joinColumnAnnotation(facets.key()))
                    .defineAnnotationArray('inverseJoinColumns', TypeDescription.ForLoadedType.of(JoinColumn), joinColumnAnnotation(facets.element()))
            if (facets.schema()) {
                joinTable = joinTable.define('schema', facets.schema())
            }
            if (facets.catalog()) {
                joinTable = joinTable.define('catalog', facets.catalog())
            }
            annotations << joinTable.build()
            if (facets.cascade().orphanRemoval()) {
                // @ManyToMany has no orphanRemoval attribute
                hibernateCascade << org.hibernate.annotations.CascadeType.DELETE_ORPHAN
            }
        } else {
            AnnotationDescription.Builder oneToMany = AnnotationDescription.Builder.ofType(OneToMany)
                    .define('fetch', fetchType)
                    .define('orphanRemoval', facets.cascade().orphanRemoval())
                    .defineEnumerationArray('cascade', CascadeType, facets.cascade().jpa() as CascadeType[])
            if (facets.mappedBy() != null) {
                oneToMany = oneToMany.define('mappedBy', facets.mappedBy())
            } else {
                annotations << joinColumnAnnotation(facets.key())
            }
            annotations << oneToMany.build()
        }
        if (facets.kind() == CollectionKind.LIST) {
            annotations << AnnotationDescription.Builder.ofType(OrderColumn)
                    .define('name', facets.index().name())
                    .define('nullable', facets.index().nullable())
                    .build()
        }
        if (facets.kind() == CollectionKind.SORTED_SET) {
            // the binder marks the collection sorted and names no comparator: the elements' natural order
            annotations << AnnotationDescription.Builder.ofType(SortNatural).build()
        }
        if (facets.orderProperty() != null) {
            annotations << AnnotationDescription.Builder.ofType(OrderBy).define('value', "${facets.orderProperty()} ${facets.orderDirection()}".toString()).build()
        }
        if (!hibernateCascade.isEmpty()) {
            annotations << AnnotationDescription.Builder.ofType(Cascade)
                    .defineEnumerationArray('value', org.hibernate.annotations.CascadeType,
                            hibernateCascade as org.hibernate.annotations.CascadeType[])
                    .build()
        }
        annotations << AnnotationDescription.Builder.ofType(Fetch)
                .define('value', facets.fetchMode() == FetchMode.JOIN ? AnnotationFetchMode.JOIN : AnnotationFetchMode.SELECT).build()
        if (facets.batchSize() > 0) {
            annotations << AnnotationDescription.Builder.ofType(BatchSize).define('size', facets.batchSize()).build()
        }
        if (facets.cacheUsage() != null) {
            annotations << AnnotationDescription.Builder.ofType(Cache)
                    .define('usage', CacheConcurrencyStrategy.parse(facets.cacheUsage())).build()
        }
        if (facets.tenantCondition() != null) {
            annotations << AnnotationDescription.Builder.ofType(Filter)
                    .define('name', GormProperties.TENANT_IDENTITY)
                    .define('condition', facets.tenantCondition())
                    .build()
        }
        for (Annotation constraint : validationAnnotations(property)) {
            annotations << AnnotationDescription.ForLoadedAnnotation.of(constraint)
        }
        TypeDescription.Generic fieldType = TypeDescription.Generic.Builder.parameterizedType(
                TypeDescription.ForLoadedType.of(facets.kind().javaType),
                [generatedType(property.hibernateAssociatedEntity)] as List<TypeDescription>).build()
        return builder.defineField(property.name, fieldType, Visibility.PRIVATE)
                .annotateField(annotations as AnnotationDescription[])
    }

    /**
     * The type of a field that refers to another generated entity. It is only a name: the class itself is built in the same
     * call and loaded together with its referrers, so entities may refer to each other in any shape, cycles included.
     */
    private static TypeDescription generatedType(GrailsHibernatePersistentEntity entity) {
        return new GeneratedType(generatedClassName(entity))
    }

    /**
     * A top-level class that is only named. ByteBuddy's own latent description refuses to say that it has no declaring or
     * enclosing type, which it asks for when it writes a field of that type.
     */
    private static final class GeneratedType extends TypeDescription.Latent {

        GeneratedType(String name) {
            super(name, Modifier.PUBLIC, TypeDescription.Generic.OBJECT)
        }

        @Override
        TypeDescription getDeclaringType() {
            return null
        }

        @Override
        TypeDescription getEnclosingType() {
            return null
        }

        @Override
        MethodDescription.InDefinedShape getEnclosingMethod() {
            return null
        }

        @Override
        TypeList getDeclaredTypes() {
            return new TypeList.Empty()
        }
    }

    /**
     * A collection of basic values or enums is an {@code @ElementCollection} field whose generic signature names the
     * element (and the {@code String} key of a map), with its table, key, element, index and fetching stated by the
     * annotations the facets describe.
     */
    private DynamicType.Builder<Object> defineCollectionField(DynamicType.Builder<Object> builder, HibernateBasicProperty property) {
        CollectionFacets facets = collectionFacets(property)
        List<AnnotationDescription> annotations = []
        annotations << AnnotationDescription.Builder.ofType(ElementCollection)
                .define('fetch', facets.lazy() ? FetchType.LAZY : FetchType.EAGER).build()
        AnnotationDescription.Builder table = AnnotationDescription.Builder.ofType(CollectionTable)
                .define('name', facets.tableName())
                .defineAnnotationArray('joinColumns', TypeDescription.ForLoadedType.of(JoinColumn), joinColumnAnnotation(facets.key()))
        if (facets.schema()) {
            table = table.define('schema', facets.schema())
        }
        if (facets.catalog()) {
            table = table.define('catalog', facets.catalog())
        }
        annotations << table.build()
        annotations << columnAnnotation(facets.element())
        if (facets.kind() == CollectionKind.LIST) {
            annotations << AnnotationDescription.Builder.ofType(OrderColumn)
                    .define('name', facets.index().name())
                    .define('nullable', facets.index().nullable())
                    .build()
        } else if (facets.kind() == CollectionKind.MAP) {
            annotations << mapKeyColumnAnnotation(facets.index())
        }
        TypeFacets type = typeFacets(property)
        if (type != null) {
            annotations << typeAnnotation(type)
        } else if (property instanceof HibernateEnumProperty) {
            annotations << enumAnnotation((HibernateEnumProperty) property)
        }
        annotations << AnnotationDescription.Builder.ofType(Fetch)
                .define('value', facets.fetchMode() == FetchMode.JOIN ? AnnotationFetchMode.JOIN : AnnotationFetchMode.SELECT).build()
        if (facets.batchSize() > 0) {
            annotations << AnnotationDescription.Builder.ofType(BatchSize).define('size', facets.batchSize()).build()
        }
        if (facets.cacheUsage() != null) {
            annotations << AnnotationDescription.Builder.ofType(Cache)
                    .define('usage', CacheConcurrencyStrategy.parse(facets.cacheUsage())).build()
        }
        for (Annotation constraint : validationAnnotations(property)) {
            annotations << AnnotationDescription.ForLoadedAnnotation.of(constraint)
        }
        Class<?> elementClass = property instanceof HibernateEnumProperty ?
                ((HibernateEnumProperty) property).enumType : ((HibernateBasicProperty) property).componentType
        TypeDescription.Generic fieldType = facets.kind() == CollectionKind.MAP ?
                TypeDescription.Generic.Builder.parameterizedType(Map, String, elementClass).build() :
                TypeDescription.Generic.Builder.parameterizedType(facets.kind().javaType, elementClass).build()
        return builder.defineField(property.name, fieldType, Visibility.PRIVATE)
                .annotateField(annotations as AnnotationDescription[])
    }

    private static AnnotationDescription joinColumnAnnotation(ColumnFacets facets) {
        AnnotationDescription.Builder annotation = AnnotationDescription.Builder.ofType(JoinColumn)
                .define('name', facets.name())
                .define('nullable', facets.nullable())
                .define('unique', facets.unique())
                .define('insertable', facets.insertable())
                .define('updatable', facets.updatable())
        if (facets.sqlType()) {
            annotation = annotation.define('columnDefinition', facets.sqlType())
        }
        return annotation.build()
    }

    private static AnnotationDescription mapKeyColumnAnnotation(ColumnFacets facets) {
        AnnotationDescription.Builder annotation = AnnotationDescription.Builder.ofType(MapKeyColumn)
                .define('name', facets.name())
                .define('nullable', facets.nullable())
                .define('unique', facets.unique())
                .define('insertable', facets.insertable())
                .define('updatable', facets.updatable())
        if (facets.length() != null) {
            annotation = annotation.define('length', facets.length())
        }
        if (facets.precision() != null) {
            annotation = annotation.define('precision', facets.precision())
        }
        if (facets.scale() != null) {
            annotation = annotation.define('scale', facets.scale())
        }
        if (facets.sqlType()) {
            annotation = annotation.define('columnDefinition', facets.sqlType())
        }
        return annotation.build()
    }

    /**
     * An embedded property is an {@code @Embedded} field of the generated embeddable type. The owner states every
     * column of the embedded type with {@code @AttributeOverride}, because the names and the nullability depend on the
     * owner (path prefix, parent property, table-per-hierarchy subclass); a nested embedded field inside the
     * embeddable states none, the owner's overrides reach it by their dotted path.
     */
    private DynamicType.Builder<Object> defineEmbeddedField(
            DynamicType.Builder<Object> builder, HibernateEmbeddedProperty property,
            Map<String, DynamicType.Unloaded<?>> embeddables, boolean withOverrides) {
        TypeDescription embeddable = embeddable(property, embeddables)
        List<AnnotationDescription> annotations = [AnnotationDescription.Builder.ofType(Embedded).build()]
        if (withOverrides) {
            List<AnnotationDescription> overrides = embeddedLeaves(property)
                    .findAll { EmbeddedLeaf leaf -> leaf.column() != null }
                    .collect { EmbeddedLeaf leaf ->
                        AnnotationDescription.Builder.ofType(AttributeOverride)
                                .define('name', leaf.path())
                                .define('column', columnAnnotation(leaf.column()))
                                .build()
                    }
            if (!overrides.isEmpty()) {
                annotations << AnnotationDescription.Builder.ofType(AttributeOverrides)
                        .defineAnnotationArray('value', TypeDescription.ForLoadedType.of(AttributeOverride),
                                overrides as AnnotationDescription[])
                        .build()
            }
        }
        return builder.defineField(property.name, embeddable, Visibility.PRIVATE)
                .annotateField(annotations as AnnotationDescription[])
    }

    /**
     * The generated {@code @Embeddable} for an embedded property, shared by every owner that embeds the same type with
     * the same properties. Its fields carry the properties' own facets (no path, no parent); the owners' overrides
     * replace the columns.
     */
    private TypeDescription embeddable(HibernateEmbeddedProperty property, Map<String, DynamicType.Unloaded<?>> embeddables) {
        GrailsHibernatePersistentEntity type = (GrailsHibernatePersistentEntity) property.associatedEntity
        List<HibernatePersistentProperty> peers = embeddedPeers(property)
        String key = [type.javaClass.name, peers*.name.join(',')].join('|')
        DynamicType.Unloaded<?> existing = embeddables.get(key)
        if (existing != null) {
            return existing.typeDescription
        }
        String base = generatedEmbeddableName(type)
        String name = base
        int variant = 1
        while (embeddables.values().any { DynamicType.Unloaded<?> other -> other.typeDescription.name == name }) {
            name = base + '_' + (++variant)
        }
        DynamicType.Builder<Object> builder = (DynamicType.Builder<Object>) new ByteBuddy()
                .subclass(Object)
                .name(name)
                .annotateType(AnnotationDescription.Builder.ofType(Embeddable).build())
        for (HibernatePersistentProperty peer : peers) {
            builder = peer instanceof HibernateEmbeddedProperty ?
                    defineEmbeddedField(builder, (HibernateEmbeddedProperty) peer, embeddables, false) :
                    defineField(builder, peer, [], embeddables)
        }
        DynamicType.Unloaded<?> unloaded = builder.make()
        embeddables.put(key, unloaded)
        return unloaded.typeDescription
    }

    /**
     * An assigned identifier is Hibernate's default for an {@code @Id} with no generator annotation, and the
     * {@code uuid} strategies are Hibernate's own {@code UuidGenerator}, so those are stated with plain Hibernate
     * annotations. Every other strategy is a GORM generator and is carried by {@link GrailsIdGenerator}.
     */
    private static List<AnnotationDescription> idGeneratorAnnotations(IdFacets facets) {
        if (facets.generatorClass() == Assigned) {
            return []
        }
        if (facets.generatorClass() == HibernateUuidGenerator) {
            return [AnnotationDescription.Builder.ofType(UuidGenerator).build()]
        }
        List<AnnotationDescription> parameters = facets.parameters().collect { String name, String value ->
            AnnotationDescription.Builder.ofType(Parameter).define('name', name).define('value', value).build()
        }
        return [AnnotationDescription.Builder.ofType(GrailsIdGenerator)
                .define('strategy', facets.strategy().name)
                .defineAnnotationArray('parameters', TypeDescription.ForLoadedType.of(Parameter),
                        parameters as AnnotationDescription[])
                .build()]
    }

    private static AnnotationDescription typeAnnotation(TypeFacets facets) {
        if (facets.jdbcTypeCode() != null) {
            return AnnotationDescription.Builder.ofType(JdbcTypeCode).define('value', facets.jdbcTypeCode().intValue()).build()
        }
        List<AnnotationDescription> parameters = facets.parameters().collect { String name, String value ->
            AnnotationDescription.Builder.ofType(Parameter).define('name', name).define('value', value).build()
        }
        return AnnotationDescription.Builder.ofType(Type)
                .define('value', TypeDescription.ForLoadedType.of(facets.userType()))
                .defineAnnotationArray('parameters', TypeDescription.ForLoadedType.of(Parameter),
                        parameters as AnnotationDescription[])
                .build()
    }

    private AnnotationDescription enumAnnotation(HibernateEnumProperty property) {
        String style = enumStyle(property)
        if (style == 'IDENTITY') {
            return AnnotationDescription.Builder.ofType(Type)
                    .define('value', TypeDescription.ForLoadedType.of(IdentityEnumType))
                    .defineAnnotationArray('parameters', TypeDescription.ForLoadedType.of(Parameter),
                            AnnotationDescription.Builder.ofType(Parameter)
                                    .define('name', 'enumClass')
                                    .define('value', property.enumType.name)
                                    .build())
                    .build()
        }
        return AnnotationDescription.Builder.ofType(Enumerated).define('value', EnumType.valueOf(style)).build()
    }

    private AnnotationDescription columnAnnotation(HibernatePersistentProperty property) {
        return columnAnnotation(columnFacets(property))
    }

    private static AnnotationDescription columnAnnotation(ColumnFacets facets) {
        AnnotationDescription.Builder annotation = AnnotationDescription.Builder.ofType(JpaColumn)
                .define('name', facets.name())
                .define('nullable', facets.nullable())
                .define('unique', facets.unique())
                .define('insertable', facets.insertable())
                .define('updatable', facets.updatable())
        if (facets.length() != null) {
            annotation = annotation.define('length', facets.length())
        }
        if (facets.precision() != null) {
            annotation = annotation.define('precision', facets.precision())
        }
        if (facets.scale() != null) {
            annotation = annotation.define('scale', facets.scale())
        }
        if (facets.sqlType()) {
            annotation = annotation.define('columnDefinition', facets.sqlType())
        }
        return annotation.build()
    }

    private List<AnnotationDescription> extraColumnAnnotations(HibernatePersistentProperty property) {
        ColumnFacets facets = columnFacets(property)
        List<AnnotationDescription> extras = []
        if (facets.defaultValue() != null) {
            extras << AnnotationDescription.Builder.ofType(ColumnDefault).define('value', facets.defaultValue()).build()
        }
        if (facets.read() != null || facets.write() != null) {
            AnnotationDescription.Builder transformer = AnnotationDescription.Builder.ofType(ColumnTransformer)
            if (facets.read() != null) {
                transformer = transformer.define('read', facets.read())
            }
            if (facets.write() != null) {
                transformer = transformer.define('write', facets.write())
            }
            extras << transformer.build()
        }
        if (facets.comment() != null) {
            extras << AnnotationDescription.Builder.ofType(Comment).define('value', facets.comment()).build()
        }
        return extras
    }

    /**
     * The Bean Validation constraints declared on the property. Hibernate turns them into DDL (not null, precision,
     * scale, length) after binding, so they are copied onto the generated field for Hibernate to apply itself.
     */
    List<Annotation> validationAnnotations(HibernatePersistentProperty property) {
        Class<?> owner = property.hibernateOwner.javaClass
        List<Annotation> found = []
        try {
            found.addAll(owner.getDeclaredField(property.name).declaredAnnotations as List<Annotation>)
        } catch (NoSuchFieldException ignored) {
            // the property has no backing field of its own
        }
        String accessor = 'get' + property.name.capitalize()
        for (Method method : owner.declaredMethods) {
            if (method.name == accessor && method.parameterCount == 0) {
                found.addAll(method.declaredAnnotations as List<Annotation>)
            }
        }
        return found.findAll { Annotation a ->
            String type = a.annotationType().name
            type.startsWith('jakarta.validation.constraints.') || type.startsWith('org.hibernate.validator.constraints.')
        }
    }

    /**
     * @return whether the binder binds the property as a Hibernate {@code Formula} with no column, which is what
     *     {@code SimpleValueBinder} does for every derived property except an enum and the tenant id (which stays a
     *     column)
     */
    boolean isDerived(HibernatePersistentProperty property) {
        return property.hibernateMappedForm.derived && !(property instanceof HibernateEnumProperty) &&
                !(property instanceof HibernateTenantIdProperty)
    }

    private static boolean isNullable(HibernatePersistentProperty property, HibernatePersistentProperty parent) {
        if (property instanceof HibernateSimpleIdentityProperty) {
            return false
        }
        if (!property.hibernateOwner.isRoot()) {
            Mapping mapping = property.hibernateOwner.hibernateMappedForm
            return mapping != null && mapping.tablePerHierarchy ? true : property.nullable
        }
        return property.nullable || (parent != null && parent.nullable)
    }
}
