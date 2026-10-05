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
import org.hibernate.FetchMode
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
import org.hibernate.mapping.Bag
import org.hibernate.mapping.BasicValue
import org.hibernate.mapping.Column
import org.hibernate.mapping.Collection as HibernateCollection
import org.hibernate.mapping.Component
import org.hibernate.mapping.DependantValue
import org.hibernate.mapping.IndexedCollection
import org.hibernate.mapping.Formula
import org.hibernate.mapping.JoinedSubclass
import org.hibernate.mapping.List as HibernateList
import org.hibernate.mapping.Map as HibernateMap
import org.hibernate.mapping.PersistentClass
import org.hibernate.mapping.Property
import org.hibernate.mapping.RootClass
import org.hibernate.mapping.Set as HibernateSet
import org.hibernate.mapping.Table
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
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateBasicProperty
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateEmbeddedProperty
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateEnumProperty
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernatePersistentProperty
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateSimpleIdentityProperty
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateSimpleProperty
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateToManyProperty
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
        int embeddedProperties = 0
        int embeddedLeaves = 0
        Map<String, Integer> explicitTypes = [:].withDefault { 0 }
        Map<String, Integer> collections = [:].withDefault { 0 }
        Map<String, Integer> known = [:].withDefault { 0 }
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
                                    property instanceof HibernateEmbeddedProperty ?
                                            "embedded: ${generator.unsupportedReason(entity, property).replaceAll(/\[[^\]]*\]/, '[..]')}".toString() :
                                            property instanceof HibernateBasicProperty ?
                                                    "${property.getClass().simpleName}: ${generator.unsupportedReason(entity, property).replaceAll(/\[[^\]]*\]/, '[..]')}".toString() :
                                                    property.getClass().simpleName]++
                            continue
                        }
                        if (!generator.validationAnnotations(property).isEmpty()) {
                            skipped['bean validation constraints (applied by Hibernate after binding)']++
                            continue
                        }
                        Property bound = boundProperty(entity.persistentClass, property)
                        if (property instanceof HibernateEmbeddedProperty) {
                            HibernateEmbeddedProperty embedded = (HibernateEmbeddedProperty) property
                            List<EmbeddedLeaf> leaves = generator.embeddedLeaves(embedded)
                            if (leaves.any { EmbeddedLeaf leaf -> !generator.validationAnnotations(leaf.property).isEmpty() }) {
                                skipped['bean validation constraints (applied by Hibernate after binding)']++
                            } else if (bound == null || !(bound.value instanceof Component)) {
                                skipped['embedded property with no bound component']++
                            } else {
                                embeddedProperties++
                                embeddedLeaves += leaves.size()
                                mismatches.addAll(compareEmbedded(entity, embedded, leaves, generator, bound, explicitTypes))
                            }
                            continue
                        }
                        if (property instanceof HibernateBasicProperty) {
                            if (!generator.validationAnnotations(property).isEmpty()) {
                                skipped['bean validation constraints (applied by Hibernate after binding)']++
                            } else if (bound == null || !(bound.value instanceof HibernateCollection)) {
                                skipped['collection property with no bound collection']++
                            } else {
                                CollectionFacets facets = generator.collectionFacets((HibernateBasicProperty) property)
                                collections["${facets.kind()}${property instanceof HibernateEnumProperty ? ' of enums' : ''}".toString()]++
                                mismatches.addAll(compareCollection(
                                        "${entity.name}.${property.name}".toString(), (HibernateBasicProperty) property, facets,
                                        generator, (HibernateCollection) bound.value, explicitTypes, known))
                            }
                            continue
                        }
                        if (bound != null && generator.isDerived(property)) {
                            compared++
                            derived++
                            mismatches.addAll(compareDerived("${entity.name}.${property.name}".toString(), property, bound))
                            mismatches.addAll(compareType("${entity.name}.${property.name}".toString(), property, generator, bound, explicitTypes))
                            continue
                        }
                        if (bound == null || bound.columns.size() != 1) {
                            skipped['no single bound column']++
                            continue
                        }
                        compared++
                        String where = "${entity.name}.${property.name}".toString()
                        mismatches.addAll(compare(where, generator.columnFacets(property), bound))
                        if (property instanceof HibernateEnumProperty && generator.typeFacets(property) == null) {
                            mismatches.addAll(compareEnum(where, (HibernateEnumProperty) property, generator, bound))
                        }
                        if (!(property instanceof HibernateSimpleIdentityProperty)) {
                            mismatches.addAll(compareType(where, property, generator, bound, explicitTypes))
                        }
                    }
                }
                mismatches.addAll(compareAnnotationBoundHierarchies(generator, boundEntities, skipped, annotationRead, known))
            } finally {
                datastore.close()
            }
        }
        StringBuilder report = new StringBuilder()
        report << "differential: ${candidates.size()} candidate classes in ${groups.size()} groups; " +
                "${unbootable.size()} groups could not boot alone; ${entities} entities, ${compared} properties compared " +
                "(${derived} derived); ${embeddedProperties} embedded properties compared, ${embeddedLeaves} embedded columns\n"
        report << "explicit types compared: ${explicitTypes}\n"
        report << "collections of basic values compared by kind: ${collections}\n"
        report << "id generators compared by strategy: ${strategies}\n"
        report << "entities compared by hierarchy role: ${hierarchies}\n"
        report << "hierarchies read back through Hibernate's annotation binder: ${annotationRead}\n"
        report << "known divergences (explained in the plan, not mismatches): ${known}\n"
        report << "unsupported by kind: ${skipped}\n"
        report << "mismatches by facet: ${mismatches.groupBy { (it =~ /\s(\w+): generator=/)[0][1] }.collectEntries { k, v -> [k, v.size()] }}\n"
        unbootable.each { report << "unbootable: ${it.key.take(120)} -> ${it.value.take(200)}\n" }
        mismatches.each { report << "MISMATCH ${it}\n" }
        new File('build/differential-report.txt').text = report.toString()

        then:
        compared > 200
        mismatches.isEmpty()
    }

    private List<String> compare(String where, ColumnFacets facets, Property bound) {
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
            "${where} ${facet}: generator=${values[0]} binder=${values[1]}".toString()
        }
    }

    /**
     * A collection of basic values or enums as {@code CollectionBinder} and its second passes bound it: the kind of
     * collection, its table, the key, element and index columns, and the fetching.
     */
    private List<String> compareCollection(
            String where, HibernateBasicProperty property, CollectionFacets facets, GrailsDomainGenerator generator,
            HibernateCollection collection, Map<String, Integer> explicitTypes, Map<String, Integer> known) {
        List<String> found = []
        Map<String, List> pairs = [
                kind     : [facets.kind(), kindOf(collection)],
                table    : [facets.tableName().replace('`', ''), collection.collectionTable.name],
                schema   : [facets.schema(), collection.collectionTable.schema],
                catalog  : [facets.catalog(), collection.collectionTable.catalog],
                lazy     : [facets.lazy(), collection.lazy],
                extraLazy: [false, collection.extraLazy],
                fetchMode: [facets.fetchMode(), collection.fetchMode == FetchMode.JOIN ? FetchMode.JOIN : FetchMode.SELECT],
                batchSize: [facets.batchSize(), Math.max(collection.batchSize, 0)],
                cache    : [facets.cacheUsage(), collection.cacheConcurrencyStrategy],
                inverse  : [false, collection.inverse],
        ]
        found.addAll(pairs.findAll { String facet, List values -> values[0] != values[1] }.collect { String facet, List values ->
            "${where} ${facet}: generator=${values[0]} binder=${values[1]}".toString()
        })
        found.addAll(keyUpdatable(where, property, facets, (DependantValue) collection.key, known))
        found.addAll(compareValueColumn("${where} key".toString(), facets.key(), collection.key, collection.collectionTable, true))
        Property element = new Property()
        element.value = collection.element
        found.addAll(compareValueColumn("${where} element".toString(), facets.element(), collection.element, collection.collectionTable, false))
        if (property instanceof HibernateEnumProperty && generator.typeFacets(property) == null) {
            found.addAll(compareEnum("${where} element".toString(), (HibernateEnumProperty) property, generator, element))
        }
        found.addAll(compareType("${where} element".toString(), property, generator, element, explicitTypes))
        List<String> described = [facets.key().name().replace('`', ''), facets.element().name().replace('`', '')]
        if (facets.index() != null) {
            described << facets.index().name().replace('`', '')
            if (collection instanceof IndexedCollection) {
                found.addAll(compareValueColumn(
                        "${where} index".toString(), facets.index(), ((IndexedCollection) collection).index, collection.collectionTable, false))
            } else {
                found << "${where} index: generator=${facets.index().name()} binder=none".toString()
            }
        } else if (collection instanceof IndexedCollection) {
            found << "${where} index: generator=none binder=${((IndexedCollection) collection).index.selectables*.text}".toString()
        }
        List<String> unused = collection.collectionTable.columns*.name.findAll { String name -> !described.contains(name) }
        if (!unused.isEmpty()) {
            if (facets.kind() == CollectionKind.MAP && unused.size() == 1) {
                known['the binder leaves an unused column in the table of a map of values (the element bound before the map replaces it)']++
            } else {
                found << "${where} tableColumns: generator=${described} binder=${collection.collectionTable.columns*.name}".toString()
            }
        }
        return found
    }

    /**
     * {@code CollectionKeyColumnUpdater} makes the key updatable only while the owner has at most one unidirectional to-many
     * property. Hibernate then refuses to insert or delete the rows of every collection of that owner, so the generator
     * never states it (and could not: a join column is insertable and updatable alike). The binder's value is therefore
     * pinned to that rule here, and the owners it hits are counted as a known defect.
     */
    private static List<String> keyUpdatable(
            String where, HibernateBasicProperty property, CollectionFacets facets, DependantValue key, Map<String, Integer> known) {
        List<String> found = []
        long unidirectional = property.hibernateOwner.persistentPropertiesToBind.count {
            it instanceof HibernateToManyProperty && !((HibernateToManyProperty) it).isBidirectional()
        }
        boolean defect = unidirectional > 1
        if (!facets.key().updatable()) {
            found << "${where} keyUpdatable: generator=false binder=${key.updateable}".toString()
        }
        if (key.updateable != !defect) {
            found << "${where} keyUpdatable: generator=${!defect} binder=${key.updateable}".toString()
        }
        if (defect) {
            known['the binder makes the key of a collection not updatable when its owner has several unidirectional to-many properties, so Hibernate never writes their rows']++
        }
        return found
    }

    /**
     * The single column of a collection's key, element or index value, against what the generator decided for it. The key's
     * size facets come from the referenced identifier, and a column of a primary key is not null whatever the binder said:
     * Hibernate makes it so when it creates the primary key.
     */
    private static List<String> compareValueColumn(String where, ColumnFacets facets, org.hibernate.mapping.Value value, Table table, boolean key) {
        List<Column> columns = value.selectables.findAll { it instanceof Column }.collect { (Column) it }
        if (columns.size() != 1) {
            return ["${where} columns: generator=1 binder=${columns.size()}".toString()]
        }
        Column column = columns[0]
        boolean primaryKey = table.primaryKey != null && table.primaryKey.columns.contains(column)
        Map<String, List> pairs = [
                name    : [facets.name().replace('`', ''), column.name],
                nullable: [facets.nullable() && !primaryKey, column.nullable],
                unique  : [facets.unique(), column.unique],
        ]
        if (!key) {
            pairs.length = [facets.length(), column.length?.intValue()]
            pairs.precision = [facets.precision(), column.precision?.intValue()]
            pairs.scale = [facets.scale(), column.scale?.intValue()]
        }
        if (facets.sqlType() != null) {
            pairs.sqlType = [facets.sqlType(), column.sqlType]
        }
        return pairs.findAll { String facet, List values -> values[0] != values[1] }.collect { String facet, List values ->
            "${where} ${facet}: generator=${values[0]} binder=${values[1]}".toString()
        }
    }

    private static CollectionKind kindOf(HibernateCollection collection) {
        if (collection instanceof HibernateList) {
            return CollectionKind.LIST
        }
        if (collection instanceof HibernateMap) {
            return CollectionKind.MAP
        }
        if (collection instanceof HibernateSet) {
            return CollectionKind.SET
        }
        return collection instanceof Bag ? CollectionKind.BAG : null
    }

    /**
     * An embedded property is a Component bound by {@code ComponentBinder}: the generator must name the same column-bearing
     * leaves as the bound component (nested components included) and decide each leaf's column facets, type and enum
     * style the way the binder bound them.
     */
    private List<String> compareEmbedded(
            GrailsHibernatePersistentEntity entity, HibernateEmbeddedProperty property, List<EmbeddedLeaf> leaves,
            GrailsDomainGenerator generator, Property bound, Map<String, Integer> explicitTypes) {
        List<String> found = []
        String where = "${entity.name}.${property.name}"
        Map<String, Property> boundLeaves = terminalProperties(bound).collectEntries { String path, Property leaf ->
            [(path.substring(property.name.length() + 1)): leaf]
        }
        if (leaves*.path().toSet() != boundLeaves.keySet()) {
            found << "${where} leaves: generator=${leaves*.path()} binder=${boundLeaves.keySet()}".toString()
            return found
        }
        for (EmbeddedLeaf leaf : leaves) {
            Property boundLeaf = boundLeaves[leaf.path()]
            String leafWhere = "${where}.${leaf.path()}".toString()
            if (leaf.column() == null) {
                found.addAll(compareDerived(leafWhere, leaf.property, boundLeaf))
            } else if (boundLeaf.columns.size() != 1) {
                found << "${leafWhere} columns: generator=1 binder=${boundLeaf.columns.size()}".toString()
                continue
            } else {
                found.addAll(compare(leafWhere, leaf.column(), boundLeaf))
                if (leaf.property instanceof HibernateEnumProperty && generator.typeFacets(leaf.property) == null) {
                    found.addAll(compareEnum(leafWhere, (HibernateEnumProperty) leaf.property, generator, boundLeaf))
                }
            }
            found.addAll(compareType(leafWhere, leaf.property, generator, boundLeaf, explicitTypes))
        }
        return found
    }

    /** The terminal properties of a property, keyed by dotted path from the property: a component is walked, anything else is its own leaf. */
    private static Map<String, Property> terminalProperties(Property property) {
        Map<String, Property> result = [:]
        if (property.value instanceof Component) {
            for (Property inner : ((Component) property.value).properties) {
                if (inner.value instanceof Component) {
                    terminalProperties(inner).each { String path, Property leaf -> result["${property.name}.${path}".toString()] = leaf }
                } else {
                    result["${property.name}.${inner.name}".toString()] = inner
                }
            }
        } else {
            result[property.name] = property
        }
        return result
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
    private List<String> compareDerived(String where, HibernatePersistentProperty property, Property bound) {
        List<String> formulas = bound.value.selectables.findAll { it instanceof Formula }.collect { ((Formula) it).getFormula() }
        List<String> columns = bound.value.selectables.findAll { it instanceof Column }.collect { ((Column) it).name }
        List<String> found = []
        if (formulas != [property.hibernateMappedForm.formula]) {
            found << "${where} formula: generator=[${property.hibernateMappedForm.formula}] binder=${formulas}".toString()
        }
        if (!columns.isEmpty()) {
            found << "${where} columns: generator=[] binder=${columns}".toString()
        }
        return found
    }

    /**
     * The explicit type the generator states must be the one the binder put on the bound value: the same
     * {@code UserType} class and parameters, or the same JDBC type for a registered type name; with no explicit type the
     * binder's type name is the property's own class and it has no parameters.
     */
    private List<String> compareType(
            String where, HibernatePersistentProperty property, GrailsDomainGenerator generator,
            Property bound, Map<String, Integer> explicitTypes) {
        BasicValue value = (BasicValue) bound.value
        TypeFacets facets = generator.typeFacets(property)
        // a collection property is typed with its element's class
        Class<?> type = property instanceof HibernateBasicProperty ? ((HibernateBasicProperty) property).componentType : property.type
        List<String> found = []
        Map<String, String> actualParameters = [:]
        value.typeParameters?.stringPropertyNames()?.each { String key -> actualParameters[key] = value.typeParameters.getProperty(key) }
        if (facets == null) {
            if (!(property instanceof HibernateEnumProperty) && value.typeName != type.name) {
                found << "${where} typeName: generator=${type.name} binder=${value.typeName}".toString()
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
            if (value.typeName != property.getTypeName(type) || actual != facets.jdbcTypeCode()) {
                found << "${where} jdbcTypeCode: generator=${facets.jdbcTypeCode()} binder=${actual} (type ${value.typeName})".toString()
            }
        }
        return found
    }

    private List<String> compareEnum(
            String where, HibernateEnumProperty property, GrailsDomainGenerator generator, Property bound) {
        BasicValue value = (BasicValue) bound.value
        String actual = value.typeName == IdentityEnumType.name ? 'IDENTITY' : value.enumerationStyle?.name()
        String expected = generator.enumStyle(property)
        return expected == actual ? [] : ["${where} enumStyle: generator=${expected} binder=${actual}".toString()]
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
            Map<String, Integer> annotationRead, Map<String, Integer> known) {
        List<String> found = []
        Map<GrailsHibernatePersistentEntity, List<GrailsHibernatePersistentEntity>> hierarchies = [:]
        for (GrailsHibernatePersistentEntity entity : entities) {
            hierarchies.get(entity.hibernateRootEntity, []) << entity
        }
        for (Map.Entry<GrailsHibernatePersistentEntity, List<GrailsHibernatePersistentEntity>> hierarchy : hierarchies.entrySet()) {
            Map<GrailsHibernatePersistentEntity, Class<?>> classes
            try {
                classes = generator.generateAll(hierarchy.value, getClass().classLoader)
            } catch (UnsupportedOperationException | IllegalArgumentException e) {
                skipped["${hierarchy.value.size() > 1 ? 'hierarchy' : 'entity'} not generated whole: ${e.getClass().simpleName}".toString()]++
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
                annotationRead["${generator.hierarchyFacets(hierarchy.key).strategy() ?: 'single class'} (${classes.size()} classes)".toString()]++
                Map<String, GrailsHibernatePersistentEntity> byName = hierarchy.value.collectEntries { [(it.name): it] }
                classes.each { GrailsHibernatePersistentEntity entity, Class<?> generated ->
                    found.addAll(compareAnnotationBound(generator, entity, metadata.getEntityBinding(generated.name), byName, annotationRead, known))
                }
            } finally {
                StandardServiceRegistryBuilder.destroy(registry)
            }
        }
        return found
    }

    private static List<String> compareAnnotationBound(
            GrailsDomainGenerator generator, GrailsHibernatePersistentEntity entity, PersistentClass annotated,
            Map<String, GrailsHibernatePersistentEntity> byName, Map<String, Integer> annotationRead, Map<String, Integer> known) {
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
        Map<String, Map<String, Object>> expectations = leafExpectations(generator, entity)
        for (Property property : bound.declaredProperties) {
            Property other = annotated.hasProperty(property.name) ? annotated.getProperty(property.name) : null
            if (other == null) {
                continue
            }
            if (property.value instanceof HibernateCollection) {
                HibernateBasicProperty collectionProperty = (HibernateBasicProperty) entity.persistentPropertiesToBind.find { it.name == property.name }
                if (!generator.validationAnnotations(collectionProperty).isEmpty()) {
                    continue
                }
                annotationRead['collections']++
                found.addAll(compareAnnotatedCollection(
                        "${where} property ${property.name}".toString(), collectionProperty, property, other,
                        generator.collectionFacets(collectionProperty), known))
                continue
            }
            Map<String, Property> boundLeaves = terminalProperties(property)
            Map<String, Property> annotatedLeaves = terminalProperties(other)
            if (boundLeaves.keySet() != annotatedLeaves.keySet()) {
                found << "${where} property ${property.name} leaves: generator=${annotatedLeaves.keySet()} binder=${boundLeaves.keySet()}".toString()
                continue
            }
            boundLeaves.each { String path, Property leaf ->
                Map<String, Object> expected = expectations[path] ?: [:]
                if (property.value instanceof Component) {
                    annotationRead['embedded columns']++
                }
                if (!expected.validated) {
                    found.addAll(compareAnnotatedLeaf(
                            "${where} property ${path}".toString(), leaf, annotatedLeaves[path], (String) expected.sqlType, false))
                }
            }
        }
        return found
    }

    /**
     * A collection of the binder's entity against the one Hibernate's annotation binder built: the same kind, table, key,
     * element and index columns, and the same fetching and caching.
     */
    private static List<String> compareAnnotatedCollection(
            String where, HibernateBasicProperty source, Property boundProperty, Property annotatedProperty, CollectionFacets facets,
            Map<String, Integer> known) {
        if (!(annotatedProperty.value instanceof HibernateCollection)) {
            return ["${where} kind: generator=${annotatedProperty.value.getClass().simpleName} binder=${boundProperty.value.getClass().simpleName}".toString()]
        }
        HibernateCollection bound = (HibernateCollection) boundProperty.value
        HibernateCollection annotated = (HibernateCollection) annotatedProperty.value
        Map<String, List> pairs = [
                kind        : [kindOf(bound), kindOf(annotated)],
                table       : [bound.collectionTable.name, annotated.collectionTable.name],
                schema      : [bound.collectionTable.schema, annotated.collectionTable.schema],
                catalog     : [bound.collectionTable.catalog, annotated.collectionTable.catalog],
                lazy        : [bound.lazy, annotated.lazy],
                extraLazy   : [bound.extraLazy, annotated.extraLazy],
                fetchMode   : [bound.fetchMode == FetchMode.JOIN ? FetchMode.JOIN : FetchMode.SELECT,
                               annotated.fetchMode == FetchMode.JOIN ? FetchMode.JOIN : FetchMode.SELECT],
                batchSize   : [Math.max(bound.batchSize, 0), Math.max(annotated.batchSize, 0)],
                cache       : [bound.cacheConcurrencyStrategy, annotated.cacheConcurrencyStrategy],
                inverse     : [bound.inverse, annotated.inverse],
                orphanDelete: [bound.hasOrphanDelete(), annotated.hasOrphanDelete()],
                keyUpdatable: [facets.key().updatable(), annotated.key.updateable],
                propertyLazy: [boundProperty.lazy, annotatedProperty.lazy],
        ]
        List<String> found = pairs.findAll { String facet, List values -> values[0] != values[1] }.collect { String facet, List values ->
            "${where} ${facet}: generator=${values[1]} binder=${values[0]}".toString()
        }
        // Hibernate forces the key column of a collection table NOT NULL ("I break the spec, but it's for good"), the binder leaves it nullable
        if (annotated.key.nullable) {
            found << "${where} keyNullable: generator=${annotated.key.nullable} binder=${bound.key.nullable}".toString()
        }
        if (bound.key.nullable) {
            known['Hibernate makes the key column of a collection table NOT NULL; the binder leaves it nullable unless it is part of the primary key']++
        }
        // a collection of values has nothing to cascade to; the binder still states a cascade on its property
        if (annotatedProperty.cascade != null && annotatedProperty.cascade != boundProperty.cascade) {
            found << "${where} propertyCascade: generator=${annotatedProperty.cascade} binder=${boundProperty.cascade}".toString()
        }
        if (annotatedProperty.cascade != boundProperty.cascade) {
            known['the binder states a cascade on a collection of values, which cascades to nothing; the annotation path states none']++
        }
        Map<String, List> values = [
                key    : [bound.key, annotated.key, facets.key()],
                element: [bound.element, annotated.element, facets.element()],
        ]
        if (bound instanceof IndexedCollection && annotated instanceof IndexedCollection) {
            values.index = [((IndexedCollection) bound).index, ((IndexedCollection) annotated).index, facets.index()]
        }
        values.each { String part, List triple ->
            Property b = new Property()
            b.value = (org.hibernate.mapping.Value) triple[0]
            Property a = new Property()
            a.value = (org.hibernate.mapping.Value) triple[1]
            found.addAll(compareAnnotatedLeaf("${where} ${part}".toString(), b, a, ((ColumnFacets) triple[2]).sqlType(), part == 'key'))
        }
        return found
    }

    /**
     * One terminal property of the binder's entity against the one Hibernate's annotation binder built: the same columns
     * or formulas, the same nullability, and every column facet the binder states (length, precision, scale, unique,
     * explicit SQL type, default, read and write expressions, comment) must have reached the annotation-built column.
     */
    private static List<String> compareAnnotatedLeaf(String where, Property bound, Property annotated, String explicitSqlType, boolean key) {
        List<String> boundSelectables = bound.selectables.collect { it instanceof Column ? ((Column) it).name : "formula:${((Formula) it).getFormula()}" }
        List<String> annotatedSelectables = annotated.selectables.collect { it instanceof Column ? ((Column) it).name : "formula:${((Formula) it).getFormula()}" }
        List<Column> boundColumns = bound.selectables.findAll { it instanceof Column }.collect { (Column) it }
        List<Column> annotatedColumns = annotated.selectables.findAll { it instanceof Column }.collect { (Column) it }
        List<Boolean> boundNullable = boundColumns*.nullable
        List<Boolean> annotatedNullable = annotatedColumns*.nullable
        // a collection's key columns are NOT NULL in the annotation path whatever the binder did (see compareAnnotatedCollection), and their size comes from the referenced identifier
        if (boundSelectables != annotatedSelectables || (!key && boundNullable != annotatedNullable)) {
            return ["${where} columns: generator=${annotatedSelectables}${annotatedNullable} binder=${boundSelectables}${boundNullable}".toString()]
        }
        List<String> found = []
        for (int i = 0; i < boundColumns.size(); i++) {
            Column boundColumn = boundColumns[i]
            Column annotatedColumn = annotatedColumns[i]
            Map<String, List> pairs = [
                    unique   : [boundColumn.unique, annotatedColumn.unique],
                    length   : [boundColumn.length?.intValue(), annotatedColumn.length?.intValue()],
                    precision: [boundColumn.precision?.intValue(), annotatedColumn.precision?.intValue()],
                    scale    : [boundColumn.scale?.intValue(), annotatedColumn.scale?.intValue()],
                    sqlType  : [explicitSqlType, annotatedColumn.sqlType],
                    default  : [boundColumn.defaultValue, annotatedColumn.defaultValue],
                    read     : [boundColumn.customRead, annotatedColumn.customRead],
                    write    : [boundColumn.customWrite, annotatedColumn.customWrite],
                    comment  : [boundColumn.comment, annotatedColumn.comment],
            ]
            // Hibernate fills in defaults the binder leaves unset (length 255): only what the binder states is comparable,
            // and an SQL type is only stated when the mapping says so (the generator's own decision, passed in)
            pairs = pairs.findAll { String facet, List values -> facet in ['unique', 'sqlType'] || values[0] != null }
            if (key) {
                pairs.remove('length')
                pairs.remove('precision')
                pairs.remove('scale')
            }
            found.addAll(pairs.findAll { String facet, List values -> values[0] != values[1] }.collect { String facet, List values ->
                "${where} ${facet}: generator=${values[1]} binder=${values[0]}".toString()
            })
        }
        return found
    }

    /**
     * What the generator expects of each column-bearing property path of an entity, by dotted path: the explicit SQL
     * type it states and whether Bean Validation constraints sit on the property (Hibernate turns those into DDL after
     * binding, which the annotation read-back does not run, so such a property cannot be compared there).
     */
    private static Map<String, Map<String, Object>> leafExpectations(GrailsDomainGenerator generator, GrailsHibernatePersistentEntity entity) {
        Map<String, Map<String, Object>> found = [:]
        List<HibernatePersistentProperty> properties = []
        if (entity.isRoot()) {
            properties << (HibernatePersistentProperty) entity.identity
            if (entity.version != null) {
                properties << entity.version
            }
        }
        properties.addAll(entity.persistentPropertiesToBind)
        for (HibernatePersistentProperty property : properties) {
            if (property instanceof HibernateEmbeddedProperty) {
                generator.embeddedLeaves((HibernateEmbeddedProperty) property).each { EmbeddedLeaf leaf ->
                    found["${property.name}.${leaf.path()}".toString()] = [
                            sqlType  : leaf.column()?.sqlType(),
                            validated: !generator.validationAnnotations(leaf.property).isEmpty(),
                    ]
                }
            } else if (property instanceof HibernateBasicProperty) {
                found[property.name] = [sqlType: null, validated: !generator.validationAnnotations(property).isEmpty()]
            } else {
                found[property.name] = [
                        sqlType  : generator.isDerived(property) ? null : generator.columnFacets(property).sqlType(),
                        validated: !generator.validationAnnotations(property).isEmpty(),
                ]
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
