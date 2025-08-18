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

package org.grails.forge.feature.view.json

import org.grails.forge.ApplicationContextSpec
import org.grails.forge.BuildBuilder
import org.grails.forge.application.ApplicationType
import org.grails.forge.application.generator.GeneratorContext
import org.grails.forge.feature.Features
import org.grails.forge.fixture.CommandOutputFixture
import org.grails.forge.options.JdkVersion
import org.grails.forge.options.Options
import org.grails.forge.options.TestFramework
import spock.lang.Unroll

class ViewJsonSpec extends ApplicationContextSpec implements CommandOutputFixture {

    void "test views-json feature"() {
        when:
        final Features features = getFeatures(["views-json"])

        then:
        features.contains("grails-web")
        features.contains("views-json")
    }

    void "test dependencies are present for Gradle"() {
        when:
        final String template = new BuildBuilder(beanContext)
                .features(["views-json"])
                .render()

        then:
        template.contains("apply plugin: \"org.apache.grails.gradle.grails-web\"")
        template.contains("apply plugin: \"org.apache.grails.gradle.grails-gson\"")
        template.contains("implementation \"org.apache.grails:grails-views-gson\"")
        template.contains("implementation \"org.apache.grails:grails-data-mongodb-gson-templates\"")
        template.contains("testImplementation \"org.apache.grails:grails-testing-support-views-gson\"")
    }

    void "test default gson views are present"() {
        when:
        final def output = generate(ApplicationType.REST_API, new Options(TestFramework.SPOCK))

        then:
        output.containsKey("grails-app/views/application/index.gson")
        output.containsKey("grails-app/views/error.gson")
        output.containsKey("grails-app/views/notFound.gson")
        output.containsKey("grails-app/views/errors/_errors.gson")
        output.containsKey("grails-app/views/object/_object.gson")
    }

    @Unroll
    void "test views-json gradle plugins and dependencies are present for #applicationType application"() {
        when:
        final def output = generate(applicationType, new Options(TestFramework.SPOCK))
        final String build = output['build.gradle']

        then:
        build.contains("apply plugin: \"org.apache.grails.gradle.grails-web\"")
        build.contains("apply plugin: \"org.apache.grails.gradle.grails-gson\"")
        build.contains("implementation \"org.apache.grails:grails-views-gson\"")
        build.contains("implementation \"org.apache.grails:grails-data-mongodb-gson-templates\"")
        build.contains("testImplementation \"org.apache.grails:grails-testing-support-views-gson\"")

        where:
        applicationType << [ApplicationType.REST_API]
    }

    @Unroll
    void "test views-json gradle plugins and dependencies are NOT present for #applicationType application"() {
        when:
        final def output = generate(applicationType, new Options(TestFramework.SPOCK))
        final String build = output['build.gradle']

        then:
        !build.contains("apply plugin: \"org.apache.grails.gradle.grails-gson\"")
        !build.contains("implementation \"org.apache.grails:grails-views-gson\"")
        !build.contains("implementation \"org.apache.grails:grails-data-mongodb-gson-templates\"")
        !build.contains("testImplementation \"org.apache.grails:grails-testing-support-views-gson\"")

        where:
        applicationType << [ApplicationType.WEB, ApplicationType.WEB_PLUGIN, ApplicationType.PLUGIN]
    }

    void "test mime configurations"() {
        when:
        final GeneratorContext ctx = buildGeneratorContext(["views-json"], new Options(null), ApplicationType.REST_API)

        then:
        ctx.getConfiguration().get("grails.mime.disable.accept.header.userAgents") == Arrays.asList("Gecko", "WebKit", "Presto", "Trident")
        ctx.getConfiguration().get("grails.mime.types.json") == Arrays.asList("application/json", "text/json")
        ctx.getConfiguration().get("grails.mime.types.hal") == Arrays.asList("application/hal+json", "application/hal+xml")
        ctx.getConfiguration().get("grails.mime.types.xml") == Arrays.asList("text/xml", "application/xml")
        ctx.getConfiguration().get("grails.mime.types.atom") == "application/atom+xml"
        ctx.getConfiguration().get("grails.mime.types.css") == "text/css"
        ctx.getConfiguration().get("grails.mime.types.csv") == "text/csv"
        ctx.getConfiguration().get("grails.mime.types.js") == "text/javascript"
        ctx.getConfiguration().get("grails.mime.types.rss") == "application/rss+xml"
        ctx.getConfiguration().get("grails.mime.types.text") == "text/plain"
        ctx.getConfiguration().get("grails.mime.types.all") == "*/*"
        !ctx.getConfiguration().containsKey("grails.mime.types.multipartForm")
        !ctx.getConfiguration().containsKey("grails.mime.types.form")
        !ctx.getConfiguration().containsKey("grails.mime.types.html")
        !ctx.getConfiguration().containsKey("grails.mime.types.pdf")
    }
}
