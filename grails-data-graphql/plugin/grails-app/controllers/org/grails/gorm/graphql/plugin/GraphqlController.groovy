package org.grails.gorm.graphql.plugin

import grails.io.IOUtils
import grails.web.mapping.LinkGenerator
import graphql.ExecutionInput
import graphql.ExecutionResult
import graphql.GraphQL
import groovy.transform.CompileStatic
import org.springframework.context.MessageSource
import org.springframework.http.HttpMethod

@CompileStatic
class GraphqlController {

    static responseFormats = ['json', 'xml']

    GraphQL graphQL

    LinkGenerator grailsLinkGenerator

    GrailsGraphQLConfiguration grailsGraphQLConfiguration

    MessageSource messageSource

    GraphQLContextBuilder graphQLContextBuilder

    def index() {
        if (!grailsGraphQLConfiguration.enabled) {
            render(status: 404)
            return
        }

        GraphQLRequest graphQLRequest

        HttpMethod method = HttpMethod.resolve(request.method)
        if (request.contentLength != 0 && method != HttpMethod.GET) {
            String encoding = request.characterEncoding ?: 'UTF-8'
            String body = IOUtils.toString(request.inputStream, encoding)
            graphQLRequest = GraphQLRequestUtils.graphQLRequestWithBodyAndMimeTypes(body, request.mimeTypes)
        } else {
            graphQLRequest = GraphQLRequestUtils.graphQLRequestWithParams(params)
        }

        if (!graphQLRequest?.validate()) {
            String message = messageSource.getMessage('graphql.invalid.request', [] as Object[], 'Invalid GraphQL request', request.locale)
            render view: '/graphql/invalidRequest', model: [error: message]
            return
        }

        Object context = graphQLContextBuilder.buildContext(currentRequestAttributes())

        Map<String, Object> result = new LinkedHashMap<>()

        ExecutionResult executionResult = graphQL.execute(ExecutionInput.newExecutionInput()
                .query(graphQLRequest.query)
                .operationName(graphQLRequest.operationName)
                .context(context)
                .root(context) // This we are doing do be backwards compatible
                .variables(graphQLRequest.variables)
                .build())

        if (executionResult.errors.size() > 0) {
            result.put('errors', executionResult.errors)
        }

        final Map extensions = executionResult.extensions
        if (Objects.nonNull(extensions) && extensions.size() > 0) {
            result.put('extensions', extensions)
        }
        result.put('data', executionResult.data)

        result
    }

    private String resolvedBrowserHtml

    def browser() {
        if (grailsGraphQLConfiguration.enabled && grailsGraphQLConfiguration.browser) {
            if (resolvedBrowserHtml == null) {
                String endpoint = grailsLinkGenerator.link(controller: 'graphql', action: 'index')
                String staticBase = grailsLinkGenerator.resource([:])

                if (!staticBase.endsWith('/')) {
                    staticBase = staticBase + '/'
                }

                resolvedBrowserHtml = IOUtils.toString(this.class.classLoader.getResourceAsStream('graphiql.html'), "UTF8")
                        .replaceAll(/\{endpoint}/, endpoint)
                        .replaceAll(/\{staticBase}/, staticBase)
            }

            render(text: resolvedBrowserHtml, contentType: 'text/html')
        } else {
            render(status: 404)
        }
    }
}
