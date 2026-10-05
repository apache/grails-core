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
import grails.gorm.tests.HibernateGormDatastoreSpec
import org.hibernate.dialect.H2Dialect
import org.hibernate.mapping.Column
import org.hibernate.mapping.PersistentClass
import org.hibernate.mapping.Property
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type

import org.grails.orm.hibernate.HibernateDatastore
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider
import org.springframework.core.type.filter.AnnotationTypeFilter

import org.grails.orm.hibernate.cfg.domainbinding.binder.ColumnConfigToColumnBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.NumericColumnConstraintsBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.StringColumnConstraintsBinder
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernatePersistentProperty
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
                for (GrailsHibernatePersistentEntity entity : datastore.mappingContext.getHibernatePersistentEntities()
                        .findAll { group.contains(it.javaClass) && it.persistentClass != null }) {
                    entities++
                    List<HibernatePersistentProperty> properties = []
                    if (entity.identity != null) {
                        properties << (HibernatePersistentProperty) entity.identity
                    }
                    properties.addAll(entity.persistentPropertiesToBind)
                    for (HibernatePersistentProperty property : properties) {
                        if (!generator.supports(property)) {
                            skipped[property.getClass().simpleName]++
                            continue
                        }
                        if (!generator.validationAnnotations(property).isEmpty()) {
                            skipped['bean validation constraints (applied by Hibernate after binding)']++
                            continue
                        }
                        Property bound = boundProperty(entity.persistentClass, property)
                        if (bound == null || bound.columns.size() != 1) {
                            skipped['no single bound column']++
                            continue
                        }
                        compared++
                        mismatches.addAll(compare(entity, property, generator.columnFacets(property), bound))
                    }
                }
            } finally {
                datastore.close()
            }
        }
        StringBuilder report = new StringBuilder()
        report << "differential: ${candidates.size()} candidate classes in ${groups.size()} groups; " +
                "${unbootable.size()} groups could not boot alone; ${entities} entities, ${compared} properties compared\n"
        report << "unsupported by kind: ${skipped}\n"
        report << "mismatches by facet: ${mismatches.groupBy { it.split(' ')[1] }.collectEntries { k, v -> [k, v.size()] }}\n"
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
        ]
        if (facets.sqlType() == null) {
            // Hibernate derives an sqlType for every column after binding; only an explicit one is comparable
            pairs.remove('sqlType')
        }
        return pairs.findAll { String facet, List values -> values[0] != values[1] }.collect { String facet, List values ->
            "${entity.name}.${property.name} ${facet}: generator=${values[0]} binder=${values[1]}".toString()
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
                new NumericColumnConstraintsBinder(new H2Dialect()))
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
