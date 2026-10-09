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
import java.util.concurrent.atomic.AtomicInteger

import jakarta.persistence.InheritanceType
import spock.lang.Unroll
import grails.gorm.tests.HibernateGormDatastoreSpec
import org.hibernate.FetchMode
import org.hibernate.Length
import org.hibernate.boot.Metadata
import org.hibernate.boot.MetadataSources
import org.hibernate.boot.registry.BootstrapServiceRegistryBuilder
import org.hibernate.boot.registry.StandardServiceRegistry
import org.hibernate.boot.registry.StandardServiceRegistryBuilder
import org.hibernate.dialect.H2Dialect
import org.hibernate.engine.jdbc.Size
import org.hibernate.engine.spi.SessionFactoryImplementor
import org.hibernate.id.enhanced.OptimizerFactory
import org.hibernate.mapping.PersistentClass
import org.hibernate.mapping.Table
import org.hibernate.spi.NavigablePath

import org.grails.datastore.mapping.multitenancy.MultiTenancySettings
import org.grails.datastore.mapping.multitenancy.resolvers.SystemPropertyTenantResolver
import org.grails.datastore.mapping.reflect.ClassUtils
import org.grails.orm.hibernate.HibernateDatastore

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
import org.grails.orm.hibernate.cfg.domainbinding.util.BackticksRemover
import org.grails.orm.hibernate.cfg.domainbinding.util.ColumnNameForPropertyAndPathFetcher
import org.grails.orm.hibernate.cfg.domainbinding.util.DefaultColumnNameFetcher

/**
 * Compares {@link GrailsDomainGenerator} with the domain binder on real entities. For every supported property of
 * every entity the binder can bind, the column facets the generator decides must equal the ones the binder put on the
 * bound Hibernate {@code Column} and {@code Property}. The binder is the oracle, so a mismatch is either a generator
 * bug or a rule that was mirrored wrongly.
 *
 * <p>The oracle is FROZEN: what the binder bound is read from {@code classic-oracle/generator-differential.txt}
 * ({@link ClassicOracle}, {@link ClassicFacts}), recorded once, and the classic binder is not booted. The generator's side is read
 * from the mapping of a datastore booted through the generated classes. {@code -Pgrails.test.verifyClassicOracle=true} boots the
 * classic binder too and requires it to produce exactly the recorded facts; {@code -Pgrails.test.refreezeClassicOracle=true}
 * rewrites the file from it.</p>
 *
 * <p>The entities are every domain class in the TCK and in the Hibernate 7 tests, which are written to exercise
 * binder permutations. They are grouped by association so each group boots as its own datastore; a group that cannot
 * boot alone is reported with its reason, and properties the generator does not support yet are counted by kind, so
 * the coverage gap stays visible.</p>
 */
class GrailsDomainGeneratorDifferentialSpec extends HibernateGormDatastoreSpec {

    /** The facts the classic binder bound for the entities of the group being compared, by entity name. */
    private Map<String, Map> classic = [:]

    /** The filter definitions of the classic session factory of that group, by name. */
    private Map<String, Map> classicFilters = [:]

    void "the generator decides the same column facets as the binder"() {
        given:
        GrailsDomainGenerator generator = newGenerator()
        ClassicOracle oracle = new ClassicOracle('generator-differential')
        List<Class<?>> candidates = ScannedDomainClasses.findEntities()
        List<List<Class<?>>> groups = ScannedDomainClasses.groupByAssociation(candidates)

        when:
        Map<String, Object> result = compareGroups(generator, oracle, groups, null)
        oracle.finish()
        writeReport(candidates.size(), groups.size(), oracle, result)

        then:
        result.compared > 200
        result.mismatches.isEmpty()
        result.oracleProblems.isEmpty()
        oracle.drift.isEmpty()
    }

    @Unroll
    void "a changed recorded fact (#label) is reported as a mismatch"() {
        given: "the smallest group the oracle holds a column of"
        GrailsDomainGenerator generator = newGenerator()
        ClassicOracle intactOracle = new ClassicOracle('generator-differential', ClassicOracle.Mode.FROZEN)
        List<Class<?>> group = ScannedDomainClasses.groupByAssociation(ScannedDomainClasses.findEntities()).findAll { List<Class<?>> candidate ->
            ClassicOracle.Section section = intactOracle.recordedSection(candidate.first().name)
            section != null && section.header.unbootable == null && section.parsedByKey('entity').values().any { Map entity ->
                entity.props.values().any { Map property -> isA(property.value, 'BasicValue') && !columnsOf(property).isEmpty() }
            }
        }.min { List<Class<?>> candidate -> candidate.size() }

        when:
        Map<String, Object> intact = compareGroups(generator, intactOracle, [group], null)
        Map<String, Object> changed = compareGroups(
                generator, new ClassicOracle('generator-differential', ClassicOracle.Mode.FROZEN), [group], perturbation)

        then: "the recorded facts alone pass, and the same facts with one thing changed do not"
        intact.compared > 0
        intact.mismatches.isEmpty()
        changed.mismatches.any { String line -> line.contains(expected) }

        where:
        label << ['the nullability of every column', 'the table of every entity', 'the kind of every class']
        perturbation << [
                { List<Class<?>> g, Map<String, Map> classic ->
                    classic.values().each { Map entity ->
                        entity.props.values().each { Map property ->
                            if (isA(property.value, 'BasicValue')) {
                                columnsOf(property).each { Map column -> column.nullable = !column.nullable }
                            }
                        }
                    }
                },
                { List<Class<?>> g, Map<String, Map> classic -> classic.values().each { Map entity -> entity.table.name = "${entity.table.name}_changed".toString() } },
                { List<Class<?>> g, Map<String, Map> classic -> classic.values().each { Map entity -> entity.kind = entity.kind == 'RootClass' ? 'JoinedSubclass' : 'RootClass' } },
        ]
        expected << [' nullable: generator=', ' tableName: generator=', ' kind: generator=']
    }

