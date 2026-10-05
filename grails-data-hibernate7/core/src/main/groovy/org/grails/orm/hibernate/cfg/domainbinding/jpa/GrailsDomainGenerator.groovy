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

import groovy.transform.CompileStatic
import jakarta.persistence.Column as JpaColumn
import jakarta.persistence.DiscriminatorColumn
import jakarta.persistence.DiscriminatorType
import jakarta.persistence.DiscriminatorValue
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Inheritance
import jakarta.persistence.InheritanceType
import jakarta.persistence.PrimaryKeyJoinColumn
import jakarta.persistence.Table
import jakarta.persistence.Version
import net.bytebuddy.ByteBuddy
import net.bytebuddy.description.annotation.AnnotationDescription
import net.bytebuddy.description.modifier.TypeManifestation
import net.bytebuddy.description.modifier.Visibility
import net.bytebuddy.description.type.TypeDescription
import net.bytebuddy.dynamic.DynamicType
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy
import org.hibernate.Length
import org.hibernate.annotations.BatchSize
import org.hibernate.annotations.ColumnDefault
import org.hibernate.annotations.ColumnTransformer
import org.hibernate.annotations.Comment
import org.hibernate.annotations.DiscriminatorFormula
import org.hibernate.annotations.DiscriminatorOptions
import org.hibernate.annotations.DynamicInsert
import org.hibernate.annotations.DynamicUpdate
import org.hibernate.annotations.Formula
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.annotations.Parameter
import org.hibernate.annotations.Type
import org.hibernate.annotations.UuidGenerator
import org.hibernate.id.uuid.UuidGenerator as HibernateUuidGenerator
import org.hibernate.mapping.Column
import org.hibernate.generator.Assigned
import org.hibernate.generator.Generator
import org.hibernate.type.BasicType
import org.hibernate.type.spi.TypeConfiguration
import org.hibernate.usertype.UserType

