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

package org.grails.forge.feature.database

import org.grails.forge.ApplicationContextSpec
import org.grails.forge.BuildBuilder
import org.grails.forge.application.generator.GeneratorContext
import org.grails.forge.feature.Features
import org.grails.forge.fixture.CommandOutputFixture
import org.grails.forge.options.DevelopmentReloading
import org.grails.forge.options.GormImpl
import org.grails.forge.options.JdkVersion
import org.grails.forge.options.Options
import org.grails.forge.options.ServletImpl

class GrailsDataMongoDBSpec extends ApplicationContextSpec implements CommandOutputFixture {

    void "test Mongo gorm features"() {
        when:
        Features features = getFeatures(['gorm-mongodb'])

        then:
        features.contains("gorm-mongodb")
    }

    void "test Mongo gorm with Embedded MongoDB features "() {
        when:
        Features features = getFeatures(['gorm-mongodb'])

        then:
        features.contains("gorm-mongodb")
    }

    void "test dependencies are present for gradle"() {
        when:
        String template = new BuildBuilder(beanContext)
                .features(["gorm-mongodb"])
                .render()

        then:
        template.contains("implementation \"org.apache.grails:grails-data-mongodb\"")
        template.contains("implementation \"org.apache.grails:grails-data-mongodb-embedded\"")
    }

    void "test config"() {
        when:
        GeneratorContext ctx = buildGeneratorContext(['gorm-mongodb'])

        then:
        ctx.configuration.containsKey("grails.mongodb.url")
    }

    void "test the embedded MongoDB is enabled for development and test only"() {
        when:
        GeneratorContext ctx = buildGeneratorContext(['gorm-mongodb'])

        then: 'development and test name the server where they would name a host, so the app runs ' +
                'with no MongoDB installed'
        ctx.configuration.get("environments.development.grails.mongodb.url") == 'mongodb://embedded/devDb'
        ctx.configuration.get("environments.test.grails.mongodb.url") == 'mongodb://embedded/testDb'

        and: 'production keeps the configured url, so deploying with MONGO_HOST set reaches that database'
        !ctx.configuration.containsKey("environments.production.grails.mongodb.url")
        ctx.configuration.get("grails.mongodb.url") == 'mongodb://${MONGO_HOST:localhost}:${MONGO_PORT:27017}/prodDb'
    }

    void "test no initializer is copied into the generated application"() {
        when:
        Map<String, String> output = generate(['gorm-mongodb'])

        then: 'the embedded support arrives as a dependency, not as generated source'
        !output.keySet().any { it.endsWith('EmbeddedMongoConfig.groovy') }
        !output.containsKey('src/main/resources/META-INF/spring.factories')
    }

    void "test a SQL driver combined with MongoDB still adds Hibernate 5 as the default SQL implementation"() {
        given:
        Options options = new Options(DevelopmentReloading.DEFAULT_OPTION, GormImpl.MONGODB, ServletImpl.DEFAULT_OPTION, JdkVersion.DEFAULT_OPTION)

        when:
        Features features = getFeatures(['gorm-mongodb', 'postgres'], options)

        then:
        features.contains("gorm-mongodb")
        features.contains("postgres")
        features.contains("gorm-hibernate5")
        !features.contains("gorm-hibernate7")

        when:
        String template = new BuildBuilder(beanContext)
                .features(['gorm-mongodb', 'postgres'])
                .gormImpl(GormImpl.MONGODB)
                .render()

        then:
        template.contains("implementation \"org.apache.grails:grails-data-mongodb\"")
        template.contains("implementation \"org.apache.grails:grails-data-hibernate5\"")
        template.contains("runtimeOnly \"org.postgresql:postgresql\"")

        when:
        GeneratorContext ctx = buildGeneratorContext(['gorm-mongodb', 'postgres'], options)

        then:
        ctx.configuration.containsKey("grails.mongodb.url")
        ctx.configuration.get("dataSource.driverClassName") == 'org.postgresql.Driver'
    }

}
