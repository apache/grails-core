package org.grails.gorm.graphql.plugin

import grails.plugins.Plugin
import grails.web.mime.MimeType
import graphql.GraphQL
import graphql.schema.GraphQLCodeRegistry
import org.grails.gorm.graphql.GraphQLServiceManager
import org.grails.gorm.graphql.Schema
import org.grails.gorm.graphql.binding.manager.DefaultGraphQLDataBinderManager
import org.grails.gorm.graphql.entity.GraphQLEntityNamingConvention
import org.grails.gorm.graphql.entity.property.manager.DefaultGraphQLDomainPropertyManager
import org.grails.gorm.graphql.fetcher.manager.DefaultGraphQLDataFetcherManager
import org.grails.gorm.graphql.interceptor.manager.DefaultGraphQLInterceptorManager
import org.grails.gorm.graphql.plugin.binding.GrailsGraphQLDataBinder
import org.grails.gorm.graphql.response.delete.DefaultGraphQLDeleteResponseHandler
import org.grails.gorm.graphql.response.errors.DefaultGraphQLErrorsResponseHandler
import org.grails.gorm.graphql.response.pagination.DefaultGraphQLPaginationResponseHandler
import org.grails.gorm.graphql.types.DefaultGraphQLTypeManager

class GormGraphqlGrailsPlugin extends Plugin {

    def grailsVersion = "4.0.0 > *"
    def title = "Gorm GraphQL"
    def author = "James Kleeh"
    def authorEmail = "james.kleeh@gmail.com"
    def profiles = ['web']
    def documentation = "https://plugins.grails.org/plugin/gorm-graphql"
    def license = "APACHE"
    def developers = [ [ name: "Puneet Behl", email: "behlp@objectcomputing.com" ]]
    def issueManagement = [ system: "GitHub", url: "https://github.com/grails/gorm-graphql/issues" ]
    def scm = [ url: "https://github.com/grails/gorm-graphql/" ]

    public static MimeType GRAPHQL_MIME =  new MimeType('application/graphql')

    Closure doWithSpring() {{ ->
        grailsGraphQLConfiguration(GrailsGraphQLConfiguration)

        if (!config.getProperty('grails.gorm.graphql.enabled', Boolean, true)) {
            return
        }

        graphQLContextBuilder(DefaultGraphQLContextBuilder)

        graphQLDataBinder(GrailsGraphQLDataBinder)
        graphQLCodeRegistry(GraphQLCodeRegistry) { bean ->
            bean.factoryMethod = "newCodeRegistry"
        }
        graphQLErrorsResponseHandler(DefaultGraphQLErrorsResponseHandler, ref("messageSource"), ref("graphQLCodeRegistry"))
        graphQLEntityNamingConvention(GraphQLEntityNamingConvention)
        graphQLDomainPropertyManager(DefaultGraphQLDomainPropertyManager)
        graphQLPaginationResponseHandler(DefaultGraphQLPaginationResponseHandler)

        graphQLTypeManager(DefaultGraphQLTypeManager, ref("graphQLCodeRegistry"), ref("graphQLEntityNamingConvention"), ref("graphQLErrorsResponseHandler"), ref("graphQLDomainPropertyManager"), ref("graphQLPaginationResponseHandler"))
        graphQLDataBinderManager(DefaultGraphQLDataBinderManager, ref("graphQLDataBinder"))
        graphQLDeleteResponseHandler(DefaultGraphQLDeleteResponseHandler)
        graphQLDataFetcherManager(DefaultGraphQLDataFetcherManager)
        graphQLInterceptorManager(DefaultGraphQLInterceptorManager)
        graphQLServiceManager(GraphQLServiceManager)

        graphQLSchemaGenerator(Schema) {
            codeRegistry = ref("graphQLCodeRegistry")
            deleteResponseHandler = ref("graphQLDeleteResponseHandler")
            namingConvention = ref("graphQLEntityNamingConvention")
            typeManager = ref("graphQLTypeManager")
            dataBinderManager = ref("graphQLDataBinderManager")
            dataFetcherManager = ref("graphQLDataFetcherManager")
            interceptorManager = ref("graphQLInterceptorManager")
            paginationResponseHandler = ref("graphQLPaginationResponseHandler")
            serviceManager = ref("graphQLServiceManager")

            dateFormats = '#{grailsGraphQLConfiguration.getDateFormats()}'
            dateFormatLenient = '#{grailsGraphQLConfiguration.getDateFormatLenient()}'
            listArguments = '#{grailsGraphQLConfiguration.getListArguments()}'
        }

        graphQLSchema(graphQLSchemaGenerator: "generate")
        graphQLBuilder(GraphQL.Builder, ref("graphQLSchema"))
        graphQL(GraphQL) { bean ->
            bean.factoryBean = "graphQLBuilder"
            bean.factoryMethod = "build"
        }
    }}
}
