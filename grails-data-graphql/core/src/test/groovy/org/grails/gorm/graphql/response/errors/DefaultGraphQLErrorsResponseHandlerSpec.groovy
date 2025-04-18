package org.grails.gorm.graphql.response.errors

import graphql.Scalars
import graphql.schema.DataFetcher
import graphql.schema.DataFetchingEnvironment
import graphql.schema.GraphQLCodeRegistry
import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLNonNull
import graphql.schema.GraphQLObjectType
import org.grails.datastore.gorm.GormValidateable
import org.grails.gorm.graphql.testing.GraphQLSchemaSpec
import org.grails.gorm.graphql.testing.MockDataFetchingEnvironment
import org.grails.gorm.graphql.types.GraphQLTypeManager
import org.springframework.context.MessageSource
import org.springframework.validation.FieldError
import spock.lang.Shared
import spock.lang.Specification

import static graphql.schema.FieldCoordinates.coordinates

class DefaultGraphQLErrorsResponseHandlerSpec extends Specification implements GraphQLSchemaSpec {

    GraphQLErrorsResponseHandler handler
    MessageSource messageSource

    @Shared GraphQLCodeRegistry.Builder codeRegistry
    @Shared GraphQLTypeManager typeManager

    void setupSpec() {
        typeManager = Stub(GraphQLTypeManager) {
            getType(String, false) >> GraphQLNonNull.nonNull(Scalars.GraphQLString)
            getType(String) >> Scalars.GraphQLString
            getCodeRegistry() >> codeRegistry
        }
    }

    void setup() {
        messageSource = Mock(MessageSource)
        codeRegistry = GraphQLCodeRegistry.newCodeRegistry()
        handler = new DefaultGraphQLErrorsResponseHandler(messageSource, codeRegistry)
    }

    void "test field definition is cached"() {
        expect:
        handler.getFieldDefinition(typeManager, Scalars.GraphQLString.name) == handler.getFieldDefinition(typeManager, Scalars.GraphQLString.name)
    }

    void "test field definition"() {
        given:
        FieldError fieldError = Mock(FieldError) {
            1 * getField() >> 'book'
        }
        1 * messageSource.getMessage(fieldError, Locale.default) >> 'hello'
        GormValidateable validateable = new MockValidateable()
        validateable.errors.rejectValue('foo', 'foo.not.valid')
        DataFetchingEnvironment mockFieldEnv = new MockDataFetchingEnvironment(source: fieldError)
        DataFetchingEnvironment mockObjectEnv = new MockDataFetchingEnvironment(source: validateable)

        when:
        GraphQLFieldDefinition field = handler.getFieldDefinition(typeManager, "MockValidateable")
        codeRegistry.build()
        GraphQLObjectType type = (GraphQLObjectType) unwrap([], field.type)

        then:
        field.description == 'A list of validation errors on the entity'
        field.name == 'errors'

        type.name == 'Error'
        type.description == 'Validation Errors'
        type.fieldDefinitions.size() == 2
        type.fieldDefinitions[0].name == "field"

        unwrap(null, type.fieldDefinitions[0].type) == Scalars.GraphQLString
        type.fieldDefinitions[1].name == "message"
        type.fieldDefinitions[1].type == Scalars.GraphQLString

        when:
        DataFetcher errorsFetcher = codeRegistry.getDataFetcher(coordinates("MockValidateable", "errors"), field)

        then:
        errorsFetcher.get(mockObjectEnv) instanceof List<FieldError>
        ((List<FieldError>) errorsFetcher.get(mockObjectEnv)).size() == 1

        when:
        DataFetcher fieldFetcher = codeRegistry.getDataFetcher(coordinates("Error", "field"), type.getFieldDefinition("field"))
        DataFetcher messageFetcher = codeRegistry.getDataFetcher(coordinates("Error", "message"), type.getFieldDefinition("message"))

        then:
        fieldFetcher.get(mockFieldEnv) == 'book'
        messageFetcher.get(mockFieldEnv) == 'hello'
    }

    class MockValidateable implements GormValidateable {
        String foo
    }
}
