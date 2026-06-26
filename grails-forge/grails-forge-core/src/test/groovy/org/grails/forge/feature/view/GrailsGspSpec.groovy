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


import org.grails.forge.ApplicationContextSpec
import org.grails.forge.BuildBuilder
import org.grails.forge.application.ApplicationType
import org.grails.forge.application.generator.GeneratorContext
import org.grails.forge.feature.Features
import org.grails.forge.fixture.CommandOutputFixture
import org.grails.forge.options.DevelopmentReloading
import org.grails.forge.options.Options
import spock.lang.Unroll

class GrailsGspSpec extends ApplicationContextSpec implements CommandOutputFixture {

    void "test gsp feature"() {
        when:
        final Features features = getFeatures(["gsp"])

        then:
        features.contains("grails-web")
        features.contains("gsp")
    }

    void "test dependencies are present for Gradle"() {
        when:
        final String template = new BuildBuilder(beanContext)
            .features(["gsp"])
            .render()

        then:
        template.contains("apply plugin: \"org.apache.grails.gradle.grails-web\"")
        template.contains("apply plugin: \"org.apache.grails.gradle.grails-gsp\"")
        template.contains("implementation \"org.apache.grails:grails-gsp\"")
    }

    void "test gsp configuration"() {
        when:
        final GeneratorContext ctx = buildGeneratorContext(["gsp"])

        then:
        ctx.getConfiguration().containsKey("grails.views.gsp.encoding")
        ctx.getConfiguration().containsKey("grails.views.gsp.htmlcodec")
        ctx.getConfiguration().containsKey("grails.views.gsp.codecs.scriptlet")
    }

    void "test mime types are not written to config as they are now framework defaults"() {
        when:
        final GeneratorContext ctx = buildGeneratorContext(["gsp"])

        then: "the generated application relies on the framework provided MimeType defaults"
        ctx.getConfiguration().keySet().every { !it.toString().startsWith("grails.mime.types") }
    }

    void "test default views are present"() {
        when:
        final def output = generate(ApplicationType.WEB, new Options(DevelopmentReloading.DEVTOOLS))
        
        then:
        output.containsKey("grails-app/views/index.gsp")
        output.containsKey("grails-app/views/error.gsp")
        output.containsKey("grails-app/views/notFound.gsp")
    }

    @Unroll
    void "test grails-gsp gradle plugins and dependencies are present for #applicationType application"() {
        when:
        final def output = generate(applicationType, new Options(DevelopmentReloading.DEVTOOLS))
        final String build = output['build.gradle']

        then:
        build.contains('apply plugin: "org.apache.grails.gradle.grails-web"')
        build.contains('apply plugin: "org.apache.grails.gradle.grails-gsp"')
        build.contains("implementation \"org.apache.grails:grails-gsp\"")

        where:
        applicationType << [ApplicationType.WEB]
    }

    @Unroll
    void "test grails-plugin gradle plugins and dependencies are present for #applicationType application"() {
        when:
        final def output = generate(applicationType, new Options(DevelopmentReloading.DEVTOOLS))
        final String build = output['build.gradle']

        then:
        build.contains('apply plugin: "org.apache.grails.gradle.grails-plugin"')
        build.contains('apply plugin: "org.apache.grails.gradle.grails-gsp"')
        build.contains("implementation \"org.apache.grails:grails-gsp\"")

        where:
        applicationType << [ApplicationType.WEB_PLUGIN]
    }

    @Unroll
    void "test grails-gsp gradle plugins and dependencies are NOT present for #applicationType application"() {
        when:
        final def output = generate(applicationType, new Options(DevelopmentReloading.DEVTOOLS))
        final String build = output['build.gradle']

        then:
        !build.contains('id "org.apache.grails.gradle.grails-gsp"')
        !build.contains("implementation \"org.apache.grails:grails-gsp\"")

        where:
        applicationType << [ApplicationType.PLUGIN, ApplicationType.REST_API]
    }

}
