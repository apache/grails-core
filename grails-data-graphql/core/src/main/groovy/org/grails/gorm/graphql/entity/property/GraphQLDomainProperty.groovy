package org.grails.gorm.graphql.entity.property

import graphql.schema.DataFetcher
import graphql.schema.GraphQLType
import org.grails.gorm.graphql.types.GraphQLPropertyType
import org.grails.gorm.graphql.types.GraphQLTypeManager

/**
 * An interface to describe a property to be used in the
 * creation of a GraphQL schema
 *
 * @author James Kleeh
 * @since 1.0.0
 */
interface GraphQLDomainProperty {

    /**
     * @return The name of the property
     */
    String getName()

    /**
     * @param typeManager The returnType manager used to retrieve GraphQL types
     * @param propertyType The returnType of property being created
     * @return The GraphQLType representing the property
     */
    GraphQLType getGraphQLType(GraphQLTypeManager typeManager, GraphQLPropertyType propertyType)

    /**
     * @return The description of the property
     */
    String getDescription()

    /**
     * @return True if the property is deprecated
     */
    boolean isDeprecated()

    /**
     * @return The reason why the property is deprecated, or null if it isn't
     */
    String getDeprecationReason()

    /**
     * @return True if the property is to be used for input operations (CREATE/UPDATE)
     */
    boolean isInput()

    /**
     * @return True if the property is to be used for output operations (GET/LIST)
     */
    boolean isOutput()

    /**
     * @return True if the property allows nulls
     */
    boolean isNullable()

    /**
     * @return The closure to retrieve the data for the property. If not null, it
     * will be used to create a {@link org.grails.gorm.graphql.fetcher.impl.ClosureDataFetcher},
     * otherwise the default fetcher will be used.
     */
    DataFetcher getDataFetcher()

}
