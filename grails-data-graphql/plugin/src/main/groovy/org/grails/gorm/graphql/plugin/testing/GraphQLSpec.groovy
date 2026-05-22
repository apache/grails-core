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
import groovy.json.JsonSlurper
import groovy.transform.TupleConstructor
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.StringHttpMessageConverter
import org.springframework.web.client.RestClient

trait GraphQLSpec {

    private static String _url
    private static GraphQLRequestHelper _graphql

    @Value('${local.server.port}')
    String serverPort

    GraphQLRequestHelper getGraphQL() {
        if (_graphql == null) {
            StringHttpMessageConverter stringConverter = new StringHttpMessageConverter()
            stringConverter.supportedMediaTypes = [MediaType.ALL]
            _graphql = new GraphQLRequestHelper(rest: RestClient.builder()
                    .baseUrl(getServerUrl())
                    .messageConverters({ converters ->
                        converters.clear()
                        converters.add(stringConverter)
                    })
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
        private static final JsonSlurper SLURPER = new JsonSlurper()

        RestClient rest

        ResponseEntity<Map> graphql(String requestBody) {
            wrapJson(exchangeGraphql(requestBody))
        }

        // Overload that returns the raw body for callers asserting on the
        // unparsed JSON payload (only String is supported - tests asserting on
        // a structured body should use the no-class overload above which parses
        // into a Map).
        @SuppressWarnings('unchecked')
        def <T> ResponseEntity<T> graphql(String requestBody, Class<T> bodyType) {
            if (bodyType != String) {
                throw new IllegalArgumentException(
                        "graphql(String, Class) only supports String.class; got ${bodyType.name}")
            }
            (ResponseEntity<T>) exchangeGraphql(requestBody)
        }

        private ResponseEntity<String> exchangeGraphql(String requestBody) {
            rest.post()
                    .uri('/graphql')
                    .contentType(APPLICATION_GRAPHQL)
                    .body(requestBody)
                    .retrieve()
                    .toEntity(String)
        }

        private ResponseEntity<Map> buildJsonRequest(Map<String, Object> data) {
            wrapJson(rest.post()
                    .uri('/graphql')
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(JsonOutput.toJson(data))
                    .retrieve()
                    .toEntity(String))
        }

        private ResponseEntity<Map> buildGetRequest(Map<String, Object> data) {
            if (data.containsKey('variables')) {
                data.put('variables', JsonOutput.toJson(data.variables))
            }
            wrapJson(rest.get()
                    .uri('/', { uriBuilder ->
                        data.each { key, value ->
                            uriBuilder.queryParam(key, value)
                        }
                        uriBuilder.build()
                    })
                    .retrieve()
                    .toEntity(String))
        }

        private static ResponseEntity<Map> wrapJson(ResponseEntity<String> raw) {
            String body = raw.body
            Map parsed = (body == null || body.isEmpty()) ? null : (Map) SLURPER.parseText(body)
            new ResponseEntity<Map>(parsed, raw.headers, raw.statusCode)
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
