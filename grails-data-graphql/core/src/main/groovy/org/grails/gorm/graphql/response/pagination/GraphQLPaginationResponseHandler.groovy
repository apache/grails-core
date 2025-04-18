package org.grails.gorm.graphql.response.pagination

import graphql.schema.DataFetchingEnvironment
import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLOutputType
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.gorm.graphql.types.GraphQLTypeManager

/**
 * Defines how a pagination response is defined and built
 *
 * @author James Kleeh
 * @since 1.0.0
 */
interface GraphQLPaginationResponseHandler {

    /**
     * Creates the fields to be used in the schema object for a pagination response
     *
     * @param resultsType The graphql type of the results
     * @param typeManager The type manager
     * @return The GraphQL type
     */
    List<GraphQLFieldDefinition> getFields(GraphQLOutputType resultsType, GraphQLTypeManager typeManager)

    /**
     * @return The description to use in the schema, or null
     */
    String getDescription(PersistentEntity entity)

    /**
     * Create the response data to be sent to the client
     *
     * @param environment The data fetching environment
     * @param results The data retrieved from the query
     * @return Response data
     */
    Object createResponse(DataFetchingEnvironment environment, PaginationResult result)

    /**
     * @return The default maximum value if none provided
     */
    int getDefaultMax()

    /**
     * @return The default offset value if none provided
     */
    int getDefaultOffset()
}
