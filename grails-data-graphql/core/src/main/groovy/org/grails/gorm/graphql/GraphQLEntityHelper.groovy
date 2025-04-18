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
                } catch (ClassNotFoundException e) { }
            }
        }
        descriptions.put(entity, description)
        description
    }

    static GraphQLMapping getMapping(final PersistentEntity entity) {
        if (mappings.containsKey(entity)) {
            return mappings.get(entity)
        }
        Object graphql
        for (Method method: entity.javaClass.declaredMethods) {
            if (Modifier.isStatic(method.modifiers) && method.name.equalsIgnoreCase('getgraphql')) {
                graphql = method.invoke(null)
                break
            }
        }
        GraphQLMapping mapping = null
        if (graphql != null) {
            if (graphql == Boolean.TRUE) {
                mapping = new GraphQLMapping()
            }
            else if (graphql instanceof Closure) {
                mapping = new GraphQLMapping().build((Closure)graphql)
            }
            else if (graphql instanceof LazyGraphQLMapping) {
                mapping = ((LazyGraphQLMapping)graphql).initialize()
            }
            else if (graphql instanceof GraphQLMapping) {
                mapping = (GraphQLMapping)graphql
            }

            if (!(mapping instanceof GraphQLMapping)) {
                throw new IllegalMappingException("The static graphql property on ${entity.name} is not a Boolean, Closure, or GraphQLMapping")
            }
            verifyMapping(mapping, entity)
        }
        mappings.put(entity, mapping)
        mapping
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
