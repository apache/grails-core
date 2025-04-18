package org.grails.gorm.graphql.response.delete

import graphql.schema.DataFetchingEnvironment
import graphql.schema.GraphQLObjectType
import org.grails.gorm.graphql.types.GraphQLTypeManager

/**
 * Responsible for determining the data available in a GraphQL delete mutation response
 *
 * @author James Kleeh
 * @since 1.0.0
 */
interface GraphQLDeleteResponseHandler {

    /**
     * Creates the schema object for a delete response
     *
     * @param typeManager The type manager
     * @return The GraphQL type
     */
    GraphQLObjectType getObjectType(GraphQLTypeManager typeManager)

    /**
     * Create the response data to be sent to the client
     *
     * @param environment The data fetching environment
     * @param success Whether or not the operation was successful
     * @param exception If not successful, the exception that occurred
     * @return Response data
     */
    Object createResponse(DataFetchingEnvironment environment, boolean success, Exception exception)
}
