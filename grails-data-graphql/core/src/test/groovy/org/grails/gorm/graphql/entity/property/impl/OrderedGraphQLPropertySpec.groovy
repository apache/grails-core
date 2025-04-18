package org.grails.gorm.graphql.entity.property.impl

import graphql.schema.DataFetcher
import graphql.schema.GraphQLType
import org.grails.gorm.graphql.types.GraphQLPropertyType
import org.grails.gorm.graphql.types.GraphQLTypeManager
import spock.lang.Specification

class OrderedGraphQLPropertySpec extends Specification {

    OrderedGraphQLProperty getProperty(Integer order, String name) {
        new OrderedGraphQLProperty() {
            @Override
            Integer getOrder() {
                order
            }

            @Override
            String getName() {
                name
            }

            @Override
            GraphQLType getGraphQLType(GraphQLTypeManager typeManager, GraphQLPropertyType propertyType) {
                return null
            }

            @Override
            String getDescription() {
                return null
            }

            @Override
            boolean isDeprecated() {
                return false
            }

            @Override
            String getDeprecationReason() {
                return null
            }

            @Override
            boolean isInput() {
                return false
            }

            @Override
            boolean isOutput() {
                return false
            }

            @Override
            boolean isNullable() {
                return false
            }

            @Override
            DataFetcher getDataFetcher() {
                return null
            }
        }
    }

    void "test order implementation"() {
        given:
        List<OrderedGraphQLProperty> properties = []
        properties.add(getProperty(null, 'c'))
        properties.add(getProperty(null, 'a'))
        properties.add(getProperty(null, 'b'))
        properties.add(getProperty(1, 'z'))
        properties.add(getProperty(1, 'x'))
        properties.add(getProperty(1, 'y'))
        properties.add(getProperty(2, 's'))
        properties.add(getProperty(4, 'q'))
        properties.add(getProperty(3, 'r'))

        when:
        properties.sort(true)

        then:
        properties[0].name == 'x'
        properties[1].name == 'y'
        properties[2].name == 'z'
        properties[3].name == 's'
        properties[4].name == 'r'
        properties[5].name == 'q'
        properties[6].name == 'a'
        properties[7].name == 'b'
        properties[8].name == 'c'
    }
}
