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
import org.grails.forge.feature.Category
import org.grails.forge.feature.Features
import org.grails.forge.fixture.CommandOutputFixture
import org.grails.forge.options.DevelopmentReloading
import org.grails.forge.options.GormImpl
import org.grails.forge.options.JdkVersion
import org.grails.forge.options.Options
import org.grails.forge.options.ServletImpl

class AsyncGormSpec extends ApplicationContextSpec implements CommandOutputFixture {

    void "test gorm-async feature is registered"() {
        when:
        Features features = getFeatures(['gorm-async'])

        then:
        features.contains('gorm-async')
    }

    void "test gorm-async defaults to gorm-hibernate5 when no GORM impl is selected"() {
        when:
        Features features = getFeatures(['gorm-async'])

        then: 'async alone falls back to Hibernate as the GORM impl'
        features.contains('gorm-async')
        features.contains('gorm-hibernate5')
    }

    void "test gorm-async is selectable alongside gorm-mongodb"() {
        given: 'a project that targets MongoDB rather than the default Hibernate'
        Options options = new Options(DevelopmentReloading.DEFAULT_OPTION,
                GormImpl.MONGODB,
                ServletImpl.DEFAULT_OPTION,
                JdkVersion.DEFAULT_OPTION)

        when:
        Features features = getFeatures(['gorm-async', 'gorm-mongodb'], options)

        then: 'async is layered on top of MongoDB without forcing Hibernate'
        features.contains('gorm-async')
        features.contains('gorm-mongodb')
        !features.contains('gorm-hibernate5')
    }

    void "test gorm-async category is Database"() {
        when:
        def feature = beanContext.getBean(AsyncGorm)

        then:
        feature.category == Category.DATABASE
    }

    void "test gorm-async supports all application types"() {
        when:
        def feature = beanContext.getBean(AsyncGorm)

        then:
        ApplicationType.values().every { feature.supports(it) }
    }

    void "test grails-datamapping-async dependency is present in gradle build"() {
        when:
        String template = new BuildBuilder(beanContext)
                .features(['gorm-async'])
                .render()

        then:
        template.contains('implementation "org.apache.grails:grails-datamapping-async"')
    }
}
