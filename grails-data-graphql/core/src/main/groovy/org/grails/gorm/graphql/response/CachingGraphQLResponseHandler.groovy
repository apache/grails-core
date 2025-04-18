package org.grails.gorm.graphql.response

import graphql.schema.GraphQLObjectType
import org.grails.gorm.graphql.types.GraphQLTypeManager

/**
 * Generic class to cache the creation of {@link GraphQLObjectType} instances
 * by providing a reference if the object was already created when requested.
 *
 * @author James Kleeh
 * @since 1.0.0
 */
abstract class CachingGraphQLResponseHandler {

    private GraphQLObjectType cachedDefinition

    GraphQLObjectType getDefinition(GraphQLTypeManager typeManager) {
        if (cachedDefinition == null) {
            cachedDefinition = buildDefinition(typeManager)
        }
        cachedDefinition
    }

    abstract protected GraphQLObjectType buildDefinition(GraphQLTypeManager typeManager)
}
