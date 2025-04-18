package org.grails.gorm.graphql.response.delete

import graphql.Scalars
import graphql.schema.GraphQLNonNull
import graphql.schema.GraphQLObjectType
import org.grails.gorm.graphql.testing.GraphQLSchemaSpec
import org.grails.gorm.graphql.types.GraphQLTypeManager
import spock.lang.Shared
import spock.lang.Specification

class DefaultGraphQLDeleteResponseHandlerSpec extends Specification implements GraphQLSchemaSpec {

    GraphQLDeleteResponseHandler handler

    @Shared GraphQLTypeManager typeManager

    void setupSpec() {
        typeManager = Stub(GraphQLTypeManager) {
            getType(Boolean, false) >> {
                GraphQLNonNull.nonNull(Scalars.GraphQLBoolean)
            }
            getType(String) >> {
                Scalars.GraphQLString
            }
        }
    }

    void setup() {
        handler = new DefaultGraphQLDeleteResponseHandler()
    }

    void "test the result is cached"() {
        expect:
        handler.getObjectType(typeManager) == handler.getObjectType(typeManager)
    }

    void "test the return data"() {
        expect:
        handler.createResponse(null, false, null) == [success: false, error: null]
        handler.createResponse(null, true, null) == [success: true, error: null]
        handler.createResponse(null, true, new RuntimeException('exception')) == [success: true, error: 'exception']
    }

    void "test the object type definition"() {
        GraphQLObjectType type = handler.getObjectType(typeManager)

        expect:
        type.name == 'DeleteResult'
        type.description == 'Whether or not the operation was successful'
        type.interfaces.empty
        type.fieldDefinitions.size() == 2
        type.fieldDefinitions[0].name == 'success'
        type.fieldDefinitions[1].name == 'error'
        unwrap(null, type.fieldDefinitions[0].type) == Scalars.GraphQLBoolean
        type.fieldDefinitions[1].type == Scalars.GraphQLString
    }
}
