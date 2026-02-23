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
package micronaut

import groovy.transform.CompileStatic

import io.micronaut.http.HttpResponse
import io.micronaut.http.client.exceptions.HttpClientResponseException
import micronaut.client.MicronautAdvancedClient
import micronaut.client.MicronautFilteredClient
import micronaut.client.MicronautPathClient
import micronaut.client.MicronautReactiveClient
import micronaut.client.MicronautTestClient

import org.springframework.beans.factory.annotation.Autowired

@CompileStatic
class ExternalApiService {

    @Autowired
    MicronautTestClient micronautTestClient

    @Autowired
    MicronautAdvancedClient advancedClient

    @Autowired
    MicronautReactiveClient reactiveClient

    @Autowired
    MicronautPathClient pathClient

    @Autowired
    MicronautFilteredClient filteredClient

    String fetchAll() {
        micronautTestClient.index()
    }

    String fetchById(String id) {
        micronautTestClient.show(id)
    }

    String createResource(String json) {
        micronautTestClient.create(json)
    }

    String updateResource(String id, String json) {
        micronautTestClient.update(id, json)
    }

    int deleteResource(String id) {
        HttpResponse<?> response = micronautTestClient.delete(id)
        response.status.code
    }

    Map fetchWithErrorHandling(String id) {
        try {
            String data = micronautTestClient.show(id)
            return [success: true, data: data]
        } catch (HttpClientResponseException ex) {
            return [success: false, status: ex.status.code, error: ex.message]
        }
    }

    String search(String query, String page) {
        advancedClient.search(query, page)
    }

    String fetchSecure(String authHeader) {
        advancedClient.secureEndpoint(authHeader)
    }

    String patchResource(String id, String json) {
        advancedClient.patch(id, json)
    }

    Map orchestrateMultiple(String id1, String id2) {
        String first = micronautTestClient.show(id1)
        String second = micronautTestClient.show(id2)
        [first: first, second: second] as Map
    }

    String fetchAsync() {
        reactiveClient.getAsync().get()
    }

    String fetchPathItem(String id) {
        pathClient.getItem(id)
    }

    String fetchFiltered() {
        filteredClient.getFilteredData()
    }
}
