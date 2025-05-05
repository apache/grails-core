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

package org.grails.forge.feature.assetPipeline

import org.grails.forge.ApplicationContextSpec
import org.grails.forge.BuildBuilder
import org.grails.forge.application.ApplicationType
import org.grails.forge.feature.Features
import org.grails.forge.fixture.CommandOutputFixture
import org.grails.forge.options.JdkVersion
import org.grails.forge.options.Options
import org.grails.forge.options.TestFramework
import spock.lang.Unroll

class AssetPipelineSpec extends ApplicationContextSpec implements CommandOutputFixture {

    void "test asset-pipeline-grails feature"() {
        when:
        final Features features = getFeatures(["asset-pipeline-grails"])

        then:
        features.contains("asset-pipeline-grails")
    }

    void "test dependencies are present for gradle"() {
        when:
        final String template = new BuildBuilder(beanContext)
                .features(["asset-pipeline-grails"])
                .render()

        then:
        template.contains("apply plugin: \"asset-pipeline\"")
        template.contains("runtimeOnly \"com.bertramlabs.plugins:asset-pipeline-grails\"")
        template.contains('''
assets {
    excludes = [
            'webjars/jquery/**',
            'webjars/bootstrap/**',
            'webjars/bootstrap-icons/**'
    ]
    includes = [
            'webjars/jquery/*/dist/jquery.js',
            'webjars/bootstrap/*/dist/js/bootstrap.bundle.js',
            'webjars/bootstrap/*/dist/css/bootstrap.css',
            'webjars/bootstrap-icons/*/font/bootstrap-icons.css',
            'webjars/bootstrap-icons/*/font/fonts/*',
    ]
}''')
    }

    void "test extension packagePlugin is set for application #applicationType"() {
        when:
        final String template = new BuildBuilder(beanContext)
                .applicationType(applicationType)
                .features(["asset-pipeline-grails"])
                .render()

        then:
        template.contains('''
assets {
    packagePlugin = true
    excludes = [
            'webjars/jquery/**',
            'webjars/bootstrap/**',
            'webjars/bootstrap-icons/**'
    ]
    includes = [
            'webjars/jquery/*/dist/jquery.js',
            'webjars/bootstrap/*/dist/js/bootstrap.bundle.js',
            'webjars/bootstrap/*/dist/css/bootstrap.css',
            'webjars/bootstrap-icons/*/font/bootstrap-icons.css',
            'webjars/bootstrap-icons/*/font/fonts/*',
    ]
}''')
        where:
        applicationType << [ApplicationType.WEB_PLUGIN]
    }

    void "test assets files are present"() {
        given:
        final Map<String, String> output = generate(ApplicationType.WEB, new Options(TestFramework.SPOCK))

        expect:
        output.containsKey("grails-app/assets/images/advancedgrails.svg")
        output.containsKey("grails-app/assets/images/apple-touch-icon.png")
        output.containsKey("grails-app/assets/images/apple-touch-icon-retina.png")
        output.containsKey("grails-app/assets/images/documentation.svg")
        output.containsKey("grails-app/assets/images/favicon.ico")
        output.containsKey("grails-app/assets/images/grails.svg")
        output.containsKey("grails-app/assets/images/grails-cupsonly-logo-white.svg")
        output.containsKey("grails-app/assets/images/slack.svg")
        output.containsKey("grails-app/assets/javascripts/application.js")
        output.containsKey("grails-app/assets/stylesheets/application.css")
        output.containsKey("grails-app/assets/stylesheets/errors.css")
        output.containsKey("grails-app/assets/stylesheets/grails.css")
    }

    @Unroll
    void "test feature asset-pipeline-grails is not supported for #applicationType application"(ApplicationType applicationType) {
        when:
        generate(applicationType, new Options(TestFramework.SPOCK), ["asset-pipeline-grails"])

        then:
        def e = thrown(IllegalArgumentException)
        e.message == 'The requested feature does not exist: asset-pipeline-grails'

        where:
        applicationType << [ApplicationType.PLUGIN, ApplicationType.REST_API]
    }
}
