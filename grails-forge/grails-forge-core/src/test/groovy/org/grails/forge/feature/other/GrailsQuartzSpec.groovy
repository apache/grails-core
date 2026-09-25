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

import groovy.yaml.YamlSlurper
import org.grails.forge.ApplicationContextSpec
import org.grails.forge.BuildBuilder
import org.grails.forge.application.ApplicationType
import org.grails.forge.feature.Features
import org.grails.forge.fixture.CommandOutputFixture
import org.grails.forge.options.DevelopmentReloading
import org.grails.forge.options.Options

class GrailsQuartzSpec extends ApplicationContextSpec implements CommandOutputFixture {

    void "test grails-quartz feature"() {
        when:
        final Features features = getFeatures([GrailsQuartz.FEATURE_NAME])

        then:
        features.contains(GrailsQuartz.FEATURE_NAME)
    }

    void "test grails-quartz dependency version is managed by the grails-bom"() {
        when:
        final String template = new BuildBuilder(beanContext)
                .features([GrailsQuartz.FEATURE_NAME])
                .render()

        then:
        template.contains('implementation "org.apache.grails:grails-quartz"')
        !template.contains('org.apache.grails:grails-quartz:')
    }

    void "test grails-quartz enables quartz auto startup"() {
        when:
        final def output = generate(ApplicationType.WEB, new Options(DevelopmentReloading.DEVTOOLS), [GrailsQuartz.FEATURE_NAME])
        final def config = new YamlSlurper().parseText(output["grails-app/conf/application.yml"])

        then:
        config['quartz.autoStartup'] == true
    }
}
