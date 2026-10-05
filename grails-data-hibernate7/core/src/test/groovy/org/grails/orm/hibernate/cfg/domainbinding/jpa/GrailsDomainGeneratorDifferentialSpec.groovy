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

import grails.gorm.annotation.Entity
import jakarta.persistence.InheritanceType
import grails.gorm.tests.HibernateGormDatastoreSpec
import org.hibernate.Length
import org.hibernate.boot.Metadata
import org.hibernate.boot.MetadataSources
import org.hibernate.boot.registry.BootstrapServiceRegistryBuilder
import org.hibernate.boot.registry.StandardServiceRegistry
import org.hibernate.boot.registry.StandardServiceRegistryBuilder
import org.hibernate.dialect.H2Dialect
import org.hibernate.engine.spi.SessionFactoryImplementor
import org.hibernate.generator.Generator
import org.hibernate.id.enhanced.OptimizerFactory
import org.hibernate.id.enhanced.SequenceStyleGenerator
import org.hibernate.id.enhanced.TableGenerator
import org.hibernate.mapping.BasicValue
import org.hibernate.mapping.Column
import org.hibernate.mapping.Formula
import org.hibernate.mapping.JoinedSubclass
import org.hibernate.mapping.PersistentClass
import org.hibernate.mapping.Property
import org.hibernate.mapping.RootClass
import org.hibernate.mapping.SingleTableSubclass
import org.hibernate.mapping.UnionSubclass
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type

import org.grails.orm.hibernate.HibernateDatastore
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider
import org.springframework.core.type.filter.AnnotationTypeFilter

import org.grails.orm.hibernate.cfg.IdentityEnumType
import org.grails.orm.hibernate.cfg.domainbinding.binder.ColumnConfigToColumnBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.NumericColumnConstraintsBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.StringColumnConstraintsBinder
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateEnumProperty
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernatePersistentProperty
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateSimpleIdentityProperty
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateSimpleProperty
import org.grails.orm.hibernate.cfg.domainbinding.util.BackticksRemover
import org.grails.orm.hibernate.cfg.domainbinding.util.ColumnNameForPropertyAndPathFetcher
import org.grails.orm.hibernate.cfg.domainbinding.util.DefaultColumnNameFetcher

/**
 * Compares {@link GrailsDomainGenerator} with the domain binder on real entities. For every supported property of
 * every entity the binder can bind, the column facets the generator decides must equal the ones the binder put on the
 * bound Hibernate {@code Column} and {@code Property}. The binder is the oracle, so a mismatch is either a generator
 * bug or a rule that was mirrored wrongly.
 *
 * <p>The entities are every domain class in the TCK and in the Hibernate 7 tests, which are written to exercise
 * binder permutations. They are grouped by association so each group boots as its own datastore; a group that cannot
 * boot alone is reported with its reason, and properties the generator does not support yet are counted by kind, so
 * the coverage gap stays visible.</p>
 */
class GrailsDomainGeneratorDifferentialSpec extends HibernateGormDatastoreSpec {

    private static final List<String> PACKAGES = [
            'org.apache.grails.data.testing.tck.domains',
            'org.grails.orm.hibernate',
            'grails.gorm',
            'grails.orm',
    ]

