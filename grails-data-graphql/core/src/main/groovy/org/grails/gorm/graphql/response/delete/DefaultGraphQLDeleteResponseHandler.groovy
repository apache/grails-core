package org.grails.gorm.graphql.response.delete

import graphql.schema.DataFetchingEnvironment
import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLOutputType
import groovy.transform.CompileStatic
import org.grails.gorm.graphql.response.CachingGraphQLResponseHandler
import org.grails.gorm.graphql.types.GraphQLTypeManager

import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition
import static graphql.schema.GraphQLObjectType.newObject

/**
 * The default data available in a delete mutation response
 *
 * success: Boolean
 *
 * @author James Kleeh
 * @since 1.0.0
 */
@CompileStatic
class DefaultGraphQLDeleteResponseHandler extends CachingGraphQLResponseHandler implements GraphQLDeleteResponseHandler {

    protected String description = 'Whether or not the operation was successful'
    protected String name = 'DeleteResult'

    @Override
    GraphQLObjectType getObjectType(GraphQLTypeManager typeManager) {
        getDefinition(typeManager)
    }

    protected List<GraphQLFieldDefinition> buildFieldDefinitions(GraphQLTypeManager typeManager) {
        [newFieldDefinition().name('success').type((GraphQLOutputType)typeManager.getType(Boolean, false)).build(),
         newFieldDefinition().name('error').type((GraphQLOutputType)typeManager.getType(String)).build()]
    }

    @Override
    protected GraphQLObjectType buildDefinition(GraphQLTypeManager typeManager) {
        newObject()
            .name(name)
            .description(description)
            .fields(buildFieldDefinitions(typeManager))
            .build()
    }

    @Override
    Object createResponse(DataFetchingEnvironment environment, boolean success, Exception exception) {
        [success: success, error: exception?.message]
    }
}
