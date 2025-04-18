package org.grails.gorm.graphql.interceptor

import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLType
import org.grails.datastore.mapping.model.PersistentEntity

/**
 * Interface to describe a class that can modify the fields and types used
 * to build the GraphQL schema.
 *
 * @author James Kleeh
 * @since 1.0.0
 */
interface GraphQLSchemaInterceptor {

    /**
     * Executed for each entity mapped with GraphQL. The fields are mutable
     * and their changes will be applied to the schema.
     *
     * @param entity The entity being processed
     * @param queryFields The query fields associated with the entity
     * @param mutationFields The query fields associated with the entity
     */
    void interceptEntity(PersistentEntity entity,
                   List<GraphQLFieldDefinition.Builder> queryFields,
                   List<GraphQLFieldDefinition.Builder> mutationFields)

    /**
     * Executed a single time before the schema is created. The types are
     * mutable and their changes will be applied in the schema.
     *
     * @param queryType The root query returnType
     * @param mutationType The root mutation returnType
     */
    void interceptSchema(GraphQLObjectType.Builder queryType,
                         GraphQLObjectType.Builder mutationType,
                         Set<GraphQLType> additionalTypes)
}