    void "the generator decides the same column facets as the binder"() {
        given:
        GrailsDomainGenerator generator = newGenerator()
        List<Class<?>> candidates = findEntities()
        List<List<Class<?>>> groups = groupByAssociation(candidates)
        List<String> mismatches = []
        Map<String, Integer> skipped = [:].withDefault { 0 }
        Map<String, String> unbootable = [:]
        int compared = 0
        int entities = 0
        int derived = 0
        Map<String, Integer> explicitTypes = [:].withDefault { 0 }
        Map<String, Integer> strategies = [:].withDefault { 0 }
        Map<String, Integer> hierarchies = [:].withDefault { 0 }
        Map<String, Integer> annotationRead = [:].withDefault { 0 }

        when:
        for (List<Class<?>> group : groups) {
            HibernateDatastore datastore
            try {
                datastore = new HibernateDatastore(group as Class[])
            } catch (Throwable e) {
                unbootable[group*.simpleName.join(',')] = (e.message ?: e.getClass().simpleName).readLines().first()
                continue
            }
            try {
                List<GrailsHibernatePersistentEntity> boundEntities = datastore.mappingContext.getHibernatePersistentEntities()
                        .findAll { group.contains(it.javaClass) && it.persistentClass != null && it.persistentClass.entityName == it.name }
                for (GrailsHibernatePersistentEntity entity : boundEntities) {
                    entities++
                    List<HibernatePersistentProperty> properties = []
                    HierarchyFacets hierarchy = null
                    try {
                        hierarchy = generator.hierarchyFacets(entity)
                    } catch (UnsupportedOperationException e) {
                        skipped["hierarchy: ${e.message.replaceAll(/\[[^\]]*\]/, '[..]')}".toString()]++
                    }
                    if (hierarchy != null) {
                        hierarchies["${hierarchy.strategy() ?: 'none'}${entity.isRoot() ? ' root' : ' subclass'}".toString()]++
                        mismatches.addAll(compareEntity(entity, generator.entityFacets(entity), hierarchy))
                        mismatches.addAll(compareHierarchy(entity, hierarchy))
                    }
                    if (entity.isRoot()) {
                        if (entity.identity instanceof HibernateSimpleIdentityProperty) {
                            mismatches.addAll(compareIdentifierGenerator(
                                    entity, generator.idFacets(entity), (SessionFactoryImplementor) datastore.sessionFactory, strategies))
                        } else {
                            skipped['entity without a simple identifier']++
                        }
                        if (entity.version != null) {
                            properties << entity.version
                        }
                        if (entity.identity != null) {
                            properties << (HibernatePersistentProperty) entity.identity
                        }
                    }
                    properties.addAll(entity.persistentPropertiesToBind)
                    for (HibernatePersistentProperty property : properties) {
                        if (!generator.supports(property)) {
                            skipped[property instanceof HibernateSimpleProperty ?
                                    "${property.getClass().simpleName} (type ${property.getTypeName()})".toString() :
                                    property.getClass().simpleName]++
                            continue
                        }
                        if (!generator.validationAnnotations(property).isEmpty()) {
                            skipped['bean validation constraints (applied by Hibernate after binding)']++
                            continue
                        }
                        Property bound = boundProperty(entity.persistentClass, property)
                        if (bound != null && generator.isDerived(property)) {
                            compared++
                            derived++
                            mismatches.addAll(compareDerived(entity, property, bound))
                            mismatches.addAll(compareType(entity, property, generator, bound, explicitTypes))
                            continue
                        }
                        if (bound == null || bound.columns.size() != 1) {
                            skipped['no single bound column']++
                            continue
                        }
                        compared++
                        mismatches.addAll(compare(entity, property, generator.columnFacets(property), bound))
                        if (property instanceof HibernateEnumProperty && generator.typeFacets(property) == null) {
                            mismatches.addAll(compareEnum(entity, (HibernateEnumProperty) property, generator, bound))
                        }
                        if (!(property instanceof HibernateSimpleIdentityProperty)) {
                            mismatches.addAll(compareType(entity, property, generator, bound, explicitTypes))
                        }
                    }
                }
                mismatches.addAll(compareAnnotationBoundHierarchies(generator, boundEntities, skipped, annotationRead))
            } finally {
                datastore.close()
            }
        }
        StringBuilder report = new StringBuilder()
        report << "differential: ${candidates.size()} candidate classes in ${groups.size()} groups; " +
                "${unbootable.size()} groups could not boot alone; ${entities} entities, ${compared} properties compared " +
                "(${derived} derived)\n"
        report << "explicit types compared: ${explicitTypes}\n"
        report << "id generators compared by strategy: ${strategies}\n"
        report << "entities compared by hierarchy role: ${hierarchies}\n"
        report << "hierarchies read back through Hibernate's annotation binder: ${annotationRead}\n"
        report << "unsupported by kind: ${skipped}\n"
        report << "mismatches by facet: ${mismatches.groupBy { (it =~ /\s(\w+): generator=/)[0][1] }.collectEntries { k, v -> [k, v.size()] }}\n"
        unbootable.each { report << "unbootable: ${it.key.take(120)} -> ${it.value.take(200)}\n" }
        mismatches.each { report << "MISMATCH ${it}\n" }
        new File('build/differential-report.txt').text = report.toString()

        then:
        compared > 200
        mismatches.isEmpty()
    }

