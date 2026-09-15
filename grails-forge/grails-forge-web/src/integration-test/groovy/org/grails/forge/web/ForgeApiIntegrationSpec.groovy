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
package org.grails.forge.web

import grails.testing.mixin.integration.Integration
import grails.web.mapping.cors.GrailsCorsConfiguration
import groovy.json.JsonSlurper
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import spock.lang.Specification

import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.zip.ZipInputStream

@Integration(applicationClass = Application)
class ForgeApiIntegrationSpec extends Specification {

    @Value('${local.server.port}')
    int serverPort

    @Autowired
    GrailsCorsConfiguration grailsCorsConfiguration

    void "GET /versions returns version map"() {
        when:
        Map response = get('/versions')
        Map json = json(response)

        then:
        response.status == 200
        response.contentType.startsWith('application/json')
        json.versions['grails.version']
    }

    void "GET application type endpoints expose the web application contract"() {
        when:
        Map listResponse = get('/application-types')
        Map typeResponse = get('/application-types/web')
        Map list = json(listResponse)
        Map web = json(typeResponse)

        then:
        listResponse.status == 200
        listResponse.contentType.startsWith('application/json')
        list.types.find { it.name == 'web' }
        typeResponse.status == 200
        typeResponse.contentType.startsWith('application/json')
        web.name == 'web'
        web.title
        web.description
        web.features instanceof List
    }

    void "GET feature endpoints distinguish available and default web features"() {
        when:
        Map featuresResponse = get('/application-types/web/features')
        Map defaultsResponse = get('/application-types/web/features/default')
        Map features = json(featuresResponse)
        Map defaults = json(defaultsResponse)

        then:
        featuresResponse.status == 200
        featuresResponse.contentType.startsWith('application/json')
        features.features
        features.features.any { it.name == 'gorm-mongodb' }
        !features.features.any { it.name == 'asset-pipeline-grails' }
        defaultsResponse.status == 200
        defaultsResponse.contentType.startsWith('application/json')
        defaults.features
        defaults.features.any { it.name == 'asset-pipeline-grails' }
    }

    void "GET feature endpoints retain query parameter compatibility"() {
        when:
        Map legacyGormResponse = get('/application-types/web/features?gorm=hibernate')
        Map invalidFilterResponse = get('/application-types/web/features?gorm=invalid&javaVersion=invalid')

        then:
        legacyGormResponse.status == 200
        json(legacyGormResponse).features
        invalidFilterResponse.status == 200
        json(invalidFilterResponse).features.any { it.name == 'gorm-mongodb' }
    }

    void "GET /create/web/:name returns a named zip archive"() {
        when:
        Map response = get('/create/web/forge-contract?features=gorm-mongodb&gorm=mongodb')

        then:
        response.status == 201
        response.contentType.startsWith('application/zip')
        response.contentDisposition.contains('forge-contract.zip')
        isZip(response.body as byte[])
    }

    void "GET /:name.zip returns a zip archive for the default application type"() {
        when:
        Map response = get('/forge-default-contract.zip')

        then:
        response.status == 201
        response.contentType.startsWith('application/zip')
        response.contentDisposition.contains('forge-default-contract.zip')
        isZip(response.body as byte[])
    }

    void "GET /preview/:type/:name returns generated project contents as JSON"() {
        when:
        Map response = get('/preview/web/forge-preview?features=grails-quartz')
        Map preview = json(response)

        then:
        response.status == 200
        response.contentType.startsWith('application/json')
        preview.contents['build.gradle']
        preview.contents['build.gradle'].contains('org.apache.grails:grails-quartz')
    }

    void "GET diff endpoints return plain text feature changes"() {
        when:
        Map appResponse = get('/diff/web/forge-diff?features=gorm-mongodb')
        Map featureResponse = get('/diff/web/feature/gorm-mongodb')
        String appDiff = text(appResponse)
        String featureDiff = text(featureResponse)

        then:
        appResponse.status == 200
        appResponse.contentType.startsWith('text/plain')
        appDiff.contains('+## Feature gorm-mongodb documentation')
        featureResponse.status == 200
        featureResponse.contentType.startsWith('text/plain')
        featureDiff.contains('+## Feature gorm-mongodb documentation')
    }

    void "GET /select-options exposes every supported option group"() {
        when:
        Map response = get('/select-options')
        Map options = json(response)

        then:
        response.status == 200
        response.contentType.startsWith('application/json')
        options.keySet().containsAll(['type', 'jdkVersion', 'lang', 'reloading', 'gorm', 'servlet'])
        options.values().every { Object group ->
            group instanceof Map && group.options && group.defaultOption
        }
        options.type.options.any { it.name == 'web' }
    }

    void "GET / returns the plain text API description"() {
        when:
        Map response = get('/')

        then:
        response.status == 200
        response.contentType.startsWith('text/plain')
        text(response).contains('/application-types')
    }

    void "JSON responses expose HAL _links"() {
        when:
        Map json = json(get('/application-types'))

        then:
        json._links
        json._links.self
        !json.containsKey('links')
    }

    void "POST to a GET-only endpoint is rejected"() {
        expect:
        exchange('POST', '/versions').status == 405
    }

    void "CORS allows the Forge UI origin"() {
        expect:
        grailsCorsConfiguration.enabled
        grailsCorsConfiguration.corsConfigurations['/**'].allowedOrigins.contains(origin)
        get('/versions', ['Origin': origin]).accessControlAllowOrigin == origin

        where:
        origin << [
                'https://start.grails.org',
                'https://grails.github.io',
                'https://grails.apache.org'
        ]
    }

    void "unknown application types are client errors"() {
        expect:
        get('/application-types/not-a-type').status == 400
        get('/create/web/bad!').status == 400
        get('/create/web/forge-unknown?features=not-a-real-feature').status == 400
        get('/preview/web/forge-unknown?features=not-a-real-feature').status == 400
        get('/diff/web/forge-unknown?features=not-a-real-feature').status == 400
    }

    void "generated links honor X-Forwarded-Proto"() {
        when:
        Map json = json(get('/application-types', [
                'X-Forwarded-Host': 'public.example',
                'X-Forwarded-Proto': 'https'
        ]))

        then:
        json._links.self.href.startsWith('https://public.example')
    }

    private Map get(String path, Map headers = [:]) {
        exchange('GET', path, headers)
    }

    private Map exchange(String method, String path, Map headers = [:]) {
        HttpRequest.Builder request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:${serverPort}${path}"))
                .timeout(Duration.ofSeconds(120))
        headers.each { String name, String value ->
            request.header(name, value)
        }
        HttpResponse<byte[]> response = HttpClient.newHttpClient().send(
                request.method(method, HttpRequest.BodyPublishers.noBody()).build(),
                HttpResponse.BodyHandlers.ofByteArray())
        [
                status: response.statusCode(),
                contentType: response.headers().firstValue('Content-Type').orElse(''),
                contentDisposition: response.headers().firstValue('Content-Disposition').orElse(''),
                accessControlAllowOrigin: response.headers().firstValue('Access-Control-Allow-Origin').orElse(''),
                body: response.body()
        ]
    }

    private static Map json(Map response) {
        new JsonSlurper().parseText(text(response)) as Map
    }

    private static String text(Map response) {
        new String(response.body as byte[], StandardCharsets.UTF_8)
    }

    private static boolean isZip(byte[] body) {
        if (body.length < 4 || body[0] != 0x50 || body[1] != 0x4b) {
            return false
        }
        ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(body))
        try {
            zip.nextEntry != null
        }
        finally {
            zip.close()
        }
    }
}
