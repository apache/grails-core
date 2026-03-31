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
package micronaut.client

import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.CookieValue
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Head
import io.micronaut.http.annotation.Header
import io.micronaut.http.annotation.Options
import io.micronaut.http.annotation.Patch
import io.micronaut.http.annotation.PathVariable
import io.micronaut.http.annotation.QueryValue
import io.micronaut.http.client.annotation.Client

@Client(id = 'grails-self')
interface MicronautAdvancedClient {

    @Patch('/micronaut-test/{id}')
    String patch(@PathVariable String id, @Body String body)

    @Get('/micronaut-test/search{?q,page}')
    String search(@QueryValue String q, @QueryValue String page)

    @Get('/micronaut-test/secure')
    String secureEndpoint(@Header('Authorization') String authorization)

    @Get('/micronaut-test/api-resource')
    String withApiKey(@Header('X-Api-Key') String apiKey)

    @Head('/micronaut-test')
    HttpResponse<?> headCheck()

    @Options('/micronaut-test')
    HttpResponse<?> optionsCheck()

    @Get('/micronaut-test/with-cookie')
    String withCookie(@CookieValue('session') String sessionId)

    @Get(value = '/micronaut-test/text', consumes = 'text/plain')
    String getPlainText()
}