    private List<String> compare(
            GrailsHibernatePersistentEntity entity, HibernatePersistentProperty property, ColumnFacets facets, Property bound) {
        Column column = (Column) bound.columns[0]
        Map<String, List> pairs = [
                name      : [facets.name().replace('`', ''), column.name],
                quoted    : [facets.name().startsWith('`'), column.quoted],
                nullable  : [facets.nullable(), column.nullable],
                unique    : [facets.unique(), column.unique],
                insertable: [facets.insertable(), bound.insertable],
                updatable : [facets.updatable(), bound.updateable],
                length    : [facets.length(), column.length?.intValue()],
                precision : [facets.precision(), column.precision?.intValue()],
                scale     : [facets.scale(), column.scale?.intValue()],
                sqlType   : [facets.sqlType(), column.sqlType],
                default   : [facets.defaultValue(), column.defaultValue],
                read      : [facets.read(), column.customRead],
                write     : [facets.write(), column.customWrite],
                comment   : [facets.comment(), column.comment],
        ]
        if (facets.sqlType() == null) {
            // Hibernate derives an sqlType for every column after binding; only an explicit one is comparable
            pairs.remove('sqlType')
        }
        return pairs.findAll { String facet, List values -> values[0] != values[1] }.collect { String facet, List values ->
            "${entity.name}.${property.name} ${facet}: generator=${values[0]} binder=${values[1]}".toString()
        }
    }

    /**
     * The generator class the binder installed on the identifier must be the one the generator names for the
     * strategy, and the parameters the generator passes on must have reached it.
     */
    private List<String> compareIdentifierGenerator(
            GrailsHibernatePersistentEntity entity, IdFacets facets, SessionFactoryImplementor sessionFactory,
            Map<String, Integer> strategies) {
        Generator bound = sessionFactory.mappingMetamodel.getEntityDescriptor(entity.name).generator
        strategies["${facets.strategy().name}".toString()]++
        List<String> found = []
        String where = "${entity.name} identifier"
        if (bound.getClass() != facets.generatorClass()) {
            found << "${where} generatorClass: generator=${facets.generatorClass().name} binder=${bound.getClass().name}".toString()
            return found
        }
        Map<String, String> parameters = facets.parameters()
        if (bound instanceof SequenceStyleGenerator) {
            String sequence = parameters['sequence_name'] ?: parameters['sequence']
            if (sequence != null && !bound.databaseStructure.physicalName.objectName.text.equalsIgnoreCase(sequence)) {
                found << "${where} sequenceName: generator=${sequence} binder=${bound.databaseStructure.physicalName.objectName.text}".toString()
            }
            if (parameters['increment_size'] != null && bound.optimizer.incrementSize != parameters['increment_size'].toInteger()) {
                found << "${where} incrementSize: generator=${parameters['increment_size']} binder=${bound.optimizer.incrementSize}".toString()
            }
            if (parameters['optimizer'] != null && bound.optimizer.class !=
                    OptimizerFactory.StandardOptimizerDescriptor.fromExternalName(parameters['optimizer']).optimizerClass) {
                found << "${where} optimizer: generator=${parameters['optimizer']} binder=${bound.optimizer.class.name}".toString()
            }
        } else if (bound instanceof TableGenerator) {
            if (parameters['table_name'] != null && !bound.tableName.toLowerCase().endsWith(parameters['table_name'].toLowerCase())) {
                found << "${where} tableName: generator=${parameters['table_name']} binder=${bound.tableName}".toString()
            }
            if (parameters['segment_value'] != null && bound.segmentValue != parameters['segment_value']) {
                found << "${where} segmentValue: generator=${parameters['segment_value']} binder=${bound.segmentValue}".toString()
            }
            if (parameters['increment_size'] != null && bound.incrementSize != parameters['increment_size'].toInteger()) {
                found << "${where} incrementSize: generator=${parameters['increment_size']} binder=${bound.incrementSize}".toString()
            }
        }
        return found
    }

