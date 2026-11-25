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
package org.grails.forge.feature.view

import spock.lang.Unroll

import org.grails.forge.ApplicationContextSpec
import org.grails.forge.application.ApplicationType
import org.grails.forge.fixture.CommandOutputFixture

/**
 * The scaffolding feature in a REST API application and a REST API plugin, where the commands of the scaffolding
 * plugin generate a REST controller and JSON views: they do so where the application has the JSON views plugin
 * and no GSP, so the feature must not bring the GSP feature and its pages there as it does in a web application.
 */
class RestScaffoldingSpec extends ApplicationContextSpec implements CommandOutputFixture {

    @Unroll
    void "scaffolding can be selected for #type, which gets the plugin and JSON views and no GSP"() {
        when:
        Map<String, String> output = generate(type, ['scaffolding'])

        then: 'the plugin, whose commands the build lists'
        output['build.gradle'].contains('implementation "org.apache.grails:grails-scaffolding"')

        and: 'what the commands take for a REST API: the JSON views plugin, and no GSP in the views'
        output['build.gradle'].contains('implementation "org.apache.grails:grails-views-gson"')
        !output['build.gradle'].contains('"org.apache.grails:grails-gsp"')
        !output.keySet().any { it.startsWith('grails-app/views/') && it.endsWith('.gsp') }

        where:
        type << [ApplicationType.REST_API, ApplicationType.REST_API_PLUGIN]
    }

    @Unroll
    void "#type does not have scaffolding by default"() {
        expect:
        !generate(type, [])['build.gradle'].contains('grails-scaffolding')

        where:
        type << [ApplicationType.REST_API, ApplicationType.REST_API_PLUGIN]
    }

    @Unroll
    void "scaffolding still brings GSP and its pages to #type"() {
        when:
        Map<String, String> output = generate(type, ['scaffolding'])

        then:
        output['build.gradle'].contains('implementation "org.apache.grails:grails-scaffolding"')
        output['build.gradle'].contains('implementation "org.apache.grails:grails-gsp"')
        output.containsKey('grails-app/views/layouts/main.gsp')

        where:
        type << [ApplicationType.WEB, ApplicationType.WEB_PLUGIN]
    }

    void "a plugin without a web layer cannot have scaffolding"() {
        when:
        generate(ApplicationType.PLUGIN, ['scaffolding'])

        then:
        thrown(IllegalArgumentException)
    }
}
