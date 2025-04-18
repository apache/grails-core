package org.grails.gorm.graphql.testing

import graphql.schema.GraphQLList
import graphql.schema.GraphQLNonNull
import graphql.schema.GraphQLType

trait GraphQLSchemaSpec {

    GraphQLType unwrap(List list, GraphQLType type) {
        if (list == null) {
            ((GraphQLNonNull)type).wrappedType
        }
        else if (list.empty) {
            ((GraphQLList)type).wrappedType
        }
        else if (list[0] == null) {
            ((GraphQLNonNull)((GraphQLList)type).wrappedType).wrappedType
        }
        else {
            null
        }
    }
}