    /** A derived property is a Formula with the same text and no column. */
    private List<String> compareDerived(GrailsHibernatePersistentEntity entity, HibernatePersistentProperty property, Property bound) {
        List<String> formulas = bound.value.selectables.findAll { it instanceof Formula }.collect { ((Formula) it).getFormula() }
        List<String> columns = bound.value.selectables.findAll { it instanceof Column }.collect { ((Column) it).name }
        List<String> found = []
        if (formulas != [property.hibernateMappedForm.formula]) {
            found << "${entity.name}.${property.name} formula: generator=[${property.hibernateMappedForm.formula}] binder=${formulas}".toString()
        }
        if (!columns.isEmpty()) {
            found << "${entity.name}.${property.name} columns: generator=[] binder=${columns}".toString()
        }
        return found
    }

    /**
     * The explicit type the generator states must be the one the binder put on the bound value: the same
     * {@code UserType} class and parameters, or the same JDBC type for a registered type name; with no explicit type the
     * binder's type name is the property's own class and it has no parameters.
     */
    private List<String> compareType(
            GrailsHibernatePersistentEntity entity, HibernatePersistentProperty property, GrailsDomainGenerator generator,
            Property bound, Map<String, Integer> explicitTypes) {
        BasicValue value = (BasicValue) bound.value
        TypeFacets facets = generator.typeFacets(property)
        String where = "${entity.name}.${property.name}"
        List<String> found = []
        Map<String, String> actualParameters = [:]
        value.typeParameters?.stringPropertyNames()?.each { String key -> actualParameters[key] = value.typeParameters.getProperty(key) }
        if (facets == null) {
            if (!(property instanceof HibernateEnumProperty) && value.typeName != property.type.name) {
                found << "${where} typeName: generator=${property.type.name} binder=${value.typeName}".toString()
            }
            if (!(property instanceof HibernateEnumProperty) && !actualParameters.isEmpty()) {
                found << "${where} typeParameters: generator=[:] binder=${actualParameters}".toString()
            }
        } else if (facets.userType() != null) {
            explicitTypes["UserType ${facets.userType().simpleName}".toString()]++
            if (value.typeName != facets.userType().name) {
                found << "${where} typeName: generator=${facets.userType().name} binder=${value.typeName}".toString()
            }
            if (actualParameters != facets.parameters()) {
                found << "${where} typeParameters: generator=${facets.parameters()} binder=${actualParameters}".toString()
            }
        } else {
            explicitTypes["registered ${value.typeName}".toString()]++
            Integer actual = value.resolve().jdbcType.defaultSqlTypeCode
            if (value.typeName != property.getTypeName() || actual != facets.jdbcTypeCode()) {
                found << "${where} jdbcTypeCode: generator=${facets.jdbcTypeCode()} binder=${actual} (type ${value.typeName})".toString()
            }
        }
        return found
    }

    private List<String> compareEnum(
            GrailsHibernatePersistentEntity entity, HibernateEnumProperty property, GrailsDomainGenerator generator, Property bound) {
        BasicValue value = (BasicValue) bound.value
        String actual = value.typeName == IdentityEnumType.name ? 'IDENTITY' : value.enumerationStyle?.name()
        String expected = generator.enumStyle(property)
        return expected == actual ? [] : ["${entity.name}.${property.name} enumStyle: generator=${expected} binder=${actual}".toString()]
    }

