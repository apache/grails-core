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

package org.grails.gorm.graphql

import groovy.transform.CompileStatic
import org.grails.datastore.mapping.config.Entity
import org.grails.datastore.mapping.model.IllegalMappingException
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.gorm.graphql.entity.dsl.GraphQLMapping
import org.grails.gorm.graphql.entity.dsl.LazyGraphQLMapping

import java.lang.reflect.Method
import java.lang.reflect.Modifier

/**
 * A helper class to get GraphQL mappings and descriptions for GORM entities
 *
 * @author James Kleeh
 * @since 1.0.0
 */
@CompileStatic
class GraphQLEntityHelper {

    private static Map<PersistentEntity, GraphQLMapping> mappings = [:]
    private static Map<PersistentEntity, String> descriptions = [:]

    static String getDescription(final PersistentEntity entity) {
        if (descriptions.containsKey(entity)) {
            return descriptions.get(entity)
        }

        String description = getMapping(entity)?.description

        if (description == null) {
            GraphQL graphQL = entity.javaClass.getAnnotation(GraphQL)
            if (graphQL != null && !graphQL.value().empty) {
                description = graphQL.value()
            }
            else {
                try {
                    Class hibernateMapping = this.classLoader.loadClass('org.grails.orm.hibernate.cfg.Mapping')
                    Entity mapping = entity.mapping.mappedForm
                    if (hibernateMapping.isAssignableFrom(mapping.class)) {
                        description = hibernateMapping.getMethod('getComment').invoke(mapping)
                    }
                } catch (ClassNotFoundException ignored) {
                    // intentional: Hibernate support is optional
                }
            }
        }
        descriptions.put(entity, description)
        description
    }

    static GraphQLMapping getMapping(final PersistentEntity entity) {
        if (mappings.containsKey(entity)) {
            return mappings.get(entity)
        }
        GraphQLMapping mapping = buildMapping(entity)
        mappings.put(entity, mapping)
        mapping
    }

    private static GraphQLMapping buildMapping(PersistentEntity entity) {
        Object graphql = findGraphqlProperty(entity)
        if (graphql == null) {
            return null
        }

        GraphQLMapping mapping = toGraphQLMapping(graphql)
        if (!(mapping instanceof GraphQLMapping)) {
            throw new IllegalMappingException("The static graphql property on ${entity.name} is not a Boolean, Closure, or GraphQLMapping")
        }
        verifyMapping(mapping, entity)
        mapping
    }

    private static Object findGraphqlProperty(PersistentEntity entity) {
        for (Method method: entity.javaClass.declaredMethods) {
            if (Modifier.isStatic(method.modifiers) && method.name.equalsIgnoreCase('getgraphql')) {
                return method.invoke(null)
            }
        }
        null
    }

    private static GraphQLMapping toGraphQLMapping(Object graphql) {
        if (graphql == Boolean.TRUE) {
            return new GraphQLMapping()
        }
        if (graphql instanceof Closure) {
            return new GraphQLMapping().build((Closure) graphql)
        }
        if (graphql instanceof LazyGraphQLMapping) {
            return ((LazyGraphQLMapping) graphql).initialize()
        }
        if (graphql instanceof GraphQLMapping) {
            return (GraphQLMapping) graphql
        }
        null
    }

    static void verifyMapping(GraphQLMapping mapping, PersistentEntity entity) {
/*
        Set<String> persistentPropertyNames = new HashSet<>()
        if (entity.identity != null) {
            persistentPropertyNames.add(entity.identity.name)
        }
        if (entity.compositeIdentity != null) {
            persistentPropertyNames.addAll(entity.compositeIdentity*.name)
        }
        persistentPropertyNames.addAll(entity.persistentPropertyNames)
*/

        for (String name: mapping.propertyMappings.keySet()) {
            if (entity.getPropertyByName(name) == null) {
                throw new IllegalMappingException("GraphQL mapping: The property '${name}' was used to reference an existing property in ${entity.javaClass.name}, but no property exists with that name")
            }
        }
    }

}
