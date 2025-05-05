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
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.runtime.config.SchemaGenerate
import io.micronaut.http.HttpStatus
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Post
import io.micronaut.http.client.annotation.Client
import org.grails.forge.analytics.Generated
import org.grails.forge.analytics.SelectedFeature
import org.grails.forge.application.ApplicationType
import org.grails.forge.options.BuildTool
import org.grails.forge.options.JdkVersion
import org.grails.forge.options.GormImpl
import org.grails.forge.options.ServletImpl
import org.grails.forge.options.TestFramework
import org.grails.forge.util.VersionInfo
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.micronaut.test.support.TestPropertyProvider
import org.testcontainers.containers.PostgreSQLContainer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import jakarta.inject.Inject
import java.util.concurrent.CompletableFuture

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
         "datasources.default.dialect": Dialect.POSTGRES.name()]
    }

    @Inject AnalyticsClient client
    @Inject ApplicationRepository repository
    @Inject FeatureRepository featureRepository

    void "test save generation data"() {
        given:
        def generated = new Generated(
                ApplicationType.WEB,
                GormImpl.HIBERNATE,
                ServletImpl.TOMCAT,
                TestFramework.SPOCK,
                JdkVersion.DEFAULT_OPTION
        )
        generated.setSelectedFeatures([new SelectedFeature("google-cloud-function")])

        when:
        HttpStatus status = client.applicationGenerated(generated).get()

        then:
        status == HttpStatus.ACCEPTED

        when:
        def application = repository.list(Pageable.UNPAGED)[0]

        then:
        application.type == generated.type
        application.gorm == generated.gorm
        application.jdkVersion == generated.jdkVersion
        application.testFramework == generated.testFramework
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
        gorm[0].name == 'HIBERNATE'
        featureRepository.topBuildTools()
        featureRepository.topJdkVersion()
        featureRepository.topTestFrameworks()
    }

    @Client("/analytics")
    static interface AnalyticsClient {
        @Post("/report")
        CompletableFuture<HttpStatus> applicationGenerated(@NonNull @Body Generated generated);
    }
}
