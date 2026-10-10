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
import java.lang.reflect.Modifier
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type

import grails.gorm.annotation.Entity
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider
import org.springframework.core.type.filter.AnnotationTypeFilter

/**
 * The domain classes the differential specs of the domain generator and of the generated-class DDL scan: every
 * {@code @Entity} in the TCK and in the Hibernate 7 tests, which are written to exercise binder permutations, grouped by
 * association so that each group boots as its own datastore.
 */
class ScannedDomainClasses {

    private static final List<String> PACKAGES = [
            'org.apache.grails.data.testing.tck.domains',
            'org.grails.orm.hibernate',
            'grails.gorm',
            'grails.orm',
    ]

    static List<Class<?>> findEntities() {
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
    static List<List<Class<?>>> groupByAssociation(List<Class<?>> classes) {
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
