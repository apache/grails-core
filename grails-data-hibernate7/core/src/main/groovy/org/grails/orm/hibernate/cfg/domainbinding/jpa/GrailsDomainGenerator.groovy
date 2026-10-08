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
import java.sql.Types

import groovy.transform.CompileStatic
import jakarta.persistence.AssociationOverride
import jakarta.persistence.AssociationOverrides
import jakarta.persistence.AttributeConverter
import jakarta.persistence.AttributeOverride
import jakarta.persistence.AttributeOverrides
import jakarta.persistence.Basic
import jakarta.persistence.Cacheable
import jakarta.persistence.CascadeType
import jakarta.persistence.CollectionTable
import jakarta.persistence.Column as JpaColumn
import jakarta.persistence.Convert
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
import jakarta.persistence.IdClass
import jakarta.persistence.Index
import jakarta.persistence.Inheritance
import jakarta.persistence.InheritanceType
import jakarta.persistence.JoinColumn
import jakarta.persistence.JoinColumns
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
import jakarta.persistence.UniqueConstraint
import jakarta.persistence.Version
import net.bytebuddy.ByteBuddy
import net.bytebuddy.description.annotation.AnnotationDescription
import net.bytebuddy.description.method.MethodDescription
import net.bytebuddy.description.modifier.TypeManifestation
import net.bytebuddy.description.modifier.Visibility
import net.bytebuddy.description.type.TypeDefinition
import net.bytebuddy.description.type.TypeDescription
import net.bytebuddy.description.type.TypeList
import net.bytebuddy.dynamic.DynamicType
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy
import org.hibernate.FetchMode
import org.hibernate.Length
import org.hibernate.engine.jdbc.Size
import org.hibernate.annotations.BatchSize
import org.hibernate.annotations.Cache
import org.hibernate.annotations.CacheConcurrencyStrategy
import org.hibernate.annotations.Cascade
import org.hibernate.annotations.CollectionType
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
import org.hibernate.annotations.Immutable
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.annotations.ListIndexJavaType
import org.hibernate.annotations.ListIndexJdbcTypeCode
import org.hibernate.annotations.MapKeyJdbcTypeCode
import org.hibernate.annotations.NaturalId as HibernateNaturalId
import org.hibernate.annotations.NotFound
import org.hibernate.annotations.NotFoundAction
import org.hibernate.annotations.OptimisticLockType
import org.hibernate.annotations.OptimisticLocking
import org.hibernate.annotations.ParamDef
import org.hibernate.annotations.Parameter
import org.hibernate.annotations.SortNatural
import org.hibernate.annotations.Type
import org.hibernate.annotations.UuidGenerator
import org.hibernate.id.uuid.UuidGenerator as HibernateUuidGenerator
import org.hibernate.mapping.Column
import org.hibernate.mapping.PrimaryKey
import org.hibernate.generator.Assigned
import org.hibernate.generator.Generator
import org.hibernate.type.BasicType
import org.hibernate.type.spi.TypeConfiguration
import org.hibernate.usertype.UserCollectionType
import org.hibernate.usertype.UserType

import org.springframework.core.GenericTypeResolver
import org.springframework.util.ClassUtils

