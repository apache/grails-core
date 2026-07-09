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

import io.micronaut.context.annotation.Property
import io.micronaut.core.annotation.NonNull
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.data.jdbc.runtime.JdbcOperations
import io.micronaut.security.token.generator.TokenGenerator
import io.micronaut.test.annotation.MockBean
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import org.grails.forge.analytics.Generated
import org.grails.forge.analytics.SelectedFeature
import org.grails.forge.application.ApplicationType
import org.grails.forge.options.DevelopmentReloading
import org.grails.forge.options.GormImpl
import org.grails.forge.options.JdkVersion
import org.grails.forge.options.ServletImpl
import spock.lang.Specification

import jakarta.inject.Inject

@MicronautTest
@Property(name = 'datasources.default.enabled', value = 'false')
@Property(name = 'flyway.enabled', value = 'false')
@Property(name = 'micronaut.security.authentication', value = 'bearer')
@Property(name = 'micronaut.security.redirect.enabled', value = 'false')
@Property(name = 'micronaut.security.token.jwt.claims-validators.audience', value = 'forge-analytics')
@Property(name = 'micronaut.security.token.jwt.claims-validators.issuer', value = 'forge-test')
@Property(name = 'micronaut.security.token.jwt.signatures.secret.generator.secret', value = 'pleaseChangeThisSecretForANewOneAndMakeItLongEnough')
@Property(name = 'grails.forge.analytics.caller-subject', value = '1234567890')
class AnalyticsControllerSecuritySpec extends Specification {

    @Inject @Client('/analytics') HttpClient client
    @Inject ApplicationRepository applicationRepository
    @Inject FeatureRepository featureRepository
    @Inject JdbcOperations jdbcOperations
    @Inject TokenGenerator tokenGenerator

    void 'report generation data requires bearer authentication'() {
        given:
        Generated generated = new Generated(
                ApplicationType.WEB,
                GormImpl.HIBERNATE,
                ServletImpl.TOMCAT,
                DevelopmentReloading.DEVTOOLS,
                JdkVersion.DEFAULT_OPTION
        )
        generated.setSelectedFeatures([new SelectedFeature('google-cloud-function')])

        when:
        client.toBlocking().exchange(HttpRequest.POST('/report', generated), String)

        then:
        HttpClientResponseException e = thrown(HttpClientResponseException)
        e.status == HttpStatus.UNAUTHORIZED
        0 * applicationRepository._
        0 * jdbcOperations._
    }

    void 'top analytics data is not rejected as unauthenticated'() {
        when:
        def response = client.toBlocking().exchange(HttpRequest.GET('/top/features'), String)

        then:
        response.status == HttpStatus.OK
        response.body() == '[]'
        1 * featureRepository.topFeatures() >> []
        0 * applicationRepository._
    }

    void 'report generation data rejects bearer token with wrong audience'() {
        given:
        Generated generated = new Generated(
                ApplicationType.WEB,
                GormImpl.HIBERNATE,
                ServletImpl.TOMCAT,
                DevelopmentReloading.DEVTOOLS,
                JdkVersion.DEFAULT_OPTION
        )
        generated.setSelectedFeatures([new SelectedFeature('google-cloud-function')])

        when:
        client.toBlocking().exchange(HttpRequest.POST('/report', generated).bearerAuth(wrongAudienceToken()), String)

        then:
        HttpClientResponseException e = thrown(HttpClientResponseException)
        e.status == HttpStatus.UNAUTHORIZED
        0 * applicationRepository._
        0 * jdbcOperations._
    }

    void 'report generation data rejects bearer token from wrong caller'() {
        given:
        Generated generated = new Generated(
                ApplicationType.WEB,
                GormImpl.HIBERNATE,
                ServletImpl.TOMCAT,
                DevelopmentReloading.DEVTOOLS,
                JdkVersion.DEFAULT_OPTION
        )
        generated.setSelectedFeatures([new SelectedFeature('google-cloud-function')])

        when:
        client.toBlocking().exchange(HttpRequest.POST('/report', generated).bearerAuth(wrongCallerToken()), String)

        then:
        HttpClientResponseException e = thrown(HttpClientResponseException)
        e.status == HttpStatus.UNAUTHORIZED
        0 * applicationRepository._
        0 * jdbcOperations._
    }

    @MockBean(ApplicationRepository)
    @NonNull
    ApplicationRepository applicationRepository() {
        Mock(ApplicationRepository)
    }

    @MockBean(FeatureRepository)
    @NonNull
    FeatureRepository featureRepository() {
        Mock(FeatureRepository)
    }

    @MockBean(JdbcOperations)
    @NonNull
    JdbcOperations jdbcOperations() {
        Mock(JdbcOperations)
    }

    @NonNull
    private String wrongAudienceToken() {
        tokenGenerator.generateToken([
                sub: '1234567890',
                iss: 'forge-test',
                aud: 'other-service'
        ]).orElseThrow()
    }

    @NonNull
    private String wrongCallerToken() {
        tokenGenerator.generateToken([
                sub: 'other-caller',
                iss: 'forge-test',
                aud: 'forge-analytics'
        ]).orElseThrow()
    }
}
