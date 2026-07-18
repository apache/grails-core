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

package org.grails.forge.analytics.postgres

import io.micronaut.context.env.Environment
import io.micronaut.core.annotation.NonNull
import io.micronaut.data.model.Pageable
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.HttpClient
import io.micronaut.security.token.generator.TokenGenerator
import org.grails.forge.analytics.Generated
import org.grails.forge.analytics.SelectedFeature
import org.grails.forge.application.ApplicationType
import org.grails.forge.options.DevelopmentReloading
import org.grails.forge.options.JdkVersion
import org.grails.forge.options.GormImpl
import org.grails.forge.options.ServletImpl
import org.grails.forge.util.VersionInfo
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.micronaut.test.support.TestPropertyProvider
import org.testcontainers.containers.PostgreSQLContainer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import jakarta.inject.Inject

@MicronautTest(
        transactional = false,
        environments = Environment.GOOGLE_COMPUTE)
class StoreGeneratedProjectStatsSpec extends Specification implements TestPropertyProvider {

    @Shared @AutoCleanup PostgreSQLContainer postgres = new PostgreSQLContainer<>("postgres:10")
            .withDatabaseName("test-database")
            .withUsername("test")
            .withPassword("test")

    @Override
    Map<String, String> getProperties() {
        postgres.start()

        ["datasources.default.url":postgres.getJdbcUrl(),
         "datasources.default.username":postgres.getUsername(),
         "datasources.default.password":postgres.getPassword(),
         "datasources.default.dialect": Dialect.POSTGRES.name(),
          "micronaut.security.token.jwt.claims-validators.audience":"forge-analytics",
          "micronaut.security.token.jwt.claims-validators.issuer":"forge-test",
          "micronaut.security.token.jwt.signatures.secret.generator.secret":"pleaseChangeThisSecretForANewOneAndMakeItLongEnough",
          "micronaut.security.token.jwt.signatures.secret.validation.secret":"pleaseChangeThisSecretForANewOneAndMakeItLongEnough",
          "grails.forge.analytics.caller-subject":"1234567890"]
    }

    @Inject @Client('/analytics') HttpClient client
    @Inject ApplicationRepository repository
    @Inject FeatureRepository featureRepository
    @Inject TokenGenerator tokenGenerator

    void "test save generation data"() {
        given:
        def generated = new Generated(
                ApplicationType.WEB,
                GormImpl.HIBERNATE5,
                ServletImpl.TOMCAT,
                DevelopmentReloading.DEVTOOLS,
                JdkVersion.DEFAULT_OPTION
        )
        generated.setSelectedFeatures([new SelectedFeature("google-cloud-function")])

        when:
        HttpResponse<?> response = client.toBlocking().exchange(authorizedRequest(generated))

        then:
        response.status == HttpStatus.ACCEPTED

        when:
        def application = repository.list(Pageable.UNPAGED)[0]

        then:
        application.type == generated.type
        application.gorm == generated.gorm
        application.jdkVersion == generated.jdkVersion
        application.reloading == generated.reloading
        application.features.find { it.name == 'google-cloud-function' }
        application.grailsVersion == VersionInfo.grailsVersion
        application.dateCreated

        when:
        def topFeatures = featureRepository.topFeatures()

        then:
        !topFeatures.isEmpty()
        topFeatures[0].name == 'google-cloud-function'
        topFeatures[0].total == 1

        when:
        def gorm = featureRepository.topGorm()

        then:
        gorm
        gorm[0].name == 'HIBERNATE5'
        featureRepository.topBuildTools()
        featureRepository.topJdkVersion()
        featureRepository.topReloading()
    }

    @NonNull
    private HttpRequest<Generated> authorizedRequest(@NonNull Generated generated) {
        HttpRequest.POST('report', generated).bearerAuth(bearerToken())
    }

    @NonNull
    private String bearerToken() {
        tokenGenerator.generateToken([
                sub: '1234567890',
                iss: 'forge-test',
                aud: 'forge-analytics'
        ]).orElseThrow()
    }
}
