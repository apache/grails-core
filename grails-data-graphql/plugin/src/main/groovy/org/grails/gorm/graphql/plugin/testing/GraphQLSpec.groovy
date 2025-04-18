package org.grails.gorm.graphql.plugin.testing

import groovy.json.StreamingJsonBuilder
import groovy.transform.TupleConstructor
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.uri.UriBuilder
import io.micronaut.rxjava2.http.client.RxHttpClient
import org.springframework.beans.factory.annotation.Value

trait GraphQLSpec {

    private static String _url
    private static GraphQLRequestHelper _graphql

    @Value('${local.server.port}')
    String serverPort

    GraphQLRequestHelper getGraphQL() {
        if (_graphql == null) {
            _graphql = new GraphQLRequestHelper(rest: RxHttpClient.create(new URL(getServerUrl())))
        }
        _graphql
    }

    String getUrl() {
       getServerUrl() + "/graphql"
    }

    String getServerUrl() {
        if (_url == null) {
            _url = "http://localhost:${serverPort}"
        }
        _url
    }

    @TupleConstructor
    static class GraphQLRequestHelper {

        RxHttpClient rest

        HttpResponse<Map> graphql(String requestBody) {
            rest.exchange(HttpRequest.POST('/graphql', requestBody).contentType('application/graphql'), Map)
                    .firstOrError().blockingGet()
        }

        def <T> HttpResponse<T> graphql(String requestBody, Class<T> bodyType) {
            rest.exchange(HttpRequest.POST('/graphql', requestBody).contentType('application/graphql'), bodyType)
                    .firstOrError().blockingGet()
        }

        private HttpResponse<Map> buildJsonRequest(Map<String, Object> data) {
            rest.exchange(HttpRequest.POST('/graphql', data), Map).firstOrError().blockingGet()
        }
        private HttpResponse<Map> buildGetRequest(Map<String, Object> data) {
            if (data.containsKey('variables')) {
                StringWriter sw = new StringWriter()
                new StreamingJsonBuilder(sw).call(data.variables)
                data.put('variables', sw.toString())
            }

            UriBuilder uriBuilder = UriBuilder.of('/')
            data.forEach({ key, value ->
                uriBuilder.queryParam(key, value)
            })

            rest.exchange(HttpRequest.GET(uriBuilder.build()), Map).firstOrError().blockingGet()
        }

        HttpResponse<Map> json(String query) {
            buildJsonRequest([query: query])
        }
        HttpResponse<Map> json(String query, String operationName) {
            buildJsonRequest([query: query, operationName: operationName])
        }
        HttpResponse<Map> json(String query, Map variables) {
            buildJsonRequest([query: query, variables: variables])
        }
        HttpResponse<Map> json(String query, Map variables, String operationName) {
            buildJsonRequest([query: query, operationName: operationName, variables: variables])
        }

        HttpResponse<Map> get(String query) {
            buildGetRequest([query: query])
        }
        HttpResponse<Map> get(String query, String operationName) {
            buildGetRequest([query: query, operationName: operationName])
        }
        HttpResponse<Map> get(String query, Map variables) {
            buildGetRequest([query: query, variables: variables])
        }
        HttpResponse<Map> get(String query, Map variables, String operationName) {
            buildGetRequest([query: query, operationName: operationName, variables: variables])
        }
    }

}