import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.datastore.mapping.model.config.GormProperties
import org.grails.datastore.mapping.model.types.Association
import org.grails.orm.hibernate.cfg.CacheConfig
import org.grails.orm.hibernate.cfg.ColumnConfig
import org.grails.orm.hibernate.cfg.DiscriminatorConfig
import org.grails.orm.hibernate.cfg.HibernateSimpleIdentity
import org.grails.orm.hibernate.cfg.IdentityEnumType
import org.grails.orm.hibernate.cfg.JoinTable
import org.grails.orm.hibernate.cfg.Mapping
import org.grails.orm.hibernate.cfg.NaturalId
import org.grails.orm.hibernate.cfg.PersistentEntityNamingStrategy
import org.grails.orm.hibernate.cfg.PropertyConfig
import org.grails.orm.hibernate.cfg.domainbinding.binder.ColumnConfigToColumnBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.GrailsDomainBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.IndexBinder
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
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateCustomProperty
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateEmbeddedCollectionProperty
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateEmbeddedProperty
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateEnumProperty
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateManyToManyProperty
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
import org.grails.orm.hibernate.cfg.domainbinding.util.CreateKeyForProps
import org.grails.orm.hibernate.cfg.domainbinding.util.DefaultColumnNameFetcher
import org.grails.orm.hibernate.cfg.domainbinding.util.ForeignKeyColumnCountCalculator
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

    private static final List<String> VALIDATION_PACKAGES = ['jakarta.validation.constraints.', 'org.hibernate.validator.constraints.']
    private static final List<String> AUDIT_PACKAGES = ['org.hibernate.envers.']

    private final PersistentEntityNamingStrategy namingStrategy
    private final ColumnNameForPropertyAndPathFetcher columnNames
    private final ColumnConfigToColumnBinder columnConfigBinder
    private final StringColumnConstraintsBinder stringConstraints
    private final NumericColumnConstraintsBinder numericConstraints
    private final TypeConfiguration typeConfiguration
    private final TableForManyCalculator tableForMany
    private final DefaultColumnNameFetcher defaultColumnNames
    private final CascadeBehaviorFetcher cascadeFetcher = new CascadeBehaviorFetcher()
    private final IndexBinder indexBinder = new IndexBinder()
    private final CreateKeyForProps keyForProps
    private final ForeignKeyColumnCountCalculator foreignKeyColumnCount = new ForeignKeyColumnCountCalculator()

    /** The collections of embedded types that more than one embedded property reaches, as {@code <embedded class>#<collection>}. */
    private Set<String> sharedEmbeddedCollections = new HashSet<String>()

    /** For each such collection, the qualifier of the embedded properties that do not keep the binder's names, by {@link #embeddedUseKey}. */
    private Map<String, String> embeddedCollectionQualifiers = new HashMap<String, String>()

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
        this.keyForProps = new CreateKeyForProps(columnNames)
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
        assignEmbeddedCollectionQualifiers(given)
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
        Map<TypeDescription, Class<?>> loaded = ClassLoadingStrategy.Default.CHILD_FIRST.load(parent, types)
        Map<GrailsHibernatePersistentEntity, Class<?>> result = [:]
        for (GrailsHibernatePersistentEntity entity : given) {
            result.put(entity, loaded.get(made.get(entity).typeDescription))
        }
        return result
    }

    /**
     * The binder names the table and the key column of a collection inside an embedded type after the embedded type and not after
     * the owner, and gives it no owner-specific identity, so two embedded properties that share a type with a collection share one
     * table whose key is a foreign key to the owner that was bound first (probed: the rows of the other owner violate it unless the
     * identifiers happen to coincide), and Hibernate refuses the duplicate collection at boot when the two properties have the same name
     * (pinned in {@code GrailsDomainBinderEmbeddedCollectionDefectSpec}).
     *
     * <p>Every embedded property gets a collection table of its own. The first one, in the order of the entity name and then the
     * property path, keeps the names the binder gives the shared table, so an existing database is undisturbed; the others get names
     * qualified with the table of their owner and the path of their embedded property ({@link #embeddedCollectionQualifier}).</p>
     */
    private void assignEmbeddedCollectionQualifiers(Collection<GrailsHibernatePersistentEntity> entities) {
        Map<String, List<EmbeddedUse>> uses = new LinkedHashMap<String, List<EmbeddedUse>>()
        for (GrailsHibernatePersistentEntity entity : entities) {
            for (HibernatePersistentProperty property : entity.persistentPropertiesToBind) {
                if (isComponent(property)) {
                    collectCollectionUses(entity, (HibernateEmbeddedProperty) property, property.name, uses, [])
                }
            }
        }
        Set<String> shared = new HashSet<String>()
        Map<String, String> qualifiers = new HashMap<String, String>()
        for (Map.Entry<String, List<EmbeddedUse>> entry : uses.entrySet()) {
            if (entry.value.size() < 2) {
                continue
            }
            shared << entry.key
            List<EmbeddedUse> ordered = entry.value.sort(false) { EmbeddedUse a, EmbeddedUse b ->
                a.owner.name <=> b.owner.name ?: a.path <=> b.path
            }
            for (EmbeddedUse use : ordered.drop(1)) {
                qualifiers.put(embeddedUseKey(use.owner, use.path, use.collection), embeddedQualifier(use))
            }
        }
        sharedEmbeddedCollections = shared
        embeddedCollectionQualifiers = qualifiers
    }

    private String embeddedQualifier(EmbeddedUse use) {
        List<String> segments = [use.owner.getTableName(namingStrategy).replace('`', '')]
        segments.addAll(use.path.split(/\./).collect { String name -> namingStrategy.resolveColumnName(name).replace('`', '') })
        return segments.join('_')
    }

    private static String embeddedUseKey(GrailsHibernatePersistentEntity owner, String path, String collection) {
        return "${owner.name}|${path}|${collection}".toString()
    }

    /**
     * @return the qualifier that the collection of an embedded type gets in the embedded property {@code path} of {@code owner} (the
     *     dotted names of the embedded properties from the owner down to the embedded type that holds the collection), or {@code null}
     *     when it keeps the names the binder gives it: it is reached through that one embedded property only, or that property is the
     *     first of those that reach it. Known after {@link #generateAll} has run.
     */
    String embeddedCollectionQualifier(GrailsHibernatePersistentEntity owner, String path, String collection) {
        return embeddedCollectionQualifiers.get(embeddedUseKey(owner, path, collection))
    }

    private static void collectCollectionUses(
            GrailsHibernatePersistentEntity owner, HibernateEmbeddedProperty property, String path,
            Map<String, List<EmbeddedUse>> uses, List<Class<?>> visiting) {
        GrailsHibernatePersistentEntity type = (GrailsHibernatePersistentEntity) property.associatedEntity
        if (type == null || visiting.contains(type.javaClass)) {
            return
        }
        for (HibernatePersistentProperty peer : embeddedPeers(property)) {
            if (isComponent(peer)) {
                collectCollectionUses(owner, (HibernateEmbeddedProperty) peer, "${path}.${peer.name}".toString(), uses, visiting + [type.javaClass])
            } else if (peer instanceof HibernateToManyProperty) {
                uses.computeIfAbsent(sharedKey(type, peer.name)) { String key -> new ArrayList<EmbeddedUse>() }
                        .add(new EmbeddedUse(owner, path, peer.name))
            }
        }
    }

    private static String sharedKey(GrailsHibernatePersistentEntity type, String collection) {
        return "${type.javaClass.name}#${collection}".toString()
    }

    private static record EmbeddedUse(GrailsHibernatePersistentEntity owner, String path, String collection) { }

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
        collectReferencedEntities(entity.persistentPropertiesToBind, found, [])
        if (entity.isRoot() && compositeIdentifier(entity)) {
            collectReferencedEntities(entity.compositeIdentity.toList(), found, [])
        }
        return found
    }

    private static void collectReferencedEntities(
            List<HibernatePersistentProperty> properties, List<GrailsHibernatePersistentEntity> found, List<Class<?>> visiting) {
        for (HibernatePersistentProperty property : properties) {
            if (property instanceof HibernateToOneProperty || property instanceof HibernateToManyEntityProperty) {
                GrailsHibernatePersistentEntity target = ((HibernateAssociation) property).hibernateAssociatedEntity
                if (target != null && !found.any { GrailsHibernatePersistentEntity other -> other.javaClass == target.javaClass }) {
                    found << target
                }
            } else if (isComponent(property)) {
                // the generated embeddable types its association fields with the generated target classes
                GrailsHibernatePersistentEntity type = (GrailsHibernatePersistentEntity) ((HibernateEmbeddedProperty) property).associatedEntity
                if (type != null && !visiting.contains(type.javaClass)) {
                    collectReferencedEntities(embeddedPeers((HibernateEmbeddedProperty) property), found, visiting + [type.javaClass])
                }
            }
        }
    }

    /**
     * @return why {@link #generateAll} cannot describe the entity, or {@code null} when it can: a hierarchy that mixes
     *     strategies, a tenant id with a mapped type, an entity with no simple identifier, or a property the generator
     *     does not support. It does not look at the other entities of the call.
     */
    String generationProblem(GrailsHibernatePersistentEntity entity) {
        if (!entity.isRoot() && compositeIdentifier(entity.hibernateRootEntity) && entity.isJoinedSubclass()) {
            return "Entity [${entity.name}] is a joined subclass of [${entity.hibernateRootEntity.name}], which has a composite identifier: " +
                    'the key of a joined subclass table copies the key of the root, which the binder binds with one column, so the ' +
                    'application does not start with the binder either'
        }
        try {
            hierarchyFacets(entity)
            tenantFacets(entity)
        } catch (UnsupportedOperationException e) {
            return e.message
        }
        if (entity.isRoot() && !(entity.identity instanceof HibernateSimpleIdentityProperty)) {
            String compositeProblem = compositeIdProblem(entity)
            if (compositeProblem != null) {
                return compositeProblem
            }
        }
        String naturalIdProblem = naturalIdProblem(entity)
        if (naturalIdProblem != null) {
            return naturalIdProblem
        }
        CacheFacets cache = cacheFacets(entity)
        if (cache != null && CacheConcurrencyStrategy.parse(cache.usage()) == null) {
            return "Entity [${entity.name}] states the cache usage [${cache.usage()}], which is none of read-only, read-write, " +
                    'nonstrict-read-write and transactional, the strategies @Cache can state'
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
        DynamicType.Builder<Object> result
        if (entity.identity instanceof HibernateSimpleIdentityProperty) {
            HibernatePersistentProperty identity = (HibernatePersistentProperty) entity.identity
            List<AnnotationDescription> idAnnotations = [AnnotationDescription.Builder.ofType(Id).build()]
            idAnnotations.addAll(idGeneratorAnnotations(idFacets(entity)))
            result = defineField(builder, identity, idAnnotations, embeddables)
        } else {
            result = defineCompositeIdentity(builder, entity, embeddables)
        }
        HibernatePersistentProperty version = entity.version
        if (version != null) {
            result = defineField(result, version, [AnnotationDescription.Builder.ofType(Version).build()], embeddables)
        }
        return result
    }

    /**
     * Describes the composite identifier of a root entity as {@code CompositeIdBinder} binds it: one identifier component with a
     * property for every part, in the order the mapping names them. The columns of a part are the ones the part would have as an
     * ordinary property, except that they are never null: the binder's component is the primary key, and Hibernate makes every
     * column of a primary key not null.
     *
     * <p>The parts are written onto the generated entity itself, each as an {@code @Id} field, and the entity names a generated
     * key class with {@code @IdClass}: Hibernate then binds a non-aggregated identifier, which has no identifier property of its
     * own, as the binder's component has none, and GORM's identifier (the entity instance itself) fits it.</p>
     */
    CompositeIdFacets compositeIdFacets(GrailsHibernatePersistentEntity entity) {
        List<EmbeddedLeaf> parts = entity.compositeIdentity.collect { HibernatePersistentProperty part ->
            part instanceof HibernateToOneProperty ?
                    new EmbeddedLeaf(part.name, part, toOneColumnFacets((HibernateToOneProperty) part), toOneFacets((HibernateToOneProperty) part)) :
                    new EmbeddedLeaf(part.name, part, columnFacets(part), null)
        }
        return new CompositeIdFacets(parts)
    }

    /**
     * @return whether the property is one of the parts of the composite identifier of its entity, which the binder binds into the
     *     identifier component instead of binding it as a property of the entity
     */
    private static boolean isCompositeIdPart(HibernatePersistentProperty property) {
        GrailsHibernatePersistentEntity owner = property.hibernateOwner
        return owner != null && owner.isRoot() && compositeIdentifier(owner) &&
                owner.compositeIdentity.any { HibernatePersistentProperty part -> part.name == property.name }
    }

    /**
     * @return why the generator cannot describe the composite identifier of the root entity, or {@code null} when it can: every
     *     part must be a simple property or an enum that the generator supports, or a many-to-one to an entity with a simple
     *     identifier (a part that refers to an entity with a composite identifier makes the foreign key columns depend on the
     *     order {@code Component.sortProperties} gave the referenced key), and the entity must have no subclasses
     */
    private String compositeIdProblem(GrailsHibernatePersistentEntity entity, Set<String> visiting = new HashSet<String>()) {
        if (!visiting.add(entity.name)) {
            return "Entity [${entity.name}] has a composite identifier that refers to itself"
        }
        if (!compositeIdentifier(entity)) {
            return "Entity [${entity.name}] has no identifier the generator knows"
        }
        if (entity.childEntities.any { GrailsHibernatePersistentEntity child -> child.isJoinedSubclass() }) {
            return "Entity [${entity.name}] has a composite identifier and a joined subclass: the key of a joined subclass table copies " +
                    'the key of the root, which the binder binds with one column, so the application does not start with the binder either'
        }
        for (HibernatePersistentProperty part : entity.compositeIdentity) {
            if (part instanceof HibernateToOneProperty) {
                HibernateToOneProperty toOne = (HibernateToOneProperty) part
                if (!boundAsManyToOne(toOne)) {
                    return "Composite identifier part [${part.name}] of [${entity.name}] is a one-to-one the binder binds as a Hibernate " +
                            'OneToOne, which it cannot do for a part of an identifier: it fails with an IndexOutOfBoundsException'
                }
                GrailsHibernatePersistentEntity partTarget = toOne.hibernateAssociatedEntity?.hibernateRootEntity
                if (partTarget != null && compositeIdentifier(partTarget)) {
                    String partTargetProblem = compositeIdProblem(partTarget, visiting)
                    if (partTargetProblem != null) {
                        return "Composite identifier part [${part.name}] of [${entity.name}] refers to [${partTarget.name}], which has a " +
                                "composite identifier the generator cannot describe: ${partTargetProblem}"
                    }
                }
                String problem = toOneProblem(toOne)
                if (problem != null) {
                    return "Composite identifier part [${part.name}] of [${entity.name}]: ${problem}"
                }
            } else if (!(part instanceof HibernateSimpleProperty) || isDerived(part)) {
                return "Composite identifier part [${part.name}] of [${entity.name}] is a ${part.getClass().simpleName}, which the " +
                        'generator does not support yet (the binder boots an embedded object as a part of the identifier, and Hibernate ' +
                        'cannot state one in an identifier class)'
            } else if (!supports(part)) {
                return "Composite identifier part [${part.name}] of [${entity.name}]: ${unsupportedReason(entity, part)}"
            }
        }
        return null
    }

    private DynamicType.Builder<Object> defineCompositeIdentity(
            DynamicType.Builder<Object> builder, GrailsHibernatePersistentEntity entity,
            Map<String, DynamicType.Unloaded<?>> embeddables) {
        CompositeIdFacets id = compositeIdFacets(entity)
        DynamicType.Builder<Object> key = (DynamicType.Builder<Object>) new ByteBuddy()
                .subclass(Object)
                .implement(Serializable)
                .name(generatedSupportName(entity) + '_Id')
        for (EmbeddedLeaf part : id.parts()) {
            // the key class only names the parts and their types; the entity carries their mapping. A part that refers to an entity
            // has that entity's generated class as its type, which Hibernate binds as an association inside the identifier
            TypeDescription type = part.property() instanceof HibernateToOneProperty ?
                    generatedType(((HibernateToOneProperty) part.property()).hibernateAssociatedEntity) :
                    TypeDescription.ForLoadedType.of(part.property().type)
            key = key.defineField(part.path(), type, Visibility.PRIVATE)
        }
        DynamicType.Unloaded<?> unloaded = key.make()
        embeddables.put('id|' + entity.name, unloaded)
        DynamicType.Builder<Object> result = builder.annotateType(
                AnnotationDescription.Builder.ofType(IdClass).define('value', unloaded.typeDescription).build())
        List<AnnotationDescription> idAnnotation = [AnnotationDescription.Builder.ofType(Id).build()]
        for (EmbeddedLeaf part : id.parts()) {
            result = defineField(result, part.property(), idAnnotation, embeddables)
        }
        return result
    }

    /**
     * @return why {@link #generate} rejects the property, as the {@link UnsupportedOperationException} message names it
     */
    String unsupportedReason(GrailsHibernatePersistentEntity entity, HibernatePersistentProperty property) {
        if (isComponent(property)) {
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
        if (property instanceof HibernateToManyEntityProperty) {
            String problem = toManyProblem((HibernateToManyEntityProperty) property)
            if (problem != null) {
                return "Association property [${property.name}] of [${entity.name}]: ${problem}"
            }
        }
        if ((property instanceof HibernateSimpleProperty || property instanceof HibernateTenantIdProperty ||
                isCustomProperty(property) || isEmbeddedValue(property)) && !decideType(property).supported) {
            return typeNotSupported(property, decideType(property))
        }
        PropertyConfig stated = property.hibernateMappedForm
        if (stated != null && stated.columns != null && stated.columns.size() > 1) {
            return "Property [${property.name}] of [${entity.name}] is mapped with ${stated.columns.size()} columns, which the binder " +
                    "cannot bind either: it fails with 'maps to ${stated.columns.size()} columns but 1 columns are required'"
        }
        return "Property [${property.name}] of [${entity.name}] is a ${property.getClass().simpleName}, " +
                'which the generator does not support yet'
    }

    private static String typeNotSupported(HibernatePersistentProperty property, TypeDecision decision) {
        return "Type [${decision.name}] of property [${property.name}] of [${property.hibernateOwner.name}] ${decision.problem}"
    }

    /**
     * The name of the class generated for an entity: the name of the domain class itself. The generated class lives in a class
     * loader of its own that answers for the name before the application's does (see {@link #generateAll}), so Hibernate's
     * entity name, which is the name of the annotated class, is the name of the domain class: statistics, entity graphs,
     * collection roles, cache regions and messages all use it.
     */
    static String generatedClassName(GrailsHibernatePersistentEntity entity) {
        return entity.javaClass.name
    }

    /** The name of a generated class that is not an entity (an identifier class, an embeddable): it must not shadow a real class. */
    static String generatedSupportName(GrailsHibernatePersistentEntity entity) {
        return GENERATED_PACKAGE + '.' + entity.javaClass.name.replace('.', '_')
    }

    /** The name of the {@code @Embeddable} generated for an embedded type; distinct from an entity's generated name. */
    static String generatedEmbeddableName(GrailsHibernatePersistentEntity type) {
        return generatedSupportName(type) + '_Embeddable'
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
                sharesTable ? null : entity.comment,
                entity.hibernateRootEntity.version != null)
    }

    /**
     * Decides the second-level cache of a root entity as {@code RootPersistentClassCommonValuesBinder} configures it: only a
     * cache that the mapping enables, only on the root (the cache of a subclass is not read, and Hibernate refuses
     * {@code @Cache} on one), with the usage of the mapping, lazy properties included unless the mapping says {@code non-lazy},
     * and the class immutable for the {@code read-only} usage.
     *
     * @return the facets, or {@code null} when the entity is not a root or its mapping enables no cache
     */
    CacheFacets cacheFacets(GrailsHibernatePersistentEntity entity) {
        CacheConfig cache = entity.isRoot() ? entity.hibernateMappedForm?.cache : null
        if (cache == null || !cache.enabled) {
            return null
        }
        String usage = cache.usage.toString()
        return new CacheFacets(usage, !'non-lazy'.equalsIgnoreCase(cache.include.toString()), !'read-only'.equalsIgnoreCase(usage))
    }

    /**
     * Decides the natural identifier the domain binder binds for the entity, as {@code NaturalIdentifierBinder} reads it from the
     * mapped identity: the properties it names and whether it is mutable. The binder makes each of those properties updatable
     * exactly when the natural id is mutable, and adds one unique key over their columns to the table of the entity (the table of the
     * hierarchy for a subclass of a single-table hierarchy). A name that is no property of the entity or of its superclasses is
     * skipped without a word, so it is not part of the facets either; a mapping whose names are all skipped has no natural id.
     * Hibernate's annotation binder does the rest for {@code @NaturalId} on a root, with a unique key of its own name; a subclass
     * cannot state it, and the aligner binds the key and the flags of a subclass.
     *
     * @return the facets, or {@code null} when the mapping names no natural identifier
     */
    NaturalIdFacets naturalIdFacets(GrailsHibernatePersistentEntity entity) {
        NaturalId natural = entity.hibernateMappedForm?.identity?.natural
        if (natural == null || natural.propertyNames == null || natural.propertyNames.isEmpty()) {
            return null
        }
        List<String> known = natural.propertyNames.findAll { String name ->
            PersistentProperty<?> found = entity.getPropertyByName(name)
            found instanceof HibernatePersistentProperty && entity.identity?.name != name
        }
        return known.isEmpty() ? null : new NaturalIdFacets(new ArrayList<String>(known), natural.mutable)
    }

    /**
     * @return why the generator cannot describe the natural identifier of the entity, or {@code null} when it can: every property it
     *     names must be a plain column, an enum, a foreign key or an embedded type of the entity
     */
    private String naturalIdProblem(GrailsHibernatePersistentEntity entity) {
        NaturalIdFacets natural = naturalIdFacets(entity)
        if (natural == null) {
            return null
        }
        for (String name : natural.propertyNames()) {
            HibernatePersistentProperty property = (HibernatePersistentProperty) entity.getPropertyByName(name)
            if (property instanceof HibernateBasicProperty ||
                    property instanceof HibernateToManyEntityProperty || isDerived(property) ||
                    (property instanceof HibernateToOneProperty && boundAsOneToOne((HibernateToOneProperty) property))) {
                return "The natural id of [${entity.name}] names [${name}], which is ${property.getClass().simpleName}: " +
                        'only a simple property, an enum, a foreign key or an embedded type can be part of a natural id, and the binder ' +
                        'cannot bind a collection (ClassCastException) or a formula (constraint involves a formula) either'
            }
        }
        return null
    }

    /**
     * @return whether the natural identifier the binder gives the property makes it updatable: the natural id of its entity or of a
     *     subclass of it, which may name a property it inherits; {@code null} when no natural id names it
     */
    private Boolean naturalIdUpdatable(HibernatePersistentProperty property) {
        Deque<GrailsHibernatePersistentEntity> pending = new ArrayDeque<GrailsHibernatePersistentEntity>()
        pending.add(property.hibernateOwner)
        while (!pending.isEmpty()) {
            GrailsHibernatePersistentEntity entity = pending.poll()
            NaturalIdFacets natural = naturalIdFacets(entity)
            if (natural != null && natural.propertyNames().contains(property.name)) {
                return natural.mutable()
            }
            pending.addAll(entity.childEntities)
        }
        return null
    }

    /** @return whether the property is part of the natural identifier of its entity, and so whether that is mutable; {@code null} when it is not part of it */
    private Boolean naturalIdMutable(HibernatePersistentProperty property) {
        GrailsHibernatePersistentEntity owner = property.hibernateOwner
        // a subclass cannot state @NaturalId: the aligner binds the natural id of a subclass
        NaturalIdFacets natural = owner.isRoot() ? naturalIdFacets(owner) : null
        return natural != null && natural.propertyNames().contains(property.name) ? natural.mutable() : null
    }

    /**
     * Decides the indexes and multi-column unique keys the domain binder puts on the table the entity owns. The binder's own
     * {@code IndexBinder} and {@code CreateKeyForProps} run, on a scratch table with the entity's table name, for every column
     * the binder passes through {@code ColumnBinder} (a simple property, the identifier, the version, a to-one foreign key, each
     * leaf of an embedded type) or {@code EnumTypeBinder} (an enum, which only gets an index from the binder and a unique group
     * from the generator), in the order the binder binds them, so the names ({@code <table>_<column>_idx}, {@code UK} and a hash) and the column order are the binder's. The
     * single-table subclasses of a hierarchy share the table of the root, so their columns are part of the root's constraints.
     *
     * @param entity an entity that owns its table: a root, or a joined or table-per-class subclass
     */
    ConstraintFacets constraintFacets(GrailsHibernatePersistentEntity entity) {
        org.hibernate.mapping.Table table = new org.hibernate.mapping.Table('grails', entityFacets(entity).tableName().replace('`', ''))
        List<GrailsHibernatePersistentEntity> contributors = [entity]
        collectSharingSubclasses(entity, contributors)
        Set<String> unbound = new HashSet<String>()
        for (GrailsHibernatePersistentEntity contributor : contributors) {
            List<ConstraintSite> identity = []
            List<ConstraintSite> version = []
            List<ConstraintSite> properties = []
            collectConstraintSites(contributor, identity, version, properties)
            applyConstraintSites(table, identity + version, unbound)
            if (contributor.isRoot()) {
                // RootPersistentClassCommonValuesBinder creates the primary key after the identifier and the version, and
                // Hibernate then drops a unique key that repeats exactly the columns of the primary key
                PrimaryKey primaryKey = new PrimaryKey(table)
                identity.each { ConstraintSite site -> primaryKey.addColumn(new Column(site.columnName)) }
                table.primaryKey = primaryKey
            }
            applyConstraintSites(table, properties, unbound)
        }
        return new ConstraintFacets(
                table.indexes.values().collect { org.hibernate.mapping.Index index ->
                    new IndexFacets(index.name, index.columns*.name)
                },
                table.uniqueKeys.values().collect { org.hibernate.mapping.UniqueKey key ->
                    new UniqueKeyFacets(key.name, key.columns*.name, !unbound.contains(key.name))
                },
                table.primaryKey?.orderingUniqueKey?.columns*.name)
    }

    private void applyConstraintSites(org.hibernate.mapping.Table table, List<ConstraintSite> sites, Set<String> unbound) {
        for (ConstraintSite site : sites) {
            Column column = new Column(site.columnName)
            indexBinder.bindIndex(site.columnName, column, site.columnConfig, table)
            Set<String> before = new HashSet<String>(table.uniqueKeys.keySet())
            keyForProps.createKeyForProps(site.property, site.path, table, site.columnName)
            if (site.enumeration) {
                // EnumTypeBinder never calls CreateKeyForProps: the key is the mapping's, not the binder's
                unbound.addAll(table.uniqueKeys.keySet() - before)
            }
        }
    }

    private static void collectSharingSubclasses(GrailsHibernatePersistentEntity entity, List<GrailsHibernatePersistentEntity> into) {
        for (GrailsHibernatePersistentEntity child : entity.childEntities) {
            if (child.isTablePerHierarchySubclass()) {
                into << child
                collectSharingSubclasses(child, into)
            }
        }
    }

    private void collectConstraintSites(
            GrailsHibernatePersistentEntity contributor, List<ConstraintSite> identity, List<ConstraintSite> version,
            List<ConstraintSite> properties) {
        if (contributor.isRoot()) {
            if (contributor.identity instanceof HibernateSimpleIdentityProperty) {
                collectConstraintSites((HibernatePersistentProperty) contributor.identity, '', identity)
            } else if (compositeIdentifier(contributor)) {
                // CompositeIdBinder binds the parts of the identifier one after the other, like any property
                for (HibernatePersistentProperty part : contributor.compositeIdentity) {
                    collectConstraintSites(part, '', identity)
                }
            }
            if (contributor.version != null) {
                collectConstraintSites(contributor.version, '', version)
            }
        }
        for (HibernatePersistentProperty property : contributor.persistentPropertiesToBind) {
            collectConstraintSites(property, '', properties)
        }
    }

    private void collectConstraintSites(HibernatePersistentProperty property, String path, List<ConstraintSite> sites) {
        if (isComponent(property)) {
            HibernateEmbeddedProperty embedded = (HibernateEmbeddedProperty) property
            String current = path.isEmpty() ? embedded.name : "${path}.${embedded.name}".toString()
            for (HibernatePersistentProperty peer : embeddedPeers(embedded)) {
                collectConstraintSites(peer, current, sites)
            }
        } else if ((property instanceof HibernateBasicProperty && !boundAsColumn(property)) || property instanceof HibernateToManyEntityProperty ||
                isDerived(property) || (property instanceof HibernateToOneProperty && boundAsOneToOne((HibernateToOneProperty) property))) {
            return
        } else {
            ColumnConfig columnConfig = firstColumnConfig(property.hibernateMappedForm)
            boolean enumeration = property instanceof HibernateEnumProperty && !boundAsColumn(property)
            String name = enumeration ?
                    ((HibernateEnumProperty) property).resolveEnumColumnName(namingStrategy, columnNames, path) :
                    columnNames.getColumnNameForPropertyAndPath(property, path, columnConfig)
            sites << new ConstraintSite(property, path, name, columnConfig, enumeration)
        }
    }

    /** One column the binder passes through {@code ColumnBinder} or {@code EnumTypeBinder}: where its index and unique group come from. */
    private static final class ConstraintSite {

        final HibernatePersistentProperty property
        final String path
        final String columnName
        final ColumnConfig columnConfig
        final boolean enumeration

        ConstraintSite(
                HibernatePersistentProperty property, String path, String columnName, ColumnConfig columnConfig, boolean enumeration) {
            this.property = property
            this.path = path
            this.columnName = columnName
            this.columnConfig = columnConfig
            this.enumeration = enumeration
        }
    }

    /**
     * Decides where the entity sits in its inheritance hierarchy the way the domain binder does: the strategy is the
     * one the entity's own accessors report ({@code isUnionSubclass}, {@code isJoinedSubclass}, else single table),
     * the discriminator is the one {@code DiscriminatorPropertyBinder} binds on the root of a single-table
     * hierarchy that has subclasses, a subclass's discriminator value is the entity's own
     * {@code getDiscriminatorValue}, and a joined subclass's key column is named like its identifier column.
     *
     * @throws UnsupportedOperationException for a hierarchy that mixes strategies (annotations state the strategy
     *     once, on the root)
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
                            'a hierarchy that mixes inheritance strategies is not supported, and the binder fails on it with a NullPointerException')
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
            return new DiscriminatorFacets(null, config.formula, typeName, type, null, null, insertable, null, null)
        }
        ColumnConfig columnConfig = config?.column
        Column column = new Column()
        columnConfigBinder.bindColumnConfigToColumn(column, columnConfig, null)
        return new DiscriminatorFacets(
                columnConfig?.name != null ? columnConfig.name : GrailsDomainBinder.DEFAULT_DISCRIMINATOR_COLUMN_NAME,
                null, typeName, type, column.length?.intValue(), column.sqlType, insertable,
                column.precision?.intValue(), column.scale?.intValue())
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
                // @DiscriminatorColumn names string, integer and character only: any other type is put on the discriminator of the model
                // after binding (GeneratedDomainClassBinder), as the binder gives it its type name, so the annotation only needs a type
                return DiscriminatorType.STRING
        }
    }

    /**
     * @return whether the generator can describe the property today: a plain single-column basic property, a
     *     derived (formula) property, the tenant id (an ordinary column), the version, an enum, an embedded object whose own properties are all
     *     supported, a collection of basic values or enums, a many-to-one association, the simple identifier, or a property of a type
     *     GORM does not know ({@link HibernateCustomProperty}). Multi-column properties are not supported.
     */
    boolean supports(HibernatePersistentProperty property) {
        if (property instanceof HibernateSimpleIdentityProperty) {
            return property.hibernateOwner.isRoot() && decideType(property).supported
        }
        if (isComponent(property)) {
            return embeddedProblem((HibernateEmbeddedProperty) property, []) == null
        }
        if (property instanceof HibernateBasicProperty) {
            return collectionProblem((HibernateBasicProperty) property) == null
        }
        if (property instanceof HibernateToOneProperty) {
            return toOneProblem((HibernateToOneProperty) property) == null
        }
        if (property instanceof HibernateToManyEntityProperty) {
            return toManyProblem((HibernateToManyEntityProperty) property) == null
        }
        if (!(property instanceof HibernateSimpleProperty) && !(property instanceof HibernateTenantIdProperty) &&
                !isCustomProperty(property) && !isEmbeddedValue(property)) {
            return false
        }
        PropertyConfig mappedForm = property.hibernateMappedForm
        if (mappedForm.columns != null && mappedForm.columns.size() > 1) {
            return false
        }
        TypeDecision decision = decideType(property)
        // the binder binds a custom property like a simple one, from the type its mapping names; with none, Hibernate infers the type from
        // the field as the binder's resolution does (a serializable class is stored as bytes)
        return decision.supported
    }

    /**
     * @return whether the property is an embedded object the binder binds as a component. An embedded property that the mapping gives a
     *     {@code UserType} is bound as one simple value of that type and not as a component, and is described like any other column.
     */
    private static boolean isComponent(HibernatePersistentProperty property) {
        return property instanceof HibernateEmbeddedProperty && !((HibernateEmbeddedProperty) property).isUserButNotCollectionType()
    }

    /** @return whether the property is an embedded property the binder binds as one simple value of a {@code UserType}, see {@link #isComponent} */
    private static boolean isEmbeddedValue(HibernatePersistentProperty property) {
        return property instanceof HibernateEmbeddedProperty && !isComponent(property)
    }

    /**
     * @return whether the property is of a type GORM does not know and that is not an enum: the binder binds it like a simple property
     */
    private static boolean isCustomProperty(HibernatePersistentProperty property) {
        return property instanceof HibernateCustomProperty && !(property instanceof HibernateEnumProperty)
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
            throw new UnsupportedOperationException(typeNotSupported(property, decision))
        }
        return decision.facets
    }

    private static final class TypeDecision {

        final boolean supported
        final String name
        final TypeFacets facets
        final String problem

        TypeDecision(boolean supported, String name, TypeFacets facets, String problem = null) {
            this.supported = supported
            this.name = name
            this.facets = facets
            this.problem = problem
        }
    }

    private TypeDecision decideType(HibernatePersistentProperty property) {
        // a collection stored in one column is typed as a column of its own Java type, which is the collection
        boolean column = boundAsColumn(property)
        boolean isEnum = property instanceof HibernateEnumProperty && !column
        // the type of a collection property is the collection's; the binder types the element with the component type
        boolean element = property instanceof HibernateBasicProperty && !column
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
            // type parameters with no type to give them to: the binder hands them to a built-in type, which ignores them
            return new TypeDecision(true, name, null)
        }
        if (column && property.isSerializableType()) {
            return serializedCollectionType()
        }
        Class<?> named = loadClass(name, property)
        if (named != null) {
            if (UserType.isAssignableFrom(named)) {
                return new TypeDecision(true, name, new TypeFacets(named, null, parameters))
            }
            if (AttributeConverter.isAssignableFrom(named) && !isEnum) {
                return convertedType(name, named, type)
            }
            // the binder resolves a class name that is no UserType or converter through the types registered under Java class names
            // (java.util.Date is the timestamp type), so the name is looked up as a registered type below
        }
        BasicType<?> registered = typeConfiguration.basicTypeRegistry.getRegisteredType(name)
        boolean identity = property instanceof HibernateSimpleIdentityProperty
        if (registered != null && registered.valueConverter instanceof AttributeConverter && !isEnum && !identity &&
                !element && registered.javaTypeDescriptor.javaTypeClass == boxed(type)) {
            // a registered type that converts its value (yes_no, true_false, numeric_boolean) is the JPA converter it wraps
            // the binder hands the type parameters to a registered type, which ignores them
            return new TypeDecision(true, name, new TypeFacets(
                    null, registered.jdbcType.defaultSqlTypeCode, [:], null, registered.valueConverter.getClass()))
        }
        if (registered != null && registered.valueConverter == null && !isEnum) {
            // the binder resolves the name and not the property's class: the field has the registered Java type, whatever the
            // property's class is (a String or a byte[] for serializable, an Integer for string), with the registered JDBC type
            Class<?> registeredJava = registered.javaTypeDescriptor.javaTypeClass
            return new TypeDecision(true, name, new TypeFacets(
                    null, registered.jdbcType.defaultSqlTypeCode, [:], registeredJava == boxed(type) ? null : registeredJava))
        }
        String problem
        if (registered == null) {
            problem = 'is neither a UserType class nor a type registered with Hibernate'
        } else if (isEnum) {
            problem = 'is a registered type on an enum: the binder boots it but cannot store a single row (the enum value cannot be cast to the ' +
                    'type), so the application would fail on its first save'
        } else {
            problem = 'is a registered type that converts its value in a way @Convert does not state (not a JPA attribute converter, ' +
                    'or the type of a collection element, an identifier or an enum)'
        }
        return new TypeDecision(false, name, null, problem)
    }

    /**
     * {@code serializable} on a collection stored in one column: the collection is serialized to bytes, the type Hibernate registers
     * under that name for {@code Serializable}. A field declared {@code Serializable} (the property of the real class is read and
     * written by its own accessors, whatever the field says) has that Java type, and the JDBC type is the binary one.
     */
    private static TypeDecision serializedCollectionType() {
        return new TypeDecision(true, 'serializable', new TypeFacets(null, Types.VARBINARY, [:], Serializable))
    }

    /**
     * A {@code type} naming an {@code AttributeConverter}: the binder resolves the name to the converter and binds the property with it,
     * whatever the property's class is. {@code @Convert} states it, on a field of the Java type the converter converts when the
     * property's class is not one the converter accepts (the binder does not check, Hibernate does).
     */
    private static TypeDecision convertedType(String name, Class<?> converter, Class<?> type) {
        Class<?>[] arguments = GenericTypeResolver.resolveTypeArguments(converter, AttributeConverter)
        Class<?> domain = arguments != null ? arguments[0] : null
        Class<?> javaType = domain != null && (type == null || !domain.isAssignableFrom(boxed(type))) ? domain : null
        return new TypeDecision(true, name, new TypeFacets(null, null, [:], javaType, converter))
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
     * @throws UnsupportedOperationException when the type of the tenant id is one the generator cannot state
     */
    TenantFacets tenantFacets(GrailsHibernatePersistentEntity entity) {
        HibernatePersistentProperty tenantId = entity.isMultiTenant() ? entity.hibernateTenantId : null
        if (tenantId == null || !(entity.isRoot() || (!entity.isTablePerHierarchySubclass() && !tenantId.isInherited()))) {
            return null
        }
        TypeDecision type = decideType(tenantId)
        if (!type.supported) {
            throw new UnsupportedOperationException(typeNotSupported(tenantId, type))
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

    private static final String SEVERAL_KEY_COLUMNS = 'the mapping states several columns for the key of a collection of an entity with a ' +
            'simple identifier: the binder gives the key as many columns as the mapping states and Hibernate refuses the foreign key ' +
            "('Foreign key must have the same number of columns as the referenced primary key'), so the application does not start"

    /**
     * @return why the generator cannot describe the collection of basic values, or {@code null} when it can
     */
    private String collectionProblem(HibernateBasicProperty property) {
        PropertyConfig mapped = property.hibernateMappedForm
        if (boundAsColumn(property)) {
            return columnCollectionProblem(property)
        }
        CollectionKind kind = CollectionKind.of(property.type)
        if (property.type == SortedSet) {
            return 'a SortedSet: the binder names java.util.SortedSet as the collection\'s custom type, which Hibernate rejects when it boots'
        }
        if (kind == null) {
            return "the declared type [${property.type?.name}] is not one of Set, List, Collection or Map, " +
                    'the only ones the binder creates a collection for'
        }
        if (mapped.type != null) {
            return mappedCollectionTypeProblem(property)
        }
        Class<?> elementType = property.componentType
        if (elementType == null || elementType == Object) {
            return 'the element type is not known'
        }
        if (compositeIdentifier(property.hibernateOwner)) {
            String compositeProblem = compositeOwnerKeyProblem(property)
            if (compositeProblem != null) {
                return compositeProblem
            }
        } else if (mapped.columns != null && mapped.columns.size() > 1) {
            return SEVERAL_KEY_COLUMNS
        }
        boolean isEnum = property instanceof HibernateEnumProperty
        if (isEnum && kind == CollectionKind.MAP) {
            return 'a map of enums: the binder types the element from the map class (java.util.Map, JAVA_OBJECT) and the schema export fails'
        }
        TypeDecision type = decideType(property)
        if (!type.supported) {
            return "the element type [${type.name}] ${type.problem}"
        }
        if (kind == CollectionKind.MAP && type.facets != null) {
            return 'a mapped element type on a map'
        }
        String indexTypeProblem = collectionIndexTypeProblem(property, kind)
        if (indexTypeProblem != null) {
            return indexTypeProblem
        }
        return null
    }

    /**
     * @return why the generator cannot describe the collection whose owner or associated entity has a composite identifier, or
     *     {@code null} when it can: the key columns of a collection of the owner are one for each identifier property
     *     ({@code DependentKeyValueBinder}), and a collection mapped by the foreign key of the other side copies the columns of
     *     that foreign key. A many-to-many, a map and a join table to an entity with a composite identifier are not described:
     *     the binder names the element columns from the column configs of the collection property, which the key reads too
     *     (the key of such a collection gets the element's columns).
     */
    private String compositeCollectionProblem(HibernateToManyEntityProperty property, GrailsHibernatePersistentEntity target) {
        for (GrailsHibernatePersistentEntity entity : [property.hibernateOwner, target]) {
            if (compositeIdentifier(entity)) {
                String problem = compositeIdProblem(entity.hibernateRootEntity)
                if (problem != null) {
                    return "the entity [${entity.name}] has a composite identifier the generator cannot describe: ${problem}"
                }
            }
        }
        if (compositeIdentifier(target) && !property.shouldBindWithForeignKey() && !(property instanceof HibernateManyToManyProperty)) {
            return 'the associated entity has a composite identifier and the collection is bound through a join table: the binder gives ' +
                    'the element columns from the column configs of the collection property, which it reads again for the key, so the ' +
                    'key gets the element\'s columns'
        }
        if (compositeIdentifier(property.hibernateOwner) && !property.shouldBindWithForeignKey()) {
            return compositeOwnerKeyProblem(property)
        }
        return null
    }

    /** @return why the key of a collection of an entity with a composite identifier cannot be described, or {@code null} when it can */
    private String compositeOwnerKeyProblem(HibernateToManyProperty property) {
        GrailsHibernatePersistentEntity owner = property.hibernateOwner
        if (property.hibernateMappedForm.hasJoinKeyMapping()) {
            return 'the join table maps its key columns and the owner has a composite identifier: the binder binds the key from the ' +
                    'column configs of the collection property and ignores them'
        }
        return compositeForeignKeyProblem(property, owner)
    }

    /**
     * The binder binds the key column of a collection like any other column ({@code DependentKeyValueBinder} runs {@code ColumnBinder}
     * on the collection property), so an {@code index:} on the property becomes an index of the collection table over the key column,
     * named as the mapping says ({@code IndexBinder}), and for a collection of enums the element column is indexed too, before the key.
     * A {@code unique} group on the property would name columns of the owner's table, which the collection table does not have: the
     * binder creates a key over columns that do not exist (a defect fixed on the 8.0.x line), so the generator states none.
     *
     * <p>The {@code index:} of a closure is not an index: it configures the index column of a list or a map, and the name the binder
     * gives the index is the closure's own ({@code toString()}, which holds the identity of the closure instance and differs on every
     * boot), so none is stated for it.</p>
     *
     * @return the indexes of the collection table in the order the binder creates them
     */
    private List<IndexFacets> collectionTableIndexes(
            HibernateToManyProperty property, String tableName, List<ColumnFacets> keys, ColumnFacets element) {
        ColumnConfig columnConfig = firstColumnConfig(property.hibernateMappedForm)
        if (tableName == null || columnConfig?.index == null || columnConfig.index instanceof Closure) {
            return []
        }
        org.hibernate.mapping.Table table = new org.hibernate.mapping.Table('grails', tableName.replace('`', ''))
        if (property instanceof HibernateEnumProperty) {
            indexBinder.bindIndex(element.name(), new Column(element.name()), columnConfig, table)
        }
        if (bindsCollectionKey(property)) {
            GrailsHibernatePersistentEntity owner = property.hibernateOwner
            List<ColumnConfig> configs = compositeIdentifier(owner) ? compositeForeignKeyConfigs(property, owner) : [columnConfig]
            for (int i = 0; i < keys.size() && i < configs.size(); i++) {
                indexBinder.bindIndex(keys[i].name(), new Column(keys[i].name()), configs[i], table)
            }
        }
        return table.indexes.values().collect { org.hibernate.mapping.Index index -> new IndexFacets(index.name, index.columns*.name) }
    }

    /** Whether the binder binds the key column of the collection like a column of the mapping (and so indexes it). */
    private static boolean bindsCollectionKey(HibernateToManyProperty property) {
        return property.isBidirectional() ?
                (property.hibernateInverseSide instanceof HibernateManyToManyProperty || Map.isAssignableFrom(property.type)) :
                !property.hibernateMappedForm.hasJoinKeyMapping()
    }

    /**
     * The binder binds a collection property as one column of the owner's table, typed with the mapping's {@code type}, when the type
     * is a class that is not a collection type (a {@code UserType}, for example) or {@code serializable}
     * ({@code GrailsPropertyBinder}): it is no collection then, and has the index, the unique group and the column settings of any
     * other column. Any other {@code type} stays a type of the collection, which no collection of values accepts (see
     * {@link #mappedCollectionTypeProblem}).
     */
    private static boolean boundAsColumn(HibernatePersistentProperty property) {
        return property instanceof HibernateBasicProperty && (property.isUserButNotCollectionType() || property.isSerializableType())
    }

    /**
     * @return why the generator cannot describe the collection of values stored in one column ({@link #boundAsColumn}), or {@code null}
     *     when it can: it is described like the column of a simple property, typed with the mapping's type
     */
    private String columnCollectionProblem(HibernateBasicProperty property) {
        PropertyConfig mapped = property.hibernateMappedForm
        if (mapped.columns != null && mapped.columns.size() > 1) {
            return 'the mapping states several columns for a collection stored in one column'
        }
        TypeDecision type = decideType(property)
        if (!type.supported) {
            return "the type [${type.name}] ${type.problem}"
        }
        return null
    }

    /**
     * A {@code type} on a collection property that is neither a class nor {@code serializable}: the binder gives the name to the
     * element of the collection as a class name and fails to boot ({@code Could not load requested class: text}, probed for the
     * registered names {@code string}, {@code text}, {@code materialized_clob} and {@code yes_no}), and so does a custom collection
     * type, which it gives to the element as its type.
     */
    private static String mappedCollectionTypeProblem(HibernateBasicProperty property) {
        Class<?> userType = property.userType
        if (userType != null && UserCollectionType.isAssignableFrom(userType)) {
            return 'a custom collection type (a UserCollectionType) is mapped on a collection of basic values: the binder gives the class to ' +
                    'the element as its type and fails to boot (Named type did not implement BasicType nor UserType)'
        }
        return "the type [${property.hibernateMappedForm.typeName}] mapped on the collection property names neither a class nor " +
                "serializable: the binder gives the name to the element as a class name and fails to boot ('Could not load requested class')"
    }

    /**
     * Decides how the binder binds a collection of basic values or enums: its table, the key column pointing at the
     * owner, the element column, the index (list) or key (map) column, and the fetching.
     *
     * @throws UnsupportedOperationException when something about the collection cannot be stated yet
     */
    CollectionFacets collectionFacets(HibernateBasicProperty property) {
        return collectionFacets(property, null)
    }

    /**
     * @param qualifier the qualifier of a collection inside an embedded type that another embedded property reaches too
     *     ({@link #embeddedCollectionQualifier}), or {@code null}: the collection then has a table and a key column of its own
     */
    CollectionFacets collectionFacets(HibernateBasicProperty property, String qualifier) {
        String problem = collectionProblem(property)
        if (problem != null) {
            throw new UnsupportedOperationException(unsupportedReason(property.hibernateOwner, property))
        }
        PropertyConfig mapped = property.hibernateMappedForm
        CollectionKind kind = CollectionKind.of(property.type)
        JoinTable joinTable = mapped.joinTable
        // TableForManyCalculator: the join table's own schema, else the owner's table schema; the catalog is never the owner's
        String schema = joinTable?.schema != null ? joinTable.schema : entityFacets(property.hibernateOwner).schema()
        String table = qualifier == null ? tableForMany.getTableName(property) : qualifiedTableName(property, qualifier)
        List<ColumnFacets> keys = qualifiedKeys(collectionKeyColumns(property), collectionKeyReferencedColumns(property), qualifier)
        ColumnFacets element = collectionElementFacets(property, kind)
        return new CollectionFacets(
                kind,
                table,
                schema,
                joinTable?.catalog,
                qualifier == null ? collectionKeyFacets(property) : keys[0],
                element,
                collectionIndexFacets(property, kind),
                property.isLazy(),
                property.getLazy() == Boolean.TRUE,
                FetchMode.JOIN == mapped.fetchMode ? FetchMode.JOIN : FetchMode.SELECT,
                Math.max(property.batchSize, 0),
                property.cacheUsage,
                keys,
                collectionKeyReferencedColumns(property),
                collectionIndexType(property, kind),
                collectionTableIndexes(property, table, keys, element))
    }

    /**
     * The table of a collection inside an embedded type that has a table of its own: the qualifier (the table of the owner and the path of
     * the embedded property) and the name the mapping gives the join table, else the name of the collection.
     */
    private String qualifiedTableName(HibernateToManyProperty property, String qualifier) {
        String named = property.hibernateMappedForm.joinTable?.name
        return "${qualifier}_${named != null ? named : namingStrategy.resolveColumnName(property.name).replace('`', '')}".toString()
    }

    /** The key columns of a collection that has a table of its own: named after the qualifier, one column for each key of the owner. */
    private static List<ColumnFacets> qualifiedKeys(List<ColumnFacets> keys, List<String> referenced, String qualifier) {
        if (qualifier == null) {
            return keys
        }
        List<ColumnFacets> result = []
        for (int i = 0; i < keys.size(); i++) {
            ColumnFacets key = keys[i]
            String name = keys.size() == 1 ? "${qualifier}_id".toString() : "${qualifier}_${referenced[i]}".toString()
            result << new ColumnFacets(
                    name, key.nullable(), key.unique(), key.insertable(), key.updatable(), key.length(), key.precision(), key.scale(),
                    key.sqlType(), key.defaultValue(), key.read(), key.write(), key.comment())
        }
        return result
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
        if (mapped.hasJoinKeyMapping()) {
            return new ColumnFacets(
                    mapped.joinTable.keys.get(0).name, true, false, true, true, null, null, null, null, null, null, null, null)
        }
        return collectionKeyFacets(property, firstColumnConfig(mapped))
    }

    /**
     * The key columns of a collection: one, or one for each identifier property when the owner has a composite identifier
     * ({@code DependentKeyValueBinder} binds them like the foreign key to a composite identifier, from the column configs of the
     * collection property; a collection mapped by the foreign key of the other side copies the columns of that foreign key).
     */
    private List<ColumnFacets> collectionKeyColumns(HibernateToManyProperty property) {
        GrailsHibernatePersistentEntity owner = property.hibernateOwner
        if (property instanceof HibernateToManyEntityProperty && property.shouldBindWithForeignKey()) {
            // CollectionKeyBinder copies the foreign key columns of the other side into the key; the key updater makes them nullable
            return toOneColumnsFacets((HibernateToOneProperty) property.hibernateInverseSide).collect { ColumnFacets column ->
                new ColumnFacets(column.name(), true, false, true, true, null, null, null, null, null, null, null, null)
            }
        }
        if (!compositeIdentifier(owner)) {
            return [collectionKeyFacets(property)]
        }
        return compositeForeignKeyConfigs(property, owner).collect { ColumnConfig columnConfig -> collectionKeyFacets(property, columnConfig) }
    }

    private List<String> collectionKeyReferencedColumns(HibernateToManyProperty property) {
        return compositeIdentifier(property.hibernateOwner) ? compositeReferencedColumns(property.hibernateOwner) : []
    }

    private ColumnFacets collectionKeyFacets(HibernateToManyProperty property, ColumnConfig columnConfig) {
        PropertyConfig mapped = property.hibernateMappedForm
        boolean updatable = true
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
     * The binder types the index column of a list and the key column of a map with the type the mapping names
     * ({@code indexColumn: [type: 'long']}, or the {@code type} of the {@code index:} settings), whatever the declared key class is.
     *
     * @return why the generator cannot state that type, or {@code null} when it can (or the mapping states none)
     */
    private String collectionIndexTypeProblem(HibernateToManyProperty property, CollectionKind kind) {
        if (!kind.indexed) {
            return null
        }
        String name = property.getIndexColumnType(kind == CollectionKind.LIST ? 'integer' : 'string')
        BasicType<?> registered = typeConfiguration.basicTypeRegistry.getRegisteredType(name)
        if (registered == null) {
            return "the index column type [${name}] is not a type registered with Hibernate, which is the only kind of type the generator " +
                    'states for the index of a list or the key of a map'
        }
        if (registered.valueConverter != null) {
            return "the index column type [${name}] is a registered type that converts its value: the binder boots it, but a list fails on " +
                    'its first row (the integer index violates the check of the converted column) and the table of a map is not created ' +
                    '(the column gets the type name as its SQL type), so the application cannot store a collection with it'
        }
        if (kind == CollectionKind.LIST) {
            Class<?> descriptor = javaTypeDescriptorClass(registered.javaTypeDescriptor.javaTypeClass)
            if (!Modifier.isPublic(descriptor.modifiers) || descriptor.constructors.every { it.parameterCount != 0 }) {
                return "the index column type [${name}] has a Java type descriptor [${descriptor.simpleName}] that Hibernate cannot instantiate " +
                        'from an annotation, which is how the generator types the index of a list'
            }
        }
        return null
    }

    /**
     * @return the type of the index column of a list or the key column of a map when the mapping types it with other than the
     *     default (an integer for a list, a string for a map), as the Java and the JDBC type of the registered type of that name;
     *     {@code null} otherwise
     */
    private TypeFacets collectionIndexType(HibernateToManyProperty property, CollectionKind kind) {
        if (!kind.indexed) {
            return null
        }
        String defaultName = kind == CollectionKind.LIST ? 'integer' : 'string'
        String name = property.getIndexColumnType(defaultName)
        if (name == defaultName) {
            return null
        }
        BasicType<?> registered = typeConfiguration.basicTypeRegistry.getRegisteredType(name)
        return new TypeFacets(null, registered.jdbcType.defaultSqlTypeCode, [:], registered.javaTypeDescriptor.javaTypeClass)
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
     * {@code ManyToOneBinder.prepareCircularManyToMany} gives a circular many-to-many that names no join key the key
     * {@code <property>_id}, by changing the mapping while it binds. The generator runs without that mutation, so it applies
     * the rule itself, to the name of the element column (the binder binds it after the rename of the other side) and, for the
     * key column, only where the binder renamed the key before it bound it, see {@link #boundBeforeInverseSide}.
     */
    private ColumnFacets circularKeyName(HibernateManyToManyProperty property, ColumnFacets facets) {
        if (!property.isCircular() || property.hibernateMappedForm.hasJoinKeyMapping()) {
            return facets
        }
        return new ColumnFacets(
                namingStrategy.resolveColumnName(property.name) + '_id', facets.nullable(), facets.unique(), facets.insertable(),
                facets.updatable(), facets.length(), facets.precision(), facets.scale(), facets.sqlType(), facets.defaultValue(),
                facets.read(), facets.write(), facets.comment())
    }

    /**
     * Whether the binder binds the key of the collection of this many-to-many before the element of the collection of its
     * inverse side. The element of a many-to-many is bound like the column of the other side and renames the join key of that
     * side (it must be circular and name no join key), so the key of a circular side that is bound first keeps the default name
     * and the key of one bound second is renamed. The collections are bound in the order of the properties of the entity
     * ({@code persistentPropertiesToBind}), the superclass before its subclasses, so a side whose inverse side is not circular
     * (it belongs to a superclass) is always bound second.
     */
    private boolean boundBeforeInverseSide(HibernateManyToManyProperty property) {
        HibernateAssociation inverse = property.hibernateInverseSide
        if (inverse == null || !inverse.isCircular() || inverse.owner != property.owner) {
            return false
        }
        List<String> order = property.hibernateOwner.persistentPropertiesToBind*.name
        return order.indexOf(property.name) <= order.indexOf(inverse.name)
    }

    /**
     * Whether the binder binds the collection of this many-to-many side as the one that writes the join table: the owning side
     * ({@code belongsTo}), and a map, which {@code MapSecondPassBinder} always makes not inverse (a map side stores its rows with
     * the key of the map whether or not the mapping says it owns the relationship).
     */
    private static boolean ownsManyToMany(HibernateManyToManyProperty property) {
        return property.owningSide || Map.isAssignableFrom(property.type)
    }

    /**
     * @return why the generator cannot describe the collection of entities, or {@code null} when it can
     */
    private String toManyProblem(HibernateToManyEntityProperty property) {
        PropertyConfig mapped = property.hibernateMappedForm
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
            String compositeProblem = compositeCollectionProblem(property, target)
            if (compositeProblem != null) {
                return compositeProblem
            }
        }
        if (!compositeIdentifier(property.hibernateOwner) && !property.shouldBindWithForeignKey() &&
                mapped.columns != null && mapped.columns.size() > 1) {
            return SEVERAL_KEY_COLUMNS
        }
        if (property.isUserButNotCollectionType()) {
            return 'a class is mapped as the type of the collection property, which the binder binds as one column of the owner\'s table ' +
                    'that holds the collection, and Hibernate fails to boot for a collection of entities'
        }
        if (property instanceof HibernateManyToManyProperty) {
            HibernateAssociation other = property.hibernateInverseSide
            if (!(other instanceof HibernateManyToManyProperty)) {
                return "the other side [${other?.name}] is not a many-to-many collection"
            }
            if (!ownsManyToMany(property) && !ownsManyToMany((HibernateManyToManyProperty) other)) {
                return 'neither side of the many-to-many owns it (no belongsTo): no row would ever be written, and the binder refuses it ' +
                        'too at startup'
            }
        } else if (property.bidirectional) {
            if (!(property.hibernateInverseSide instanceof HibernateManyToOneProperty)) {
                return "the other side [${property.hibernateInverseSide?.name}] is not a many-to-one"
            }
        }
        String indexTypeProblem = collectionIndexTypeProblem(property, kind)
        if (indexTypeProblem != null) {
            return indexTypeProblem
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
    ToManyFacets toManyFacets(HibernateToManyEntityProperty property) {
        return toManyFacets(property, null)
    }

    /**
     * @param qualifier the qualifier of a collection inside an embedded type that another embedded property reaches too
     *     ({@link #embeddedCollectionQualifier}), or {@code null}: a collection that has a join table then has a table and a key
     *     column of its own
     */
    ToManyFacets toManyFacets(HibernateToManyEntityProperty property, String qualifier) {
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
        List<ColumnFacets> keys = null
        ColumnFacets element = null
        List<ColumnFacets> elements = null
        List<String> referencedElements = []
        boolean manyToMany = true
        if (property instanceof HibernateManyToManyProperty) {
            HibernateManyToManyProperty other = (HibernateManyToManyProperty) property.hibernateInverseSide
            JoinTable joinTable = mapped.joinTable
            table = tableForMany.getTableName(property)
            schema = joinTable?.schema != null ? joinTable.schema : entityFacets(property.hibernateOwner).schema()
            catalog = joinTable?.catalog
            key = collectionKeyFacets(property)
            if (!boundBeforeInverseSide((HibernateManyToManyProperty) property)) {
                key = circularKeyName((HibernateManyToManyProperty) property, key)
            }
            if (compositeIdentifier(property.hibernateOwner)) {
                // DependentKeyValueBinder: one key column for each identifier property of the owner
                keys = collectionKeyColumns(property)
                key = keys[0]
            }
            // ManyToOneBinder binds the element like the other side's own column: its name rules and its (never) nullable column
            // (named by the other side's column rules alone: the other side refers to this owner, whose identifier may be composite)
            element = circularKeyName(other, toOneColumnFacets(other, '', firstColumnConfig(other.hibernateMappedForm)))
            if (compositeIdentifier(target)) {
                // the element is a foreign key to a composite identifier: one column for each identifier property of the target
                if (ownsManyToMany(property)) {
                    elements = compositeForeignKeyConfigs(property, target, false).collect { ColumnConfig columnConfig ->
                        toOneColumnFacets(other, '', columnConfig)
                    }
                    referencedElements = compositeReferencedColumns(target)
                } else {
                    // the inverse side reads the table of the owning side: its element columns are the key columns of the owner
                    elements = collectionKeyColumns((HibernateManyToManyProperty) other)
                    referencedElements = collectionKeyReferencedColumns((HibernateManyToManyProperty) other)
                }
                element = elements[0]
            }
            String joinColumnName = joinTable?.column?.name
            if (joinColumnName != null && ownsManyToMany(property) && !property.isCircular()) {
                // the owning side names the element column of the join table it writes (the inverse side adopts it); a circular
                // side keeps naming it after the property of the other side, as the binder does
                element = new ColumnFacets(
                        joinColumnName, element.nullable(), element.unique(), element.insertable(), element.updatable(),
                        element.length(), element.precision(), element.scale(), element.sqlType(), element.defaultValue(),
                        element.read(), element.write(), element.comment())
            }
            mappedBy = ownsManyToMany(property) ? null : other.name
        } else if (property.shouldBindWithForeignKey()) {
            HibernateToOneProperty inverse = (HibernateToOneProperty) property.hibernateInverseSide
            // CollectionKeyBinder copies the other side's foreign key column into the key; the key updater makes it nullable
            keys = collectionKeyColumns(property)
            key = keys[0]
            mappedBy = kind == CollectionKind.LIST ? null : inverse.name
            manyToMany = false
        } else {
            JoinTable joinTable = mapped.joinTable
            table = tableForMany.getTableName(property)
            schema = joinTable?.schema != null ? joinTable.schema : entityFacets(property.hibernateOwner).schema()
            catalog = joinTable?.catalog
            keys = collectionKeyColumns(property)
            key = keys[0]
            if (property.bidirectional) {
                // BidirectionalMapElementBinder binds the element like the many-to-one of the other side (its column name rules)
                ColumnFacets inverseColumn = toOneColumnFacets((HibernateToOneProperty) property.hibernateInverseSide)
                element = new ColumnFacets(
                        inverseColumn.name(), inverseColumn.nullable(), inverseColumn.unique(), true, true, null, null, null,
                        inverseColumn.sqlType(), null, null, null, null)
            } else {
                element = new ColumnFacets(
                        property.resolveJoinTableForeignKeyColumnName(namingStrategy), true, false, true, true, null, null, null,
                        null, null, null, null, null)
            }
        }
        String condition = property instanceof HibernateOneToManyProperty && target.isMultiTenant() ?
                target.getMultiTenantFilterCondition(defaultColumnNames) : null
        if (qualifier != null && table != null && mappedBy == null) {
            table = qualifiedTableName(property, qualifier)
            keys = qualifiedKeys(keys != null ? keys : [key], keys != null ? collectionKeyReferencedColumns(property) : [], qualifier)
            key = keys[0]
        }
        return new ToManyFacets(
                kind,
                target.name,
                manyToMany,
                mappedBy,
                table,
                schema,
                catalog,
                key,
                element,
                collectionIndexFacets(property, kind),
                property.isLazy(),
                property.getLazy() == Boolean.TRUE,
                FetchMode.JOIN == mapped.fetchMode ? FetchMode.JOIN : FetchMode.SELECT,
                Math.max(property.batchSize, 0),
                property.cacheUsage,
                cascadeFacets(property),
                // a list is ordered by its index column, so the binder's order-by of a list changes nothing (probed: the elements come back in
                // the order they were stored)
                property.hasSort() && kind != CollectionKind.LIST ? property.sort : null,
                property.hasSort() && kind != CollectionKind.LIST ? (property.order != null ? property.order : 'asc') : null,
                condition,
                keys != null ? keys : [key],
                keys != null ? collectionKeyReferencedColumns(property) : [],
                collectionIndexType(property, kind),
                collectionTableIndexes(property, table, keys != null ? keys : [key], element),
                mapped.type != null && property.userType != null && UserCollectionType.isAssignableFrom(property.userType) ? property.userType : null,
                element == null ? [] : (elements != null ? elements : [element]),
                referencedElements)
    }

    /**
     * @return why the generator cannot describe the to-one association, or {@code null} when it can
     */
    private String toOneProblem(HibernateToOneProperty property) {
        boolean inverseOneToOne = boundAsOneToOne(property)
        if (inverseOneToOne && ((HibernateOneToOneProperty) property).needsSimpleValueBinding()) {
            return 'the binder binds this one-to-one as a Hibernate OneToOne with a column of its own (constrained), ' +
                    'which it cannot do: both sides are hasOne and the application does not start with the binder either ' +
                    "('PostInitCallback queue could not be processed')"
        }
        GrailsHibernatePersistentEntity target = property.hibernateAssociatedEntity
        if (target == null) {
            return 'the associated entity is unknown'
        }
        PropertyConfig mapped = property.hibernateMappedForm
        if (inverseOneToOne) {
            return null
        }
        boolean compositeTarget = compositeIdentifier(target)
        if (compositeTarget) {
            String problem = compositeForeignKeyProblem(property, target)
            if (problem != null) {
                return problem
            }
        } else if (mapped.columns != null && mapped.columns.size() > 1) {
            return 'the mapping states several columns, which the binder binds as a composite foreign key and Hibernate refuses because ' +
                    "the key it points at has one column ('Foreign key must have the same number of columns as the referenced primary key')"
        }
        if (mapped.derived) {
            return 'the association is mapped with a formula, which the binder cannot bind either (AssertionFailure: value involves formulas)'
        }
        return null
    }

    /**
     * The foreign key to an entity with a composite identifier has one column for each identifier property
     * ({@code CompositeIdentifierToManyToOneBinder}). The generator reproduces that when the identifier properties are simple
     * columns or foreign keys to entities with a simple identifier, and the mapping states either no columns or exactly one for
     * each identifier property, all named.
     *
     * @return why the generator cannot describe the foreign key, or {@code null} when it can
     */
    private String compositeForeignKeyProblem(HibernateAssociation property, GrailsHibernatePersistentEntity target) {
        GrailsHibernatePersistentEntity root = target.hibernateRootEntity
        if (!root.hibernateCompositeIdentity.isPresent()) {
            return "the associated entity [${target.name}] has a composite identifier that the mapping does not state, which the " +
                    'binder binds as a simple one'
        }
        String idProblem = compositeIdProblem(root)
        if (idProblem != null) {
            return "the associated entity [${target.name}] has a composite identifier the generator cannot describe: ${idProblem}"
        }
        HibernatePersistentProperty simplePart = root.compositeIdentity.find { HibernatePersistentProperty part ->
            part instanceof HibernateToOneProperty && !compositeIdentifier(((HibernateToOneProperty) part).hibernateAssociatedEntity)
        }
        if (simplePart != null) {
            return "the composite identifier of the associated entity [${target.name}] has a part [${simplePart.name}] that refers to an " +
                    'entity with a simple identifier: ForeignKeyColumnCountCalculator counts such a part as no column, so the binder gives ' +
                    'the foreign key fewer columns than the key and Hibernate refuses it at boot (pinned in GrailsDomainBinderCompositeIdDefectSpec)'
        }
        HibernatePersistentProperty deepPart = root.compositeIdentity.find { HibernatePersistentProperty part ->
            part instanceof HibernateToOneProperty && compositeIdentifier(((HibernateToOneProperty) part).hibernateAssociatedEntity) &&
                    ((HibernateToOneProperty) part).hibernateAssociatedEntity.hibernateRootEntity.compositeIdentity.any { HibernatePersistentProperty inner ->
                        inner instanceof HibernateToOneProperty && compositeIdentifier(((HibernateToOneProperty) inner).hibernateAssociatedEntity)
                    }
        }
        if (deepPart != null) {
            return "the composite identifier of the associated entity [${target.name}] has a part [${deepPart.name}] whose own composite " +
                    'identifier refers to a composite identifier: the binder names the columns of a foreign key to a composite identifier ' +
                    'only one level deep (CompositeIdentifierToManyToOneBinder.tryExpandNestedComposite), so it gives the foreign key ' +
                    'fewer columns than the key'
        }
        List<ColumnConfig> columns = property.hibernateMappedForm.columns
        int expected = foreignKeyColumnCount.calculateForeignKeyColumnCount(root, root.hibernateCompositeIdentity.get().propertyNames)
        if (columns.size() > expected) {
            return "the mapping states ${columns.size()} columns for a foreign key to a composite identifier with ${expected} " +
                    'properties: the binder fails with an IndexOutOfBoundsException or Hibernate refuses the foreign key because it has ' +
                    'more columns than the key'
        }
        return null
    }

    /**
     * The column configs the binder binds the foreign key to an entity with a composite identifier with. A mapping that states
     * them names them; otherwise {@code CompositeIdentifierToManyToOneBinder} (which adds them to the mapping, as it binds)
     * names one for each identifier property after the table of the associated entity and the default column name of the
     * identifier property.
     */
    private List<ColumnConfig> compositeForeignKeyConfigs(
            HibernateAssociation property, GrailsHibernatePersistentEntity target, boolean useMappedColumns = true) {
        List<ColumnConfig> mapped = useMappedColumns ? property.hibernateMappedForm.columns : []
        GrailsHibernatePersistentEntity root = target.hibernateRootEntity
        String prefix = root.getTableName(namingStrategy)
        List<ColumnConfig> generated = []
        for (String propertyName : root.hibernateCompositeIdentity.get().propertyNames) {
            HibernatePersistentProperty referenced = root.getHibernatePropertyByName(propertyName)
            HibernatePersistentProperty[] nested = referenced instanceof HibernateToOneProperty ?
                    ((HibernateToOneProperty) referenced).hibernateAssociatedEntity.compositeIdentity : null
            if (nested != null && nested.length > 0) {
                // a part that refers to an entity with a composite identifier: one column for each of its identifier properties
                for (HibernatePersistentProperty inner : nested) {
                    generated << new ColumnConfig(name: foreignKeyColumnName(
                            prefix, namingStrategy.resolveColumnName(propertyName), defaultColumnNames.getDefaultColumnName(inner)))
                }
            } else {
                String suffix = referenced != null ? defaultColumnNames.getDefaultColumnName(referenced) : propertyName
                generated << new ColumnConfig(name: foreignKeyColumnName(prefix, suffix))
            }
        }
        if (mapped.isEmpty()) {
            return generated
        }
        // the binder keeps the columns the mapping states, in order, and names the missing ones as it would have named all of them
        List<ColumnConfig> result = new ArrayList<ColumnConfig>(mapped)
        if (mapped.size() < generated.size()) {
            result.addAll(generated.drop(mapped.size()))
        }
        return result
    }

    private static String foreignKeyColumnName(String... parts) {
        return parts.collect { String part -> part.replace('`', '') }.join('_')
    }

    /** The column of the key of the entity that each foreign key column points at, in the order of the identifier properties. */
    private List<String> compositeReferencedColumns(GrailsHibernatePersistentEntity target) {
        GrailsHibernatePersistentEntity root = target.hibernateRootEntity
        List<String> referenced = []
        for (String propertyName : root.hibernateCompositeIdentity.get().propertyNames) {
            HibernatePersistentProperty part = root.getHibernatePropertyByName(propertyName)
            if (part instanceof HibernateToOneProperty) {
                // a part that refers to an entity with a composite identifier has one column for each of its identifier properties
                referenced.addAll(toOneColumnsFacets((HibernateToOneProperty) part)*.name())
            } else {
                referenced << columnFacets(part).name()
            }
        }
        return referenced
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
                    oneToOne.hibernateReferencedEntityName,
                    [],
                    [])
        }
        List<ColumnFacets> columns = toOneColumnsFacets(property)
        ColumnFacets column = columns[0]
        return new ToOneFacets(
                property.hibernateAssociatedEntity.name,
                property.isLazy(),
                FetchMode.JOIN == mapped.fetchMode ? FetchMode.JOIN : FetchMode.SELECT,
                columns.every { ColumnFacets c -> c.nullable() },
                mapped.ignoreNotFound,
                cascadeFacets(property),
                column,
                null,
                null,
                columns,
                compositeIdentifier(property.hibernateAssociatedEntity) ? compositeReferencedColumns(property.hibernateAssociatedEntity) : [])
    }

    private ColumnFacets toOneColumnFacets(HibernateAssociation property, String path = '') {
        return toOneColumnsFacets(property, path)[0]
    }

    /** The foreign key columns of the association, one for an ordinary association, one for each identifier property of a composite identifier. */
    private List<ColumnFacets> toOneColumnsFacets(HibernateAssociation property, String path = '') {
        GrailsHibernatePersistentEntity target = property.hibernateAssociatedEntity
        if (target != null && compositeIdentifier(target) && property.hibernateMappedForm.derived == false &&
                target.hibernateRootEntity.hibernateCompositeIdentity.isPresent() && compositeForeignKeyProblem(property, target) == null) {
            return compositeForeignKeyConfigs(property, target).collect { ColumnConfig columnConfig ->
                toOneColumnFacets(property, path, columnConfig)
            }
        }
        return [toOneColumnFacets(property, path, firstColumnConfig(property.hibernateMappedForm))]
    }

    private ColumnFacets toOneColumnFacets(HibernateAssociation property, String path, ColumnConfig columnConfig) {
        PropertyConfig mapped = property.hibernateMappedForm
        Column column = new Column()
        columnConfigBinder.bindColumnConfigToColumn(column, columnConfig, mapped)
        if (columnConfig != null) {
            column.comment = columnConfig.comment
            column.defaultValue = columnConfig.defaultValue
            column.customRead = columnConfig.read
            column.customWrite = columnConfig.write
        }
        String name = columnNames.getColumnNameForPropertyAndPath(property, path, columnConfig)
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
        // a part of the composite identifier is a primary key column, and Hibernate makes those not null
        return facets(property, column, name, nullable && !isCompositeIdPart((HibernatePersistentProperty) property), unique)
    }

    /**
     * Decides the cascade of an association with the binder's own {@code CascadeBehaviorFetcher} (an explicit
     * {@code cascade} mapping, else the behavior implied by the kind of association and who owns it) and splits it into what
     * the annotations can say: JPA's {@code cascade}, Hibernate's {@code @Cascade} for what JPA cannot name, and
     * {@code orphanRemoval}.
     */
    CascadeFacets cascadeFacets(HibernateAssociation property) {
        if (isCompositeIdPart((HibernatePersistentProperty) property)) {
            // a part of the identifier is a key: nothing cascades through it
            return new CascadeFacets([], [], false)
        }
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
        if (boundAsColumn(property)) {
            return basicColumnFacets(property, null, null)
        }
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
            if (isComponent(peer)) {
                collectLeaves((HibernateEmbeddedProperty) peer, currentPath, path, enclosing, leaves)
            } else if (peer instanceof HibernateToManyProperty) {
                // a collection has a table of its own and no column in the owner's table: the embeddable states its table and columns itself
                continue
            } else if (isDerived(peer)) {
                leaves << new EmbeddedLeaf(path, peer, null, null)
            } else if (peer instanceof HibernateToOneProperty && compositeIdentifier(((HibernateToOneProperty) peer).hibernateAssociatedEntity)) {
                // a foreign key to a composite identifier has several columns, named by the identifier and not by the path; each one is
                // nullable when an enclosing component is
                ToOneFacets toOne = toOneFacets((HibernateToOneProperty) peer)
                List<ColumnFacets> columns = toOne.joinColumns().collect { ColumnFacets column ->
                    nullableInComponent(column, enclosing)
                }
                leaves << new EmbeddedLeaf(path, peer, columns[0], new ToOneFacets(
                        toOne.target(), toOne.lazy(), toOne.fetchMode(), columns.every { ColumnFacets c -> c.nullable() },
                        toOne.ignoreNotFound(), toOne.cascade(), columns[0], toOne.mappedBy(), toOne.referencedEntity(), columns,
                        toOne.referencedColumns()))
            } else {
                leaves << new EmbeddedLeaf(
                        path, peer, componentColumnFacets(peer, embedded, currentPath, enclosing),
                        peer instanceof HibernateToOneProperty ? toOneFacets((HibernateToOneProperty) peer) : null)
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
        ColumnFacets facets = peer instanceof HibernateToOneProperty ? toOneColumnFacets((HibernateToOneProperty) peer, path) :
                (peer instanceof HibernateEnumProperty ?
                        enumColumnFacets((HibernateEnumProperty) peer, path) : basicColumnFacets(peer, path, parent))
        return nullableInComponent(facets, enclosing)
    }

    /**
     * {@code ComponentUpdater} makes every column of an enclosing component nullable when that component is. The binder also writes
     * the component as a whole only when its property is insertable and updatable ({@code PropertyBinder}), so a column is written
     * only when every enclosing embedded property allows it as well.
     */
    private static ColumnFacets nullableInComponent(ColumnFacets facets, List<HibernateEmbeddedProperty> enclosing) {
        boolean nullable = facets.nullable() || enclosing.any { HibernateEmbeddedProperty e ->
            e.hibernateOwner.isComponentPropertyNullable(e)
        }
        boolean insertable = facets.insertable() && enclosing.every { HibernateEmbeddedProperty e -> e.hibernateMappedForm?.insertable != false }
        boolean updatable = facets.updatable() && enclosing.every { HibernateEmbeddedProperty e -> e.hibernateMappedForm?.updatable != false }
        return new ColumnFacets(
                facets.name(), nullable, facets.unique(), insertable, updatable, facets.length(),
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
     *     the embedded type, nested embedded types included, must be supported, and embedded types must not contain each other
     */
    private String embeddedProblem(HibernateEmbeddedProperty property, List<Class<?>> visiting) {
        GrailsHibernatePersistentEntity type = (GrailsHibernatePersistentEntity) property.associatedEntity
        if (type == null) {
            return 'the embedded type is unknown'
        }
        if (visiting.contains(type.javaClass)) {
            return "the embedded type [${type.name}] contains itself"
        }
        List<Class<?>> path = new ArrayList<Class<?>>(visiting)
        path << type.javaClass
        for (HibernatePersistentProperty peer : embeddedPeers(property)) {
            if (isComponent(peer)) {
                String problem = embeddedProblem((HibernateEmbeddedProperty) peer, path)
                if (problem != null) {
                    return problem
                }
            } else if (peer instanceof HibernateToManyProperty) {
                if (!supports(peer)) {
                    return unsupportedReason(type, peer)
                }
            } else if (peer instanceof HibernateToOneProperty) {
                if (!boundAsManyToOne((HibernateToOneProperty) peer)) {
                    return "the property [${peer.name}] of [${type.name}] is the inverse side of a one-to-one inside an embedded type, " +
                            'which the generator does not support yet'
                }
                String problem = toOneProblem((HibernateToOneProperty) peer)
                if (problem != null) {
                    return "the association [${peer.name}] of [${type.name}]: ${problem}"
                }

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
        return facets(property, column, name, isNullable(property, parent) && !isCompositeIdPart(property),
                mappedForm.isUnique() && !mappedForm.isUniqueWithinGroup())
    }

    /** Mirrors {@code EnumTypeBinder}: the column config's length, precision, scale, SQL type and uniqueness, plus the comment, default and read and write expressions it forgets. */
    private ColumnFacets enumColumnFacets(HibernateEnumProperty property, String path) {
        PropertyConfig mappedForm = property.hibernateMappedForm
        Column column = new Column()
        ColumnConfig columnConfig = firstColumnConfig(mappedForm)
        if (columnConfig != null) {
            columnConfigBinder.bindColumnConfigToColumn(column, columnConfig, mappedForm)
        }
        if (columnConfig != null) {
            // EnumTypeBinder ignores the comment, the default and the read and write expressions of the column config (a binder
            // defect, pinned in GrailsDomainBinderOptionDefectSpec); the generator states what the mapping asks for
            column.comment = columnConfig.comment
            column.defaultValue = columnConfig.defaultValue
            column.customRead = columnConfig.read
            column.customWrite = columnConfig.write
        }
        String name = property.resolveEnumColumnName(namingStrategy, columnNames, path)
        return facets(property, column, name, property.isEnumColumnNullable(), column.unique)
    }

    private ColumnFacets facets(
            HibernatePersistentProperty property, Column column, String name, boolean nullable, boolean unique) {
        PropertyConfig mappedForm = property.hibernateMappedForm
        // NaturalId.createUniqueKey sets the updatability of each of its properties to the mutability of the natural id
        Boolean naturalMutable = naturalIdUpdatable(property)
        return new ColumnFacets(
                name,
                nullable,
                unique,
                mappedForm.insertable,
                naturalMutable != null ? naturalMutable : mappedForm.updatable,
                column.length?.intValue(),
                column.precision?.intValue(),
                column.scale?.intValue(),
                column.sqlType,
                column.defaultValue,
                column.customRead,
                column.customWrite,
                column.comment)
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
        for (Annotation audit : auditAnnotations(entity)) {
            annotations << AnnotationDescription.ForLoadedAnnotation.of(audit)
        }

        if (hierarchy.ownsTable()) {
            AnnotationDescription.Builder table = AnnotationDescription.Builder.ofType(Table).define('name', facets.tableName())
            if (facets.schema()) {
                table = table.define('schema', facets.schema())
            }
            if (facets.catalog()) {
                table = table.define('catalog', facets.catalog())
            }
            ConstraintFacets constraints = constraintFacets(entity)
            if (!constraints.indexes().isEmpty()) {
                table = table.defineAnnotationArray('indexes', TypeDescription.ForLoadedType.of(Index),
                        constraints.indexes().collect { IndexFacets index ->
                            AnnotationDescription.Builder.ofType(Index)
                                    .define('name', index.name())
                                    .define('columnList', index.columns().join(', '))
                                    .build()
                        } as AnnotationDescription[])
            }
            if (!constraints.uniqueKeys().isEmpty()) {
                table = table.defineAnnotationArray('uniqueConstraints', TypeDescription.ForLoadedType.of(UniqueConstraint),
                        constraints.uniqueKeys().collect { UniqueKeyFacets key ->
                            AnnotationDescription.Builder.ofType(UniqueConstraint)
                                    .define('name', key.name())
                                    .defineArray('columnNames', key.columns() as String[])
                                    .build()
                        } as AnnotationDescription[])
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
        if (entity.isRoot() && !facets.versioned()) {
            // VersionBinder sets the lock style NONE for a root without a version; Hibernate's default is VERSION
            annotations << AnnotationDescription.Builder.ofType(OptimisticLocking).define('type', OptimisticLockType.NONE).build()
        }
        if (facets.batchSize() > 0) {
            annotations << AnnotationDescription.Builder.ofType(BatchSize).define('size', facets.batchSize()).build()
        }
        if (facets.comment()) {
            annotations << AnnotationDescription.Builder.ofType(Comment).define('value', facets.comment()).build()
        }
        CacheFacets cache = cacheFacets(entity)
        if (cache != null) {
            annotations << AnnotationDescription.Builder.ofType(Cacheable).build()
            annotations << AnnotationDescription.Builder.ofType(Cache)
                    .define('usage', CacheConcurrencyStrategy.parse(cache.usage()))
                    .define('includeLazy', cache.includeLazy())
                    .build()
            if (!cache.mutable()) {
                annotations << AnnotationDescription.Builder.ofType(Immutable).build()
            }
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
            // Hibernate adds a check constraint over the discriminator values to the column unless the discriminator is forced; the
            // binder adds none. The class is bound forced so that Hibernate adds no check, and GeneratedDomainClassBinder restores the
            // binder's unforced discriminator (which has no check-constraint role, only a query one) once the mappings are bound.
            AnnotationDescription.Builder options = AnnotationDescription.Builder.ofType(DiscriminatorOptions)
            if (discriminator.formula() == null) {
                options = options.define('force', true)
            }
            if (!discriminator.insertable()) {
                options = options.define('insert', false)
            }
            if (discriminator.formula() == null || !discriminator.insertable()) {
                annotations << options.build()
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
        if (isComponent(property)) {
            return defineEmbeddedField(builder, (HibernateEmbeddedProperty) property, embeddables, true)
        }
        if (property instanceof HibernateBasicProperty && !boundAsColumn(property)) {
            return defineCollectionField(builder, (HibernateBasicProperty) property)
        }
        if (property instanceof HibernateToOneProperty) {
            return defineToOneField(builder, (HibernateToOneProperty) property, extra)
        }
        if (property instanceof HibernateToManyEntityProperty) {
            return defineToManyField(builder, (HibernateToManyEntityProperty) property)
        }
        List<AnnotationDescription> annotations = new ArrayList<>(extra)
        if (isDerived(property)) {
            annotations << AnnotationDescription.Builder.ofType(Formula).define('value', property.hibernateMappedForm.formula).build()
        } else {
            annotations << columnAnnotation(property)
            annotations.addAll(extraColumnAnnotations(property))
        }
        Boolean naturalMutable = naturalIdMutable(property)
        if (naturalMutable != null) {
            annotations << AnnotationDescription.Builder.ofType(HibernateNaturalId).define('mutable', naturalMutable).build()
        }
        if (!(property instanceof HibernateSimpleIdentityProperty) && property.isLazy()) {
            // PropertyBinder marks the property lazy when the mapping says lazy: true (the version never is)
            annotations << AnnotationDescription.Builder.ofType(Basic).define('fetch', FetchType.LAZY).build()
        }
        TypeFacets type = typeFacets(property)
        if (type != null) {
            annotations.addAll(typeAnnotations(type))
        } else if (property instanceof HibernateEnumProperty) {
            annotations << enumAnnotation((HibernateEnumProperty) property)
        }
        for (Annotation constraint : carriedAnnotations(property)) {
            annotations << AnnotationDescription.ForLoadedAnnotation.of(constraint)
        }
        return builder.defineField(property.name, fieldType(property, type), Visibility.PRIVATE)
                .annotateField(annotations as AnnotationDescription[])
    }

    /**
     * The type of the field of a column: the Java type the type facets name, else the property's. Hibernate's annotation binder takes a
     * field of a collection type with no element type for a basic collection and fails to bind it, so a collection stored in one column
     * is declared with its element type (and the {@code String} key of a map), and a serialized one with {@code Serializable}.
     */
    private TypeDefinition fieldType(HibernatePersistentProperty property, TypeFacets type) {
        if (type?.javaType() != null) {
            return TypeDescription.ForLoadedType.of(type.javaType())
        }
        if (boundAsColumn(property)) {
            HibernateBasicProperty collection = (HibernateBasicProperty) property
            Class<?> element = property instanceof HibernateEnumProperty ? ((HibernateEnumProperty) property).enumType : collection.componentType
            if (element != null && element != Object) {
                return CollectionKind.MAP.javaType.isAssignableFrom(property.type) ?
                        TypeDescription.Generic.Builder.parameterizedType(property.type, String, element).build() :
                        TypeDescription.Generic.Builder.parameterizedType(property.type, element).build()
            }
        }
        return TypeDescription.ForLoadedType.of(columnFieldType(property))
    }

    /**
     * The type of the field of a basic property. Hibernate treats a primitive field as not optional and, for every property of a
     * single-table hierarchy with subclasses (the properties the root declares as well as those of the subclasses), adds a table
     * check constraint ({@code class <> 'X' or (column is not null)}) to a subclass when the column is nullable; the domain binder
     * adds none, and the column facets already say whether the column is nullable. The carrier is never instantiated, so such a
     * field is boxed.
     */
    private static Class<?> columnFieldType(HibernatePersistentProperty property) {
        Class<?> type = property.type
        if (type.primitive && property.owner instanceof GrailsHibernatePersistentEntity) {
            GrailsHibernatePersistentEntity owner = (GrailsHibernatePersistentEntity) property.owner
            if (inheritanceType(owner) == InheritanceType.SINGLE_TABLE && (!owner.isRoot() || !owner.childEntities.isEmpty())) {
                return ClassUtils.resolvePrimitiveIfNecessary(type)
            }
        }
        return type
    }

    /**
     * A many-to-one association is a field typed with the class generated for its target (which is why the target must be
     * part of the same call), with {@code @ManyToOne} stating the fetch type, the optionality and the cascade, a
     * {@code @JoinColumn}, an explicit {@code @Fetch} (without it an eager association would be a join fetch, which the
     * binder's default is not), and {@code @NotFound} when the mapping says to ignore a missing row.
     */
    private DynamicType.Builder<Object> defineToOneField(
            DynamicType.Builder<Object> builder, HibernateToOneProperty property, List<AnnotationDescription> extra) {
        ToOneFacets facets = toOneFacets(property)
        List<AnnotationDescription> annotations = new ArrayList<>(extra)
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
            if (facets.joinColumns().size() > 1) {
                // a foreign key to a composite identifier: each column states the column of the key it points at
                List<AnnotationDescription> columns = []
                for (int i = 0; i < facets.joinColumns().size(); i++) {
                    columns << joinColumnAnnotation(facets.joinColumns()[i], facets.referencedColumns()[i])
                }
                annotations << AnnotationDescription.Builder.ofType(JoinColumns)
                        .defineAnnotationArray('value', TypeDescription.ForLoadedType.of(JoinColumn), columns as AnnotationDescription[])
                        .build()
            } else {
                annotations << joinColumnAnnotation(facets.joinColumn())
            }
        }
        Boolean naturalMutable = naturalIdMutable(property)
        if (naturalMutable != null) {
            annotations << AnnotationDescription.Builder.ofType(HibernateNaturalId).define('mutable', naturalMutable).build()
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
        for (Annotation constraint : carriedAnnotations(property)) {
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
    private DynamicType.Builder<Object> defineToManyField(DynamicType.Builder<Object> builder, HibernateToManyEntityProperty property) {
        ToManyFacets facets = toManyFacets(property)
        List<AnnotationDescription> annotations = []
        FetchType fetchType = facets.lazy() ? FetchType.LAZY : FetchType.EAGER
        List<org.hibernate.annotations.CascadeType> hibernateCascade = new ArrayList<org.hibernate.annotations.CascadeType>(facets.cascade().hibernate())
        if (facets.manyToMany()) {
            AnnotationDescription.Builder manyToMany = AnnotationDescription.Builder.ofType(ManyToMany)
                    .define('fetch', fetchType)
                    .defineEnumerationArray('cascade', CascadeType, facets.cascade().jpa() as CascadeType[])
            if (facets.mappedBy() != null) {
                // the inverse side takes its table from the owning side
                manyToMany = manyToMany.define('mappedBy', facets.mappedBy())
            } else {
                AnnotationDescription.Builder joinTable = AnnotationDescription.Builder.ofType(JpaJoinTable)
                        .define('name', facets.tableName())
                        .defineAnnotationArray('joinColumns', TypeDescription.ForLoadedType.of(JoinColumn), keyJoinColumns(facets.keys(), facets.referencedKeys()))
                        .defineAnnotationArray('inverseJoinColumns', TypeDescription.ForLoadedType.of(JoinColumn), keyJoinColumns(facets.elements(), facets.referencedElements()))
                if (facets.schema()) {
                    joinTable = joinTable.define('schema', facets.schema())
                }
                if (facets.catalog()) {
                    joinTable = joinTable.define('catalog', facets.catalog())
                }
                if (!sharedEmbeddedCollection(property)) {
                    annotations << joinTable.build()
                }
            }
            annotations << manyToMany.build()
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
                if (facets.keys().size() > 1) {
                    // the foreign key to a composite identifier of the owner: each column states the key column it points at
                    annotations << AnnotationDescription.Builder.ofType(JoinColumns)
                            .defineAnnotationArray('value', TypeDescription.ForLoadedType.of(JoinColumn), keyJoinColumns(facets.keys(), facets.referencedKeys()))
                            .build()
                } else {
                    annotations << joinColumnAnnotation(facets.key(), facets.referencedKeys().isEmpty() ? null : facets.referencedKeys()[0])
                }
            }
            annotations << oneToMany.build()
        }
        if (facets.kind() == CollectionKind.LIST && facets.mappedBy() == null) {
            annotations << AnnotationDescription.Builder.ofType(OrderColumn)
                    .define('name', facets.index().name())
                    .define('nullable', facets.index().nullable())
                    .build()
            annotations.addAll(listIndexTypeAnnotations(facets.indexType()))
        } else if (facets.kind() == CollectionKind.MAP) {
            annotations << mapKeyColumnAnnotation(facets.index())
            annotations.addAll(mapKeyTypeAnnotations(facets.indexType()))
        }
        if (facets.kind() == CollectionKind.SORTED_SET) {
            // the binder marks the collection sorted and names no comparator: the elements' natural order
            annotations << AnnotationDescription.Builder.ofType(SortNatural).build()
        }
        if (facets.collectionType() != null) {
            annotations << AnnotationDescription.Builder.ofType(CollectionType)
                    .define('type', TypeDescription.ForLoadedType.of(facets.collectionType())).build()
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
        for (Annotation constraint : carriedAnnotations(property)) {
            annotations << AnnotationDescription.ForLoadedAnnotation.of(constraint)
        }
        List<TypeDescription> arguments = facets.kind() == CollectionKind.MAP ?
                [TypeDescription.ForLoadedType.of(mapKeyClass(facets.indexType())), generatedType(property.hibernateAssociatedEntity)] :
                [generatedType(property.hibernateAssociatedEntity)]
        TypeDescription.Generic fieldType = TypeDescription.Generic.Builder.parameterizedType(
                TypeDescription.ForLoadedType.of(facets.kind().javaType), arguments).build()
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
                .defineAnnotationArray('joinColumns', TypeDescription.ForLoadedType.of(JoinColumn), keyJoinColumns(facets.keys(), facets.referencedKeys()))
        if (facets.schema()) {
            table = table.define('schema', facets.schema())
        }
        if (facets.catalog()) {
            table = table.define('catalog', facets.catalog())
        }
        if (!sharedEmbeddedCollection(property)) {
            annotations << table.build()
        }
        annotations << columnAnnotation(facets.element(), property.componentType)
        if (facets.kind() == CollectionKind.LIST) {
            annotations << AnnotationDescription.Builder.ofType(OrderColumn)
                    .define('name', facets.index().name())
                    .define('nullable', facets.index().nullable())
                    .build()
            annotations.addAll(listIndexTypeAnnotations(facets.indexType()))
        } else if (facets.kind() == CollectionKind.MAP) {
            annotations << mapKeyColumnAnnotation(facets.index())
            annotations.addAll(mapKeyTypeAnnotations(facets.indexType()))
        }
        TypeFacets type = typeFacets(property)
        if (type != null) {
            annotations.addAll(typeAnnotations(type))
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
        for (Annotation constraint : carriedAnnotations(property)) {
            annotations << AnnotationDescription.ForLoadedAnnotation.of(constraint)
        }
        Class<?> elementClass = property instanceof HibernateEnumProperty ?
                ((HibernateEnumProperty) property).enumType : ((HibernateBasicProperty) property).componentType
        TypeDescription.Generic fieldType = facets.kind() == CollectionKind.MAP ?
                TypeDescription.Generic.Builder.parameterizedType(Map, mapKeyClass(facets.indexType()), elementClass).build() :
                TypeDescription.Generic.Builder.parameterizedType(facets.kind().javaType, elementClass).build()
        return builder.defineField(property.name, fieldType, Visibility.PRIVATE)
                .annotateField(annotations as AnnotationDescription[])
    }

    /** The join columns of a collection key: one, or one for each identifier property of a composite identifier of the owner. */
    private static AnnotationDescription[] keyJoinColumns(List<ColumnFacets> keys, List<String> referenced) {
        List<AnnotationDescription> columns = []
        for (int i = 0; i < keys.size(); i++) {
            columns << joinColumnAnnotation(keys[i], referenced.isEmpty() ? null : referenced[i])
        }
        return columns as AnnotationDescription[]
    }

    private static AnnotationDescription joinColumnAnnotation(ColumnFacets facets, String referencedColumn = null) {
        AnnotationDescription.Builder annotation = AnnotationDescription.Builder.ofType(JoinColumn)
                .define('name', facets.name())
                .define('nullable', facets.nullable())
                .define('unique', facets.unique())
                .define('insertable', facets.insertable())
                .define('updatable', facets.updatable())
        if (facets.sqlType()) {
            annotation = annotation.define('columnDefinition', facets.sqlType())
        }
        if (referencedColumn != null) {
            annotation = annotation.define('referencedColumnName', referencedColumn)
        }
        return annotation.build()
    }

    /** The Java type of the key of a map: the declared one is a string, the mapping can type the key column with another registered type. */
    private static Class<?> mapKeyClass(TypeFacets indexType) {
        return indexType == null ? String : indexType.javaType()
    }

    /** The Java and the JDBC type of the index column of a list, which the binder types as the mapping says, as it types the key of a map. */
    private List<AnnotationDescription> listIndexTypeAnnotations(TypeFacets indexType) {
        if (indexType == null) {
            return []
        }
        return [
                AnnotationDescription.Builder.ofType(ListIndexJavaType).define('value', TypeDescription.ForLoadedType.of(javaTypeDescriptorClass(indexType.javaType()))).build(),
                AnnotationDescription.Builder.ofType(ListIndexJdbcTypeCode).define('value', indexType.jdbcTypeCode()).build(),
        ]
    }

    /** The class of Hibernate's Java type descriptor of a Java type, which an annotation names and Hibernate instantiates. */
    private Class<?> javaTypeDescriptorClass(Class<?> javaType) {
        return typeConfiguration.javaTypeRegistry.getDescriptor(javaType).getClass()
    }

    /** The JDBC type of the key column of a map; its Java type is the key of the generated field. */
    private static List<AnnotationDescription> mapKeyTypeAnnotations(TypeFacets indexType) {
        if (indexType == null) {
            return []
        }
        return [AnnotationDescription.Builder.ofType(MapKeyJdbcTypeCode).define('value', indexType.jdbcTypeCode()).build()]
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
     * Whether the collection is inside an embedded type that more than one embedded property reaches. Its table and key are then stated by
     * each owner with an {@code @AssociationOverride} ({@code @CollectionTable} on the field of the shared embeddable would win over it).
     */
    private boolean sharedEmbeddedCollection(HibernatePersistentProperty property) {
        return sharedEmbeddedCollections.contains(sharedKey(property.hibernateOwner, property.name))
    }

    /**
     * The collections of the embedded type of an embedded property, and of the embedded types nested in it, that more than one embedded
     * property reaches, each with its path relative to the embedded property and the dotted path of the embedded property that holds it.
     */
    private void sharedCollectionPeers(
            HibernateEmbeddedProperty property, String relative, String binderPath, List<Class<?>> visiting, List<SharedCollection> into) {
        GrailsHibernatePersistentEntity type = (GrailsHibernatePersistentEntity) property.associatedEntity
        if (type == null || visiting.contains(type.javaClass)) {
            return
        }
        for (HibernatePersistentProperty peer : embeddedPeers(property)) {
            if (isComponent(peer)) {
                sharedCollectionPeers((HibernateEmbeddedProperty) peer, relative.isEmpty() ? peer.name : "${relative}.${peer.name}".toString(),
                        "${binderPath}.${peer.name}".toString(), visiting + [type.javaClass], into)
            } else if (peer instanceof HibernateToManyProperty && sharedEmbeddedCollection(peer)) {
                into << new SharedCollection(relative.isEmpty() ? peer.name : "${relative}.${peer.name}".toString(), binderPath, peer)
            }
        }
    }

    private static record SharedCollection(String relativePath, String binderPath, HibernatePersistentProperty property) { }

    /**
     * The {@code @AssociationOverride}s that state the table and the key of each shared collection of an embedded property for its
     * owner: the table and the key column of the binder for the first embedded property that reaches the collection, names qualified with
     * the owner and the path for the others.
     */
    private List<AnnotationDescription> sharedCollectionOverrides(HibernateEmbeddedProperty property) {
        List<SharedCollection> shared = []
        sharedCollectionPeers(property, '', property.name, [], shared)
        return shared.collect { SharedCollection collection ->
            String qualifier = embeddedCollectionQualifier(property.hibernateOwner, collection.binderPath(), collection.property().name)
            AnnotationDescription.Builder joinTable
            if (collection.property() instanceof HibernateToManyEntityProperty) {
                ToManyFacets facets = toManyFacets((HibernateToManyEntityProperty) collection.property(), qualifier)
                if (facets.mappedBy() != null || !facets.manyToMany()) {
                    return null
                }
                joinTable = AnnotationDescription.Builder.ofType(JpaJoinTable)
                        .define('name', facets.tableName())
                        .defineAnnotationArray('joinColumns', TypeDescription.ForLoadedType.of(JoinColumn), keyJoinColumns(facets.keys(), facets.referencedKeys()))
                        .defineAnnotationArray('inverseJoinColumns', TypeDescription.ForLoadedType.of(JoinColumn), keyJoinColumns(facets.elements(), facets.referencedElements()))
                if (facets.schema()) {
                    joinTable = joinTable.define('schema', facets.schema())
                }
                if (facets.catalog()) {
                    joinTable = joinTable.define('catalog', facets.catalog())
                }
            } else {
                CollectionFacets facets = collectionFacets((HibernateBasicProperty) collection.property(), qualifier)
                joinTable = AnnotationDescription.Builder.ofType(JpaJoinTable)
                        .define('name', facets.tableName())
                        .defineAnnotationArray('joinColumns', TypeDescription.ForLoadedType.of(JoinColumn), keyJoinColumns(facets.keys(), facets.referencedKeys()))
                if (facets.schema()) {
                    joinTable = joinTable.define('schema', facets.schema())
                }
                if (facets.catalog()) {
                    joinTable = joinTable.define('catalog', facets.catalog())
                }
            }
            return AnnotationDescription.Builder.ofType(AssociationOverride)
                    .define('name', collection.relativePath())
                    .define('joinTable', joinTable.build())
                    .build()
        }.findAll { AnnotationDescription override -> override != null }
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
            List<EmbeddedLeaf> leaves = embeddedLeaves(property)
            List<AnnotationDescription> associationOverrides = leaves
                    .findAll { EmbeddedLeaf leaf -> leaf.toOne() != null }
                    .collect { EmbeddedLeaf leaf ->
                        AnnotationDescription.Builder.ofType(AssociationOverride)
                                .define('name', leaf.path())
                                .defineAnnotationArray('joinColumns', TypeDescription.ForLoadedType.of(JoinColumn),
                                        leaf.toOne().joinColumns().size() > 1 ?
                                                keyJoinColumns(leaf.toOne().joinColumns(), leaf.toOne().referencedColumns()) :
                                                [joinColumnAnnotation(leaf.column())] as AnnotationDescription[])
                                .build()
                    }
            associationOverrides.addAll(sharedCollectionOverrides(property))
            if (!associationOverrides.isEmpty()) {
                annotations << AnnotationDescription.Builder.ofType(AssociationOverrides)
                        .defineAnnotationArray('value', TypeDescription.ForLoadedType.of(AssociationOverride),
                                associationOverrides as AnnotationDescription[])
                        .build()
            }
            List<AnnotationDescription> overrides = leaves
                    .findAll { EmbeddedLeaf leaf -> leaf.column() != null && leaf.toOne() == null }
                    .collect { EmbeddedLeaf leaf ->
                        AnnotationDescription.Builder.ofType(AttributeOverride)
                                .define('name', leaf.path())
                                .define('column', columnAnnotation(leaf.column(), leaf.property().type))
                                .build()
                    }
            if (!overrides.isEmpty()) {
                annotations << AnnotationDescription.Builder.ofType(AttributeOverrides)
                        .defineAnnotationArray('value', TypeDescription.ForLoadedType.of(AttributeOverride),
                                overrides as AnnotationDescription[])
                        .build()
            }
        }
        Boolean naturalMutable = naturalIdMutable(property)
        if (naturalMutable != null) {
            annotations << AnnotationDescription.Builder.ofType(HibernateNaturalId).define('mutable', naturalMutable).build()
        }
        for (Annotation audit : auditAnnotations(property)) {
            annotations << AnnotationDescription.ForLoadedAnnotation.of(audit)
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
        // Hibernate checks the declared element type of a collection of entities against the class it finds for the declaring type by
        // name, and retries a failed check without end. It finds the class generated for an entity under the entity's name, which has
        // no getters to check, but would find the real class of an embedded type with its getters and the generated class of the
        // entity as the element type, so the embeddable that holds such a collection takes the name of the embedded type like an entity.
        boolean holdsEntities = peers.any { HibernatePersistentProperty peer -> peer instanceof HibernateToManyEntityProperty }
        String base = holdsEntities ? type.javaClass.name : generatedEmbeddableName(type)
        String name = base
        int variant = 1
        if (holdsEntities && embeddables.values().any { DynamicType.Unloaded<?> other -> other.typeDescription.name == name }) {
            throw new UnsupportedOperationException(
                    "The embedded type [${type.name}] holds a collection of entities and is embedded with two different sets of " +
                            'properties, which the generator cannot describe: Hibernate needs the class generated for it to carry the name of the type')
        }
        while (embeddables.values().any { DynamicType.Unloaded<?> other -> other.typeDescription.name == name }) {
            name = base + '_' + (++variant)
        }
        DynamicType.Builder<Object> builder = (DynamicType.Builder<Object>) new ByteBuddy()
                .subclass(Object)
                .name(name)
                .annotateType(AnnotationDescription.Builder.ofType(Embeddable).build())
        for (HibernatePersistentProperty peer : peers) {
            builder = isComponent(peer) ?
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

    private static List<AnnotationDescription> typeAnnotations(TypeFacets facets) {
        if (facets.converter() != null) {
            List<AnnotationDescription> converted = [AnnotationDescription.Builder.ofType(Convert)
                    .define('converter', TypeDescription.ForLoadedType.of(facets.converter())).build()]
            if (facets.jdbcTypeCode() != null) {
                converted << AnnotationDescription.Builder.ofType(JdbcTypeCode).define('value', facets.jdbcTypeCode().intValue()).build()
            }
            return converted
        }
        if (facets.jdbcTypeCode() != null) {
            return [AnnotationDescription.Builder.ofType(JdbcTypeCode).define('value', facets.jdbcTypeCode().intValue()).build()]
        }
        List<AnnotationDescription> parameters = facets.parameters().collect { String name, String value ->
            AnnotationDescription.Builder.ofType(Parameter).define('name', name).define('value', value).build()
        }
        return [AnnotationDescription.Builder.ofType(Type)
                .define('value', TypeDescription.ForLoadedType.of(facets.userType()))
                .defineAnnotationArray('parameters', TypeDescription.ForLoadedType.of(Parameter),
                        parameters as AnnotationDescription[])
                .build()]
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
        return columnAnnotation(columnFacets(property), property.type)
    }

    /**
     * The scale of a decimal column that states a precision and no scale. The domain binder sets the precision and leaves the
     * scale null, so the database default scale of the type applies (2 for a {@code BigDecimal}); {@code @Column} cannot leave a
     * scale unset next to a precision (its {@code scale} is 0 by default and Hibernate then forces 0), so the default is stated.
     */
    private static Integer statedScale(ColumnFacets facets, Class<?> javaType) {
        if (facets.scale() == null && facets.precision() != null && javaType == BigDecimal) {
            return Size.DEFAULT_SCALE
        }
        return facets.scale()
    }

    private static AnnotationDescription columnAnnotation(ColumnFacets facets, Class<?> javaType = null) {
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
        Integer scale = statedScale(facets, javaType)
        if (scale != null) {
            annotation = annotation.define('scale', scale)
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
     * The annotations of other libraries that Hibernate or its integrations read from the entity class and that the
     * generated field must therefore carry: the Bean Validation constraints and the Envers annotations.
     */
    List<Annotation> carriedAnnotations(HibernatePersistentProperty property) {
        List<Annotation> carried = validationAnnotations(property)
        carried.addAll(auditAnnotations(property))
        return carried
    }

    /**
     * The Bean Validation constraints declared on the property. Hibernate turns them into DDL (not null, precision,
     * scale, length) after binding, so they are copied onto the generated field for Hibernate to apply itself.
     */
    List<Annotation> validationAnnotations(HibernatePersistentProperty property) {
        return memberAnnotations(property, VALIDATION_PACKAGES)
    }

    /**
     * The Hibernate Envers annotations ({@code @Audited}, {@code @NotAudited}, {@code @AuditJoinTable}, ...) declared on the
     * property. Envers reads them from the bound entity's class, which is the generated class while the mappings are bound,
     * so they are copied onto the generated field. Envers is not a dependency of this module: the annotations are recognised by
     * package.
     */
    List<Annotation> auditAnnotations(HibernatePersistentProperty property) {
        return memberAnnotations(property, AUDIT_PACKAGES)
    }

    /**
     * The Hibernate Envers annotations declared on the domain class itself ({@code @Audited}, {@code @AuditTable},
     * {@code @AuditOverride}, ...), which the generated class carries so that Envers audits the entity.
     */
    List<Annotation> auditAnnotations(GrailsHibernatePersistentEntity entity) {
        return entity.javaClass.declaredAnnotations.findAll { Annotation a -> inPackages(a, AUDIT_PACKAGES) } as List<Annotation>
    }

    private static List<Annotation> memberAnnotations(HibernatePersistentProperty property, List<String> packages) {
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
        // a field and its getter may both carry the annotation: a class can state it once
        Set<Class<? extends Annotation>> seen = new HashSet<Class<? extends Annotation>>()
        return found.findAll { Annotation a -> inPackages(a, packages) && seen.add(a.annotationType()) }
    }

    private static boolean inPackages(Annotation annotation, List<String> packages) {
        String type = annotation.annotationType().name
        return packages.any { String prefix -> type.startsWith(prefix) }
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