    private List<String> compareEntity(GrailsHibernatePersistentEntity entity, EntityFacets facets, HierarchyFacets hierarchy) {
        PersistentClass persistentClass = entity.persistentClass
        Map<String, List> pairs = [
                jpaName      : [facets.jpaName(), persistentClass.jpaEntityName],
                tableName    : [facets.tableName().replace('`', ''), persistentClass.table.name],
                dynamicInsert: [facets.dynamicInsert(), persistentClass.useDynamicInsert()],
                dynamicUpdate: [facets.dynamicUpdate(), persistentClass.useDynamicUpdate()],
                // the binder leaves a subclass's unset batch size at -1 and a root's at 0: both mean "not stated"
                batchSize    : [facets.batchSize(), Math.max(persistentClass.batchSize, 0)],
        ]
        if (hierarchy.ownsTable()) {
            pairs.comment = [facets.comment(), persistentClass.table.comment]
        }
        if (facets.schema() != null) {
            pairs.schema = [facets.schema(), persistentClass.table.schema]
        }
        if (facets.catalog() != null) {
            pairs.catalog = [facets.catalog(), persistentClass.table.catalog]
        }
        return pairs.findAll { String facet, List values -> values[0] != values[1] }.collect { String facet, List values ->
            "${entity.name} ${facet}: generator=${values[0]} binder=${values[1]}".toString()
        }
    }

    /**
     * Where the entity sits in its hierarchy: the Hibernate class the binder created for it, its direct superclass,
     * its table, abstractness and discriminator.
     */
    private List<String> compareHierarchy(GrailsHibernatePersistentEntity entity, HierarchyFacets facets) {
        PersistentClass persistentClass = entity.persistentClass
        Map<String, List> pairs = [
                kind              : [expectedKind(entity, facets), persistentClass.getClass().simpleName],
                superclass        : [facets.superclass(), persistentClass.superclass?.entityName],
                abstractClass     : [facets.abstractClass(), Boolean.TRUE == persistentClass.isAbstract()],
                abstractTable     : [facets.abstractTable(), persistentClass.table.isAbstract()],
                ownsTable         : [facets.ownsTable(),
                                     persistentClass.superclass == null || !persistentClass.table.is(persistentClass.superclass.table)],
                discriminatorValue: [facets.discriminatorValue(), persistentClass.discriminatorValue],
        ]
        List<String> found = pairs.findAll { String facet, List values -> values[0] != values[1] }.collect { String facet, List values ->
            "${entity.name} ${facet}: generator=${values[0]} binder=${values[1]}".toString()
        }
        if (persistentClass instanceof RootClass) {
            found.addAll(compareDiscriminator(entity, facets.discriminator(), (RootClass) persistentClass))
        }
        if (persistentClass instanceof JoinedSubclass) {
            List<String> keyColumns = ((JoinedSubclass) persistentClass).key.columns*.name
            if ([facets.keyColumn()] != keyColumns) {
                found << "${entity.name} keyColumn: generator=${[facets.keyColumn()]} binder=${keyColumns}".toString()
            }
        } else if (facets.keyColumn() != null) {
            found << "${entity.name} keyColumn: generator=${facets.keyColumn()} binder=none".toString()
        }
        return found
    }

    private static String expectedKind(GrailsHibernatePersistentEntity entity, HierarchyFacets facets) {
        if (entity.isRoot()) {
            return RootClass.simpleName
        }
        switch (facets.strategy()) {
            case InheritanceType.JOINED:
                return JoinedSubclass.simpleName
            case InheritanceType.TABLE_PER_CLASS:
                return UnionSubclass.simpleName
            default:
                return SingleTableSubclass.simpleName
        }
    }

