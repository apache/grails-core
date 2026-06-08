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

package specs

import io.micronaut.http.client.BlockingHttpClient
import io.micronaut.http.client.HttpClient
import spock.lang.Shared
import spock.lang.Specification

abstract class HttpClientSpec extends Specification {

    @Shared HttpClient _httpClient
    @Shared BlockingHttpClient _client

    HttpClient getHttpClient() {
        if(!_httpClient) {
            _httpClient = createHttpClient()
        }
        _httpClient
    }

    BlockingHttpClient getClient() {
        if(!_client) {
            _client = getHttpClient().toBlocking()
        }
        _client
    }

    HttpClient createHttpClient() {
        String baseUrl = "http://localhost:$serverPort"
        HttpClient.create(baseUrl.toURL())
    }

    def cleanupSpec() {
        resetHttpClient()
    }

    void resetHttpClient() {
        _httpClient?.close()
        _httpClient = null
        _client = null
    }
}
