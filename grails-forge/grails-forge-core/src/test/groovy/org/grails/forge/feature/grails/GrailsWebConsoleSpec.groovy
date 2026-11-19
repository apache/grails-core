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

package org.grails.forge.feature.grails

import org.grails.forge.ApplicationContextSpec
import org.grails.forge.BuildBuilder
import org.grails.forge.application.generator.GeneratorContext
import org.apache.grails.forge.core.pack.PackFeature
import org.grails.forge.application.ApplicationType
import org.grails.forge.feature.FeatureRegistry
import org.grails.forge.feature.Features
import org.grails.forge.fixture.CommandOutputFixture

class GrailsWebConsoleSpec extends ApplicationContextSpec implements CommandOutputFixture {

    void "test grails-web-console feature"() {
        when:
        Features features = getFeatures(["grails-web-console"])

        then:
        features.contains("grails-web-console")
    }

    void "test dependency is present for gradle"() {
        when:
        final String template = new BuildBuilder(beanContext)
                .features(["grails-web-console"])
                .render()

        then:
        template.contains("runtimeOnly \"org.grails.plugins:grails-web-console")
    }

    void "test config"() {
        when:
        GeneratorContext ctx = buildGeneratorContext([ "grails-web-console"])

        then:
        ctx.configuration.containsKey("environments.production.grails.plugin.console.enabled")
        ctx.configuration.containsKey("environments.production.grails.plugin.console.fileStore.remote.enabled")
    }

    void "the feature is a pack of Forge and gives what the compiled feature gave"() {
        when:
        Map<String, String> output = generate(["grails-web-console"])

        then: 'the version of the plugin is the one Forge knows, which the pack does not name'
        output['build.gradle'] =~ /(?m)^    runtimeOnly "org\.grails\.plugins:grails-web-console:\d[^"]*"$/
        output['README.md'].contains('## Feature grails-web-console documentation\n\n' +
                '- [Grails Web Console documentation](https://github.com/grails-plugins/grails-web-console/#readme)\n')
        output['grails-app/conf/application.yml'].contains(
                '      plugin:\n        console:\n          enabled: false\n          fileStore:\n            remote:\n              enabled: false')

        and:
        beanContext.getBean(FeatureRegistry).require(ApplicationType.WEB, 'grails-web-console') instanceof PackFeature
    }
}
