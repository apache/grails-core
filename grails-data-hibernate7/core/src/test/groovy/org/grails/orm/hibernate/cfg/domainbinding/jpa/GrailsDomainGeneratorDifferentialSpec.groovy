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

import java.sql.Types

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
import org.hibernate.engine.OptimisticLockStyle
import org.hibernate.engine.jdbc.Size
import org.hibernate.engine.spi.FilterDefinition
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
import org.hibernate.mapping.FilterConfiguration
import org.hibernate.mapping.IndexedCollection
import org.hibernate.mapping.Formula
import org.hibernate.mapping.JoinedSubclass
import org.hibernate.mapping.List as HibernateList
import org.hibernate.mapping.Map as HibernateMap
import org.hibernate.mapping.ManyToOne
import org.hibernate.mapping.OneToMany
import org.hibernate.mapping.OneToOne
import org.hibernate.mapping.ToOne
import org.hibernate.mapping.Backref
import org.hibernate.mapping.IndexBackref
import org.hibernate.mapping.PersistentClass
import org.hibernate.mapping.Property
import org.hibernate.mapping.RootClass
import org.hibernate.mapping.Set as HibernateSet
import org.hibernate.mapping.Table
import org.hibernate.mapping.SingleTableSubclass
import org.hibernate.mapping.UnionSubclass
import org.hibernate.spi.NavigablePath

import org.grails.datastore.mapping.multitenancy.MultiTenancySettings
import org.grails.datastore.mapping.multitenancy.resolvers.SystemPropertyTenantResolver
import org.grails.datastore.mapping.reflect.ClassUtils
import org.grails.orm.hibernate.HibernateDatastore