    /** The discriminator the binder put on the root: a column or a formula, its type, length and whether it is inserted. */
    private List<String> compareDiscriminator(GrailsHibernatePersistentEntity entity, DiscriminatorFacets facets, RootClass root) {
        String where = "${entity.name} discriminator"
        if ((facets != null) != (root.discriminator != null)) {
            return ["${where} present: generator=${facets != null} binder=${root.discriminator != null}".toString()]
        }
        if (facets == null) {
            return []
        }
        BasicValue value = (BasicValue) root.discriminator
        List<String> formulas = value.selectables.findAll { it instanceof Formula }.collect { ((Formula) it).getFormula() }
        List<Column> columns = value.selectables.findAll { it instanceof Column }.collect { (Column) it }
        Map<String, List> pairs = [
                typeName  : [facets.typeName(), value.typeName],
                insertable: [facets.insertable(), root.isDiscriminatorInsertable()],
                formula   : [facets.formula() == null ? [] : [facets.formula()], formulas],
                column    : [facets.column() == null ? [] : [facets.column()], columns*.name],
        ]
        if (!columns.isEmpty()) {
            pairs.length = [facets.length(), columns[0].length?.intValue()]
            if (facets.sqlType() != null) {
                pairs.sqlType = [facets.sqlType(), columns[0].sqlType]
            }
        }
        return pairs.findAll { String facet, List values -> values[0] != values[1] }.collect { String facet, List values ->
            "${where} ${facet}: generator=${values[0]} binder=${values[1]}".toString()
        }
    }

    /**
     * For every hierarchy the generator can describe whole, binds the generated classes with Hibernate's own annotation
     * binder and compares the resulting {@code PersistentClass} of each entity with the one the domain binder built:
     * the link between what the generator decides and what Hibernate makes of the annotations.
     */
    private List<String> compareAnnotationBoundHierarchies(
            GrailsDomainGenerator generator, List<GrailsHibernatePersistentEntity> entities, Map<String, Integer> skipped,
            Map<String, Integer> annotationRead) {
        List<String> found = []
        Map<GrailsHibernatePersistentEntity, List<GrailsHibernatePersistentEntity>> hierarchies = [:]
        for (GrailsHibernatePersistentEntity entity : entities) {
            if (entity.isRoot() && entity.childEntities.isEmpty()) {
                continue
            }
            hierarchies.get(entity.hibernateRootEntity, []) << entity
        }
        for (Map.Entry<GrailsHibernatePersistentEntity, List<GrailsHibernatePersistentEntity>> hierarchy : hierarchies.entrySet()) {
            Map<GrailsHibernatePersistentEntity, Class<?>> classes
            try {
                classes = generator.generateAll(hierarchy.value, getClass().classLoader)
            } catch (UnsupportedOperationException | IllegalArgumentException e) {
                skipped["hierarchy not generated whole: ${e.getClass().simpleName}".toString()]++
                continue
            }
            StandardServiceRegistry registry = new StandardServiceRegistryBuilder(
                    new BootstrapServiceRegistryBuilder().applyClassLoader(classes.values().first().classLoader).build())
                    .applySetting('hibernate.dialect', H2Dialect.name)
                    .applySetting('hibernate.connection.url', 'jdbc:h2:mem:generator-differential;DB_CLOSE_DELAY=-1')
                    .build()
            try {
                MetadataSources sources = new MetadataSources(registry)
                classes.values().each { sources.addAnnotatedClass(it) }
                Metadata metadata
                try {
                    metadata = sources.buildMetadata()
                } catch (Exception e) {
                    found << "${hierarchy.key.name} annotationBinder: generator=accepted binder=${e.message?.readLines()?.first()}".toString()
                    continue
                }
                annotationRead["${generator.hierarchyFacets(hierarchy.key).strategy()} (${classes.size()} classes)".toString()]++
                Map<String, GrailsHibernatePersistentEntity> byName = hierarchy.value.collectEntries { [(it.name): it] }
                classes.each { GrailsHibernatePersistentEntity entity, Class<?> generated ->
                    found.addAll(compareAnnotationBound(entity, metadata.getEntityBinding(generated.name), byName))
                }
            } finally {
                StandardServiceRegistryBuilder.destroy(registry)
            }
        }
        return found
    }