    /**
     * The comparison over the given groups, against the facts the oracle holds. A {@code perturb} closure (called with the group and the
     * facts of its entities, which it may change) lets a spec of the oracle itself prove that a changed recorded fact is reported.
     */
    Map<String, Object> compareGroups(
            GrailsDomainGenerator generator, ClassicOracle oracle, List<List<Class<?>>> groups, Closure<?> perturb) {
        List<String> mismatches = []
        List<String> oracleProblems = []
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

        for (List<Class<?>> group : groups) {
            ClassicOracle.Section section = oracle.section(group.first().name, group*.name) { classicSection(group) }
            if (section.header.unbootable != null) {
                unbootable[group*.simpleName.join(',')] = section.header.unbootable.toString()
                continue
            }
            classic = section.parsedByKey('entity')
            classicFilters = section.parsedByKey('filter')
            if (perturb != null) {
                perturb.call(group, classic)
            }
            HibernateDatastore datastore
            try {
                datastore = bootGenerated(group)
            } catch (Throwable e) {
                oracleProblems << "${group*.simpleName.join(',')}: the generated-class mode cannot boot the group the classic binder booted: ${e.message?.readLines()?.first()}".toString()
                continue
            }
            try {
                List<GrailsHibernatePersistentEntity> boundEntities = datastore.mappingContext.getHibernatePersistentEntities()
                        .findAll { group.contains(it.javaClass) && classic.containsKey(it.name) }
                if (boundEntities*.name.toSet() != classic.keySet()) {
                    oracleProblems << "${group*.simpleName.join(',')}: the recorded entities ${classic.keySet()} are not the entities ${boundEntities*.name} the mapping holds: ${ClassicOracle.REFREEZE_HINT}".toString()
                }
                boundEntities.each { GrailsHibernatePersistentEntity entity -> replayJoinTables(entity) }
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
                            mismatches.addAll(compareConstraints(generator, entity, generator.constraintFacets(entity), constraints))
                        }
                        if (generator.generationProblem(entity) == null) {
                            mismatches.addAll(compareNaturalId(generator, entity, generator.naturalIdFacets(entity), naturals))
                            mismatches.addAll(compareCache(entity, generator.cacheFacets(entity), caches))
                        }
                    }
                    mismatches.addAll(compareTenantFilter(entity, generator, skipped, tenants))
                    if (entity.isRoot()) {
                        if (entity.identity instanceof HibernateSimpleIdentityProperty) {
                            mismatches.addAll(compareIdentifierGenerator(entity, generator.idFacets(entity), strategies))
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
                        Map bound = boundProperty(classic[entity.name], property)
                        if (property instanceof HibernateEmbeddedProperty) {
                            HibernateEmbeddedProperty embedded = (HibernateEmbeddedProperty) property
                            List<EmbeddedLeaf> leaves = generator.embeddedLeaves(embedded)
                            if (leaves.any { EmbeddedLeaf leaf -> !generator.validationAnnotations(leaf.property).isEmpty() }) {
                                skipped['bean validation constraints (applied by Hibernate after binding)']++
                            } else if (bound == null || !isA(bound.value, 'Component')) {
                                skipped['embedded property with no bound component']++
                            } else {
                                embeddedProperties++
                                embeddedLeaves += leaves.size()
                                mismatches.addAll(compareEmbedded(entity, embedded, leaves, generator, bound, explicitTypes, known, collections, associations))
                            }
                            continue
                        }
                        if (property instanceof HibernateToManyEntityProperty) {
                            if (bound == null || !isA(bound.value, 'Collection')) {
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
                        // a collection the mapping types with a class or serializable is one column of the owner's table, compared like any column
                        if (property instanceof HibernateBasicProperty && !isA(bound?.value, 'BasicValue')) {
                            if (!generator.validationAnnotations(property).isEmpty()) {
                                skipped['bean validation constraints (applied by Hibernate after binding)']++
                            } else if (bound == null || !isA(bound.value, 'Collection')) {
                                skipped['collection property with no bound collection']++
                            } else {
                                CollectionFacets facets = generator.collectionFacets((HibernateBasicProperty) property)
                                collections["${facets.kind()}${property instanceof HibernateEnumProperty ? ' of enums' : ''}".toString()]++
                                mismatches.addAll(compareCollection(
                                        "${entity.name}.${property.name}".toString(), (HibernateBasicProperty) property, facets,
                                        generator, bound.value, explicitTypes, known))
                            }
                            continue
                        }
                        if (property instanceof HibernateToOneProperty) {
                            if (bound == null || !isA(bound.value, 'ToOne') ||
                                    (!isA(bound.value, 'OneToOne') && columnsOf(bound).isEmpty())) {
                                skipped['association with no bound column']++
                                continue
                            }
                            compared++
                            String where = "${entity.name}.${property.name}".toString()
                            associations[toOneKind(property, bound)]++
                            if (isA(bound.value, 'OneToOne')) {
                                mismatches.addAll(compareOneToOne(where, generator.toOneFacets((HibernateToOneProperty) property), bound, known))
                                continue
                            }
                            // Hibernate copies the length, precision and scale of the referenced identifier onto a foreign key column after binding
                            ToOneFacets toOneFacets = generator.toOneFacets((HibernateToOneProperty) property)
                            if (toOneFacets.joinColumns().size() != columnsOf(bound).size()) {
                                mismatches << "${where} columns: generator=${toOneFacets.joinColumns()*.name()} binder=${columnsOf(bound)*.name}".toString()
                                continue
                            }
                            if (columnsOf(bound).size() > 1) {
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
                        if (bound == null || columnsOf(bound).size() != 1) {
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
                            mismatches.addAll(compareType(where, property, generator, bound, explicitTypes, property instanceof HibernateBasicProperty))
                        }
                    }
                }
                mismatches.addAll(compareAnnotationBoundHierarchies(generator, boundEntities, skipped, annotationRead, known, tenants))
            } finally {
                datastore.close()
            }
        }
        return [
                mismatches: mismatches, oracleProblems: oracleProblems, skipped: skipped, unbootable: unbootable, compared: compared,
                entities: entities, derived: derived, embeddedProperties: embeddedProperties, embeddedLeaves: embeddedLeaves,
                explicitTypes: explicitTypes, collections: collections, known: known, tenants: tenants, strategies: strategies,
                hierarchies: hierarchies, annotationRead: annotationRead, associations: associations, constraints: constraints,
                naturals: naturals, caches: caches, composites: composites, rejectedComposites: rejectedComposites,
        ]
    }

    private static void writeReport(int candidates, int groups, ClassicOracle oracle, Map<String, Object> result) {
        List<String> mismatches = result.mismatches
        Map<String, String> unbootable = result.unbootable
        StringBuilder report = new StringBuilder()
        report << "differential: ${candidates} candidate classes in ${groups} groups; " +
                "${unbootable.size()} groups could not boot alone; ${result.entities} entities, ${result.compared} properties compared " +
                "(${result.derived} derived); ${result.embeddedProperties} embedded properties compared, ${result.embeddedLeaves} embedded columns\n"
        report << "classic oracle: ${oracle.mode}${oracle.drift.isEmpty() ? '' : ", ${oracle.drift.size()} recorded groups differ from the live classic binder"}\n"
        oracle.drift.each { report << "DRIFT ${it}\n" }
        result.oracleProblems.each { report << "ORACLE ${it}\n" }
        report << "explicit types compared: ${result.explicitTypes}\n"
        report << "collections of basic values compared by kind: ${result.collections}\n"
        report << "associations compared by kind: ${result.associations}\n"
        report << "tenant filters compared: ${result.tenants}\n"
        report << "table constraints compared: ${result.constraints}\n"
        report << "natural ids compared: ${result.naturals}\n"
        report << "entity caches compared: ${result.caches}\n"
        report << "composite identifiers compared: ${result.composites}\n"
        result.rejectedComposites.each { report << "composite identifier rejected: ${it}\n" }
        report << "id generators compared by strategy: ${result.strategies}\n"
        report << "entities compared by hierarchy role: ${result.hierarchies}\n"
        report << "hierarchies read back through Hibernate's annotation binder: ${result.annotationRead}\n"
        report << "known divergences (explained in the plan, not mismatches): ${result.known}\n"
        report << "unsupported by kind: ${result.skipped}\n"
        report << "mismatches by facet: ${mismatches.groupBy { (it =~ /\s(\w+): generator=/)[0][1] }.collectEntries { k, v -> [k, v.size()] }}\n"
        unbootable.each { report << "unbootable: ${it.key.take(120)} -> ${it.value.take(200)}\n" }
        mismatches.each { report << "MISMATCH ${it}\n" }
        new File('build/differential-report.txt').text = report.toString()
    }

    /**
     * What the classic binder bound for a group, as a section of the oracle file: for each entity the facts {@link ClassicFacts} reads
     * from its {@code PersistentClass} (and the identifier generator the session factory holds for a root with a simple identifier), the
     * filter definitions of the session factory, or the first line of the reason it cannot boot the group. Only called when the classic
     * binder is booted (VERIFY and REFREEZE).
     */
    static ClassicOracle.Section classicSection(List<Class<?>> group) {
        HibernateDatastore datastore
        try {
            datastore = boot(group)
        } catch (Throwable e) {
            return new ClassicOracle.Section(group.first().name, [
                    members   : group*.name,
                    unbootable: ClassicOracle.stableReason((e.message ?: e.getClass().simpleName).readLines().first()),
            ])
        }
        try {
            ClassicOracle.Section section = new ClassicOracle.Section(group.first().name, [members: group*.name])
            SessionFactoryImplementor sessionFactory = (SessionFactoryImplementor) datastore.sessionFactory
            List<GrailsHibernatePersistentEntity> bound = datastore.mappingContext.getHibernatePersistentEntities()
                    .findAll { group.contains(it.javaClass) && it.persistentClass != null && it.persistentClass.entityName == it.name }
                    .sort { it.name }
            Map<Table, String> tokens = new IdentityHashMap<Table, String>()
            for (GrailsHibernatePersistentEntity entity : bound) {
                Set<String> names = new TreeSet<String>(entity.persistentPropertiesToBind*.name)
                if (entity.version != null) {
                    names << entity.version.name
                }
                if (entity.identity != null) {
                    names << entity.identity.name
                }
                Map facts = ClassicFacts.persistentClass(entity.persistentClass, names, tokens)
                if (entity.isRoot() && entity.identity instanceof HibernateSimpleIdentityProperty) {
                    facts.generator = ClassicFacts.generator(sessionFactory.mappingMetamodel.getEntityDescriptor(entity.name).generator)
                }
                Map<String, Map> joinTables = [:]
                entity.persistentPropertiesToBind.each { HibernatePersistentProperty property ->
                    Map state = ClassicFacts.joinTableState(property.hibernateMappedForm?.joinTable)
                    if (state != null) {
                        joinTables[property.name] = state
                    }
                }
                facts.joinTables = joinTables
                section.add('entity', entity.name, facts)
            }
            sessionFactory.definedFilterNames.toList().sort().each { String name ->
                section.add('filter', name, ClassicFacts.filterDefinition(sessionFactory.getFilterDefinition(name)))
            }
            return section
        } finally {
            datastore.close()
        }
    }

    /**
     * Puts back on the mapping of an entity the join tables the classic binder left on it. The classic binder writes into the mapping while it
     * binds (the inverse side of a many-to-many adopts the owning side's join table, a circular many-to-many gets a renamed key) and this
     * comparison has always read the mapping the binder left, so the generator reads the same state here, on a mapping the binder never touched.
     */
    private void replayJoinTables(GrailsHibernatePersistentEntity entity) {
        Map<String, Map> states = (Map<String, Map>) classic[entity.name].joinTables ?: [:]
        states.each { String name, Map state ->
            HibernatePersistentProperty property = entity.persistentPropertiesToBind.find { it.name == name }
            property.hibernateMappedForm.joinTable = ClassicFacts.restoreJoinTable(state)
        }
    }

    /**
     * Boots a group of entities with the classic binder (stated, since native binding is the default). GORM only gives an entity a tenant id (and the binder only adds the tenant filter) in
     * discriminator multi-tenancy mode, so a group with multi-tenant entities is booted in that mode when it can be.
     */
    private static HibernateDatastore boot(List<Class<?>> group) {
        if (group.any { Class<?> type -> ClassUtils.isMultiTenant(type) }) {
            try {
                return new HibernateDatastore([
                        'dataSource.dbCreate'                     : 'create-drop',
                        'hibernate.generatedDomainClasses'        : false,
                        'grails.gorm.multiTenancy.mode'          : MultiTenancySettings.MultiTenancyMode.DISCRIMINATOR,
                        'grails.gorm.multiTenancy.tenantResolver': new SystemPropertyTenantResolver(),
                ], group as Class[])
            } catch (Exception ignored) {
                // a domain that needs another configuration is booted by default below
            }
        }
        // the same create-drop the no-argument constructor applies, which resolves the SQL types the records hold
        return new HibernateDatastore(['dataSource.dbCreate': 'create-drop', 'hibernate.generatedDomainClasses': false], group as Class[])
    }

    /**
     * Boots a group through the generated classes, which is what the comparison reads the mapping of: the entities, their properties and the
     * facets the generator decides from them. The tenant mode is the one the classic binder was booted in.
     */
    private static HibernateDatastore bootGenerated(List<Class<?>> group) {
        Map<String, Object> config = [
                'dataSource.url'                  : "jdbc:h2:mem:generatorDiff${BOOTS.incrementAndGet()};LOCK_TIMEOUT=10000".toString(),
                'dataSource.dbCreate'             : 'create-drop',
                'hibernate.generatedDomainClasses': true,
        ]
        if (group.any { Class<?> type -> ClassUtils.isMultiTenant(type) }) {
            config['grails.gorm.multiTenancy.mode'] = MultiTenancySettings.MultiTenancyMode.DISCRIMINATOR
            config['grails.gorm.multiTenancy.tenantResolver'] = new SystemPropertyTenantResolver()
        }
        try {
            return new HibernateDatastore(config, group as Class[])
        } catch (Exception e) {
            if (!config.containsKey('grails.gorm.multiTenancy.mode')) {
                throw e
            }
            config.remove('grails.gorm.multiTenancy.mode')
            config.remove('grails.gorm.multiTenancy.tenantResolver')
            return new HibernateDatastore(config, group as Class[])
        }
    }

    private static final AtomicInteger BOOTS = new AtomicInteger()

    private static boolean isA(Map value, String type) {
        return ClassicFacts.isA(value, type)
    }

    private static List<Map> columnsOf(Map holder) {
        return ClassicFacts.columnsOf(holder)
    }

    /**
     * The column facets the generator decided against the bound column and property. A mapping that says {@code insertable: false}
     * or {@code updatable: false} is a known divergence, not a mismatch: {@code PropertyBinder} overwrites those flags with the
     * ones of the columns, which are always set, so the binder ignores the option (pinned in
     * {@link GrailsDomainBinderOptionDefectSpec}) and the generator states what the mapping asks for.
     */
    private List<String> compare(
            String where, ColumnFacets facets, Map bound, Map<String, Integer> known, Collection<String> ignore = [],
            boolean enumeration = false, int columnIndex = 0) {
        Map column = columnsOf(bound)[columnIndex]
        Map<String, List> pairs = [
                name      : [facets.name().replace('`', ''), column.name],
                quoted    : [facets.name().startsWith('`'), column.quoted],
                nullable  : [facets.nullable(), column.nullable],
                unique    : [facets.unique(), column.unique],
                insertable: [facets.insertable(), bound.insertable],
                updatable : [facets.updatable(), bound.updateable],
                length    : [facets.length(), column.length],
                precision : [facets.precision(), column.precision],
                scale     : [facets.scale(), column.scale],
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
    private List<String> compareConstraints(
            GrailsDomainGenerator generator, GrailsHibernatePersistentEntity entity, ConstraintFacets facets, Map<String, Integer> constraints) {
        Map table = classic[entity.name].table
        String where = "${entity.name} table ${table.name}"
        Map<String, List<String>> boundIndexes = table.indexes.collectEntries { Map index ->
            [(index.name): index.columns]
        } as Map<String, List<String>>
        Map<String, List<String>> boundKeys = tableKeysWithoutNaturalId(classic[entity.name], naturalKeySets(generator, entity))
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
            String where, List<ColumnFacets> columns, Map bound, Map<String, Integer> known, Collection<String> ignore) {
        List<String> found = []
        for (ColumnFacets facets : columns) {
            int index = columnsOf(bound).findIndexOf { Map column -> column.name == facets.name().replace('`', '') }
            if (index < 0) {
                found << "${where} columns: generator=${columns*.name()} binder=${columnsOf(bound)*.name}".toString()
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
        Map persistentClass = classic[entity.name]
        String where = "${entity.name} composite id"
        if (!isA(persistentClass.identifier, 'Component')) {
            return ["${where} kind: generator=component binder=${persistentClass.identifierKind}".toString()]
        }
        Map id = persistentClass.identifier
        composites['entities']++
        List<String> found = []
        if (facets.parts()*.path().toSet() != id.members*.name.toSet()) {
            return ["${where} parts: generator=${facets.parts()*.path()} binder=${id.members*.name}".toString()]
        }
        for (EmbeddedLeaf part : facets.parts()) {
            Map bound = id.members.find { Map member -> member.name == part.path() }
            String partWhere = "${where} part ${part.path()}".toString()
            List<ColumnFacets> partColumns = part.toOne() != null ? part.toOne().joinColumns() : [part.column()]
            if (columnsOf(bound).size() != partColumns.size()) {
                found << "${partWhere} columns: generator=${partColumns*.name()} binder=${columnsOf(bound)*.name}".toString()
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
        if (persistentClass.table.primaryKey?.toSet() != expectedKey) {
            found << "${where} primaryKey: generator=${expectedKey} binder=${persistentClass.table.primaryKey}".toString()
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
            String where, Map bound, Map annotated, CompositeIdFacets facets, Map<String, Integer> known) {
        if (!isA(annotated.identifier, 'Component')) {
            return ["${where} compositeId: generator=${annotated.identifierKind} binder=Component".toString()]
        }
        Map boundId = bound.identifier
        Map annotatedId = annotated.identifier
        List<String> found = []
        if (annotated.identifierProperty != null) {
            found << "${where} compositeId identifierProperty: generator=${annotated.identifierProperty} binder=none".toString()
        }
        if (!annotatedId.embedded) {
            found << "${where} compositeId embedded: generator=false binder=true".toString()
        }
        if (annotated.identifierMapper == null ||
                annotated.identifierMapper.toSet() != boundId.members*.name.toSet()) {
            found << "${where} compositeId identifierMapper: generator=${annotated.identifierMapper} binder=${boundId.members*.name}".toString()
        } else {
            known['Hibernate adds an _identifierMapper property and component to an @IdClass entity; the binder has none']++
        }
        if (boundId.members*.name.toSet() != annotatedId.members*.name.toSet()) {
            return ["${where} compositeId parts: generator=${annotatedId.members*.name} binder=${boundId.members*.name}".toString()]
        }
        boundId.members.each { Map boundPart ->
            Map annotatedPart = annotatedId.members.find { Map member -> member.name == boundPart.name }
            boolean multiple = columnsOf(boundPart).size() > 1
            Map<String, List> pairs = [
                    columns : multiple ? [columnsOf(boundPart)*.name.toSet(), columnsOf(annotatedPart)*.name.toSet()] :
                            [columnsOf(boundPart)*.name, columnsOf(annotatedPart)*.name],
                    nullable: [columnsOf(boundPart)*.nullable.toSet(), columnsOf(annotatedPart)*.nullable.toSet()],
                    kind    : [boundPart.value.kind, annotatedPart.value.kind],
            ]
            found.addAll(pairs.findAll { String facet, List values -> values[0] != values[1] }.collect { String facet, List values ->
                "${where} compositeId part ${boundPart.name} ${facet}: generator=${values[1]} binder=${values[0]}".toString()
            })
            if (isA(boundPart.value, 'ToOne') && isA(annotatedPart.value, 'ToOne')) {
                String target = annotatedPart.value.referencedEntityName
                if (target != boundPart.value.referencedEntityName) {
                    found << "${where} compositeId part ${boundPart.name} target: generator=${target} binder=${boundPart.value.referencedEntityName}".toString()
                }
            }
        }
        Set<String> boundKey = bound.table.primaryKey?.toSet()
        Set<String> annotatedKey = annotated.table.primaryKey?.toSet()
        if (boundKey != annotatedKey) {
            found << ("${where} compositeId primaryKey: generator=${annotated.table.primaryKey} " +
                    "binder=${bound.table.primaryKey}").toString()
        } else if (bound.table.primaryKey != annotated.table.primaryKey) {
            known['Hibernate orders the primary key columns of an @IdClass by the sorted identifier properties; the binder by its component']++
        }
        Set<Set<String>> boundKeys = bound.table.foreignKeys.collect { it.toSet() }.toSet()
        Set<Set<String>> annotatedKeys = annotated.table.foreignKeys.collect { it.toSet() }.toSet()
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
        Map root = classic[entity.name]
        if (root.kind != 'RootClass') {
            return facets == null ? [] : ["${entity.name} cache: generator=${facets} binder=not a root".toString()]
        }
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
    private static List<String> compareAnnotatedCache(String where, Map bound, Map annotated) {
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
     * The natural identifier: the properties the binder marked natural are the ones a natural id of the class or of a subclass
     * names (a subclass may name a property it inherits), each property the entity's own natural id names is updatable exactly when
     * the natural id is mutable, and one unique key on the table of the class spans their columns in the order the mapping names them.
     */
    private List<String> compareNaturalId(
            GrailsDomainGenerator generator, GrailsHibernatePersistentEntity entity, NaturalIdFacets facets, Map<String, Integer> naturals) {
        Map persistentClass = classic[entity.name]
        List<Map> natural = persistentClass.declaredNames.collect { String name -> persistentClass.props[name] }.findAll { Map p -> p.naturalIdentifier }
        String where = "${entity.name} natural id"
        Set<String> named = hierarchyNaturalNames(generator, entity)
        if (!named.containsAll(natural*.name)) {
            return ["${where} properties: generator=${named} binder=${natural*.name}".toString()]
        }
        if (facets == null) {
            return []
        }
        naturals['entities']++
        naturals['properties'] += facets.propertyNames().size()
        List<String> found = []
        facets.propertyNames().each { String name ->
            Map p = persistentClass.props[name]
            if (p == null || !p.naturalIdentifier) {
                found << "${where} properties: generator=${facets.propertyNames()} binder=not natural: ${name}".toString()
            } else if (p.updateable != facets.mutable()) {
                found << "${where} updatable of ${p.name}: generator=${facets.mutable()} binder=${p.updateable}".toString()
            }
        }
        List<String> columns = naturalKeyColumns(generator, entity, persistentClass, facets)
        if (!persistentClass.table.uniqueKeys.any { Map key -> key.columns == columns }) {
            found << "${where} uniqueKey: generator=${columns} binder=${persistentClass.table.uniqueKeys*.columns}".toString()
        }
        return found
    }

    /**
     * The columns of the natural id the binder puts in its unique key: those of each property's value, in the order of the mapping.
     * The columns of an embedded property are in the order of the properties of the embedded type as it declares them, which is the
     * order of the generator's leaves: the binder adds them to the key before Hibernate sorts the properties of the component by
     * name, so the bound component no longer tells.
     */
    private static List<String> naturalKeyColumns(
            GrailsDomainGenerator generator, GrailsHibernatePersistentEntity entity, Map persistentClass, NaturalIdFacets facets) {
        return facets.propertyNames().collectMany { String name ->
            Object mapped = entity.getPropertyByName(name)
            List<String> bound = columnsOf(persistentClass.props[name]).collect { Map column -> (String) column.name }
            if (mapped instanceof HibernateEmbeddedProperty) {
                List<String> declared = generator.embeddedLeaves((HibernateEmbeddedProperty) mapped).collectMany { EmbeddedLeaf leaf ->
                    leaf.toOne() != null ? leaf.toOne().joinColumns()*.name() : (leaf.column() != null ? [leaf.column().name()] : [])
                }
                return declared.toSet() == bound.toSet() ? declared : bound
            }
            return bound
        } as List<String>
    }

    /** The properties the natural id of the entity or of one of its subclasses names. */
    private static Set<String> hierarchyNaturalNames(GrailsDomainGenerator generator, GrailsHibernatePersistentEntity entity) {
        Set<String> names = new HashSet<String>()
        Deque<GrailsHibernatePersistentEntity> pending = new ArrayDeque<GrailsHibernatePersistentEntity>([entity])
        while (!pending.isEmpty()) {
            GrailsHibernatePersistentEntity next = pending.poll()
            NaturalIdFacets facets = generator.naturalIdFacets(next)
            if (facets != null) {
                names.addAll(facets.propertyNames())
            }
            pending.addAll(next.childEntities)
        }
        return names
    }

    /**
     * The column sets of the natural ids of the entity and its subclasses whose unique key lies on the table of the entity: the
     * binder puts the key of a subclass of a single-table hierarchy on the table of the hierarchy. They are compared with the natural
     * id, not with the unique keys the mapping states.
     */
    private Set<Set<String>> naturalKeySets(GrailsDomainGenerator generator, GrailsHibernatePersistentEntity entity) {
        Set<Set<String>> sets = new HashSet<Set<String>>()
        Deque<GrailsHibernatePersistentEntity> pending = new ArrayDeque<GrailsHibernatePersistentEntity>([entity])
        while (!pending.isEmpty()) {
            GrailsHibernatePersistentEntity next = pending.poll()
            NaturalIdFacets facets = generator.naturalIdFacets(next)
            if (facets != null && classic[next.name].table.token == classic[entity.name].table.token) {
                sets << naturalKeyColumns(generator, next, classic[next.name], facets).toSet()
            }
            pending.addAll(next.childEntities)
        }
        return sets
    }

    /**
     * The natural identifier Hibernate's annotation binder reads from {@code @NaturalId}: the same properties, with the same
     * updatability, and a unique key over the same columns. The key has the name Hibernate's implicit naming gives it and its
     * columns follow the order of the fields, not the order the mapping names them: listed, not reported.
     */
    private static List<String> compareAnnotatedNaturalId(
            String where, Map bound, Map annotated, NaturalIdFacets facets, Map<String, Integer> known) {
        // the properties a subclass names are marked by the binder, not by the annotation (Hibernate refuses @NaturalId on a subclass)
        List<Map> boundNatural = bound.propertyNames.collect { String name -> bound.props[name] }.findAll { Map p ->
            p.naturalIdentifier && facets != null && facets.propertyNames().contains(p.name)
        }
        List<Map> annotatedNatural = annotated.propertyNames.collect { String name -> annotated.props[name] }.findAll { Map p -> p.naturalIdentifier }
        if (boundNatural.isEmpty() && annotatedNatural.isEmpty()) {
            return []
        }
        List<String> found = []
        if (boundNatural*.name.toSet() != annotatedNatural*.name.toSet()) {
            return ["${where} naturalProperties: generator=${annotatedNatural*.name} binder=${boundNatural*.name}".toString()]
        }
        boundNatural.each { Map p ->
            if (p.updateable != annotated.props[p.name].updateable) {
                found << "${where} natural updatable of ${p.name}: generator=${annotated.props[p.name].updateable} binder=${p.updateable}".toString()
            }
        }
        List<String> boundColumns = boundNatural.collectMany { Map p -> columnsOf(p)*.name }
        Map boundKey = bound.table.uniqueKeys.find { Map key -> key.columns.toSet() == boundColumns.toSet() }
        Map annotatedKey = annotated.table.uniqueKeys.find { Map key -> key.columns.toSet() == boundColumns.toSet() }
        if (annotatedKey == null) {
            found << "${where} naturalKey: generator=none binder=${boundKey?.columns}".toString()
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
    private static Map<String, List<String>> tableKeysWithoutNaturalId(Map persistentClass, Set<Set<String>> naturalColumns) {
        return persistentClass.table.uniqueKeys.findAll { Map key ->
            !(key.columns.size() == 1 && key.name.startsWith('UK_')) && !naturalColumns.contains(key.columns.toSet())
        }.collectEntries { Map key ->
            [(key.name): key.columns]
        } as Map<String, List<String>>
    }

    /**
     * The tenant filter {@code MultiTenantFilterBinder} puts on the entity's class and the one global definition it
     * registers: the same name, condition and parameter, or no filter at all when the generator says there is none.
     */
    private List<String> compareTenantFilter(
            GrailsHibernatePersistentEntity entity, GrailsDomainGenerator generator,
            Map<String, Integer> skipped, Map<String, Integer> tenants) {
        TenantFacets facets
        try {
            facets = generator.tenantFacets(entity)
        } catch (UnsupportedOperationException e) {
            skipped['tenant filter: a mapped type on the tenant id']++
            return []
        }
        List<Map> filters = classic[entity.name].ownFilters
        String where = "${entity.name} tenant filter"
        if (facets == null) {
            return filters.isEmpty() ? [] : ["${where}: generator=none binder=${filters*.condition}".toString()]
        }
        tenants['filters']++
        if (filters.size() != 1) {
            return ["${where}: generator=1 binder=${filters.size()}".toString()]
        }
        Map filter = filters[0]
        Map definition = classicFilters[facets.filterName()]
        Map<String, List> pairs = [
                condition     : [facets.condition(), filter.condition],
                autoAlias     : [true, filter.autoAlias],
                parameterNames: [[facets.filterName()].toSet(), definition?.parameterNames?.toSet()],
                parameterType : [facets.parameterType()?.name, definition?.parameterTypes?.get(facets.filterName())],
                defaultCond   : [null, definition?.defaultCondition],
                autoEnabled   : [false, definition?.autoEnabled],
                loadByKey     : [false, definition?.appliedToLoadByKey],
        ]
        return pairs.findAll { String facet, List values -> values[0] != values[1] }.collect { String facet, List values ->
            "${where} ${facet}: generator=${values[0]} binder=${values[1]}".toString()
        }
    }

    /** The filter the generated class gets, read back by Hibernate's annotation binder, against the binder's. */
    private List<String> compareAnnotatedTenantFilter(
            GrailsHibernatePersistentEntity entity, GrailsDomainGenerator generator, Map annotated, Map<String, Integer> tenants) {
        TenantFacets facets
        try {
            facets = generator.tenantFacets(entity)
        } catch (UnsupportedOperationException ignored) {
            return []
        }
        List<Map> bound = classic[entity.name].ownFilters
        List<Map> read = annotated.ownFilters
        String where = "${entity.name} hibernate tenant filter"
        if (bound.size() != read.size()) {
            return ["${where}: generator=${read*.condition} binder=${bound*.condition}".toString()]
        }
        if (!bound.isEmpty()) {
            tenants['filters read back']++
        }
        return [bound, read].transpose().collectMany { List pair ->
            Map b = (Map) pair[0]
            Map r = (Map) pair[1]
            Map<String, List> pairs = [
                    condition: [b.condition, r.condition],
                    autoAlias: [b.autoAlias, r.autoAlias],
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
            List<GrailsHibernatePersistentEntity> entities, GrailsDomainGenerator generator, Map definition, Map<String, Integer> tenants) {
        TenantFacets facets = null
        try {
            facets = entities.collect { generator.tenantFacets(it) }.find { it != null }
        } catch (UnsupportedOperationException ignored) {
            return []
        }
        String where = "${entities.first().hibernateRootEntity.name} hibernate filter definition"
        if (facets == null) {
            return definition == null ? [] : ["${where}: generator=${definition.name} binder=none".toString()]
        }
        tenants['filter definitions read back']++
        if (definition == null) {
            return ["${where}: generator=none binder=${facets.filterName()}".toString()]
        }
        Map<String, List> pairs = [
                parameterNames: [[facets.filterName()].toSet(), definition.parameterNames.toSet()],
                parameterType : [facets.parameterType()?.name, definition.parameterTypes?.get(facets.filterName())],
                defaultCond   : [null, definition.defaultCondition ?: null],
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
            Map collection, Map<String, Integer> explicitTypes, Map<String, Integer> known) {
        List<String> found = []
        Map<String, List> pairs = [
                kind     : [facets.kind()?.name(), collection.collectionKind],
                table    : [facets.tableName().replace('`', ''), collection.collectionTable.name],
                schema   : [facets.schema(), collection.collectionTable.schema],
                catalog  : [facets.catalog(), collection.collectionTable.catalog],
                lazy     : [facets.lazy(), collection.lazy],
                extraLazy: [facets.extraLazy(), collection.extraLazy],
                fetchMode: [facets.fetchMode()?.name(), collection.fetchMode],
                batchSize: [facets.batchSize(), Math.max(collection.batchSize, 0)],
                cache    : [facets.cacheUsage(), collection.cacheConcurrencyStrategy],
                inverse  : [false, collection.inverse],
        ]
        found.addAll(pairs.findAll { String facet, List values -> values[0] != values[1] }.collect { String facet, List values ->
            "${where} ${facet}: generator=${values[0]} binder=${values[1]}".toString()
        })
        found.addAll(keyUpdatable(where, property, facets, collection.key, known))
        found.addAll(compareValueColumns("${where} key".toString(), facets.keys(), collection.key, collection.collectionTable, true))
        Map element = ClassicFacts.propertyOf(collection.element)
        found.addAll(compareValueColumn("${where} element".toString(), facets.element(), collection.element, collection.collectionTable, false))
        if (property instanceof HibernateEnumProperty && generator.typeFacets(property) == null) {
            found.addAll(compareEnum("${where} element".toString(), (HibernateEnumProperty) property, generator, element))
        }
        found.addAll(compareType("${where} element".toString(), property, generator, element, explicitTypes))
        List<String> described = facets.keys().collect { ColumnFacets key -> key.name().replace('`', '') } + [facets.element().name().replace('`', '')]
        if (facets.index() != null) {
            described << facets.index().name().replace('`', '')
            if (isA(collection, 'IndexedCollection')) {
                found.addAll(compareValueColumn(
                        "${where} index".toString(), facets.index(), collection.index, collection.collectionTable, false))
                found.addAll(compareIndexType("${where} index".toString(), facets.kind(), facets.indexType(), collection))
            } else {
                found << "${where} index: generator=${facets.index().name()} binder=none".toString()
            }
        } else if (isA(collection, 'IndexedCollection')) {
            found << "${where} index: generator=none binder=${collection.index.selectables*.text}".toString()
        }
        List<String> unused = collection.collectionTable.columns.findAll { String name -> !described.contains(name) }
        if (!unused.isEmpty()) {
            if (facets.kind() == CollectionKind.MAP && unused.size() == 1) {
                known['the binder leaves an unused column in the table of a map of values (the element bound before the map replaces it)']++
            } else {
                found << "${where} tableColumns: generator=${described} binder=${collection.collectionTable.columns}".toString()
            }
        }
        found.addAll(compareCollectionTableIndexes(where, facets.indexes(), collection, false, known))
        return found
    }

    /**
     * The indexes the binder put on the table of a collection against the ones the generator decided: the same names over the same
     * columns in the same order. The two sides of a bidirectional many-to-many share the one join table, so each side's indexes are
     * part of the binder's, which holds both sides'. An index the binder named after a closure (the closure's own {@code toString()}, which
     * holds the identity of the closure instance) is the binder's alone: the generator states none for a closure.
     */
    private static List<String> compareCollectionTableIndexes(
            String where, List<IndexFacets> facets, Map collection, boolean sharedTable, Map<String, Integer> known) {
        Map<String, List<String>> bound = [:]
        for (Map index : collection.collectionTable.indexes) {
            if ((index.name =~ /_closure\d+@[0-9a-f]+/).find()) {
                known['the binder names an index of the collection table after the closure mapped as its index, which differs on every boot']++
            } else {
                bound[(String) index.name] = (List<String>) index.columns
            }
        }
        Map<String, List<String>> expected = facets.collectEntries { IndexFacets index -> [(index.name()): index.columns().collect { it.replace('`', '') }] }
        if (sharedTable) {
            return expected.findAll { String name, List<String> columns -> !bound.containsKey(name) || !bound[name].containsAll(columns) }.collect {
                String name, List<String> columns -> "${where} collectionTableIndexes: generator=${name}:${columns} binder=${bound}".toString()
            }
        }
        return expected == bound ? [] : ["${where} collectionTableIndexes: generator=${expected} binder=${bound}".toString()]
    }

    /** The key of a collection of basic values must be updatable: Hibernate writes no rows for a collection whose key is not. */
    private static List<String> keyUpdatable(
            String where, HibernateBasicProperty property, CollectionFacets facets, Map key, Map<String, Integer> known) {
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
    private static List<String> compareValueColumn(String where, ColumnFacets facets, Map value, Map table, boolean key) {
        return compareValueColumns(where, [facets], value, table, key)
    }

    /**
     * The columns of a collection's key against what the generator decided for them: one column, or one for each identifier property
     * when the owner has a composite identifier, paired by name because the binder orders them like the identifier (sorted) and the
     * generator like the mapping.
     */
    private static List<String> compareValueColumns(
            String where, List<ColumnFacets> allFacets, Map value, Map table, boolean key) {
        List<Map> columns = columnsOf(value)
        if (columns.size() != allFacets.size()) {
            return ["${where} columns: generator=${allFacets.size()} binder=${columns.size()}".toString()]
        }
        List<String> found = []
        for (ColumnFacets facets : allFacets) {
            Map column = allFacets.size() == 1 ? columns[0] : columns.find { Map c -> c.name == facets.name().replace('`', '') }
            if (column == null) {
                found << "${where} columns: generator=${allFacets*.name()} binder=${columns*.name}".toString()
                continue
            }
            // two collections may share one table (the sides of a many-to-many), and then the primary key holds one of the Column objects of that name
            boolean primaryKey = table.primaryKey != null && table.primaryKey.any { it == column.name }
            Map<String, List> pairs = [
                    name    : [facets.name().replace('`', ''), column.name],
                    nullable: [facets.nullable() && !primaryKey, column.nullable && !primaryKey],
                    unique  : [facets.unique(), column.unique],
            ]
            if (!key) {
                pairs.length = [facets.length(), column.length]
                pairs.precision = [facets.precision(), column.precision]
                pairs.scale = [facets.scale(), column.scale]
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

    /**
     * An embedded property is a Component bound by {@code ComponentBinder}: the generator must name the same column-bearing
     * leaves as the bound component (nested components included) and decide each leaf's column facets, type and enum
     * style the way the binder bound them.
     */
    private List<String> compareEmbedded(
            GrailsHibernatePersistentEntity entity, HibernateEmbeddedProperty property, List<EmbeddedLeaf> leaves,
            GrailsDomainGenerator generator, Map bound, Map<String, Integer> explicitTypes, Map<String, Integer> known,
            Map<String, Integer> collections, Map<String, Integer> associations) {
        List<String> found = []
        String where = "${entity.name}.${property.name}"
        Map<String, Map> boundLeaves = terminalProperties(bound).collectEntries { String path, Map leaf ->
            [(path.substring(property.name.length() + 1)): leaf]
        }
        if (leaves*.path().toSet() != boundLeaves.keySet()) {
            found << "${where} leaves: generator=${leaves*.path()} binder=${boundLeaves.keySet()}".toString()
            return found
        }
        for (EmbeddedLeaf leaf : leaves) {
            Map boundLeaf = boundLeaves[leaf.path()]
            String leafWhere = "${where}.${leaf.path()}".toString()
            if (leaf.column() == null) {
                found.addAll(compareDerived(leafWhere, leaf.property, boundLeaf))
            } else if (columnsOf(boundLeaf).size() != (leaf.toOne() != null && leaf.toOne().joinColumns().size() > 1 ? leaf.toOne().joinColumns().size() : 1)) {
                found << "${leafWhere} columns: generator=1 binder=${columnsOf(boundLeaf).size()}".toString()
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
        found.addAll(compareEmbeddedCollections(where, property, bound.value, generator, explicitTypes, known, collections, associations))
        return found
    }

    /**
     * A collection inside an embedded type has a table of its own and no leaf in the owner's table: the facets the generator decides
     * for it are compared with the collection the binder bound inside the component, nested components included.
     */
    private List<String> compareEmbeddedCollections(
            String where, HibernateEmbeddedProperty property, Map component, GrailsDomainGenerator generator,
            Map<String, Integer> explicitTypes, Map<String, Integer> known, Map<String, Integer> collections,
            Map<String, Integer> associations) {
        List<String> found = []
        GrailsHibernatePersistentEntity type = (GrailsHibernatePersistentEntity) property.associatedEntity
        for (HibernatePersistentProperty peer : type.getHibernatePersistentProperties(property.owner.javaClass)) {
            Map inner = component.members.find { Map candidate -> candidate.name == peer.name }
            if (inner == null) {
                continue
            }
            String peerWhere = "${where}.${peer.name}".toString()
            if (peer instanceof HibernateEmbeddedProperty && isA(inner.value, 'Component')) {
                found.addAll(compareEmbeddedCollections(
                        peerWhere, (HibernateEmbeddedProperty) peer, inner.value, generator, explicitTypes, known, collections, associations))
            } else if (peer instanceof HibernateToManyEntityProperty && isA(inner.value, 'Collection')) {
                ToManyFacets facets = generator.toManyFacets((HibernateToManyEntityProperty) peer)
                associations["embedded ${facets.manyToMany() ? 'many-to-many' : 'one-to-many'}, ${facets.kind()}".toString()]++
                found.addAll(compareToMany(peerWhere, (HibernateToManyEntityProperty) peer, facets, inner, known))
            } else if (peer instanceof HibernateBasicProperty && isA(inner.value, 'Collection')) {
                CollectionFacets facets = generator.collectionFacets((HibernateBasicProperty) peer)
                collections["embedded ${facets.kind()}${peer instanceof HibernateEnumProperty ? ' of enums' : ''}".toString()]++
                found.addAll(compareCollection(
                        peerWhere, (HibernateBasicProperty) peer, facets, generator, inner.value, explicitTypes, known))
            }
        }
        return found
    }

    /** The terminal properties of a property, keyed by dotted path from the property: a component is walked, anything else is its own leaf. */
    private static Map<String, Map> terminalProperties(Map property) {
        Map<String, Map> result = [:]
        if (isA(property.value, 'Component')) {
            for (Map inner : property.value.members) {
                if (isA(inner.value, 'Component')) {
                    terminalProperties(inner).each { String path, Map leaf -> result["${property.name}.${path}".toString()] = leaf }
                } else if (!isA(inner.value, 'Collection')) {
                    result["${property.name}.${inner.name}".toString()] = inner
                }
            }
        } else {
            result[(String) property.name] = property
        }
        return result
    }

    /**
     * The generator class the binder installed on the identifier must be the one the generator names for the
     * strategy, and the parameters the generator passes on must have reached it.
     */
    private List<String> compareIdentifierGenerator(
            GrailsHibernatePersistentEntity entity, IdFacets facets, Map<String, Integer> strategies) {
        Map bound = classic[entity.name].generator
        strategies["${facets.strategy().name}".toString()]++
        List<String> found = []
        String where = "${entity.name} identifier"
        if (bound.className != facets.generatorClass().name) {
            found << "${where} generatorClass: generator=${facets.generatorClass().name} binder=${bound.className}".toString()
            return found
        }
        Map<String, String> parameters = facets.parameters()
        if (bound.sequence != null) {
            String sequence = parameters['sequence_name'] ?: parameters['sequence']
            if (sequence != null && !bound.sequence.name.equalsIgnoreCase(sequence)) {
                found << "${where} sequenceName: generator=${sequence} binder=${bound.sequence.name}".toString()
            }
            if (parameters['increment_size'] != null && bound.sequence.incrementSize != parameters['increment_size'].toInteger()) {
                found << "${where} incrementSize: generator=${parameters['increment_size']} binder=${bound.sequence.incrementSize}".toString()
            }
            if (parameters['optimizer'] != null && bound.sequence.optimizer !=
                    OptimizerFactory.StandardOptimizerDescriptor.fromExternalName(parameters['optimizer']).optimizerClass.name) {
                found << "${where} optimizer: generator=${parameters['optimizer']} binder=${bound.sequence.optimizer}".toString()
            }
        } else if (bound.table != null) {
            if (parameters['table_name'] != null && !bound.table.tableName.toLowerCase().endsWith(parameters['table_name'].toLowerCase())) {
                found << "${where} tableName: generator=${parameters['table_name']} binder=${bound.table.tableName}".toString()
            }
            if (parameters['segment_value'] != null && bound.table.segmentValue != parameters['segment_value']) {
                found << "${where} segmentValue: generator=${parameters['segment_value']} binder=${bound.table.segmentValue}".toString()
            }
            if (parameters['increment_size'] != null && bound.table.incrementSize != parameters['increment_size'].toInteger()) {
                found << "${where} incrementSize: generator=${parameters['increment_size']} binder=${bound.table.incrementSize}".toString()
            }
        }
        return found
    }

    /** A derived property is a Formula with the same text and no column. */
    private List<String> compareDerived(String where, HibernatePersistentProperty property, Map bound) {
        List<String> formulas = bound.value.selectables.findAll { Map selectable -> selectable.formula != null }.collect { Map selectable -> selectable.formula }
        List<String> columns = columnsOf(bound).collect { Map column -> column.name }
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
     * binder's type name is the property's own class and it has no parameters. A converter named by its class carries no JDBC type.
     */
    private List<String> compareType(
            String where, HibernatePersistentProperty property, GrailsDomainGenerator generator,
            Map bound, Map<String, Integer> explicitTypes, boolean wholeCollection = false) {
        Map value = bound.value
        TypeFacets facets = generator.typeFacets(property)
        // a collection property is typed with its element's class, unless the whole collection is one column
        Class<?> type = property instanceof HibernateBasicProperty && !wholeCollection ? ((HibernateBasicProperty) property).componentType : property.type
        List<String> found = []
        Map<String, String> actualParameters = value.typeParameters ?: [:]
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
            if (value.typeName != property.getTypeName(type) || value.converter != facets.converter().name ||
                    (facets.jdbcTypeCode() != null && value.jdbcCode != facets.jdbcTypeCode())) {
                // a converter named by its class states no JDBC type: the one the converter alone resolves to is the binder's too
                found << "${where} converter: generator=${facets.converter()}/${facets.jdbcTypeCode()} binder=${value.converter}/${value.jdbcCode} (type ${value.typeName})".toString()
            }
        } else {
            explicitTypes["registered ${value.typeName}".toString()]++
            Integer actual = value.jdbcCode
            if (value.typeName != property.getTypeName(type) || actual != facets.jdbcTypeCode()) {
                found << "${where} jdbcTypeCode: generator=${facets.jdbcTypeCode()} binder=${actual} (type ${value.typeName})".toString()
            }
        }
        return found
    }

    private List<String> compareEnum(
            String where, HibernateEnumProperty property, GrailsDomainGenerator generator, Map bound) {
        String actual = bound.value.enumStyle
        String expected = generator.enumStyle(property)
        return expected == actual ? [] : ["${where} enumStyle: generator=${expected} binder=${actual}".toString()]
    }

    private List<String> compareEntity(GrailsHibernatePersistentEntity entity, EntityFacets facets, HierarchyFacets hierarchy) {
        Map persistentClass = classic[entity.name]
        Map<String, List> pairs = [
                jpaName      : [facets.jpaName(), persistentClass.jpaEntityName],
                tableName    : [facets.tableName().replace('`', ''), persistentClass.table.name],
                dynamicInsert: [facets.dynamicInsert(), persistentClass.dynamicInsert],
                dynamicUpdate: [facets.dynamicUpdate(), persistentClass.dynamicUpdate],
                // the binder leaves a subclass's unset batch size at -1 and a root's at 0: both mean "not stated"
                batchSize    : [facets.batchSize(), Math.max(persistentClass.batchSize, 0)],
                // VersionBinder: NONE for a root without a version, VERSION otherwise; a subclass reads its root's
                versioned    : [facets.versioned(), persistentClass.optimisticLock == 'VERSION'],
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
        Map persistentClass = classic[entity.name]
        Map<String, List> pairs = [
                kind              : [expectedKind(entity, facets), persistentClass.kind],
                superclass        : [facets.superclass(), persistentClass.superclass],
                abstractClass     : [facets.abstractClass(), persistentClass.abstractClass],
                abstractTable     : [facets.abstractTable(), persistentClass.table.abstractTable],
                ownsTable         : [facets.ownsTable(), persistentClass.ownsTable],
                discriminatorValue: [facets.discriminatorValue(), persistentClass.discriminatorValue],
        ]
        List<String> found = pairs.findAll { String facet, List values -> values[0] != values[1] }.collect { String facet, List values ->
            "${entity.name} ${facet}: generator=${values[0]} binder=${values[1]}".toString()
        }
        if (persistentClass.kind == 'RootClass') {
            found.addAll(compareDiscriminator(entity, facets.discriminator(), persistentClass))
        }
        if (persistentClass.kind == 'JoinedSubclass') {
            List<String> keyColumns = persistentClass.keyColumns
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
            return 'RootClass'
        }
        switch (facets.strategy()) {
            case InheritanceType.JOINED:
                return 'JoinedSubclass'
            case InheritanceType.TABLE_PER_CLASS:
                return 'UnionSubclass'
            default:
                return 'SingleTableSubclass'
        }
    }

    /** The discriminator the binder put on the root: a column or a formula, its type, length, precision, scale and whether it is inserted. */
    private List<String> compareDiscriminator(GrailsHibernatePersistentEntity entity, DiscriminatorFacets facets, Map root) {
        String where = "${entity.name} discriminator"
        if ((facets != null) != (root.discriminator != null)) {
            return ["${where} present: generator=${facets != null} binder=${root.discriminator != null}".toString()]
        }
        if (facets == null) {
            return []
        }
        Map value = root.discriminator
        List<String> formulas = value.selectables.findAll { Map selectable -> selectable.formula != null }.collect { Map selectable -> selectable.formula }
        List<Map> columns = columnsOf(value)
        Map<String, List> pairs = [
                typeName  : [facets.typeName(), value.typeName],
                insertable: [facets.insertable(), root.discriminatorInsertable],
                formula   : [facets.formula() == null ? [] : [facets.formula()], formulas],
                column    : [facets.column() == null ? [] : [facets.column()], columns*.name],
        ]
        if (!columns.isEmpty()) {
            pairs.length = [facets.length(), columns[0].length]
            pairs.precision = [facets.precision(), columns[0].precision]
            pairs.scale = [facets.scale(), columns[0].scale]
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
     * {@code PersistentClass} of each entity with the facts the domain binder recorded: the link between what the generator
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
            found.addAll(compareAnnotatedFilterDefinition(
                    generatable, generator, (Map) ClassicOracle.normalized(ClassicFacts.filterDefinition(metadata.getFilterDefinition('tenantId'))), tenants))
            classes.each { GrailsHibernatePersistentEntity entity, Class<?> generated ->
                Map annotated = annotatedFacts(metadata.getEntityBinding(generated.name))
                found.addAll(compareAnnotatedTenantFilter(entity, generator, annotated, tenants))
                found.addAll(compareAnnotationBound(generator, entity, annotated, byName, annotationRead, known))
            }
        } finally {
            StandardServiceRegistryBuilder.destroy(registry)
        }
        return found
    }

    /** What Hibernate's annotation binder bound for a generated class, as facts (the same extraction as the recorded classic facts). */
    private static Map annotatedFacts(PersistentClass annotated) {
        return annotated == null ? null :
                (Map) ClassicOracle.normalized(ClassicFacts.persistentClass(annotated, [], new IdentityHashMap<Table, String>()))
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

    private List<String> compareAnnotationBound(
            GrailsDomainGenerator generator, GrailsHibernatePersistentEntity entity, Map annotated,
            Map<String, GrailsHibernatePersistentEntity> byName, Map<String, Integer> annotationRead, Map<String, Integer> known) {
        Map bound = classic[entity.name]
        String where = "${entity.name} hibernate"
        if (annotated == null) {
            return ["${where} entity: generator=bound binder=missing".toString()]
        }
        Map<String, List> pairs = [
                kind              : [bound.kind, annotated.kind],
                superclass        : [bound.superclass == null ? null : GrailsDomainGenerator.generatedClassName(byName[bound.superclass]),
                                     annotated.superclass],
                tableName         : [bound.table.name, annotated.table.name],
                ownsTable         : [bound.ownsTable, annotated.ownsTable],
                abstractClass     : [bound.abstractClass, annotated.abstractClass],
                abstractUnionTable: [bound.table.abstractUnionTable, annotated.table.abstractUnionTable],
                properties        : [declaredNames(bound), declaredNames(annotated)],
                optimisticLock    : [bound.optimisticLock, annotated.optimisticLock],
        ]
        if (bound.kind == 'SingleTableSubclass' || bound.kind == 'RootClass' && bound.discriminator != null) {
            pairs.discriminatorValue = [bound.discriminatorValue, annotated.discriminatorValue]
        }
        if (bound.kind == 'JoinedSubclass') {
            pairs.keyColumn = [bound.keyColumns, annotated.keyColumns]
        }
        List<String> found = pairs.findAll { String facet, List values -> values[0] != values[1] }.collect { String facet, List values ->
            "${where} ${facet}: generator=${values[1]} binder=${values[0]}".toString()
        }
        if (bound.kind == 'RootClass') {
            found.addAll(compareAnnotatedDiscriminator(where, bound, annotated, known))
            found.addAll(compareAnnotatedNaturalId(where, bound, annotated, generator.naturalIdFacets(entity), known))
            found.addAll(compareAnnotatedCache(where, bound, annotated))
            if (entity.identity == null && generator.generationProblem(entity) == null) {
                annotationRead['composite identifiers']++
                found.addAll(compareAnnotatedCompositeId(where, bound, annotated, generator.compositeIdFacets(entity), known))
            }
        }
        if (pairs.ownsTable[0]) {
            found.addAll(compareAnnotatedConstraints(where, bound, annotated, generator.constraintFacets(entity), naturalKeySets(generator, entity)))
        }
        Map<String, Map<String, Object>> expectations = leafExpectations(generator, entity)
        for (String propertyName : bound.declaredNames) {
            Map property = bound.props[propertyName]
            Map other = annotated.props[property.name]
            if (other == null) {
                continue
            }
            if (isA(property.value, 'Collection') &&
                    entity.persistentPropertiesToBind.find { it.name == property.name } instanceof HibernateToManyEntityProperty) {
                HibernateToManyEntityProperty toMany = (HibernateToManyEntityProperty) entity.persistentPropertiesToBind.find { it.name == property.name }
                if (generator.validationAnnotations(toMany).isEmpty()) {
                    annotationRead['entity collections']++
                    found.addAll(compareAnnotatedToMany(
                            "${where} property ${property.name}".toString(), generator.toManyFacets(toMany), entity, property, other, byName, known))
                }
                continue
            }
            if (isA(property.value, 'Collection')) {
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
            if (isA(property.value, 'ToOne')) {
                HibernatePersistentProperty source = entity.persistentPropertiesToBind.find { it.name == property.name }
                if (source instanceof HibernateToOneProperty && generator.validationAnnotations(source).isEmpty()) {
                    annotationRead['associations']++
                    found.addAll(compareAnnotatedToOne(
                            "${where} property ${property.name}".toString(), generator.toOneFacets((HibernateToOneProperty) source),
                            property, other, byName, known))
                }
            }
            if (isA(property.value, 'OneToOne')) {
                // the inverse side of a one-to-one has no column of its own
                continue
            }
            Map<String, Map> boundLeaves = terminalProperties(property)
            Map<String, Map> annotatedLeaves = terminalProperties(other)
            if (boundLeaves.keySet() != annotatedLeaves.keySet()) {
                found << "${where} property ${property.name} leaves: generator=${annotatedLeaves.keySet()} binder=${boundLeaves.keySet()}".toString()
                continue
            }
            boundLeaves.each { String path, Map leaf ->
                Map<String, Object> expected = expectations[path] ?: [:]
                if (isA(property.value, 'Component')) {
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
            String where, Map bound, Map annotated, ConstraintFacets facets, Set<Set<String>> naturalKeys) {
        Map<String, List<String>> boundIndexes = bound.table.indexes.collectEntries { Map index ->
            [(index.name): index.columns]
        } as Map<String, List<String>>
        Map<String, List<String>> annotatedIndexes = annotated.table.indexes.collectEntries { Map index ->
            [(index.name): index.columns]
        } as Map<String, List<String>>
        List<String> found = []
        if (boundIndexes != annotatedIndexes) {
            found << "${where} indexes: generator=${annotatedIndexes} binder=${boundIndexes}".toString()
        }
        Map<String, List<String>> boundKeys = tableKeysWithoutNaturalId(bound, naturalKeys)
        // a key the mapping asks for and the binder does not create is stated by the generator, so Hibernate reads it too
        Set<String> unbound = facets.uniqueKeys().findAll { UniqueKeyFacets key -> !key.bound() }*.name().toSet()
        Map<String, List<String>> annotatedKeys = tableKeysWithoutNaturalId(annotated, naturalKeys).findAll { String name, List<String> columns ->
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
    private static Set<String> declaredNames(Map persistentClass) {
        return persistentClass.declaredNames.findAll { String name ->
            !persistentClass.props[name].backref && name != NavigablePath.IDENTIFIER_MAPPER_PROPERTY
        }.toSet()
    }

    private static String toOneKind(HibernatePersistentProperty property, Map bound) {
        if (isA(bound.value, 'OneToOne')) {
            return 'one-to-one (inverse side)'
        }
        return property instanceof HibernateManyToOneProperty ? 'many-to-one' : 'one-to-one (foreign key)'
    }

    /**
     * The inverse side of a one-to-one as {@code OneToOneBinder} bound it: no column, the property of the other side that
     * holds the foreign key, the entity that declares it, the foreign key direction and the fetching.
     */
    private static List<String> compareOneToOne(String where, ToOneFacets facets, Map bound, Map<String, Integer> known) {
        Map value = bound.value
        Map<String, List> pairs = [
                referencedEntity    : [facets.referencedEntity(), value.referencedEntityName],
                referencedProperty  : [facets.mappedBy(), value.referencedPropertyName],
                constrained         : [false, value.constrained],
                foreignKeyDirection : ['TO_PARENT', value.foreignKeyType],
                lazy                : [facets.lazy(), value.lazy],
                fetchMode           : [facets.fetchMode() == FetchMode.JOIN ? 'JOIN' : 'SELECT', value.fetchMode],
                cascade             : [cascadeActions(facets.cascade()), cascadeActions((String) bound.cascade)],
                hasNoColumns        : [true, value.selectables.every { Map selectable -> selectable.formula != null } || columnsOf(value).isEmpty()],
        ]
        List<String> found = pairs.findAll { String facet, List values -> values[0] != values[1] }.collect { String facet, List values ->
            "${where} ${facet}: generator=${values[0]} binder=${values[1]}".toString()
        }
        if (facets.optional() != bound.optional) {
            known['the binder makes a to-one association optional or not independently of its foreign key column being nullable; annotations state both together, so the generated field follows the column']++
        }
        return found
    }

    /**
     * A to-one association against the binder's {@code ToOne} value and property: the referenced entity, whether it is lazy,
     * how it is fetched, what a missing row does, the cascade and whether the property is optional. The foreign key column
     * itself goes through the ordinary column comparison.
     */
    private static List<String> compareToOne(String where, ToOneFacets facets, Map bound, Map<String, Integer> known) {
        Map value = bound.value
        Map<String, List> pairs = [
                target        : [facets.target(), value.referencedEntityName],
                lazy          : [facets.lazy(), value.lazy],
                fetchMode     : [facets.fetchMode() == FetchMode.JOIN ? 'JOIN' : 'SELECT', value.fetchMode],
                ignoreNotFound: [facets.ignoreNotFound(), value.ignoreNotFound],
                cascade       : [cascadeActions(facets.cascade()), cascadeActions((String) bound.cascade)],
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
            String where, ToOneFacets facets, Map boundProperty, Map annotatedProperty,
            Map<String, GrailsHibernatePersistentEntity> byName, Map<String, Integer> known) {
        if (!isA(annotatedProperty.value, 'ToOne')) {
            return ["${where} kind: generator=${annotatedProperty.value.kind} binder=${boundProperty.value.kind}".toString()]
        }
        Map bound = boundProperty.value
        Map annotated = annotatedProperty.value
        if (isA(bound, 'OneToOne') != isA(annotated, 'OneToOne')) {
            return ["${where} kind: generator=${annotated.kind} binder=${bound.kind}".toString()]
        }
        // the binder names the entity that declares the other side of a one-to-one, which can be a superclass of the field's type
        String targetName = GrailsDomainGenerator.generatedClassName(byName[facets.target()])
        Map<String, List> pairs = [
                target        : [targetName, annotated.referencedEntityName],
                fetchMode     : [bound.fetchMode, annotated.fetchMode],
                ignoreNotFound: [bound.ignoreNotFound, annotated.ignoreNotFound],
                cascade       : [cascadeActions((String) boundProperty.cascade), cascadeActions((String) annotatedProperty.cascade)],
                insertable    : [boundProperty.insertable, annotatedProperty.insertable],
                updatable     : [boundProperty.updateable, annotatedProperty.updateable],
                propertyLazy  : [boundProperty.lazy, annotatedProperty.lazy],
        ]
        if (isA(bound, 'OneToOne')) {
            pairs.referencedProperty = [bound.referencedPropertyName, annotated.referencedPropertyName]
            pairs.constrained = [bound.constrained, annotated.constrained]
            pairs.foreignKeyDirection = [bound.foreignKeyType, annotated.foreignKeyType]
        }
        List<String> found = pairs.findAll { String facet, List values -> values[0] != values[1] }.collect { String facet, List values ->
            "${where} ${facet}: generator=${values[1]} binder=${values[0]}".toString()
        }
        if (bound.referencedEntityName != facets.target()) {
            if (isA(bound, 'OneToOne') && bound.referencedEntityName == facets.referencedEntity()) {
                known['the binder names the declaring superclass of the other side as the referenced entity of a one-to-one; the generated field is typed with the target itself']++
            } else {
                found << "${where} referencedEntity: generator=${facets.target()} binder=${bound.referencedEntityName}".toString()
            }
        }
        if (bound.lazy != annotated.lazy) {
            if (bound.lazy && !annotated.lazy && (facets.ignoreNotFound() || bound.fetchMode == 'JOIN')) {
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

    private String orderByOf(HibernateToManyEntityProperty property, ToManyFacets facets) {
        if (facets.orderProperty() == null) {
            return null
        }
        Map sortBy = classic[property.hibernateAssociatedEntity.name].props[facets.orderProperty()]
        return sortBy.value.selectables.collect { Map selectable -> "${selectable.text} ${facets.orderDirection()}".toString() }.join(', ')
    }

    /**
     * The order-by of the generator's facets against the binder's collection. The binder keeps the default sort of a list on its
     * model although a list is ordered by its index column (probed: the elements come back in the order they were stored), so the
     * generator states none.
     */
    private List orderByPair(
            HibernateToManyEntityProperty property, ToManyFacets facets, Map collection, Map<String, Integer> known) {
        if (facets.kind() == CollectionKind.LIST && collection.orderBy != null) {
            known['the binder keeps the default sort of a list on its model, which is ordered by its index column; the generator states none']++
            return [null, null]
        }
        return [normalizedOrderBy(orderByOf(property, facets)), normalizedOrderBy((String) collection.orderBy)]
    }

    private static String normalizedOrderBy(String orderBy) {
        return orderBy?.toLowerCase()?.replaceAll(/\s+/, ' ')?.trim()
    }

    private static String roleProperty(Map collection) {
        return collection.role.substring(collection.role.lastIndexOf('.') + 1)
    }

    /**
     * A collection of entities as {@code CollectionBinder} and its second passes bound it: kind, ownership, join table, key,
     * element and index columns, fetching, cascade, ordering and tenant filter.
     */
    private List<String> compareToMany(
            String where, HibernateToManyEntityProperty property, ToManyFacets facets, Map boundProperty, Map<String, Integer> known) {
        Map collection = boundProperty.value
        Map<String, List> pairs = [
                kind       : [facets.kind()?.name(), collection.collectionKind],
                lazy       : [facets.lazy(), collection.lazy],
                extraLazy  : [facets.extraLazy(), collection.extraLazy],
                fetchMode  : [facets.fetchMode()?.name(), collection.fetchMode],
                batchSize  : [facets.batchSize(), Math.max(collection.batchSize, 0)],
                cache      : [facets.cacheUsage(), collection.cacheConcurrencyStrategy],
                inverse    : [facets.mappedBy() != null, collection.inverse],
                orphanDelete: [facets.cascade().orphanRemoval(), collection.orphanDelete],
                cascade    : [cascadeActions(facets.cascade()), cascadeActions((String) boundProperty.cascade)],
                oneToMany  : [!facets.manyToMany(), collection.oneToMany],
                orderBy    : orderByPair(property, facets, collection, known),
                element    : [facets.target(), collection.element.referencedEntityName],
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
            found.addAll(compareValueColumns(
                    "${where} element".toString(), facets.elements().isEmpty() ? [facets.element()] : facets.elements(), collection.element,
                    collection.collectionTable, true))
            found.addAll(compareCollectionTableIndexes(
                    where, facets.indexes(), collection, property instanceof HibernateManyToManyProperty && property.bidirectional, known))
        }
        found.addAll(circularKeyOrFound(property, compareValueColumns("${where} key".toString(), facets.keys(), collection.key, collection.collectionTable, true), known))
        if (!collection.key.updateable) {
            known['the binder makes the key of an entity collection not updatable when its owner has several unidirectional to-many properties; Hibernate then writes no join table rows, and annotations cannot state it']++
        }
        if (facets.index() != null) {
            if (isA(collection, 'IndexedCollection')) {
                found.addAll(compareValueColumn(
                        "${where} index".toString(), facets.index(), collection.index, collection.collectionTable, false))
                found.addAll(compareIndexType("${where} index".toString(), facets.kind(), facets.indexType(), collection))
            } else {
                found << "${where} index: generator=${facets.index().name()} binder=none".toString()
            }
        } else if (isA(collection, 'IndexedCollection')) {
            found << "${where} index: generator=none binder=${collection.index.selectables*.text}".toString()
        }
        return found
    }

    /**
     * The type the binder gave the index column of a list or the key column of a map against the one the generator decided: the JDBC type
     * and the Java type of both.
     */
    private static List<String> compareIndexType(String where, CollectionKind kind, TypeFacets indexType, Map collection) {
        if (!isA(collection.index, 'BasicValue')) {
            return []
        }
        Map index = collection.index
        List<String> found = []
        int expectedJdbc = indexType != null ? indexType.jdbcTypeCode() : (kind == CollectionKind.LIST ? Types.INTEGER : Types.VARCHAR)
        int boundJdbc = index.jdbcCode
        if (expectedJdbc != boundJdbc) {
            found << "${where} jdbcTypeCode: generator=${expectedJdbc} binder=${boundJdbc}".toString()
        }
        Class<?> expectedJava = indexType != null ? indexType.javaType() : (kind == CollectionKind.LIST ? Integer : String)
        String boundJava = index.javaType
        if (expectedJava.name != boundJava) {
            found << "${where} javaType: generator=${expectedJava.name} binder=${boundJava}".toString()
        }
        return found
    }

    /**
     * A collection of entities against the one Hibernate's annotation binder built from the generated field: the same kind,
     * ownership, join table, key, element and index columns, fetching, cascade, ordering and filters.
     */
    private static List<String> compareAnnotatedToMany(
            String where, ToManyFacets facets, GrailsHibernatePersistentEntity entity, Map boundProperty,
            Map annotatedProperty, Map<String, GrailsHibernatePersistentEntity> byName, Map<String, Integer> known) {
        if (!isA(annotatedProperty.value, 'Collection')) {
            return ["${where} kind: generator=${annotatedProperty.value.kind} binder=${boundProperty.value.kind}".toString()]
        }
        Map bound = boundProperty.value
        Map annotated = annotatedProperty.value
        Map<String, List> pairs = [
                kind        : [bound.collectionKind, annotated.collectionKind],
                role        : [roleProperty(bound), roleProperty(annotated)],
                lazy        : [bound.lazy, annotated.lazy],
                extraLazy   : [bound.extraLazy && !annotated.extraLazy ? extraLazyAligned(known) : bound.extraLazy, annotated.extraLazy],
                fetchMode   : [bound.fetchMode, annotated.fetchMode],
                batchSize   : [Math.max(bound.batchSize, 0), Math.max(annotated.batchSize, 0)],
                cache       : [bound.cacheConcurrencyStrategy, annotated.cacheConcurrencyStrategy],
                inverse     : [bound.inverse, annotated.inverse],
                orphanDelete: [bound.orphanDelete, annotated.orphanDelete],
                oneToMany   : [bound.oneToMany, annotated.oneToMany],
                cascade     : [cascadeActions((String) boundProperty.cascade), cascadeActions((String) annotatedProperty.cascade)],
                orderBy     : annotatedOrderByPair(facets, bound, annotated, byName, known),
                element     : [GrailsDomainGenerator.generatedClassName(byName[facets.target()]), annotated.element.referencedEntityName],
                filters     : [bound.filters.toSet(), annotated.filters.toSet()],
                manyToManyFilters: [bound.manyToManyFilters.toSet(), annotated.manyToManyFilters.toSet()],
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
        if (facets.element() != null && columnsOf(bound.element)*.nullable != columnsOf(annotated.element)*.nullable) {
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
        if (isA(bound, 'IndexedCollection') && isA(annotated, 'IndexedCollection')) {
            values.index = [bound.index, annotated.index, facets.index()]
        }
        values.each { String part, List triple ->
            List<String> leaf = compareAnnotatedLeaf(
                    "${where} ${part}".toString(), ClassicFacts.propertyOf((Map) triple[0]), ClassicFacts.propertyOf((Map) triple[1]),
                    ((ColumnFacets) triple[2]).sqlType(), part != 'index', known)
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
     * The order-by of the binder's collection against the one the annotation binder built. A list states none (see {@link #orderByPair}),
     * and a sort by an association is the foreign key column for the binder and the name of the association for the annotation.
     */
    private static List annotatedOrderByPair(
            ToManyFacets facets, Map bound, Map annotated,
            Map<String, GrailsHibernatePersistentEntity> byName, Map<String, Integer> known) {
        String boundOrder = normalizedOrderBy((String) bound.orderBy)
        String annotatedOrder = normalizedOrderBy((String) (annotated.orderBy ?: annotated.manyToManyOrdering))
        if (facets.kind() == CollectionKind.LIST && boundOrder != null && annotatedOrder == null) {
            known['the binder keeps the default sort of a list on its model, which is ordered by its index column; the generator states none']++
            return [null, null]
        }
        if (boundOrder != annotatedOrder && facets.orderProperty() != null &&
                byName[facets.target()].getHibernatePropertyByName(facets.orderProperty()) instanceof HibernateToOneProperty) {
            known['a sort by an association is the foreign key column for the binder and the name of the association for the annotation']++
            return [annotatedOrder, annotatedOrder]
        }
        return [boundOrder, annotatedOrder]
    }

    /**
     * A collection of the binder's entity against the one Hibernate's annotation binder built: the same kind, table, key,
     * element and index columns, and the same fetching and caching.
     */
    private static List<String> compareAnnotatedCollection(
            String where, HibernateBasicProperty source, Map boundProperty, Map annotatedProperty, CollectionFacets facets,
            Map<String, Integer> known) {
        if (!isA(annotatedProperty.value, 'Collection')) {
            return ["${where} kind: generator=${annotatedProperty.value.kind} binder=${boundProperty.value.kind}".toString()]
        }
        Map bound = boundProperty.value
        Map annotated = annotatedProperty.value
        Map<String, List> pairs = [
                kind        : [bound.collectionKind, annotated.collectionKind],
                table       : [bound.collectionTable.name, annotated.collectionTable.name],
                schema      : [bound.collectionTable.schema, annotated.collectionTable.schema],
                catalog     : [bound.collectionTable.catalog, annotated.collectionTable.catalog],
                lazy        : [bound.lazy, annotated.lazy],
                extraLazy   : [bound.extraLazy && !annotated.extraLazy ? extraLazyAligned(known) : bound.extraLazy, annotated.extraLazy],
                fetchMode   : [bound.fetchMode, annotated.fetchMode],
                batchSize   : [Math.max(bound.batchSize, 0), Math.max(annotated.batchSize, 0)],
                cache       : [bound.cacheConcurrencyStrategy, annotated.cacheConcurrencyStrategy],
                inverse     : [bound.inverse, annotated.inverse],
                orphanDelete: [bound.orphanDelete, annotated.orphanDelete],
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
        if (isA(bound, 'IndexedCollection') && isA(annotated, 'IndexedCollection')) {
            values.index = [bound.index, annotated.index, facets.index()]
        }
        values.each { String part, List triple ->
            found.addAll(compareAnnotatedLeaf(
                    "${where} ${part}".toString(), ClassicFacts.propertyOf((Map) triple[0]), ClassicFacts.propertyOf((Map) triple[1]),
                    ((ColumnFacets) triple[2]).sqlType(), part == 'key', known))
        }
        return found
    }

    /**
     * One terminal property of the binder's entity against the one Hibernate's annotation binder built: the same columns
     * or formulas, the same nullability, and every column facet the binder states (length, precision, scale, unique,
     * explicit SQL type, default, read and write expressions, comment) must have reached the annotation-built column.
     */
    private static List<String> compareAnnotatedLeaf(
            String where, Map bound, Map annotated, String explicitSqlType, boolean key, Map<String, Integer> known = [:]) {
        List<String> boundSelectables = bound.value.selectables.collect { Map it -> it.formula == null ? it.name : "formula:${it.formula}" }
        List<String> annotatedSelectables = annotated.value.selectables.collect { Map it -> it.formula == null ? it.name : "formula:${it.formula}" }
        List<Map> boundColumns = columnsOf(bound)
        List<Map> annotatedColumns = columnsOf(annotated)
        if (boundColumns.size() > 1 && boundSelectables != annotatedSelectables && boundSelectables.toSet() == annotatedSelectables.toSet()) {
            // the columns of a foreign key to a composite identifier: Hibernate orders them like the columns of the referenced key
            known['Hibernate orders the columns of a foreign key to a composite identifier like the referenced key, the binder like the mapping']++
            annotatedColumns = boundColumns.collect { Map column -> annotatedColumns.find { Map other -> other.name == column.name } }
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
            Map boundColumn = boundColumns[i]
            Map annotatedColumn = annotatedColumns[i]
            Map<String, List> pairs = [
                    unique   : [boundColumn.unique, annotatedColumn.unique],
                    length   : [boundColumn.length, annotatedColumn.length],
                    precision: [boundColumn.precision, annotatedColumn.precision],
                    scale    : [boundColumn.scale, annotatedColumn.scale],
                    sqlType  : [explicitSqlType, annotatedColumn.sqlType],
                    default  : [boundColumn.defaultValue, annotatedColumn.defaultValue],
                    read     : [boundColumn.customRead, annotatedColumn.customRead],
                    write    : [boundColumn.customWrite, annotatedColumn.customWrite],
                    comment  : [boundColumn.comment, annotatedColumn.comment],
            ]
            // a precision with no scale: the binder leaves the scale null and the database default scale of the type applies, which
            // @Column can only state (it cannot leave the scale unset next to a precision), so the scale is compared as that default
            if (!key && boundColumn.precision != null && boundColumn.scale == null && annotatedColumn.precision != null &&
                    isA(bound.value, 'BasicValue')) {
                String javaType = bound.value.javaType
                pairs.scale = [javaType == BigDecimal.name ? Size.DEFAULT_SCALE : 0, annotatedColumn.scale ?: 0]
            }
            // Hibernate fills in defaults the binder leaves unset (length 255): only what the binder states is comparable,
            // and an SQL type is only stated when the mapping says so (the generator's own decision, passed in)
            pairs = pairs.findAll { String facet, List values -> facet in ['unique', 'sqlType'] || values[0] != null }
            if (key) {
                pairs.remove('length')
                pairs.remove('precision')
                pairs.remove('scale')
            }
            if (key || isA(bound.value, 'ToOne')) {
                ['default', 'read', 'write', 'comment'].each { String facet ->
                    if (pairs[facet] != null && pairs[facet][1] == null) {
                        known['the default, comment and read and write expressions of a foreign key column: a join column annotation cannot state them, GeneratedDomainClassBinder sets them after binding']++
                        pairs.remove(facet)
                    }
                }
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

    private static List<String> compareAnnotatedDiscriminator(String where, Map bound, Map annotated, Map<String, Integer> known) {
        if ((bound.discriminator != null) != (annotated.discriminator != null)) {
            return ["${where} discriminator: generator=${annotated.discriminator != null} binder=${bound.discriminator != null}".toString()]
        }
        if (bound.discriminator == null) {
            return []
        }
        List<String> boundSelectables = bound.discriminator.selectables.collect { Map it -> it.formula == null ? it.name : "formula:${it.formula}" }
        List<String> annotatedSelectables = annotated.discriminator.selectables.collect { Map it -> it.formula == null ? it.name : "formula:${it.formula}" }
        Map<String, List> pairs = [
                selectables: [boundSelectables, annotatedSelectables],
                jdbcType   : [bound.discriminator.jdbcCode, annotated.discriminator.jdbcCode],
                insertable : [bound.discriminatorInsertable, annotated.discriminatorInsertable],
        ]
        if (pairs.jdbcType[0] != pairs.jdbcType[1] && !(bound.discriminator.typeName in ['string', 'integer', 'character'])) {
            known['a discriminator typed other than string, integer or character: @DiscriminatorColumn cannot state the type, GeneratedDomainClassBinder binds the discriminator again with it']++
            pairs.remove('jdbcType')
        }
        Map boundColumn = columnsOf(bound.discriminator).find { true }
        if (boundColumn != null && bound.discriminator.typeName == 'string') {
            // the binder leaves an unset length null, which Hibernate reads as its default
            pairs.length = [boundColumn.length ?: Length.DEFAULT, columnsOf(annotated.discriminator).first().length]
        }
        return pairs.findAll { String facet, List values -> values[0] != values[1] }.collect { String facet, List values ->
            "${where} discriminator ${facet}: generator=${values[1]} binder=${values[0]}".toString()
        }
    }

    private static Map boundProperty(Map persistentClass, HibernatePersistentProperty property) {
        return (Map) persistentClass.props[property.name]
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
