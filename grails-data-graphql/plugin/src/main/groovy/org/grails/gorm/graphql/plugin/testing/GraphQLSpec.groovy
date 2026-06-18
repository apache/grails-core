/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */

package org.grails.gorm.graphql.plugin.testing

import groovy.json.JsonOutput
import groovy.transform.TupleConstructor
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.client.RestClient

trait GraphQLSpec {

    private static String _url
    private static GraphQLRequestHelper _graphql

    @Value('${local.server.port}')
    String serverPort

    GraphQLRequestHelper getGraphQL() {
        if (_graphql == null) {
            _graphql = new GraphQLRequestHelper(rest: RestClient.builder()
                    .baseUrl(getServerUrl())
                    .build())
        }
        _graphql
    }

    String getUrl() {
        getServerUrl() + '/graphql'
    }

    String getServerUrl() {
        if (_url == null) {
            _url = "http://localhost:${serverPort}"
        }
        _url
    }

    @TupleConstructor
    static class GraphQLRequestHelper {

        private static final MediaType APPLICATION_GRAPHQL = MediaType.parseMediaType('application/graphql')

        RestClient rest

        ResponseEntity<Map> graphql(String requestBody) {
            graphql(requestBody, Map)
        }

        def <T> ResponseEntity<T> graphql(String requestBody, Class<T> bodyType) {
            rest.post()
                    .uri('/graphql')
                    .contentType(APPLICATION_GRAPHQL)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .toEntity(bodyType)
        }

        private ResponseEntity<Map> buildJsonRequest(Map<String, Object> data) {
            rest.post()
                    .uri('/graphql')
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(data)
                    .retrieve()
                    .toEntity(Map)
        }

        private ResponseEntity<Map> buildGetRequest(Map<String, Object> data) {
            // The GraphQL-over-HTTP GET protocol requires `variables` to be a
            // URL-encoded JSON string query parameter, not an HTTP body, so it is
            // JSON-encoded here rather than negotiated by a message converter.
            Map<String, Object> queryParams = new LinkedHashMap<>(data)
            if (queryParams.containsKey('variables')) {
                queryParams.put('variables', JsonOutput.toJson(queryParams.get('variables')))
            }
            rest.get()
                    .uri('/graphql', { uriBuilder ->
                        // Bind values as URI variables so the GraphQL query braces are encoded, not parsed as URI templates.
                        Map<String, Object> uriVariables = [:]
                        queryParams.eachWithIndex { entry, index ->
                            String name = "value${index}"
                            uriBuilder.queryParam(entry.key, '{' + name + '}')
                            uriVariables[name] = entry.value
                        }
                        uriBuilder.build(uriVariables)
                    })
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .toEntity(Map)
        }

        ResponseEntity<Map> json(String query) {
            buildJsonRequest([query: query])
        }
        ResponseEntity<Map> json(String query, String operationName) {
            buildJsonRequest([query: query, operationName: operationName])
        }
        ResponseEntity<Map> json(String query, Map variables) {
            buildJsonRequest([query: query, variables: variables])
        }
        ResponseEntity<Map> json(String query, Map variables, String operationName) {
            buildJsonRequest([query: query, operationName: operationName, variables: variables])
        }

        ResponseEntity<Map> get(String query) {
            buildGetRequest([query: query])
        }
        ResponseEntity<Map> get(String query, String operationName) {
            buildGetRequest([query: query, operationName: operationName])
        }
        ResponseEntity<Map> get(String query, Map variables) {
            buildGetRequest([query: query, variables: variables])
        }
        ResponseEntity<Map> get(String query, Map variables, String operationName) {
            buildGetRequest([query: query, operationName: operationName, variables: variables])
        }
    }

}
