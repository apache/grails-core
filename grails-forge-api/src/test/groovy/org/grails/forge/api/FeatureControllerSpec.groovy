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

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.ObjectMapper
import io.micronaut.http.HttpRequest
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.json.tree.JsonNode
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import org.grails.forge.application.ApplicationType
import org.grails.forge.options.FeatureFilter
import org.grails.forge.options.GormImpl
import org.grails.forge.options.JdkVersion
import org.grails.forge.options.TestFramework
import org.grails.forge.options.ServletImpl
import spock.lang.Specification

@MicronautTest
class FeatureControllerSpec extends Specification {

    @Inject
    ApplicationTypeClient applicationTypeClient

    @Inject
    @Client("/")
    HttpClient httpClient

    void 'test list features'() {
        when:
        List<FeatureDTO> features = applicationTypeClient
                .features(ApplicationType.DEFAULT_OPTION,
                        RequestInfo.LOCAL,
                        new FeatureFilter(test: TestFramework.DEFAULT_OPTION,
                        gorm: GormImpl.DEFAULT_OPTION,
                        servlet: ServletImpl.DEFAULT_OPTION,
                        javaVersion: JdkVersion.DEFAULT_OPTION)).features

        then:
        !features.isEmpty()
    }

    void 'test community features'() {
        when:
        List<FeatureDTO> communityFeatures = applicationTypeClient
                .features(ApplicationType.DEFAULT_OPTION,
                        RequestInfo.LOCAL,
                        new FeatureFilter(test: TestFramework.DEFAULT_OPTION,
                                gorm: GormImpl.DEFAULT_OPTION,
                                servlet: ServletImpl.DEFAULT_OPTION,
                                javaVersion: JdkVersion.DEFAULT_OPTION)).features
                .findAll { it.community }

        then:
        communityFeatures.isEmpty()
    }

    void 'test list features - spanish'() {
        when:
        List<FeatureDTO> features = applicationTypeClient
                .spanishFeatures(ApplicationType.DEFAULT_OPTION,
                        new FeatureFilter(test: TestFramework.DEFAULT_OPTION,
                                gorm: GormImpl.DEFAULT_OPTION,
                                servlet: ServletImpl.DEFAULT_OPTION,
                                javaVersion: JdkVersion.DEFAULT_OPTION)).features
        def mongoGorm = features.find { it.name == 'asciidoctor' }

        then:
        mongoGorm.description == 'Agrega soporte para crear documentación de Asciidoctor'
        !mongoGorm.isPreview()
        !mongoGorm.isCommunity()
    }

    void 'test list default features - spanish'() {
        when:
        List<FeatureDTO> features = applicationTypeClient
                .spanishDefaultFeatures(ApplicationType.DEFAULT_OPTION,
                        new FeatureFilter(test: TestFramework.DEFAULT_OPTION,
                                gorm: GormImpl.DEFAULT_OPTION,
                                servlet: ServletImpl.DEFAULT_OPTION,
                                javaVersion: JdkVersion.DEFAULT_OPTION)).features
        def assetPipeline = features.find { it.name == 'asset-pipeline-grails' }

        then:
        assetPipeline.description == 'El activo-Pipeline es un complemento utilizado para administrar y procesar activos estáticos en aplicaciones JVM principalmente a través de Gradle (sin embargo, no es obligatorio). Leer más en https'
        !assetPipeline.isPreview()
        !assetPipeline.isCommunity()
    }

    void 'test list default features for application type'() {
        when:
        def features = applicationTypeClient
                .defaultFeatures(ApplicationType.PLUGIN,
                        RequestInfo.LOCAL,
                        new FeatureFilter(test: TestFramework.DEFAULT_OPTION,
                                gorm: GormImpl.DEFAULT_OPTION,
                                servlet: ServletImpl.DEFAULT_OPTION,
                                javaVersion: JdkVersion.DEFAULT_OPTION)).features

        then:
        !features.any { it.name == 'geb-with-testcontainers' }
        features.any { it.name == 'gorm-hibernate5' }

        when:
        features = applicationTypeClient
                .defaultFeatures(ApplicationType.DEFAULT_OPTION,
                        RequestInfo.LOCAL,
                        new FeatureFilter(test: TestFramework.DEFAULT_OPTION,
                                gorm: GormImpl.DEFAULT_OPTION,
                                servlet: ServletImpl.DEFAULT_OPTION,
                                javaVersion: JdkVersion.DEFAULT_OPTION)).features

        then:
        features.any { it.name == 'geb-with-testcontainers' }
    }

    void 'test list features for application type'() {
        when:
        def features = applicationTypeClient
                .features(ApplicationType.PLUGIN,
                        RequestInfo.LOCAL,
                        new FeatureFilter(test: TestFramework.DEFAULT_OPTION,
                                gorm: GormImpl.DEFAULT_OPTION,
                                servlet: ServletImpl.DEFAULT_OPTION,
                                javaVersion: JdkVersion.DEFAULT_OPTION)).features

        then:
        !features.any { it.name == 'geb-with-testcontainers' }

        when:
        features = applicationTypeClient
                .features(ApplicationType.DEFAULT_OPTION,
                        RequestInfo.LOCAL,
                        new FeatureFilter(test: TestFramework.DEFAULT_OPTION,
                                gorm: GormImpl.DEFAULT_OPTION,
                                servlet: ServletImpl.DEFAULT_OPTION,
                                javaVersion: JdkVersion.DEFAULT_OPTION)).features

        then:
        features.any { it.name == 'gorm-mongodb' }
    }

    void 'test list features for application type should NOT return default included features'() {
        when:
        def features = applicationTypeClient
                .features(ApplicationType.WEB,
                        RequestInfo.LOCAL,
                        new FeatureFilter(test: TestFramework.DEFAULT_OPTION,
                                gorm: GormImpl.DEFAULT_OPTION,
                                servlet: ServletImpl.DEFAULT_OPTION,
                                javaVersion: JdkVersion.DEFAULT_OPTION)).features

        then:
        !features.any { it.name == 'asset-pipeline-grails' }
    }

    void "test feature filter - invalid option as query parameter"() {
        when:
        String response = httpClient.toBlocking().withCloseable { client ->
            client.exchange(HttpRequest.GET('/application-types/' + ApplicationType.WEB.name + '/features?javaVersion=invalid'), String.class).body()
        }

        then:
        response

        and:
        ObjectMapper mapper = new ObjectMapper()
        def map = mapper.readValue(response, Map)

        map.features.collect { it -> it.name }.find { it == 'gorm-mongodb'}
    }
}
