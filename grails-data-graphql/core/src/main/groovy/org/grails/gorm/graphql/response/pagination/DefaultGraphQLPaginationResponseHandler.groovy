package org.grails.gorm.graphql.response.pagination

import graphql.schema.DataFetchingEnvironment
import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLList
import graphql.schema.GraphQLOutputType
import groovy.transform.CompileStatic
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.gorm.graphql.types.GraphQLTypeManager

import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition

/**
 * Controls how a page of results are returned
 *
 * @author James Kleeh
 * @since 1.0.0
 */
@CompileStatic
class DefaultGraphQLPaginationResponseHandler implements GraphQLPaginationResponseHandler {

    protected String resultsField = 'results'
    protected String totalField = 'totalCount'

    @Override
    List<GraphQLFieldDefinition> getFields(GraphQLOutputType resultsType, GraphQLTypeManager typeManager) {
        [newFieldDefinition()
                .name(resultsField)
                .type(GraphQLList.list(resultsType))
                .build(),
         newFieldDefinition()
                .name(totalField)
                .type((GraphQLOutputType)typeManager.getType(Long))
                .build()]
    }

    @Override
    String getDescription(PersistentEntity entity) {
        null
    }

    @Override
    Object createResponse(DataFetchingEnvironment environment, PaginationResult result) {
        Map response = new LinkedHashMap<String, Object>(2)
        response.put(resultsField, result.results)
        response.put(totalField, result.totalCount)
        response
    }

    @Override
    int getDefaultMax() {
        100
    }

    @Override
    int getDefaultOffset() {
        0
    }
}
