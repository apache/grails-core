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

package org.grails.forge.feature.lang

import org.grails.forge.ApplicationContextSpec
import org.grails.forge.application.ApplicationType
import org.grails.forge.fixture.CommandOutputFixture
import org.grails.forge.options.DevelopmentReloading
import org.grails.forge.options.JdkVersion
import org.grails.forge.options.Language
import org.grails.forge.options.Options
import org.grails.forge.options.TestFramework
import spock.lang.Unroll

class GrailsApplicationSpec extends ApplicationContextSpec implements CommandOutputFixture {

    @Unroll
    void 'Application file is generated for a #applicationType application for language: groovy'() {
        when:
        def output = generate(applicationType,
                new Options(DevelopmentReloading.DEVTOOLS),
                [])

        then:
        output.containsKey("grails-app/init/example/grails/Application.${Language.GROOVY.extension}".toString())
        def applicationGroovyFile = output.get("grails-app/init/example/grails/Application.${Language.GROOVY.extension}".toString())
        applicationGroovyFile.contains("@CompileStatic")
        !applicationGroovyFile.contains("@PluginSource")

        where:
        applicationType << [ApplicationType.WEB, ApplicationType.REST_API]
    }

    void 'Application file is generated with annotation @PluginSource for #applicationType application type'() {
        when:
        def output = generate(applicationType,
                new Options(DevelopmentReloading.DEVTOOLS),
                [])

        then:
        output.containsKey("grails-app/init/example/grails/Application.${Language.GROOVY.extension}".toString())
        def applicationGroovyFile = output.get("grails-app/init/example/grails/Application.${Language.GROOVY.extension}".toString())
        applicationGroovyFile.contains("@PluginSource")

        where:
        applicationType << [ApplicationType.PLUGIN, ApplicationType.WEB_PLUGIN, ApplicationType.REST_API_PLUGIN]
    }

    void "REST-API application should have ApplicationController"() {
        when:
        def output = generate(ApplicationType.REST_API)

        then:
        output.containsKey("grails-app/controllers/example/grails/ApplicationController.groovy")
    }

    void "REST-API plugin has no controller, no views and no excludes for them"() {
        when:
        def output = generate(ApplicationType.REST_API_PLUGIN)

        then:
        !output.containsKey("grails-app/controllers/example/grails/ApplicationController.groovy")
        !output.keySet().any { it.startsWith('grails-app/views/') }
        !output.get('src/main/groovy/example/grails/FooGrailsPlugin.groovy').contains('grails-app/views')
    }

    void "PluginDescriptor is generated for #applicationType application type"() {
        when:
        def output = generate(applicationType)

        then:
        output.containsKey('src/main/groovy/example/grails/FooGrailsPlugin.groovy')
        def pluginGroovy = output.get("src/main/groovy/example/grails/FooGrailsPlugin.groovy")

        where:
        applicationType << [ApplicationType.PLUGIN, ApplicationType.WEB_PLUGIN, ApplicationType.REST_API_PLUGIN]
    }

    void "the plugin descriptor of a #applicationType excludes the error view the project has from packaging: #view"() {
        when:
        def descriptor = generate(applicationType).get('src/main/groovy/example/grails/FooGrailsPlugin.groovy')

        then:
        descriptor.contains("def pluginExcludes = [\n           \"grails-app/views/${view}\"\n       ]")

        where:
        applicationType            | view
        ApplicationType.PLUGIN     | 'error.gsp'
        ApplicationType.WEB_PLUGIN | 'error.gsp'
    }

    void "the plugin descriptor of a REST API plugin excludes nothing, as the project has no view"() {
        when:
        def descriptor = generate(ApplicationType.REST_API_PLUGIN).get('src/main/groovy/example/grails/FooGrailsPlugin.groovy')

        then:
        descriptor.contains('def pluginExcludes = []')
        !descriptor.contains('.gsp')
        !descriptor.contains('.gson')
    }

    void "PluginDescriptor is NOT generated for #applicationType application type"() {
        when:
        def output = generate(applicationType)

        then:
        !output.containsKey('src/main/groovy/example/grails/FooGrailsPlugin.groovy')

        where:
        applicationType << [ApplicationType.WEB, ApplicationType.REST_API]
    }
  
    void "ApplicationController is not present for #applicationType application type"() {
        when:
        def output = generate(applicationType)

        then:
        !output.containsKey("grails-app/controllers/example/grails/ApplicationController.groovy")

        where:
        applicationType << [ApplicationType.WEB, ApplicationType.PLUGIN, ApplicationType.WEB_PLUGIN]
    }

    void "test build plugins"() {
        given:
        final def output = generate(ApplicationType.WEB, new Options(DevelopmentReloading.DEVTOOLS))
        def buildGradle = output['build.gradle']

        expect:
        buildGradle.contains("war")
    }

    @Unroll
    void "test BootStrap.groovy is present for application type #applicationType"() {

        when:
        def output = generate(applicationType)

        then:
        output.containsKey("grails-app/init/example/grails/BootStrap.groovy")

        where:
        applicationType << ApplicationType.values().toList()

    }
}