import org.grails.orm.hibernate.cfg.ColumnConfig
import org.grails.orm.hibernate.cfg.DiscriminatorConfig
import org.grails.orm.hibernate.cfg.HibernateSimpleIdentity
import org.grails.orm.hibernate.cfg.IdentityEnumType
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
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateEnumProperty
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernatePersistentProperty
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateSimpleIdentityProperty
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateSimpleProperty
import org.grails.orm.hibernate.cfg.domainbinding.util.ColumnNameForPropertyAndPathFetcher
import org.grails.orm.hibernate.cfg.domainbinding.util.GrailsEnumType

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
        List<GrailsHibernatePersistentEntity> ordered = new ArrayList<GrailsHibernatePersistentEntity>(given)
        ordered.sort { GrailsHibernatePersistentEntity a, GrailsHibernatePersistentEntity b -> depth(a) <=> depth(b) }

        Map<GrailsHibernatePersistentEntity, DynamicType.Unloaded<?>> made = [:]
        Map<TypeDescription, byte[]> types = [:]
        for (GrailsHibernatePersistentEntity entity : ordered) {
            TypeDescription superType = entity.isRoot() ?
                    TypeDescription.ForLoadedType.of(Object) : made.get(superEntity(entity, given)).typeDescription
            DynamicType.Unloaded<?> unloaded = make(entity, superType)
            made.put(entity, unloaded)
            types.putAll(unloaded.allTypes)
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

    private DynamicType.Unloaded<?> make(GrailsHibernatePersistentEntity entity, TypeDescription superType) {
        HierarchyFacets hierarchy = hierarchyFacets(entity)
        DynamicType.Builder<Object> builder = (DynamicType.Builder<Object>) new ByteBuddy()
                .subclass(superType)
                .name(generatedClassName(entity))
                .annotateType(classAnnotations(entity, hierarchy) as AnnotationDescription[])
        if (hierarchy.abstractClass()) {
            builder = builder.modifiers(Visibility.PUBLIC, TypeManifestation.ABSTRACT)
        }
        if (entity.isRoot()) {
            builder = defineIdentityAndVersion(builder, entity)
        }
        for (HibernatePersistentProperty property : entity.persistentPropertiesToBind) {
            if (!supports(property)) {
                throw new UnsupportedOperationException(unsupportedReason(entity, property))
            }
            builder = defineField(builder, property, [])
        }
        return builder.make()
    }

    private DynamicType.Builder<Object> defineIdentityAndVersion(
            DynamicType.Builder<Object> builder, GrailsHibernatePersistentEntity entity) {
        HibernatePersistentProperty identity = (HibernatePersistentProperty) entity.identity
        if (!(identity instanceof HibernateSimpleIdentityProperty)) {
            throw new UnsupportedOperationException(
                    "Entity [${entity.name}] has no simple identifier (a composite identifier, for example), " +
                            'which the generator does not support yet')
        }
        List<AnnotationDescription> idAnnotations = [AnnotationDescription.Builder.ofType(Id).build()]
        idAnnotations.addAll(idGeneratorAnnotations(idFacets(entity)))
        DynamicType.Builder<Object> result = defineField(builder, identity, idAnnotations)
        HibernatePersistentProperty version = entity.version
        if (version != null) {
            result = defineField(result, version, [AnnotationDescription.Builder.ofType(Version).build()])
        }
        return result
    }

    private String unsupportedReason(GrailsHibernatePersistentEntity entity, HibernatePersistentProperty property) {
        if (property instanceof HibernateSimpleProperty && !decideType(property).supported) {
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
        if (strategy != InheritanceType.SINGLE_TABLE) {
            throw new UnsupportedOperationException("Inheritance strategy ${strategy} of [${entity.name}] is not supported yet")
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
     *     derived (formula) property, the version, an enum, or the simple identifier. Custom types,
     *     multi-column properties and every association are not supported yet.
     */
    boolean supports(HibernatePersistentProperty property) {
        if (property instanceof HibernateSimpleIdentityProperty) {
            return property.hibernateOwner.isRoot()
        }
        if (!(property instanceof HibernateSimpleProperty)) {
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
        Class<?> type = isEnum ? ((HibernateEnumProperty) property).enumType : property.type
        String name = property.getTypeName(type)
        // a non-enum property is bound with its own class name when nothing says otherwise
        boolean explicit = name != null && (isEnum || type == null || name != type.name)
        Map<String, String> parameters = [:]
        if (isEnum) {
            // EnumTypeBinder replaces the configured type parameters with the enum class
            parameters[GrailsDomainBinder.ENUM_CLASS_PROP] = type.name
        } else {
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
     * Decides the column facets for a supported property by running the domain binder's own rules on a scratch
     * {@link Column}, in the order the binder applies them.
     */
    ColumnFacets columnFacets(HibernatePersistentProperty property) {
        if (property instanceof HibernateEnumProperty) {
            return enumColumnFacets((HibernateEnumProperty) property)
        }
        return basicColumnFacets(property)
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

    private ColumnFacets basicColumnFacets(HibernatePersistentProperty property) {
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
        String name = columnNames.getColumnNameForPropertyAndPath(property, null, columnConfig)
        Class<?> type = property.type
        if (type != null && (String.isAssignableFrom(type) || byte[].isAssignableFrom(type))) {
            stringConstraints.bindStringColumnConstraints(column, mappedForm, property.typeName)
        } else if (type != null && Number.isAssignableFrom(type)) {
            numericConstraints.bindNumericColumnConstraints(column, columnConfig, mappedForm, type)
        }
        return facets(property, column, name, isNullable(property),
                mappedForm.isUnique() && !mappedForm.isUniqueWithinGroup(), true)
    }

    /** Mirrors {@code EnumTypeBinder}: only the column config rules apply, and the column config's own uniqueness. */
    private ColumnFacets enumColumnFacets(HibernateEnumProperty property) {
        PropertyConfig mappedForm = property.hibernateMappedForm
        Column column = new Column()
        ColumnConfig columnConfig = firstColumnConfig(mappedForm)
        if (columnConfig != null) {
            columnConfigBinder.bindColumnConfigToColumn(column, columnConfig, mappedForm)
        }
        String name = property.resolveEnumColumnName(namingStrategy, columnNames, null)
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

    private List<AnnotationDescription> classAnnotations(GrailsHibernatePersistentEntity entity, HierarchyFacets hierarchy) {
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
            DynamicType.Builder<Object> builder, HibernatePersistentProperty property, List<AnnotationDescription> extra) {
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
        ColumnFacets facets = columnFacets(property)
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
     *     {@code SimpleValueBinder} does for every derived property except an enum
     */
    boolean isDerived(HibernatePersistentProperty property) {
        return property.hibernateMappedForm.derived && !(property instanceof HibernateEnumProperty)
    }

    private static boolean isNullable(HibernatePersistentProperty property) {
        if (property instanceof HibernateSimpleIdentityProperty) {
            return false
        }
        if (!property.hibernateOwner.isRoot()) {
            Mapping mapping = property.hibernateOwner.hibernateMappedForm
            return mapping != null && mapping.tablePerHierarchy ? true : property.nullable
        }
        return property.nullable
    }
}
