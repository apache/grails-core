package org.grails.gorm.graphql.plugin

import grails.util.TypeConvertingMap
import grails.web.mime.MimeType
import grails.web.servlet.mvc.GrailsParameterMap
import groovy.json.JsonSlurper
import groovy.transform.CompileStatic

@CompileStatic
class GraphQLRequestUtils {

    static GraphQLRequest graphQLRequestWithParams(GrailsParameterMap params) {
        GraphQLRequest graphQLRequest = new GraphQLRequest()
        graphQLRequest.query = params.query
        graphQLRequest.operationName = params.containsKey('operationName') ? params.operationName : null
        if (params.containsKey('variables')) {
            graphQLRequest.variables = new JsonSlurper().parseText(params.variables as String) as Map
        } else {
            graphQLRequest.variables = Collections.emptyMap() as Map<String, Object>
        }
        graphQLRequest
    }

    static GraphQLRequest graphQLRequestWithBodyAndMimeTypes(String body, MimeType[] mimeTypes) {
        if (mimeTypes == null) {
            return null
        }

        if (mimeTypes.contains(MimeType.JSON)) {
            return graphQLRequestWithJSONBody(body)
        }

        if (mimeTypes.contains(GormGraphqlGrailsPlugin.GRAPHQL_MIME)) {
            return graphQLRequestWithGraphqlBody(body)
        }

        return null
    }

    static GraphQLRequest graphQLRequestWithJSONBody(String body) {
        TypeConvertingMap json = new TypeConvertingMap(new JsonSlurper().parseText(body) as Map)
        GraphQLRequest graphQLRequest = new GraphQLRequest()
        graphQLRequest.with {
            query = json.query.toString()
            operationName = json.containsKey('operationName') ? json.operationName : null

            variables = (json.variables instanceof Map) ? (Map)json.variables : Collections.emptyMap()
        }
        graphQLRequest
    }

    static GraphQLRequest graphQLRequestWithGraphqlBody(String body) {
        GraphQLRequest graphQLRequest = new GraphQLRequest()
        graphQLRequest.with {
            query = body
            operationName = null
            variables = Collections.emptyMap() as Map<String, Object>
        }
        graphQLRequest
    }
}
