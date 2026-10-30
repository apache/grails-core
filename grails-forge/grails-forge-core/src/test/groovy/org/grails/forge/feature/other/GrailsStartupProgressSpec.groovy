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

package org.grails.forge.feature.other

import org.grails.forge.ApplicationContextSpec
import org.grails.forge.BuildBuilder
import org.grails.forge.application.ApplicationType
import org.grails.forge.feature.Category
import org.grails.forge.feature.Features
import org.grails.forge.fixture.CommandOutputFixture
import org.grails.forge.options.DevelopmentReloading
import org.grails.forge.options.Options

class GrailsStartupProgressSpec extends ApplicationContextSpec implements CommandOutputFixture {

    void "test grails-startup-progress feature"() {
        when:
        final Features features = getFeatures([GrailsStartupProgress.FEATURE_NAME])

        then:
        features.contains(GrailsStartupProgress.FEATURE_NAME)
    }

    void "test grails-startup-progress is a development tool"() {
        expect:
        beanContext.getBean(GrailsStartupProgress).category == Category.DEV_TOOLS
    }

    void "test grails-startup-progress dependency version is managed by the grails-bom"() {
        when:
        final String template = new BuildBuilder(beanContext)
                .features([GrailsStartupProgress.FEATURE_NAME])
                .render()

        then:
        template.contains('implementation "org.apache.grails:grails-startup-progress"')
        !template.contains('org.apache.grails:grails-startup-progress:')
    }

    void "test an application generated with grails-startup-progress depends on it"(ApplicationType applicationType) {
        when:
        final String buildGradle = generate(applicationType, new Options(DevelopmentReloading.DEVTOOLS), [GrailsStartupProgress.FEATURE_NAME])['build.gradle']

        then:
        buildGradle.contains('implementation "org.apache.grails:grails-startup-progress"')

        where:
        applicationType << [ApplicationType.WEB, ApplicationType.REST_API]
    }

    void "test an application generated without grails-startup-progress does not depend on it"(ApplicationType applicationType) {
        when:
        final String buildGradle = generate(applicationType, new Options(DevelopmentReloading.DEVTOOLS))['build.gradle']

        then:
        !buildGradle.contains('grails-startup-progress')

        where:
        applicationType << ApplicationType.values()
    }

    void "test grails-startup-progress is not offered to plugins, whose dependencies reach every application using them"(ApplicationType applicationType) {
        when:
        generate(applicationType, new Options(DevelopmentReloading.DEVTOOLS), [GrailsStartupProgress.FEATURE_NAME])

        then:
        def e = thrown(IllegalArgumentException)
        e.message == 'The requested feature does not exist: grails-startup-progress'

        where:
        applicationType << [ApplicationType.PLUGIN, ApplicationType.WEB_PLUGIN]
    }
}
