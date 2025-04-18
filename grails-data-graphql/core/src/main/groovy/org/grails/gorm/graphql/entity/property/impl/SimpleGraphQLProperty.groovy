package org.grails.gorm.graphql.entity.property.impl

import graphql.schema.DataFetcher
import graphql.schema.GraphQLType
import groovy.transform.AutoClone
import groovy.transform.CompileStatic
import org.grails.gorm.graphql.entity.dsl.helpers.Typed
import org.grails.gorm.graphql.fetcher.impl.ClosureDataFetcher
import org.grails.gorm.graphql.types.GraphQLPropertyType
import org.grails.gorm.graphql.types.GraphQLTypeManager

/**
 * A class for creating custom properties that have a simple type
 *
 * @author James Kleeh
 * @since 1.0.0
 */
@AutoClone
@CompileStatic
class SimpleGraphQLProperty extends CustomGraphQLProperty<SimpleGraphQLProperty> implements Typed<SimpleGraphQLProperty> {

    @Override
    GraphQLType getGraphQLType(GraphQLTypeManager typeManager, GraphQLPropertyType propertyType) {
        resolveType(typeManager, mappingContext, propertyType, nullable)
    }

    DataFetcher getDataFetcher() {
        closureDataFetcher ? new ClosureDataFetcher(closureDataFetcher, returnType) : null
    }
}