    private static List<String> compareAnnotationBound(
            GrailsHibernatePersistentEntity entity, PersistentClass annotated, Map<String, GrailsHibernatePersistentEntity> byName) {
        PersistentClass bound = entity.persistentClass
        String where = "${entity.name} hibernate"
        if (annotated == null) {
            return ["${where} entity: generator=bound binder=missing".toString()]
        }
        Map<String, List> pairs = [
                kind              : [bound.getClass().simpleName, annotated.getClass().simpleName],
                superclass        : [bound.superclass == null ? null : GrailsDomainGenerator.generatedClassName(byName[bound.superclass.entityName]),
                                     annotated.superclass?.entityName],
                tableName         : [bound.table.name, annotated.table.name],
                ownsTable         : [bound.superclass == null || !bound.table.is(bound.superclass.table),
                                     annotated.superclass == null || !annotated.table.is(annotated.superclass.table)],
                abstractClass     : [Boolean.TRUE == bound.isAbstract(), Boolean.TRUE == annotated.isAbstract()],
                abstractUnionTable: [bound.table.isAbstractUnionTable(), annotated.table.isAbstractUnionTable()],
                properties        : [bound.declaredProperties*.name.toSet(), annotated.declaredProperties*.name.toSet()],
        ]
        if (bound instanceof SingleTableSubclass || bound instanceof RootClass && bound.discriminator != null) {
            pairs.discriminatorValue = [bound.discriminatorValue, annotated.discriminatorValue]
        }
        if (bound instanceof JoinedSubclass) {
            pairs.keyColumn = [((JoinedSubclass) bound).key.columns*.name, ((JoinedSubclass) annotated).key.columns*.name]
        }
        List<String> found = pairs.findAll { String facet, List values -> values[0] != values[1] }.collect { String facet, List values ->
            "${where} ${facet}: generator=${values[1]} binder=${values[0]}".toString()
        }
        if (bound instanceof RootClass) {
            found.addAll(compareAnnotatedDiscriminator(where, (RootClass) bound, (RootClass) annotated))
        }
        for (Property property : bound.declaredProperties) {
            Property other = annotated.hasProperty(property.name) ? annotated.getProperty(property.name) : null
            if (other == null) {
                continue
            }
            List<String> boundColumns = property.selectables.collect { it instanceof Column ? ((Column) it).name : "formula:${((Formula) it).getFormula()}" }
            List<String> annotatedColumns = other.selectables.collect { it instanceof Column ? ((Column) it).name : "formula:${((Formula) it).getFormula()}" }
            List<Boolean> boundNullable = property.columns*.nullable
            List<Boolean> annotatedNullable = other.columns*.nullable
            if (boundColumns != annotatedColumns || boundNullable != annotatedNullable) {
                found << ("${where} property ${property.name}: generator=${annotatedColumns}${annotatedNullable} " +
                        "binder=${boundColumns}${boundNullable}").toString()
            }
        }
        return found
    }

    private static List<String> compareAnnotatedDiscriminator(String where, RootClass bound, RootClass annotated) {
        if ((bound.discriminator != null) != (annotated.discriminator != null)) {
            return ["${where} discriminator: generator=${annotated.discriminator != null} binder=${bound.discriminator != null}".toString()]
        }
        if (bound.discriminator == null) {
            return []
        }
        List<String> boundSelectables = bound.discriminator.selectables.collect { it instanceof Column ? ((Column) it).name : "formula:${((Formula) it).getFormula()}" }
        List<String> annotatedSelectables = annotated.discriminator.selectables.collect { it instanceof Column ? ((Column) it).name : "formula:${((Formula) it).getFormula()}" }
        Map<String, List> pairs = [
                selectables: [boundSelectables, annotatedSelectables],
                jdbcType   : [((BasicValue) bound.discriminator).resolve().jdbcType.defaultSqlTypeCode,
                              ((BasicValue) annotated.discriminator).resolve().jdbcType.defaultSqlTypeCode],
                insertable : [bound.isDiscriminatorInsertable(), annotated.isDiscriminatorInsertable()],
        ]
        Column boundColumn = (Column) bound.discriminator.selectables.find { it instanceof Column }
        if (boundColumn != null && ((BasicValue) bound.discriminator).typeName == 'string') {
            // the binder leaves an unset length null, which Hibernate reads as its default
            pairs.length = [boundColumn.length ?: (long) Length.DEFAULT, ((Column) annotated.discriminator.selectables.first()).length]
        }
        return pairs.findAll { String facet, List values -> values[0] != values[1] }.collect { String facet, List values ->
            "${where} discriminator ${facet}: generator=${values[1]} binder=${values[0]}".toString()
        }
    }

