package org.grails.gorm.graphql.response.errors

import graphql.schema.DataFetcher
import graphql.schema.DataFetchingEnvironment
import graphql.schema.GraphQLCodeRegistry
import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLOutputType
import groovy.transform.CompileStatic
import org.grails.datastore.gorm.GormValidateable
import org.grails.gorm.graphql.fetcher.context.LocaleAwareContext
import org.grails.gorm.graphql.types.GraphQLTypeManager
import org.springframework.context.MessageSource
import org.springframework.validation.FieldError

import static graphql.schema.FieldCoordinates.coordinates
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition
import static graphql.schema.GraphQLList.list
import static graphql.schema.GraphQLObjectType.newObject

/**
 * The default way to respond with validation errors in GraphQL.
 * Will look for the locale in the environment context to properly format
 * error messages. Defaults to {@link Locale#getDefault()}.
 *
 * errors {
 *     field
 *     message
 * }
 *
 * @author James Kleeh
 * @since 1.0.0
 */
@CompileStatic
class DefaultGraphQLErrorsResponseHandler implements GraphQLErrorsResponseHandler {

    protected MessageSource messageSource
    protected String name = 'Error'
    protected String description = 'Validation Errors'
    protected String fieldName = 'errors'
    protected String fieldDescription = 'A list of validation errors on the entity'
    protected final GraphQLCodeRegistry.Builder codeRegistry

    DefaultGraphQLErrorsResponseHandler(MessageSource messageSource, GraphQLCodeRegistry.Builder codeRegistry) {
        this.messageSource = messageSource
        this.codeRegistry = codeRegistry
    }

    protected Locale getLocale(DataFetchingEnvironment environment) {
        if (environment.context instanceof Map) {
            Map context = (Map)environment.context
            if (context.containsKey('locale')) {
                Object localContext = context.get('locale')
                if (localContext instanceof Locale) {
                    return (Locale)localContext
                }
            }
        }
        if (environment.context instanceof LocaleAwareContext) {
            return ((LocaleAwareContext) environment.context).locale
        }
        Locale.default
    }

    protected DataFetcher fieldFetcher = new DataFetcher<String>() {
        @Override
        String get(DataFetchingEnvironment environment) {
            ((FieldError) environment.source).field
        }
    }

    protected DataFetcher messageFetcher = new DataFetcher<String>() {
        @Override
        String get(DataFetchingEnvironment environment) {
            messageSource.getMessage((FieldError) environment.source, getLocale(environment))
        }
    }

    protected DataFetcher errorsFetcher = new DataFetcher<List<FieldError>>() {
        @Override
        List<FieldError> get(DataFetchingEnvironment environment) {
            ((GormValidateable) environment.source).errors.fieldErrors
        }
    }

    protected List<GraphQLFieldDefinition> getFieldDefinitions(GraphQLTypeManager typeManager) {
        [newFieldDefinition()
            .name('field')
            .type((GraphQLOutputType)typeManager.getType(String, false))
            .build(),

        newFieldDefinition()
            .name('message')
            .type((GraphQLOutputType)typeManager.getType(String))
            .build()]
    }

    protected GraphQLObjectType buildDefinition(GraphQLTypeManager typeManager) {
        codeRegistry.dataFetcher(
                        coordinates(name, 'field'),
                        fieldFetcher)
                .dataFetcher(
                        coordinates(name, 'message'),
                        messageFetcher)

        newObject()
            .name(name)
            .description(description)
            .fields(getFieldDefinitions(typeManager))
            .build()
    }

    private GraphQLFieldDefinition cachedDefinition

    @Override
    GraphQLFieldDefinition getFieldDefinition(GraphQLTypeManager typeManager,
                                              String parentType) {
        if (cachedDefinition == null) {
            cachedDefinition = newFieldDefinition()
                    .name(fieldName)
                    .description(fieldDescription)
                    .type(list(buildDefinition(typeManager)))
                    .build()
        }
        codeRegistry.dataFetcher(
                coordinates(parentType, fieldName),
                errorsFetcher
        )
        cachedDefinition
    }
}
