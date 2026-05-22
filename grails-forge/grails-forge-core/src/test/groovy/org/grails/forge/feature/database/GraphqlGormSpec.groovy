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
import org.grails.forge.application.ApplicationType
import org.grails.forge.application.generator.GeneratorContext
import org.grails.forge.feature.Category
import org.grails.forge.feature.Features
import org.grails.forge.fixture.CommandOutputFixture
import org.grails.forge.options.DevelopmentReloading
import org.grails.forge.options.GormImpl
import org.grails.forge.options.JdkVersion
import org.grails.forge.options.Options
import org.grails.forge.options.ServletImpl

class GraphqlGormSpec extends ApplicationContextSpec implements CommandOutputFixture {

    void "test gorm-graphql feature is registered"() {
        when:
        Features features = getFeatures(['gorm-graphql'])

        then:
        features.contains('gorm-graphql')
    }

    void "test gorm-graphql defaults to gorm-hibernate5 when no GORM impl is selected"() {
        when:
        Features features = getFeatures(['gorm-graphql'])

        then: 'GraphQL alone falls back to Hibernate as the GORM impl'
        features.contains('gorm-graphql')
        features.contains('gorm-hibernate5')
    }

    void "test gorm-graphql is selectable alongside gorm-mongodb"() {
        given: 'a project that targets MongoDB rather than the default Hibernate'
        Options options = new Options(DevelopmentReloading.DEFAULT_OPTION,
                GormImpl.MONGODB,
                ServletImpl.DEFAULT_OPTION,
                JdkVersion.DEFAULT_OPTION)

        when:
        Features features = getFeatures(['gorm-graphql', 'gorm-mongodb'], options)

        then: 'GraphQL is layered on top of MongoDB without forcing Hibernate'
        features.contains('gorm-graphql')
        features.contains('gorm-mongodb')
        !features.contains('gorm-hibernate5')
    }

    void "test gorm-graphql category is API"() {
        when:
        def feature = beanContext.getBean(GraphqlGorm)

        then:
        feature.category == Category.API
    }

    void "test gorm-graphql supports WEB and REST_API but not plugins"() {
        when:
        def feature = beanContext.getBean(GraphqlGorm)

        then:
        feature.supports(ApplicationType.WEB)
        feature.supports(ApplicationType.REST_API)
        !feature.supports(ApplicationType.WEB_PLUGIN)
        !feature.supports(ApplicationType.PLUGIN)
    }

    void "test grails-data-graphql dependency is present in gradle build"() {
        when:
        String template = new BuildBuilder(beanContext)
                .features(['gorm-graphql'])
                .render()

        then:
        template.contains('implementation "org.apache.grails:grails-data-graphql"')
    }

    void "test GraphqlGorm.apply does not write GraphQL-specific configuration"() {
        when: 'no extra config keys should be required - the plugin auto-configures itself'
        GeneratorContext ctx = buildGeneratorContext(['gorm-graphql'])

        then:
        !ctx.configuration.containsKey('grails.gorm.graphql')
    }
}