    private static Property boundProperty(PersistentClass persistentClass, HibernatePersistentProperty property) {
        if (persistentClass.identifierProperty != null && persistentClass.identifierProperty.name == property.name) {
            return persistentClass.identifierProperty
        }
        return persistentClass.hasProperty(property.name) ? persistentClass.getProperty(property.name) : null
    }

    private GrailsDomainGenerator newGenerator() {
        def naming = getGrailsDomainBinder().getNamingStrategy()
        return new GrailsDomainGenerator(
                naming,
                new ColumnNameForPropertyAndPathFetcher(naming, new DefaultColumnNameFetcher(naming), new BackticksRemover()),
                new ColumnConfigToColumnBinder(),
                new StringColumnConstraintsBinder(),
                new NumericColumnConstraintsBinder(new H2Dialect()),
                getSessionFactory().typeConfiguration)
    }

    private static List<Class<?>> findEntities() {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false) {
            @Override
            protected boolean isCandidateComponent(AnnotatedBeanDefinition beanDefinition) {
                return true
            }
        }
        scanner.addIncludeFilter(new AnnotationTypeFilter(Entity))
        Set<Class<?>> found = new TreeSet<>({ Class a, Class b -> a.name <=> b.name } as Comparator<Class<?>>)
        for (String basePackage : PACKAGES) {
            for (def definition : scanner.findCandidateComponents(basePackage)) {
                found << Class.forName(definition.beanClassName)
            }
        }
        return found as List<Class<?>>
    }

    /** Splits the classes into groups that reference each other, so each group can be bound on its own. */
    private static List<List<Class<?>>> groupByAssociation(List<Class<?>> classes) {
        Map<Class<?>, Set<Class<?>>> edges = [:].withDefault { new HashSet<Class<?>>() }
        Set<Class<?>> known = classes.toSet()
        for (Class<?> type : classes) {
            edges[type]
            for (Class<?> referenced : referencedTypes(type)) {
                if (known.contains(referenced)) {
                    edges[type] << referenced
                    edges[referenced] << type
                }
            }
        }
        Set<Class<?>> visited = new HashSet<>()
        List<List<Class<?>>> groups = []
        for (Class<?> type : classes) {
            if (visited.contains(type)) {
                continue
            }
            List<Class<?>> group = []
            Deque<Class<?>> queue = new ArrayDeque<>([type])
            while (!queue.isEmpty()) {
                Class<?> next = queue.poll()
                if (visited.add(next)) {
                    group << next
                    queue.addAll(edges[next])
                }
            }
            groups << group.sort { it.name }
        }
        return groups
    }

    private static Set<Class<?>> referencedTypes(Class<?> type) {
        Set<Class<?>> referenced = new HashSet<>()
        if (type.superclass != null) {
            referenced << type.superclass
        }
        for (Field field : type.declaredFields) {
            if (Modifier.isStatic(field.modifiers)) {
                if (field.name in ['hasMany', 'belongsTo', 'hasOne']) {
                    field.accessible = true
                    collectClasses(field.get(null), referenced)
                }
            } else {
                addType(field.genericType, referenced)
            }
        }
        return referenced
    }

    private static void addType(Type type, Set<Class<?>> referenced) {
        if (type instanceof Class) {
            referenced << (Class<?>) type
        } else if (type instanceof ParameterizedType) {
            ((ParameterizedType) type).actualTypeArguments.each { addType(it, referenced) }
        }
    }

    private static void collectClasses(Object value, Set<Class<?>> referenced) {
        if (value instanceof Class) {
            referenced << (Class<?>) value
        } else if (value instanceof Map) {
            ((Map) value).values().each { collectClasses(it, referenced) }
        } else if (value instanceof Collection) {
            ((Collection) value).each { collectClasses(it, referenced) }
        }
    }
}