import org.grails.orm.hibernate.cfg.IdentityEnumType
import org.grails.orm.hibernate.cfg.domainbinding.binder.ColumnConfigToColumnBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.NumericColumnConstraintsBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.StringColumnConstraintsBinder
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateBasicProperty
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateEmbeddedProperty
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateEnumProperty
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateManyToManyProperty
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernatePersistentProperty
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateSimpleIdentityProperty
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateManyToOneProperty
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateOneToManyProperty
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateToManyEntityProperty
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateSimpleProperty
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateToOneProperty
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

    void "the generator decides the same column facets as the binder"() {
        given:
        GrailsDomainGenerator generator = newGenerator()
        List<Class<?>> candidates = ScannedDomainClasses.findEntities()
        List<List<Class<?>>> groups = ScannedDomainClasses.groupByAssociation(candidates)
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
        Map<String, Integer> tenants = [:].withDefault { 0 }
        Map<String, Integer> strategies = [:].withDefault { 0 }
        Map<String, Integer> hierarchies = [:].withDefault { 0 }
        Map<String, Integer> annotationRead = [:].withDefault { 0 }
        Map<String, Integer> associations = [:].withDefault { 0 }
        Map<String, Integer> constraints = [:].withDefault { 0 }
        Map<String, Integer> naturals = [:].withDefault { 0 }
        Map<String, Integer> caches = [:].withDefault { 0 }
        Map<String, Integer> composites = [:].withDefault { 0 }
        List<String> rejectedComposites = []

        when:
        for (List<Class<?>> group : groups) {
            HibernateDatastore datastore
            try {
                datastore = boot(group)
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
                        if (hierarchy.ownsTable() && generator.generationProblem(entity) == null) {
                            mismatches.addAll(compareConstraints(entity, generator.constraintFacets(entity), constraints))
                        }
                        if (generator.generationProblem(entity) == null) {
                            mismatches.addAll(compareNaturalId(entity, generator.naturalIdFacets(entity), naturals))
                            mismatches.addAll(compareCache(entity, generator.cacheFacets(entity), caches))
                        }
                    }
                    mismatches.addAll(compareTenantFilter(entity, generator, (SessionFactoryImplementor) datastore.sessionFactory, skipped, tenants))
                    if (entity.isRoot()) {
                        if (entity.identity instanceof HibernateSimpleIdentityProperty) {
                            mismatches.addAll(compareIdentifierGenerator(
                                    entity, generator.idFacets(entity), (SessionFactoryImplementor) datastore.sessionFactory, strategies))
                        } else if (generator.generationProblem(entity) == null) {
                            mismatches.addAll(compareCompositeId(entity, generator.compositeIdFacets(entity), composites, known))
                        } else {
                            rejectedComposites << "${entity.name}: ${generator.generationProblem(entity)}".toString()
                            skipped["entity without a simple identifier: ${generator.generationProblem(entity).replaceAll(/\[[^\]]*\]/, '[..]')}".toString()]++
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
                                    "${property.getClass().simpleName}: ${generator.unsupportedReason(entity, property).replaceAll(/\[[^\]]*\]/, '[..]')}".toString() :
                                    property instanceof HibernateEmbeddedProperty ?
                                            "embedded: ${generator.unsupportedReason(entity, property).replaceAll(/\[[^\]]*\]/, '[..]')}".toString() :
                                            property instanceof HibernateBasicProperty || property instanceof HibernateToOneProperty || property instanceof HibernateToManyEntityProperty ?
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
                                mismatches.addAll(compareEmbedded(entity, embedded, leaves, generator, bound, explicitTypes, known))
                            }
                            continue
                        }
                        if (property instanceof HibernateToManyEntityProperty) {
                            if (bound == null || !(bound.value instanceof HibernateCollection)) {
                                skipped['collection property with no bound collection']++
                            } else {
                                ToManyFacets facets = generator.toManyFacets((HibernateToManyEntityProperty) property)
                                String shape = facets.manyToMany() ?
                                        (facets.mappedBy() != null ? 'many-to-many (inverse)' : property instanceof HibernateOneToManyProperty ?
                                                'one-to-many (join table)' : 'many-to-many (owning)') :
                                        (facets.mappedBy() != null ? 'one-to-many (inverse)' : 'one-to-many (owned foreign key)')
                                associations["${shape}, ${facets.kind()}".toString()]++
                                mismatches.addAll(compareToMany(
                                        "${entity.name}.${property.name}".toString(), (HibernateToManyEntityProperty) property, facets, bound, known))
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
                        if (property instanceof HibernateToOneProperty) {
                            if (bound == null || !(bound.value instanceof ToOne) ||
                                    (!(bound.value instanceof OneToOne) && bound.columns.isEmpty())) {
                                skipped['association with no bound column']++
                                continue
                            }
                            compared++
                            String where = "${entity.name}.${property.name}".toString()
                            associations[toOneKind(property, bound)]++
                            if (bound.value instanceof OneToOne) {
                                mismatches.addAll(compareOneToOne(where, generator.toOneFacets((HibernateToOneProperty) property), bound, known))
                                continue
                            }
                            // Hibernate copies the length, precision and scale of the referenced identifier onto a foreign key column after binding
                            ToOneFacets toOneFacets = generator.toOneFacets((HibernateToOneProperty) property)
                            if (toOneFacets.joinColumns().size() != bound.columns.size()) {
                                mismatches << "${where} columns: generator=${toOneFacets.joinColumns()*.name()} binder=${bound.columns*.name}".toString()
                                continue
                            }
                            if (bound.columns.size() > 1) {
                                associations['to-one to a composite identifier']++
                            }
                            mismatches.addAll(compareColumnsByName(where, toOneFacets.joinColumns(), bound, known, ['length', 'precision', 'scale']))
                            mismatches.addAll(compareToOne(where, toOneFacets, bound, known))
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
                        mismatches.addAll(compare(where, generator.columnFacets(property), bound, known, [], property instanceof HibernateEnumProperty))
                        if (property instanceof HibernateEnumProperty && generator.typeFacets(property) == null) {
                            mismatches.addAll(compareEnum(where, (HibernateEnumProperty) property, generator, bound))
                        }
                        if (!(property instanceof HibernateSimpleIdentityProperty)) {
                            mismatches.addAll(compareType(where, property, generator, bound, explicitTypes))
                        }
                    }
                }
                mismatches.addAll(compareAnnotationBoundHierarchies(generator, boundEntities, skipped, annotationRead, known, tenants))
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
        report << "associations compared by kind: ${associations}\n"
        report << "tenant filters compared: ${tenants}\n"
        report << "table constraints compared: ${constraints}\n"
        report << "natural ids compared: ${naturals}\n"
        report << "entity caches compared: ${caches}\n"
        report << "composite identifiers compared: ${composites}\n"
        rejectedComposites.each { report << "composite identifier rejected: ${it}\n" }
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

    /**
     * Boots a group of entities. GORM only gives an entity a tenant id (and the binder only adds the tenant filter) in
     * discriminator multi-tenancy mode, so a group with multi-tenant entities is booted in that mode when it can be.
     */
    private static HibernateDatastore boot(List<Class<?>> group) {
        if (group.any { Class<?> type -> ClassUtils.isMultiTenant(type) }) {
            try {
                return new HibernateDatastore([
                        'dataSource.dbCreate'                     : 'create-drop',
                        'grails.gorm.multiTenancy.mode'          : MultiTenancySettings.MultiTenancyMode.DISCRIMINATOR,
                        'grails.gorm.multiTenancy.tenantResolver': new SystemPropertyTenantResolver(),
                ], group as Class[])
            } catch (Exception ignored) {
                // a domain that needs another configuration is booted by default below
            }
        }
        return new HibernateDatastore(group as Class[])
    }

    /**
     * The column facets the generator decided against the bound column and property. A mapping that says {@code insertable: false}
     * or {@code updatable: false} is a known divergence, not a mismatch: {@code PropertyBinder} overwrites those flags with the
     * ones of the columns, which are always set, so the binder ignores the option (pinned in
     * {@link GrailsDomainBinderOptionDefectSpec}) and the generator states what the mapping asks for.
     */
    private List<String> compare(
            String where, ColumnFacets facets, Property bound, Map<String, Integer> known, Collection<String> ignore = [],
            boolean enumeration = false, int columnIndex = 0) {
        Column column = (Column) bound.columns[columnIndex]
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
        ignore.each { pairs.remove(it) }
        if (enumeration) {
            // EnumTypeBinder ignores the comment, default and read and write expressions of the column config (a binder defect,
            // pinned in GrailsDomainBinderOptionDefectSpec); the generator states them
            ['default', 'read', 'write', 'comment'].each { String facet ->
                if (pairs[facet][0] != null && pairs[facet][1] == null) {
                    known["the binder ignores the comment, default and read and write expressions of an enum column; the generator states them".toString()]++
                    pairs.remove(facet)
                }
            }
        }
        ['insertable', 'updatable'].each { String facet ->
            if (pairs[facet] != null && pairs[facet][0] == false && pairs[facet][1] == true) {
                known["the binder ignores insertable: false and updatable: false in a mapping (PropertyBinder overwrites them); the generator states them".toString()]++
                pairs.remove(facet)
            }
        }
        return pairs.findAll { String facet, List values -> values[0] != values[1] }.collect { String facet, List values ->
            "${where} ${facet}: generator=${values[0]} binder=${values[1]}".toString()
        }
    }

    /**
     * The indexes and multi-column unique keys on the table the entity owns: the same names, the same columns in the same
     * order. The unique key the binder adds for a natural id is not part of them (it is compared with the natural id).
     */
    private List<String> compareConstraints(GrailsHibernatePersistentEntity entity, ConstraintFacets facets, Map<String, Integer> constraints) {
        org.hibernate.mapping.Table table = entity.persistentClass.table
        String where = "${entity.name} table ${table.name}"
        Map<String, List<String>> boundIndexes = table.indexes.values().collectEntries { org.hibernate.mapping.Index index ->
            [(index.name): index.columns*.name]
        } as Map<String, List<String>>
        Map<String, List<String>> boundKeys = tableKeysWithoutNaturalId(entity.persistentClass)
        Map<String, List<String>> generatedIndexes = facets.indexes().collectEntries { IndexFacets index -> [(index.name()): index.columns()] } as Map<String, List<String>>
        Map<String, List<String>> generatedKeys = facets.uniqueKeys().findAll { UniqueKeyFacets key -> key.bound() }
                .collectEntries { UniqueKeyFacets key -> [(key.name()): key.columns()] } as Map<String, List<String>>
        constraints['unique keys the binder does not create'] += facets.uniqueKeys().count { UniqueKeyFacets key -> !key.bound() }
        constraints['indexes'] += boundIndexes.size()
        constraints['unique keys'] += boundKeys.size()
        List<String> found = []
        if (generatedIndexes != boundIndexes) {
            found << "${where} indexes: generator=${generatedIndexes} binder=${boundIndexes}".toString()
        }
        if (generatedKeys != boundKeys) {
            found << "${where} uniqueKeys: generator=${generatedKeys} binder=${boundKeys}".toString()
        }
        return found
    }

    /**
     * The columns of a property that has several (a foreign key to a composite identifier), each paired with the bound column of the
     * same name: the binder orders them like the referenced key once that key has been sorted, the generator like the mapping.
     */
    private List<String> compareColumnsByName(
            String where, List<ColumnFacets> columns, Property bound, Map<String, Integer> known, Collection<String> ignore) {
        List<String> found = []
        for (ColumnFacets facets : columns) {
            int index = bound.columns.findIndexOf { Column column -> column.name == facets.name().replace('`', '') }
            if (index < 0) {
                found << "${where} columns: generator=${columns*.name()} binder=${bound.columns*.name}".toString()
            } else {
                found.addAll(compare(where, facets, bound, known, ignore, false, index))
            }
        }
        return found
    }

    /**
     * The composite identifier as {@code CompositeIdBinder} bound it: one component with a property for every part, whose columns
     * are the primary key and so not null. A simple part must have the column facets of an ordinary property; a many-to-one part
     * the foreign key column and the associated entity (nothing cascades through an identifier, so the cascade the binder states on
     * the part is not compared).
     */
    private List<String> compareCompositeId(
            GrailsHibernatePersistentEntity entity, CompositeIdFacets facets, Map<String, Integer> composites, Map<String, Integer> known) {
        PersistentClass persistentClass = entity.persistentClass
        String where = "${entity.name} composite id"
        if (!(persistentClass.identifier instanceof Component)) {
            return ["${where} kind: generator=component binder=${persistentClass.identifier.getClass().simpleName}".toString()]
        }
        Component id = (Component) persistentClass.identifier
        composites['entities']++
        List<String> found = []
        if (facets.parts()*.path().toSet() != id.properties*.name.toSet()) {
            return ["${where} parts: generator=${facets.parts()*.path()} binder=${id.properties*.name}".toString()]
        }
        for (EmbeddedLeaf part : facets.parts()) {
            Property bound = id.getProperty(part.path())
            String partWhere = "${where} part ${part.path()}".toString()
            List<ColumnFacets> partColumns = part.toOne() != null ? part.toOne().joinColumns() : [part.column()]
            if (bound.columns.size() != partColumns.size()) {
                found << "${partWhere} columns: generator=${partColumns*.name()} binder=${bound.columns*.name}".toString()
                continue
            }
            if (part.toOne() != null) {
                composites['to-one parts']++
                found.addAll(compareColumnsByName(partWhere, partColumns, bound, known, ['length', 'precision', 'scale']))
                found.addAll(compareToOne(partWhere, part.toOne(), bound, known).findAll { String line ->
                    !line.contains(' cascade: ') && !line.contains(' optional: ')
                })
            } else {
                composites['simple parts']++
                found.addAll(compare(partWhere, part.column(), bound, known, [], part.property instanceof HibernateEnumProperty))
            }
        }
        Set<String> expectedKey = facets.parts().collectMany { EmbeddedLeaf part ->
            (part.toOne() != null ? part.toOne().joinColumns() : [part.column()])*.name()
        }*.replace('`', '').toSet()
        if (persistentClass.table.primaryKey?.columns*.name?.toSet() != expectedKey) {
            found << "${where} primaryKey: generator=${expectedKey} binder=${persistentClass.table.primaryKey?.columns*.name}".toString()
        }
        return found
    }

    /**
     * The composite identifier Hibernate's annotation binder reads from an {@code @IdClass}: no identifier property, as the
     * binder's, an embedded component with the same parts, the same columns (not null), the same primary key columns (the order is a listed
     * divergence) and the same foreign key columns. The binder's component has the unsaved value {@code undefined}; Hibernate's has none, and
     * Hibernate adds an {@code _identifierMapper} property and component to the entity: both listed.
     */
    private static List<String> compareAnnotatedCompositeId(
            String where, PersistentClass bound, PersistentClass annotated, CompositeIdFacets facets, Map<String, Integer> known) {
        if (!(annotated.identifier instanceof Component)) {
            return ["${where} compositeId: generator=${annotated.identifier.getClass().simpleName} binder=Component".toString()]
        }
        Component boundId = (Component) bound.identifier
        Component annotatedId = (Component) annotated.identifier
        List<String> found = []
        if (annotated.identifierProperty != null) {
            found << "${where} compositeId identifierProperty: generator=${annotated.identifierProperty.name} binder=none".toString()
        }
        if (!annotatedId.embedded) {
            found << "${where} compositeId embedded: generator=false binder=true".toString()
        }
        if (annotated.identifierMapper == null ||
                annotated.identifierMapper.properties*.name.toSet() != boundId.properties*.name.toSet()) {
            found << "${where} compositeId identifierMapper: generator=${annotated.identifierMapper?.properties*.name} binder=${boundId.properties*.name}".toString()
        } else {
            known['Hibernate adds an _identifierMapper property and component to an @IdClass entity; the binder has none']++
        }
        if (boundId.properties*.name.toSet() != annotatedId.properties*.name.toSet()) {
            return ["${where} compositeId parts: generator=${annotatedId.properties*.name} binder=${boundId.properties*.name}".toString()]
        }
        boundId.properties.each { Property boundPart ->
            Property annotatedPart = annotatedId.getProperty(boundPart.name)
            boolean multiple = boundPart.columns.size() > 1
            Map<String, List> pairs = [
                    columns : multiple ? [boundPart.columns*.name.toSet(), annotatedPart.columns*.name.toSet()] :
                            [boundPart.columns*.name, annotatedPart.columns*.name],
                    nullable: [boundPart.columns*.nullable.toSet(), annotatedPart.columns*.nullable.toSet()],
                    kind    : [boundPart.value.getClass().simpleName, annotatedPart.value.getClass().simpleName],
            ]
            found.addAll(pairs.findAll { String facet, List values -> values[0] != values[1] }.collect { String facet, List values ->
                "${where} compositeId part ${boundPart.name} ${facet}: generator=${values[1]} binder=${values[0]}".toString()
            })
            if (boundPart.value instanceof ToOne && annotatedPart.value instanceof ToOne) {
                String target = ((ToOne) annotatedPart.value).referencedEntityName
                if (target != ((ToOne) boundPart.value).referencedEntityName) {
                    found << "${where} compositeId part ${boundPart.name} target: generator=${target} binder=${((ToOne) boundPart.value).referencedEntityName}".toString()
                }
            }
        }
        Set<String> boundKey = bound.table.primaryKey?.columns*.name?.toSet()
        Set<String> annotatedKey = annotated.table.primaryKey?.columns*.name?.toSet()
        if (boundKey != annotatedKey) {
            found << ("${where} compositeId primaryKey: generator=${annotated.table.primaryKey?.columns*.name} " +
                    "binder=${bound.table.primaryKey?.columns*.name}").toString()
        } else if (bound.table.primaryKey?.columns*.name != annotated.table.primaryKey?.columns*.name) {
            known['Hibernate orders the primary key columns of an @IdClass by the sorted identifier properties; the binder by its component']++
        }
        Set<Set<String>> boundKeys = bound.table.foreignKeys.values().collect { it.columns*.name.toSet() }.toSet()
        Set<Set<String>> annotatedKeys = annotated.table.foreignKeys.values().collect { it.columns*.name.toSet() }.toSet()
        if (boundKeys != annotatedKeys) {
            found << "${where} compositeId foreignKeys: generator=${annotatedKeys} binder=${boundKeys}".toString()
        }
        if (boundId.nullValue != annotatedId.nullValue) {
            known['the binder gives a composite identifier the unsaved value undefined; Hibernate gives an @IdClass identifier none']++
        }
        return found
    }

    /**
     * The second-level cache of the root: the concurrency strategy, whether the class is cached and mutable, and whether lazy
     * properties are cached, or none of it when the generator states no cache.
     */
    private List<String> compareCache(GrailsHibernatePersistentEntity entity, CacheFacets facets, Map<String, Integer> caches) {
        if (!(entity.persistentClass instanceof RootClass)) {
            return facets == null ? [] : ["${entity.name} cache: generator=${facets} binder=not a root".toString()]
        }
        RootClass root = (RootClass) entity.persistentClass
        String where = "${entity.name} cache"
        if (facets == null) {
            return root.cacheConcurrencyStrategy == null && root.mutable ? [] :
                    ["${where}: generator=none binder=${root.cacheConcurrencyStrategy} mutable=${root.mutable}".toString()]
        }
        caches[facets.usage()]++
        Map<String, List> pairs = [
                usage      : [facets.usage(), root.cacheConcurrencyStrategy],
                cached     : [true, root.cached],
                mutable    : [facets.mutable(), root.mutable],
                includeLazy: [facets.includeLazy(), root.lazyPropertiesCacheable],
        ]
        return pairs.findAll { String facet, List values -> values[0] != values[1] }.collect { String facet, List values ->
            "${where} ${facet}: generator=${values[0]} binder=${values[1]}".toString()
        }
    }

    /**
     * The cache Hibernate's annotation binder reads from {@code @Cacheable}, {@code @Cache} and {@code @Immutable}: the same
     * strategy, the same mutability and the same treatment of lazy properties as the binder bound.
     */
    private static List<String> compareAnnotatedCache(String where, RootClass bound, RootClass annotated) {
        // an annotated class has a default strategy and lazy-property setting that mean nothing while it is not cached
        Map<String, List> pairs = [
                cached : [bound.cached, annotated.cached],
                mutable: [bound.mutable, annotated.mutable],
        ]
        if (bound.cached) {
            pairs.usage = [bound.cacheConcurrencyStrategy, annotated.cacheConcurrencyStrategy]
            pairs.includeLazy = [bound.lazyPropertiesCacheable, annotated.lazyPropertiesCacheable]
        }
        return pairs.findAll { String facet, List values -> values[0] != values[1] }.collect { String facet, List values ->
            "${where} cache ${facet}: generator=${values[1]} binder=${values[0]}".toString()
        }
    }

    /**
     * The natural identifier: the properties the binder marked natural are the ones the generator names, each is updatable exactly
     * when the natural id is mutable, and one unique key spans their columns in the order the mapping names them.
     */
    private List<String> compareNaturalId(GrailsHibernatePersistentEntity entity, NaturalIdFacets facets, Map<String, Integer> naturals) {
        PersistentClass persistentClass = entity.persistentClass
        List<Property> natural = persistentClass.properties.findAll { Property p -> p.naturalIdentifier }
        String where = "${entity.name} natural id"
        if (facets == null) {
            return natural.isEmpty() ? [] : ["${where} properties: generator=none binder=${natural*.name}".toString()]
        }
        naturals['entities']++
        naturals['properties'] += facets.propertyNames().size()
        List<String> found = []
        if (facets.propertyNames().toSet() != natural*.name.toSet()) {
            found << "${where} properties: generator=${facets.propertyNames()} binder=${natural*.name}".toString()
            return found
        }
        natural.each { Property p ->
            if (p.updateable != facets.mutable()) {
                found << "${where} updatable of ${p.name}: generator=${facets.mutable()} binder=${p.updateable}".toString()
            }
        }
        List<String> columns = facets.propertyNames().collectMany { String name -> persistentClass.getProperty(name).columns*.name }
        if (!persistentClass.table.uniqueKeys.values().any { org.hibernate.mapping.UniqueKey key -> key.columns*.name == columns }) {
            found << "${where} uniqueKey: generator=${columns} binder=${persistentClass.table.uniqueKeys.values()*.columns*.name}".toString()
        }
        return found
    }

    /**
     * The natural identifier Hibernate's annotation binder reads from {@code @NaturalId}: the same properties, with the same
     * updatability, and a unique key over the same columns. The key has the name Hibernate's implicit naming gives it and its
     * columns follow the order of the fields, not the order the mapping names them: listed, not reported.
     */
    private static List<String> compareAnnotatedNaturalId(
            String where, PersistentClass bound, PersistentClass annotated, Map<String, Integer> known) {
        List<Property> boundNatural = bound.properties.findAll { Property p -> p.naturalIdentifier }
        List<Property> annotatedNatural = annotated.properties.findAll { Property p -> p.naturalIdentifier }
        if (boundNatural.isEmpty() && annotatedNatural.isEmpty()) {
            return []
        }
        List<String> found = []
        if (boundNatural*.name.toSet() != annotatedNatural*.name.toSet()) {
            return ["${where} naturalProperties: generator=${annotatedNatural*.name} binder=${boundNatural*.name}".toString()]
        }
        boundNatural.each { Property p ->
            if (p.updateable != annotated.getProperty(p.name).updateable) {
                found << "${where} natural updatable of ${p.name}: generator=${annotated.getProperty(p.name).updateable} binder=${p.updateable}".toString()
            }
        }
        List<String> boundColumns = boundNatural.collectMany { Property p -> p.columns*.name }
        org.hibernate.mapping.UniqueKey boundKey = bound.table.uniqueKeys.values().find { it.columns*.name.toSet() == boundColumns.toSet() }
        org.hibernate.mapping.UniqueKey annotatedKey = annotated.table.uniqueKeys.values().find { it.columns*.name.toSet() == boundColumns.toSet() }
        if (annotatedKey == null) {
            found << "${where} naturalKey: generator=none binder=${boundKey?.columns*.name}".toString()
        } else {
            known['Hibernate names the unique key of a natural id itself, and orders its columns by the fields, not as the mapping names the properties']++
        }
        return found
    }

    /**
     * The unique keys of the table that a mapping states with a group, by name. Left out: the single-column keys Hibernate derives
     * from a unique column once the metadata is complete (their names start with {@code UK_}), and the unique key over the columns
     * of a natural id, which is compared with the natural id.
     */
    private static Map<String, List<String>> tableKeysWithoutNaturalId(PersistentClass persistentClass) {
        List<Set<String>> naturalColumns = persistentClass.subclassClosure.collect { PersistentClass member ->
            member.declaredProperties.findAll { Property p -> p.naturalIdentifier }*.columns.flatten()*.name.toSet()
        }.findAll { Set<String> columns -> !columns.isEmpty() }
        return persistentClass.table.uniqueKeys.values().findAll { org.hibernate.mapping.UniqueKey key ->
            !(key.columns.size() == 1 && key.name.startsWith('UK_')) && !naturalColumns.contains(key.columns*.name.toSet())
        }.collectEntries { org.hibernate.mapping.UniqueKey key ->
            [(key.name): key.columns*.name]
        } as Map<String, List<String>>
    }

    /**
     * The tenant filter {@code MultiTenantFilterBinder} puts on the entity's class and the one global definition it
     * registers: the same name, condition and parameter, or no filter at all when the generator says there is none.
     */
    private List<String> compareTenantFilter(
            GrailsHibernatePersistentEntity entity, GrailsDomainGenerator generator, SessionFactoryImplementor sessionFactory,
            Map<String, Integer> skipped, Map<String, Integer> tenants) {
        TenantFacets facets
        try {
            facets = generator.tenantFacets(entity)
        } catch (UnsupportedOperationException e) {
            skipped['tenant filter: a mapped type on the tenant id']++
            return []
        }
        List<FilterConfiguration> filters = ownTenantFilters(entity.persistentClass)
        String where = "${entity.name} tenant filter"
        if (facets == null) {
            return filters.isEmpty() ? [] : ["${where}: generator=none binder=${filters*.condition}".toString()]
        }
        tenants['filters']++
        if (filters.size() != 1) {
            return ["${where}: generator=1 binder=${filters.size()}".toString()]
        }
        FilterConfiguration filter = filters[0]
        FilterDefinition definition = sessionFactory.getFilterDefinition(facets.filterName())
        Map<String, List> pairs = [
                condition     : [facets.condition(), filter.condition],
                autoAlias     : [true, filter.useAutoAliasInjection()],
                parameterNames: [[facets.filterName()].toSet(), definition?.parameterNames],
                parameterType : [facets.parameterType(), definition?.getParameterJdbcMapping(facets.filterName())?.javaTypeDescriptor?.javaTypeClass],
                defaultCond   : [null, definition?.defaultFilterCondition],
                autoEnabled   : [false, definition?.autoEnabled],
                loadByKey     : [false, definition?.appliedToLoadByKey],
        ]
        return pairs.findAll { String facet, List values -> values[0] != values[1] }.collect { String facet, List values ->
            "${where} ${facet}: generator=${values[0]} binder=${values[1]}".toString()
        }
    }

    /** The tenant filters a class added itself: a subclass reports the filters of its superclass as its own, too. */
    private static List<FilterConfiguration> ownTenantFilters(PersistentClass persistentClass) {
        return persistentClass.filters.findAll { FilterConfiguration f ->
            f.name == 'tenantId' && (persistentClass.superclass == null || !persistentClass.superclass.filters.any { it.is(f) })
        }
    }

    /** The filter the generated class gets, read back by Hibernate's annotation binder, against the binder's. */
    private static List<String> compareAnnotatedTenantFilter(
            GrailsHibernatePersistentEntity entity, GrailsDomainGenerator generator, PersistentClass annotated, Map<String, Integer> tenants) {
        TenantFacets facets
        try {
            facets = generator.tenantFacets(entity)
        } catch (UnsupportedOperationException ignored) {
            return []
        }
        List<FilterConfiguration> bound = ownTenantFilters(entity.persistentClass)
        List<FilterConfiguration> read = ownTenantFilters(annotated)
        String where = "${entity.name} hibernate tenant filter"
        if (bound.size() != read.size()) {
            return ["${where}: generator=${read*.condition} binder=${bound*.condition}".toString()]
        }
        if (!bound.isEmpty()) {
            tenants['filters read back']++
        }
        return [bound, read].transpose().collectMany { List pair ->
            FilterConfiguration b = (FilterConfiguration) pair[0]
            FilterConfiguration r = (FilterConfiguration) pair[1]
            Map<String, List> pairs = [
                    condition: [b.condition, r.condition],
                    autoAlias: [b.useAutoAliasInjection(), r.useAutoAliasInjection()],
            ]
            pairs.findAll { String facet, List values -> values[0] != values[1] }.collect { String facet, List values ->
                "${where} ${facet}: generator=${values[1]} binder=${values[0]}".toString()
            }
        } as List<String>
    }

    /**
     * The filter definition Hibernate's annotation binder registers for a generated hierarchy: present exactly when the
     * hierarchy has a tenant filter, with the one parameter of the tenant id's type and nothing else.
     */
    private static List<String> compareAnnotatedFilterDefinition(
            List<GrailsHibernatePersistentEntity> entities, GrailsDomainGenerator generator, Metadata metadata, Map<String, Integer> tenants) {
        TenantFacets facets = null
        try {
            facets = entities.collect { generator.tenantFacets(it) }.find { it != null }
        } catch (UnsupportedOperationException ignored) {
            return []
        }
        FilterDefinition definition = metadata.getFilterDefinition('tenantId')
        String where = "${entities.first().hibernateRootEntity.name} hibernate filter definition"
        if (facets == null) {
            return definition == null ? [] : ["${where}: generator=${definition.filterName} binder=none".toString()]
        }
        tenants['filter definitions read back']++
        if (definition == null) {
            return ["${where}: generator=none binder=${facets.filterName()}".toString()]
        }
        Map<String, List> pairs = [
                parameterNames: [[facets.filterName()].toSet(), definition.parameterNames],
                parameterType : [facets.parameterType(), definition.getParameterJdbcMapping(facets.filterName())?.javaTypeDescriptor?.javaTypeClass],
                defaultCond   : [null, definition.defaultFilterCondition ?: null],
                autoEnabled   : [false, definition.autoEnabled],
                loadByKey     : [false, definition.appliedToLoadByKey],
        ]
        return pairs.findAll { String facet, List values -> values[0] != values[1] }.collect { String facet, List values ->
            "${where} ${facet}: generator=${values[1]} binder=${values[0]}".toString()
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
                extraLazy: [facets.extraLazy(), collection.extraLazy],
                fetchMode: [facets.fetchMode(), collection.fetchMode == FetchMode.JOIN ? FetchMode.JOIN : FetchMode.SELECT],
                batchSize: [facets.batchSize(), Math.max(collection.batchSize, 0)],
                cache    : [facets.cacheUsage(), collection.cacheConcurrencyStrategy],
                inverse  : [false, collection.inverse],
        ]
        found.addAll(pairs.findAll { String facet, List values -> values[0] != values[1] }.collect { String facet, List values ->
            "${where} ${facet}: generator=${values[0]} binder=${values[1]}".toString()
        })
        found.addAll(keyUpdatable(where, property, facets, (DependantValue) collection.key, known))
        found.addAll(compareValueColumns("${where} key".toString(), facets.keys(), collection.key, collection.collectionTable, true))
        Property element = new Property()
        element.value = collection.element
        found.addAll(compareValueColumn("${where} element".toString(), facets.element(), collection.element, collection.collectionTable, false))
        if (property instanceof HibernateEnumProperty && generator.typeFacets(property) == null) {
            found.addAll(compareEnum("${where} element".toString(), (HibernateEnumProperty) property, generator, element))
        }
        found.addAll(compareType("${where} element".toString(), property, generator, element, explicitTypes))
        List<String> described = facets.keys().collect { ColumnFacets key -> key.name().replace('`', '') } + [facets.element().name().replace('`', '')]
        if (facets.index() != null) {
            described << facets.index().name().replace('`', '')
            if (collection instanceof IndexedCollection) {
                found.addAll(compareValueColumn(
                        "${where} index".toString(), facets.index(), ((IndexedCollection) collection).index, collection.collectionTable, false))
                found.addAll(compareIndexType("${where} index".toString(), facets.kind(), facets.indexType(), (IndexedCollection) collection))
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
        found.addAll(compareCollectionTableIndexes(where, collection))
        return found
    }

    /**
     * The generator states no index on the table of a collection: the binder only creates one there when the mapping puts
     * {@code index:} on the collection property, which the generator rejects, so any index it finds is a mapping the generator
     * accepted and drops.
     */
    private static List<String> compareCollectionTableIndexes(String where, HibernateCollection collection) {
        List<String> indexes = collection.collectionTable.indexes.values().collect { org.hibernate.mapping.Index index ->
            "${index.name}:${index.columns*.name}".toString()
        }
        return indexes.isEmpty() ? [] : ["${where} collectionTableIndexes: generator=[] binder=${indexes}".toString()]
    }

    /** The key of a collection of basic values must be updatable: Hibernate writes no rows for a collection whose key is not. */
    private static List<String> keyUpdatable(
            String where, HibernateBasicProperty property, CollectionFacets facets, DependantValue key, Map<String, Integer> known) {
        List<String> found = []
        if (!facets.key().updatable()) {
            found << "${where} keyUpdatable: generator=false binder=${key.updateable}".toString()
        }
        if (!key.updateable) {
            found << "${where} keyUpdatable: generator=true binder=false".toString()
        }
        return found
    }

    /**
     * The single column of a collection's key, element or index value, against what the generator decided for it. The key's
     * size facets come from the referenced identifier, and a column of a primary key is not null whatever the binder said:
     * Hibernate makes it so when it creates the primary key.
     */
    private static List<String> compareValueColumn(String where, ColumnFacets facets, org.hibernate.mapping.Value value, Table table, boolean key) {
        return compareValueColumns(where, [facets], value, table, key)
    }

    /**
     * The columns of a collection's key against what the generator decided for them: one column, or one for each identifier property
     * when the owner has a composite identifier, paired by name because the binder orders them like the identifier (sorted) and the
     * generator like the mapping.
     */
    private static List<String> compareValueColumns(
            String where, List<ColumnFacets> allFacets, org.hibernate.mapping.Value value, Table table, boolean key) {
        List<Column> columns = value.selectables.findAll { it instanceof Column }.collect { (Column) it }
        if (columns.size() != allFacets.size()) {
            return ["${where} columns: generator=${allFacets.size()} binder=${columns.size()}".toString()]
        }
        List<String> found = []
        for (ColumnFacets facets : allFacets) {
            Column column = allFacets.size() == 1 ? columns[0] : columns.find { Column c -> c.name == facets.name().replace('`', '') }
            if (column == null) {
                found << "${where} columns: generator=${allFacets*.name()} binder=${columns*.name}".toString()
                continue
            }
            // two collections may share one table (the sides of a many-to-many), and then the primary key holds one of the Column objects of that name
            boolean primaryKey = table.primaryKey != null && table.primaryKey.columns.any { it.name == column.name }
            Map<String, List> pairs = [
                    name    : [facets.name().replace('`', ''), column.name],
                    nullable: [facets.nullable() && !primaryKey, column.nullable && !primaryKey],
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
            found.addAll(pairs.findAll { String facet, List values -> values[0] != values[1] }.collect { String facet, List values ->
                "${where} ${facet}: generator=${values[0]} binder=${values[1]}".toString()
            })
        }
        return found
    }

    private static CollectionKind kindOf(HibernateCollection collection) {
        if (collection instanceof HibernateList) {
            return CollectionKind.LIST
        }
        if (collection instanceof HibernateMap) {
            return CollectionKind.MAP
        }
        if (collection instanceof HibernateSet) {
            return collection.sorted ? CollectionKind.SORTED_SET : CollectionKind.SET
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
            GrailsDomainGenerator generator, Property bound, Map<String, Integer> explicitTypes, Map<String, Integer> known) {
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
            } else if (boundLeaf.columns.size() != (leaf.toOne() != null && leaf.toOne().joinColumns().size() > 1 ? leaf.toOne().joinColumns().size() : 1)) {
                found << "${leafWhere} columns: generator=1 binder=${boundLeaf.columns.size()}".toString()
                continue
            } else if (leaf.toOne() != null) {
                // Hibernate copies the size of the referenced identifier onto a foreign key column after binding
                found.addAll(compareColumnsByName(
                        leafWhere, leaf.toOne().joinColumns().size() > 1 ? leaf.toOne().joinColumns() : [leaf.column()], boundLeaf, known,
                        ['length', 'precision', 'scale']))
                found.addAll(compareToOne(leafWhere, leaf.toOne(), boundLeaf, known))
                continue
            } else {
                found.addAll(compare(leafWhere, leaf.column(), boundLeaf, known, [], leaf.property instanceof HibernateEnumProperty))
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
                // the binder hands type parameters that are mapped with no type to a built-in type, which ignores them; the generator states none
                explicitTypes['parameters with no type (ignored by the built-in type)']++
            }
        } else if (facets.userType() != null) {
            explicitTypes["UserType ${facets.userType().simpleName}".toString()]++
            if (value.typeName != facets.userType().name) {
                found << "${where} typeName: generator=${facets.userType().name} binder=${value.typeName}".toString()
            }
            if (actualParameters != facets.parameters()) {
                found << "${where} typeParameters: generator=${facets.parameters()} binder=${actualParameters}".toString()
            }
        } else if (facets.converter() != null) {
            explicitTypes["registered ${value.typeName} (converter)".toString()]++
            BasicValue.Resolution<?> resolution = value.resolve()
            if (value.typeName != property.getTypeName(type) || resolution.valueConverter?.getClass() != facets.converter() ||
                    resolution.jdbcType.defaultSqlTypeCode != facets.jdbcTypeCode()) {
                found << "${where} converter: generator=${facets.converter()}/${facets.jdbcTypeCode()} binder=${resolution.valueConverter?.getClass()}/${resolution.jdbcType.defaultSqlTypeCode} (type ${value.typeName})".toString()
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
                // VersionBinder: NONE for a root without a version, VERSION otherwise; a subclass reads its root's
                versioned    : [facets.versioned(), persistentClass.optimisticLockStyle == OptimisticLockStyle.VERSION],
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
     * Binds the classes generated for a group with Hibernate's own annotation binder and compares the resulting
     * {@code PersistentClass} of each entity with the one the domain binder built: the link between what the generator
     * decides and what Hibernate makes of the annotations. A group is generated whole, because an association's field is
     * typed with the class generated for its target; entities the generator cannot describe are left out together with
     * everything that refers to them.
     */
    private List<String> compareAnnotationBoundHierarchies(
            GrailsDomainGenerator generator, List<GrailsHibernatePersistentEntity> entities, Map<String, Integer> skipped,
            Map<String, Integer> annotationRead, Map<String, Integer> known, Map<String, Integer> tenants) {
        List<String> found = []
        List<GrailsHibernatePersistentEntity> generatable = generatableClosure(generator, entities, skipped)
        if (generatable.isEmpty()) {
            return found
        }
        Map<GrailsHibernatePersistentEntity, Class<?>> classes
        try {
            classes = generator.generateAll(generatable, getClass().classLoader)
        } catch (UnsupportedOperationException | IllegalArgumentException e) {
            found << "${generatable*.name} generateAll: generator=rejected binder=accepted (${e.message?.readLines()?.first()})".toString()
            return found
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
                found << "${generatable*.name} annotationBinder: generator=accepted binder=${e.message?.readLines()?.first()} at ${e.stackTrace.take(5)*.toString()}".toString()
                return found
            }
            annotationRead['groups']++
            Map<Class<?>, List<GrailsHibernatePersistentEntity>> hierarchies = generatable.groupBy { it.hibernateRootEntity.javaClass }
            hierarchies.each { Class<?> root, List<GrailsHibernatePersistentEntity> members ->
                annotationRead["${generator.hierarchyFacets(members.first().hibernateRootEntity).strategy() ?: 'single class'} (${members.size()} classes)".toString()]++
            }
            Map<String, GrailsHibernatePersistentEntity> byName = generatable.collectEntries { [(it.name): it] }
            found.addAll(compareAnnotatedFilterDefinition(generatable, generator, metadata, tenants))
            classes.each { GrailsHibernatePersistentEntity entity, Class<?> generated ->
                found.addAll(compareAnnotatedTenantFilter(entity, generator, metadata.getEntityBinding(generated.name), tenants))
                found.addAll(compareAnnotationBound(generator, entity, metadata.getEntityBinding(generated.name), byName, annotationRead, known))
            }
        } finally {
            StandardServiceRegistryBuilder.destroy(registry)
        }
        return found
    }

    /**
     * The entities of a group that can be generated together: those the generator describes, whose whole hierarchy it
     * describes, and whose association targets it describes too. An entity that fails any of that is dropped with its whole
     * hierarchy, which can in turn drop the entities that refer to it, until nothing changes.
     */
    private static List<GrailsHibernatePersistentEntity> generatableClosure(
            GrailsDomainGenerator generator, List<GrailsHibernatePersistentEntity> entities, Map<String, Integer> skipped) {
        List<GrailsHibernatePersistentEntity> candidates = new ArrayList<GrailsHibernatePersistentEntity>(entities)
        boolean changed = true
        while (changed) {
            changed = false
            for (GrailsHibernatePersistentEntity entity : new ArrayList<GrailsHibernatePersistentEntity>(candidates)) {
                if (!candidates.contains(entity)) {
                    continue
                }
                String reason = generator.generationProblem(entity)
                if (reason == null) {
                    GrailsHibernatePersistentEntity missing = GrailsDomainGenerator.referencedEntities(entity).find {
                        GrailsHibernatePersistentEntity target -> !candidates.any { it.javaClass == target.javaClass }
                    }
                    if (missing != null) {
                        reason = "it refers to [${missing.name}], which is not generated"
                    }
                }
                if (reason != null) {
                    Class<?> root = entity.hibernateRootEntity.javaClass
                    List<GrailsHibernatePersistentEntity> removed = candidates.findAll { it.hibernateRootEntity.javaClass == root }
                    candidates.removeAll(removed)
                    skipped["not read back (${removed.size() > 1 ? 'hierarchy' : 'entity'}): ${reason.replaceAll(/\[[^\]]*\]/, '[..]')}".toString()]++
                    changed = true
                }
            }
        }
        return candidates
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
                properties        : [declaredNames(bound), declaredNames(annotated)],
                optimisticLock    : [bound.optimisticLockStyle, annotated.optimisticLockStyle],
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
            found.addAll(compareAnnotatedNaturalId(where, bound, annotated, known))
            found.addAll(compareAnnotatedCache(where, (RootClass) bound, (RootClass) annotated))
            if (entity.identity == null && generator.generationProblem(entity) == null) {
                annotationRead['composite identifiers']++
                found.addAll(compareAnnotatedCompositeId(where, bound, annotated, generator.compositeIdFacets(entity), known))
            }
        }
        if (pairs.ownsTable[0]) {
            found.addAll(compareAnnotatedConstraints(where, bound, annotated, generator.constraintFacets(entity)))
        }
        Map<String, Map<String, Object>> expectations = leafExpectations(generator, entity)
        for (Property property : bound.declaredProperties) {
            Property other = annotated.hasProperty(property.name) ? annotated.getProperty(property.name) : null
            if (other == null) {
                continue
            }
            if (property.value instanceof HibernateCollection &&
                    entity.persistentPropertiesToBind.find { it.name == property.name } instanceof HibernateToManyEntityProperty) {
                HibernateToManyEntityProperty toMany = (HibernateToManyEntityProperty) entity.persistentPropertiesToBind.find { it.name == property.name }
                if (generator.validationAnnotations(toMany).isEmpty()) {
                    annotationRead['entity collections']++
                    found.addAll(compareAnnotatedToMany(
                            "${where} property ${property.name}".toString(), generator.toManyFacets(toMany), entity, property, other, byName, known))
                }
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
            if (property.value instanceof ToOne) {
                HibernatePersistentProperty source = entity.persistentPropertiesToBind.find { it.name == property.name }
                if (source instanceof HibernateToOneProperty && generator.validationAnnotations(source).isEmpty()) {
                    annotationRead['associations']++
                    found.addAll(compareAnnotatedToOne(
                            "${where} property ${property.name}".toString(), generator.toOneFacets((HibernateToOneProperty) source),
                            property, other, byName, known))
                }
            }
            if (property.value instanceof OneToOne) {
                // the inverse side of a one-to-one has no column of its own
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
                if (!expected.validated && expected.toOne != null) {
                    found.addAll(compareAnnotatedToOne(
                            "${where} property ${path}".toString(), (ToOneFacets) expected.toOne, leaf, annotatedLeaves[path], byName, known))
                }
                if (!expected.validated) {
                    found.addAll(compareAnnotatedLeaf(
                            "${where} property ${path}".toString(), leaf, annotatedLeaves[path], (String) expected.sqlType, false, known))
                }
            }
        }
        return found
    }

    /**
     * The indexes and unique keys Hibernate's annotation binder puts on the table of the generated class: the same as the
     * binder's, by name and column order. Hibernate adds a unique key of its own for a natural id, with its own name, so that one
     * is compared by columns in the natural id comparison and not here.
     */
    private static List<String> compareAnnotatedConstraints(
            String where, PersistentClass bound, PersistentClass annotated, ConstraintFacets facets) {
        Map<String, List<String>> boundIndexes = bound.table.indexes.values().collectEntries { org.hibernate.mapping.Index index ->
            [(index.name): index.columns*.name]
        } as Map<String, List<String>>
        Map<String, List<String>> annotatedIndexes = annotated.table.indexes.values().collectEntries { org.hibernate.mapping.Index index ->
            [(index.name): index.columns*.name]
        } as Map<String, List<String>>
        List<String> found = []
        if (boundIndexes != annotatedIndexes) {
            found << "${where} indexes: generator=${annotatedIndexes} binder=${boundIndexes}".toString()
        }
        Map<String, List<String>> boundKeys = tableKeysWithoutNaturalId(bound)
        // a key the mapping asks for and the binder does not create is stated by the generator, so Hibernate reads it too
        Set<String> unbound = facets.uniqueKeys().findAll { UniqueKeyFacets key -> !key.bound() }*.name().toSet()
        Map<String, List<String>> annotatedKeys = tableKeysWithoutNaturalId(annotated).findAll { String name, List<String> columns ->
            !unbound.contains(name)
        } as Map<String, List<String>>
        if (boundKeys != annotatedKeys) {
            found << "${where} uniqueKeys: generator=${annotatedKeys} binder=${boundKeys}".toString()
        }
        return found
    }

    /**
     * The names of the properties a class declares itself: the binder's and Hibernate's back references are not part of the field
     * set, nor is the {@code _identifierMapper} Hibernate adds to an entity with an {@code @IdClass} (listed as a known divergence).
     */
    private static Set<String> declaredNames(PersistentClass persistentClass) {
        return persistentClass.declaredProperties.findAll {
            !(it instanceof Backref) && !(it instanceof IndexBackref) && it.name != NavigablePath.IDENTIFIER_MAPPER_PROPERTY
        }*.name.toSet()
    }

    private static String toOneKind(HibernatePersistentProperty property, Property bound) {
        if (bound.value instanceof OneToOne) {
            return 'one-to-one (inverse side)'
        }
        return property instanceof HibernateManyToOneProperty ? 'many-to-one' : 'one-to-one (foreign key)'
    }

    /**
     * The inverse side of a one-to-one as {@code OneToOneBinder} bound it: no column, the property of the other side that
     * holds the foreign key, the entity that declares it, the foreign key direction and the fetching.
     */
    private static List<String> compareOneToOne(String where, ToOneFacets facets, Property bound, Map<String, Integer> known) {
        OneToOne value = (OneToOne) bound.value
        Map<String, List> pairs = [
                referencedEntity    : [facets.referencedEntity(), value.referencedEntityName],
                referencedProperty  : [facets.mappedBy(), value.referencedPropertyName],
                constrained         : [false, value.constrained],
                foreignKeyDirection : ['TO_PARENT', value.foreignKeyType?.name()],
                lazy                : [facets.lazy(), value.lazy],
                fetchMode           : [facets.fetchMode() == FetchMode.JOIN ? 'JOIN' : 'SELECT', fetchModeOf(value)],
                cascade             : [cascadeActions(facets.cascade()), cascadeActions(bound.cascade)],
                hasNoColumns        : [true, value.selectables.every { !(it instanceof Column) } || value.columns.isEmpty()],
        ]
        List<String> found = pairs.findAll { String facet, List values -> values[0] != values[1] }.collect { String facet, List values ->
            "${where} ${facet}: generator=${values[0]} binder=${values[1]}".toString()
        }
        if (facets.optional() != bound.optional) {
            known['the binder makes a to-one association optional or not independently of its foreign key column being nullable; annotations state both together, so the generated field follows the column']++
        }
        return found
    }

    private static String fetchModeOf(ToOne value) {
        return value.fetchMode == FetchMode.JOIN ? 'JOIN' : 'SELECT'
    }

    /**
     * A to-one association against the binder's {@code ToOne} value and property: the referenced entity, whether it is lazy,
     * how it is fetched, what a missing row does, the cascade and whether the property is optional. The foreign key column
     * itself goes through the ordinary column comparison.
     */
    private static List<String> compareToOne(String where, ToOneFacets facets, Property bound, Map<String, Integer> known) {
        ToOne value = (ToOne) bound.value
        Map<String, List> pairs = [
                target        : [facets.target(), value.referencedEntityName],
                lazy          : [facets.lazy(), value.lazy],
                fetchMode     : [facets.fetchMode() == FetchMode.JOIN ? 'JOIN' : 'SELECT', fetchModeOf(value)],
                ignoreNotFound: [facets.ignoreNotFound(), value instanceof ManyToOne && ((ManyToOne) value).ignoreNotFound],
                cascade       : [cascadeActions(facets.cascade()), cascadeActions(bound.cascade)],
        ]
        List<String> found = pairs.findAll { String facet, List values -> values[0] != values[1] }.collect { String facet, List values ->
            "${where} ${facet}: generator=${values[0]} binder=${values[1]}".toString()
        }
        if (facets.optional() != bound.optional) {
            if (facets.optional() == facets.joinColumn().nullable()) {
                known['the binder makes a to-one association optional or not independently of its foreign key column being nullable; annotations state both together, so the generated field follows the column']++
            } else {
                found << "${where} optional: generator=${facets.optional()} binder=${bound.optional}".toString()
            }
        }
        return found
    }

    private static Set<String> cascadeActions(CascadeFacets facets) {
        Map<String, String> jpa = [ALL: 'all', PERSIST: 'persist', MERGE: 'merge', REMOVE: 'delete', REFRESH: 'refresh', DETACH: 'evict']
        List<String> tokens = facets.jpa().collect { jpa[it.name()] }
        tokens.addAll(facets.hibernate().collect { it.name().toLowerCase().replace('_', '-') })
        if (facets.orphanRemoval()) {
            tokens << 'delete-orphan'
        }
        return normalizedCascade(tokens)
    }

    private static Set<String> cascadeActions(String cascade) {
        return normalizedCascade(cascade == null ? [] : cascade.split(',').collect { it.trim() })
    }

    /** The actions a cascade string stands for: {@code all} subsumes the others but orphan removal, and {@code none} is no action. */
    private static Set<String> normalizedCascade(Collection<String> tokens) {
        Set<String> actions = new TreeSet<String>()
        tokens.each { String token ->
            if (token == 'all-delete-orphan') {
                actions << 'all' << 'delete-orphan'
            } else if (token != 'none' && !token.isEmpty()) {
                actions << token
            }
        }
        if (actions.contains('all')) {
            actions.retainAll(['all', 'delete-orphan'])
        }
        return actions
    }

    /**
     * A to-one association of the binder's entity against the one Hibernate's annotation binder built: the same referenced
     * entity (by the name of the class generated for it), fetching, cascade and write behavior. Where Hibernate's
     * annotation path cannot say what the binder says, the difference is listed as a known divergence.
     */
    private static List<String> compareAnnotatedToOne(
            String where, ToOneFacets facets, Property boundProperty, Property annotatedProperty,
            Map<String, GrailsHibernatePersistentEntity> byName, Map<String, Integer> known) {
        if (!(annotatedProperty.value instanceof ToOne)) {
            return ["${where} kind: generator=${annotatedProperty.value.getClass().simpleName} binder=${boundProperty.value.getClass().simpleName}".toString()]
        }
        ToOne bound = (ToOne) boundProperty.value
        ToOne annotated = (ToOne) annotatedProperty.value
        if ((bound instanceof OneToOne) != (annotated instanceof OneToOne)) {
            return ["${where} kind: generator=${annotated.getClass().simpleName} binder=${bound.getClass().simpleName}".toString()]
        }
        // the binder names the entity that declares the other side of a one-to-one, which can be a superclass of the field's type
        String targetName = GrailsDomainGenerator.generatedClassName(byName[facets.target()])
        Map<String, List> pairs = [
                target        : [targetName, annotated.referencedEntityName],
                fetchMode     : [fetchModeOf(bound), fetchModeOf(annotated)],
                ignoreNotFound: [bound instanceof ManyToOne && ((ManyToOne) bound).ignoreNotFound,
                                 annotated instanceof ManyToOne && ((ManyToOne) annotated).ignoreNotFound],
                cascade       : [cascadeActions(boundProperty.cascade), cascadeActions(annotatedProperty.cascade)],
                insertable    : [boundProperty.insertable, annotatedProperty.insertable],
                updatable     : [boundProperty.updateable, annotatedProperty.updateable],
                propertyLazy  : [boundProperty.lazy, annotatedProperty.lazy],
        ]
        if (bound instanceof OneToOne) {
            pairs.referencedProperty = [bound.referencedPropertyName, annotated.referencedPropertyName]
            pairs.constrained = [((OneToOne) bound).constrained, ((OneToOne) annotated).constrained]
            pairs.foreignKeyDirection = [((OneToOne) bound).foreignKeyType, ((OneToOne) annotated).foreignKeyType]
        }
        List<String> found = pairs.findAll { String facet, List values -> values[0] != values[1] }.collect { String facet, List values ->
            "${where} ${facet}: generator=${values[1]} binder=${values[0]}".toString()
        }
        if (bound.referencedEntityName != facets.target()) {
            if (bound instanceof OneToOne && bound.referencedEntityName == facets.referencedEntity()) {
                known['the binder names the declaring superclass of the other side as the referenced entity of a one-to-one; the generated field is typed with the target itself']++
            } else {
                found << "${where} referencedEntity: generator=${facets.target()} binder=${bound.referencedEntityName}".toString()
            }
        }
        if (bound.lazy != annotated.lazy) {
            if (bound.lazy && !annotated.lazy && (facets.ignoreNotFound() || fetchModeOf(bound) == 'JOIN')) {
                known['Hibernate binds a @NotFound or fetch-join association as eager (lazy false), the binder keeps the value lazy']++
            } else {
                found << "${where} lazy: generator=${annotated.lazy} binder=${bound.lazy}".toString()
            }
        }
        if (boundProperty.optional != annotatedProperty.optional) {
            if (annotatedProperty.optional == facets.optional()) {
                known['the binder makes a to-one association optional or not independently of its foreign key column being nullable; annotations state both together, so the generated field follows the column']++
            } else {
                found << "${where} optional: generator=${annotatedProperty.optional} binder=${boundProperty.optional}".toString()
            }
        }
        return found
    }

    /**
     * This comparison reads the mapping after the binder has bound it, and {@code ManyToOneBinder.prepareCircularManyToMany}
     * writes the renamed join key into the mapping of a circular many-to-many while it binds, so the generator, which sees that
     * written key as a mapped one, states the renamed name for both sides, where the binder bound the side bound first before
     * the rename. The generator flow itself never sees the written key (it runs on the unbound mapping): the key names are
     * compared against the binder's own in {@code GeneratedDomainClassesDdlDifferentialSpec} and
     * {@code GeneratedDomainClassesCircularManyToManySpec}, which boot both modes. The element column is compared here.
     */
    private static List<String> circularKeyOrFound(HibernateToManyEntityProperty property, List<String> found, Map<String, Integer> known) {
        if (!found.isEmpty() && property instanceof HibernateManyToManyProperty && property.isCircular()) {
            known['the binder writes the renamed join key of a circular many-to-many into the mapping while it binds, so a generator that reads the bound mapping states the renamed key for the side the binder bound first (the generator flow reads the unbound mapping; the DDL differential compares the key names)']++
            return []
        }
        return found
    }

    private static String orderByOf(HibernateToManyEntityProperty property, ToManyFacets facets) {
        if (facets.orderProperty() == null) {
            return null
        }
        org.hibernate.mapping.Property sortBy = property.hibernateAssociatedEntity.persistentClass.getProperty(facets.orderProperty())
        return sortBy.selectables.collect { "${it.text} ${facets.orderDirection()}".toString() }.join(', ')
    }

    private static String normalizedOrderBy(String orderBy) {
        return orderBy?.toLowerCase()?.replaceAll(/\s+/, ' ')?.trim()
    }

    private static String roleProperty(HibernateCollection collection) {
        return collection.role.substring(collection.role.lastIndexOf('.') + 1)
    }

    /**
     * A collection of entities as {@code CollectionBinder} and its second passes bound it: kind, ownership, join table, key,
     * element and index columns, fetching, cascade, ordering and tenant filter.
     */
    private List<String> compareToMany(
            String where, HibernateToManyEntityProperty property, ToManyFacets facets, Property boundProperty, Map<String, Integer> known) {
        HibernateCollection collection = (HibernateCollection) boundProperty.value
        Map<String, List> pairs = [
                kind       : [facets.kind(), kindOf(collection)],
                lazy       : [facets.lazy(), collection.lazy],
                extraLazy  : [facets.extraLazy(), collection.extraLazy],
                fetchMode  : [facets.fetchMode(), collection.fetchMode == FetchMode.JOIN ? FetchMode.JOIN : FetchMode.SELECT],
                batchSize  : [facets.batchSize(), Math.max(collection.batchSize, 0)],
                cache      : [facets.cacheUsage(), collection.cacheConcurrencyStrategy],
                inverse    : [facets.mappedBy() != null, collection.inverse],
                orphanDelete: [facets.cascade().orphanRemoval(), collection.hasOrphanDelete()],
                cascade    : [cascadeActions(facets.cascade()), cascadeActions(boundProperty.cascade)],
                oneToMany  : [!facets.manyToMany(), collection.oneToMany],
                orderBy    : [normalizedOrderBy(orderByOf(property, facets)), normalizedOrderBy(collection.orderBy)],
                element    : [facets.target(), collection.element instanceof OneToMany ? ((OneToMany) collection.element).referencedEntityName :
                        ((ToOne) collection.element).referencedEntityName],
        ]
        List<String> found = pairs.findAll { String facet, List values -> values[0] != values[1] }.collect { String facet, List values ->
            "${where} ${facet}: generator=${values[0]} binder=${values[1]}".toString()
        }
        if (facets.tableName() != null) {
            // the inverse side of a many-to-many has the table the binder computed for it, which is the owning side's
            Map<String, List> table = [
                    table  : [facets.tableName().replace('`', ''), collection.collectionTable.name],
                    schema : [facets.schema(), collection.collectionTable.schema],
                    catalog: [facets.catalog(), collection.collectionTable.catalog],
            ]
            found.addAll(table.findAll { String facet, List values -> values[0] != values[1] }.collect { String facet, List values ->
                "${where} ${facet}: generator=${values[0]} binder=${values[1]}".toString()
            })
            // Hibernate copies the size of the referenced identifier onto the element column after binding
            found.addAll(compareValueColumn("${where} element".toString(), facets.element(), collection.element, collection.collectionTable, true))
            found.addAll(compareCollectionTableIndexes(where, collection))
        }
        found.addAll(circularKeyOrFound(property, compareValueColumns("${where} key".toString(), facets.keys(), collection.key, collection.collectionTable, true), known))
        if (!((DependantValue) collection.key).updateable) {
            known['the binder makes the key of an entity collection not updatable when its owner has several unidirectional to-many properties; Hibernate then writes no join table rows, and annotations cannot state it']++
        }
        if (facets.index() != null) {
            if (collection instanceof IndexedCollection) {
                found.addAll(compareValueColumn(
                        "${where} index".toString(), facets.index(), ((IndexedCollection) collection).index, collection.collectionTable, false))
                found.addAll(compareIndexType("${where} index".toString(), facets.kind(), facets.indexType(), (IndexedCollection) collection))
            } else {
                found << "${where} index: generator=${facets.index().name()} binder=none".toString()
            }
        } else if (collection instanceof IndexedCollection) {
            found << "${where} index: generator=none binder=${((IndexedCollection) collection).index.selectables*.text}".toString()
        }
        return found
    }

    /**
     * The type the binder gave the index column of a list or the key column of a map against the one the generator decided: the JDBC type
     * and the Java type of both.
     */
    private static List<String> compareIndexType(String where, CollectionKind kind, TypeFacets indexType, IndexedCollection collection) {
        if (!(collection.index instanceof BasicValue)) {
            return []
        }
        BasicValue index = (BasicValue) collection.index
        List<String> found = []
        int expectedJdbc = indexType != null ? indexType.jdbcTypeCode() : (kind == CollectionKind.LIST ? Types.INTEGER : Types.VARCHAR)
        int boundJdbc = index.resolve().jdbcType.defaultSqlTypeCode
        if (expectedJdbc != boundJdbc) {
            found << "${where} jdbcTypeCode: generator=${expectedJdbc} binder=${boundJdbc}".toString()
        }
        Class<?> expectedJava = indexType != null ? indexType.javaType() : (kind == CollectionKind.LIST ? Integer : String)
        Class<?> boundJava = index.resolve().domainJavaType.javaTypeClass
        if (expectedJava != boundJava) {
            found << "${where} javaType: generator=${expectedJava.name} binder=${boundJava.name}".toString()
        }
        return found
    }

    /**
     * A collection of entities against the one Hibernate's annotation binder built from the generated field: the same kind,
     * ownership, join table, key, element and index columns, fetching, cascade, ordering and filters.
     */
    private static List<String> compareAnnotatedToMany(
            String where, ToManyFacets facets, GrailsHibernatePersistentEntity entity, Property boundProperty,
            Property annotatedProperty, Map<String, GrailsHibernatePersistentEntity> byName, Map<String, Integer> known) {
        if (!(annotatedProperty.value instanceof HibernateCollection)) {
            return ["${where} kind: generator=${annotatedProperty.value.getClass().simpleName} binder=${boundProperty.value.getClass().simpleName}".toString()]
        }
        HibernateCollection bound = (HibernateCollection) boundProperty.value
        HibernateCollection annotated = (HibernateCollection) annotatedProperty.value
                Map<String, List> pairs = [
                kind        : [kindOf(bound), kindOf(annotated)],
                role        : [roleProperty(bound), roleProperty(annotated)],
                lazy        : [bound.lazy, annotated.lazy],
                extraLazy   : [bound.extraLazy && !annotated.extraLazy ? extraLazyAligned(known) : bound.extraLazy, annotated.extraLazy],
                fetchMode   : [bound.fetchMode == FetchMode.JOIN ? FetchMode.JOIN : FetchMode.SELECT,
                               annotated.fetchMode == FetchMode.JOIN ? FetchMode.JOIN : FetchMode.SELECT],
                batchSize   : [Math.max(bound.batchSize, 0), Math.max(annotated.batchSize, 0)],
                cache       : [bound.cacheConcurrencyStrategy, annotated.cacheConcurrencyStrategy],
                inverse     : [bound.inverse, annotated.inverse],
                orphanDelete: [bound.hasOrphanDelete(), annotated.hasOrphanDelete()],
                oneToMany   : [bound.oneToMany, annotated.oneToMany],
                cascade     : [cascadeActions(boundProperty.cascade), cascadeActions(annotatedProperty.cascade)],
                orderBy     : [normalizedOrderBy(bound.orderBy), normalizedOrderBy(annotated.orderBy ?: annotated.manyToManyOrdering)],
                element     : [GrailsDomainGenerator.generatedClassName(byName[facets.target()]),
                               annotated.element instanceof OneToMany ? ((OneToMany) annotated.element).referencedEntityName :
                                       ((ToOne) annotated.element).referencedEntityName],
                filters     : [bound.filters*.condition.toSet(), annotated.filters*.condition.toSet()],
                manyToManyFilters: [bound.manyToManyFilters*.condition.toSet(), annotated.manyToManyFilters*.condition.toSet()],
        ]
        boolean inverseManyToMany = facets.manyToMany() && facets.mappedBy() != null
        HibernatePersistentProperty sourceProperty = entity.persistentPropertiesToBind.find { it.name == boundProperty.name }
        boolean circularSelf = sourceProperty instanceof HibernateManyToManyProperty && sourceProperty.isCircular()
        if (bound.collectionTable.name != annotated.collectionTable.name) {
            if (inverseManyToMany) {
                known['the binder names the join table of the inverse side of a many-to-many from the inverse side\'s own mapping, so it differs from the owning side\'s table when only the owner names it; Hibernate uses the owning side\'s table']++
            } else {
                pairs.table = [bound.collectionTable.name, annotated.collectionTable.name]
            }
        }
        List<String> found = pairs.findAll { String facet, List values -> values[0] != values[1] }.collect { String facet, List values ->
            "${where} ${facet}: generator=${values[1]} binder=${values[0]}".toString()
        }
        if (bound.where != null && annotated.where == null) {
            known['the binder states a discriminator where on a collection of a table-per-hierarchy subclass; Hibernate applies the discriminator of the subclass itself']++
        } else if (bound.where != annotated.where) {
            found << "${where} where: generator=${annotated.where} binder=${bound.where}".toString()
        }
        if (facets.element() != null && bound.element.columns*.nullable != annotated.element.columns*.nullable) {
            known['Hibernate makes the element column of a join table NOT NULL; the binder leaves it nullable']++
        }
        if (bound.key.nullable != annotated.key.nullable) {
            known['the nullability of the key column of an entity collection differs between the binder and Hibernate (a join table key is NOT NULL for Hibernate, a copied foreign key keeps its own)']++
        }
        if (bound.key.updateable != annotated.key.updateable) {
            known['the binder makes the key of an entity collection not updatable when its owner has several unidirectional to-many properties; annotations cannot state it']++
        }
        Map<String, List> values = [key: [bound.key, annotated.key, facets.key()]]
        if (facets.element() != null) {
            values.element = [bound.element, annotated.element, facets.element()]
        }
        if (bound instanceof IndexedCollection && annotated instanceof IndexedCollection) {
            values.index = [((IndexedCollection) bound).index, ((IndexedCollection) annotated).index, facets.index()]
        }
        values.each { String part, List triple ->
            Property b = new Property()
            b.value = (org.hibernate.mapping.Value) triple[0]
            Property a = new Property()
            a.value = (org.hibernate.mapping.Value) triple[1]
            List<String> leaf = compareAnnotatedLeaf("${where} ${part}".toString(), b, a, ((ColumnFacets) triple[2]).sqlType(), part != 'index', known)
            if (!leaf.isEmpty() && part == 'key' && facets.manyToMany() && circularSelf) {
                known['the binder writes the renamed join key of a circular many-to-many into the mapping while it binds, so a generator that reads the bound mapping states the renamed key for the side the binder bound first (the generator flow reads the unbound mapping; the DDL differential compares the key names)']++
            } else if (inverseManyToMany && !leaf.isEmpty() && part != 'index') {
                known['the binder names the key and element columns of the inverse side of a many-to-many from its own mapping, so they differ from the owning side\'s when only the owner names them; Hibernate uses the owning side\'s']++
            } else {
                found.addAll(leaf)
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
                extraLazy   : [bound.extraLazy && !annotated.extraLazy ? extraLazyAligned(known) : bound.extraLazy, annotated.extraLazy],
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
            found.addAll(compareAnnotatedLeaf("${where} ${part}".toString(), b, a, ((ColumnFacets) triple[2]).sqlType(), part == 'key', known))
        }
        return found
    }

    /**
     * One terminal property of the binder's entity against the one Hibernate's annotation binder built: the same columns
     * or formulas, the same nullability, and every column facet the binder states (length, precision, scale, unique,
     * explicit SQL type, default, read and write expressions, comment) must have reached the annotation-built column.
     */
    private static List<String> compareAnnotatedLeaf(
            String where, Property bound, Property annotated, String explicitSqlType, boolean key, Map<String, Integer> known = [:]) {
        List<String> boundSelectables = bound.selectables.collect { it instanceof Column ? ((Column) it).name : "formula:${((Formula) it).getFormula()}" }
        List<String> annotatedSelectables = annotated.selectables.collect { it instanceof Column ? ((Column) it).name : "formula:${((Formula) it).getFormula()}" }
        List<Column> boundColumns = bound.selectables.findAll { it instanceof Column }.collect { (Column) it }
        List<Column> annotatedColumns = annotated.selectables.findAll { it instanceof Column }.collect { (Column) it }
        if (boundColumns.size() > 1 && boundSelectables != annotatedSelectables && boundSelectables.toSet() == annotatedSelectables.toSet()) {
            // the columns of a foreign key to a composite identifier: Hibernate orders them like the columns of the referenced key
            known['Hibernate orders the columns of a foreign key to a composite identifier like the referenced key, the binder like the mapping']++
            annotatedColumns = boundColumns.collect { Column column -> annotatedColumns.find { Column other -> other.name == column.name } }
            annotatedSelectables = boundSelectables
        }
        List<Boolean> boundNullable = boundColumns*.nullable
        List<Boolean> annotatedNullable = annotatedColumns*.nullable
        // a collection's key columns are NOT NULL in the annotation path whatever the binder did (see compareAnnotatedCollection), and their size comes from the referenced identifier
        if (boundSelectables != annotatedSelectables || (!key && boundNullable != annotatedNullable)) {
            return ["${where} columns: generator=${annotatedSelectables}${annotatedNullable} binder=${boundSelectables}${boundNullable}".toString()]
        }
        List<String> found = []
        if (bound.lazy != annotated.lazy) {
            found << "${where} propertyLazy: generator=${annotated.lazy} binder=${bound.lazy}".toString()
        }
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
            // a precision with no scale: the binder leaves the scale null and the database default scale of the type applies, which
            // @Column can only state (it cannot leave the scale unset next to a precision), so the scale is compared as that default
            if (!key && boundColumn.precision != null && boundColumn.scale == null && annotatedColumn.precision != null &&
                    bound.value instanceof BasicValue) {
                Class<?> javaType = ((BasicValue) bound.value).resolve().domainJavaType.javaTypeClass
                pairs.scale = [javaType == BigDecimal ? Size.DEFAULT_SCALE : 0, annotatedColumn.scale?.intValue() ?: 0]
            }
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
            if (entity.identity != null) {
                properties << (HibernatePersistentProperty) entity.identity
            }
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
                            toOne    : leaf.toOne(),
                            validated: !generator.validationAnnotations(leaf.property).isEmpty(),
                    ]
                }
            } else if (property instanceof HibernateBasicProperty) {
                found[property.name] = [sqlType: null, validated: !generator.validationAnnotations(property).isEmpty()]
            } else if (property instanceof HibernateToManyEntityProperty) {
                found[property.name] = [sqlType: null, validated: !generator.validationAnnotations(property).isEmpty()]
            } else if (property instanceof HibernateToOneProperty) {
                found[property.name] = [
                        sqlType  : generator.toOneFacets((HibernateToOneProperty) property).joinColumn()?.sqlType(),
                        validated: !generator.validationAnnotations(property).isEmpty(),
                ]
            } else {
                found[property.name] = [
                        sqlType  : generator.isDerived(property) ? null : generator.columnFacets(property).sqlType(),
                        validated: !generator.validationAnnotations(property).isEmpty(),
                ]
            }
        }
        return found
    }

    /**
     * Hibernate's annotation binder cannot state an extra-lazy collection (it hard-codes {@code extraLazy = false}); the binder
     * that wires the generated classes sets the flag after binding, and the real-boot spec proves it. Here the annotation
     * path is read before that step, so the difference is recorded as known and the annotated value is the expected one.
     */
    private static boolean extraLazyAligned(Map<String, Integer> known) {
        known['an extra-lazy collection (lazy: true): Hibernate 7 annotations cannot state it; GeneratedDomainClassBinder sets it after binding']++
        return false
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
}
