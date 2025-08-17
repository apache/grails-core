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

package org.grails.forge.api

import io.micronaut.context.i18n.ResourceBundleMessageSource
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpRequest
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Header
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import jakarta.inject.Singleton
import spock.lang.Specification

@MicronautTest
class ApplicationControllerSpec extends Specification {

    @Inject
    @Client("/")
    HttpClient client

    @Inject
    ApplicationTypeClient applicationTypeClient

    void "test versions"() {
        given:
        def response = client.toBlocking().retrieve(HttpRequest.GET('/versions'), Map)

        expect:
        response.containsKey("versions")
        response.versions["grails.version"]
    }

    void "test application types"() {
        when:
        def types = applicationTypeClient.spanishTypes()

        then:
        types.types.find { it.name == 'web' }.description == 'Una aplicación web de marco Grails®'
    }

    @Client('/')
    static interface ApplicationTypeClient extends ApplicationTypeOperations {
        @Get("/application-types")
        @Header(name = HttpHeaders.ACCEPT_LANGUAGE, value = "es")
        ApplicationTypeList spanishTypes();
    }

    @Singleton
    static class Spanish extends ResourceBundleMessageSource {

        Spanish() {
            super("features", new Locale("es"))
        }
    }
}
