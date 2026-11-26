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
package org.grails.forge.feature.publish

import org.grails.forge.ApplicationContextSpec
import org.grails.forge.application.ApplicationType
import org.grails.forge.feature.Feature
import org.grails.forge.feature.FeatureRegistry
import org.grails.forge.feature.FeatureSelectionException
import org.grails.forge.fixture.CommandOutputFixture
import spock.lang.Unroll

/**
 * The feature {@code grails-publish} sets a plugin up for publishing with the Grails Publish Gradle
 * plugin. It takes the place of the {@code maven-publish} feature the {@code plugin} profile had.
 */
class GrailsPublishSpec extends ApplicationContextSpec implements CommandOutputFixture {

    static final List<ApplicationType> PLUGINS = [ApplicationType.PLUGIN, ApplicationType.WEB_PLUGIN, ApplicationType.REST_API_PLUGIN]

    @Unroll
    void "the build of a #type.name with grails-publish applies the plugin, from the Grails BOM, and has the block to fill in"() {
        when:
        Map<String, String> output = generate(type, ['grails-publish'])
        String build = output['build.gradle']
        List<String> lines = build.readLines()

        then: 'the plugin is on the class path of the build script, in the version the BOM manages'
        lines.contains('        classpath platform("org.apache.grails:grails-bom:$grailsVersion")')
        lines.contains('        classpath "org.apache.grails.gradle:grails-publish"')

        and: 'it is applied after the Grails plugin, which it reads the plugin descriptor of'
        lines.indexOf('apply plugin: "org.apache.grails.gradle.grails-plugin"') >= 0
        lines.indexOf('apply plugin: "org.apache.grails.gradle.grails-publish"') > lines.indexOf('apply plugin: "org.apache.grails.gradle.grails-plugin"')

        and: 'the block has what the plugin cannot publish without, each marked as to fill in'
        build.contains('''\
grailsPublish {
    // TODO: the GitHub repository of the plugin, as owner/name. The links of the published POM are made from it.
    githubSlug = 'TODO-owner/TODO-repository'
    license {
        name = 'Apache-2.0'
    }
    // The title is the name of the project unless it is set here.
    // title = 'My Plugin'
    // TODO: one sentence that says what the plugin is for.
    desc = 'TODO: describe the plugin'
    // TODO: the developers of the plugin, each by an id and a name.
    developers = [todo: 'TODO: your name']
}
''')
        lines.indexOf('grailsPublish {') > lines.indexOf('apply plugin: "org.apache.grails.gradle.grails-publish"')

        and: 'no version of the plugin is written anywhere'
        !build.contains('org.apache.grails.gradle:grails-publish:')
        !output['gradle.properties'].contains('publish')

        where:
        type << PLUGINS
    }

    @Unroll
    void "the readme of a #type.name with grails-publish says how a snapshot and a release are published"() {
        when:
        String readme = generate(type, ['grails-publish'])['README.md']

        then:
        readme.contains('## Publishing the plugin')
        readme.contains('`./gradlew publishToMavenLocal`')
        readme.contains('`./gradlew publish`')
        readme.contains('`./gradlew publishToSonatype closeSonatypeStagingRepository`')

        and: 'the variables are the ones the Grails Publish plugin reads'
        ['GRAILS_PUBLISH_RELEASE', 'MAVEN_PUBLISH_URL', 'MAVEN_PUBLISH_USERNAME', 'MAVEN_PUBLISH_PASSWORD', 'NEXUS_PUBLISH_URL',
         'NEXUS_PUBLISH_USERNAME', 'NEXUS_PUBLISH_PASSWORD', 'NEXUS_PUBLISH_STAGING_PROFILE_ID', 'SIGNING_KEY', 'SIGNING_KEYRING',
         'SIGNING_PASSPHRASE'].every { readme.contains("`${it}`") }

        where:
        type << PLUGINS
    }

    @Unroll
    void "a #type.name has no publishing without the feature: it is not a default"() {
        when:
        Map<String, String> output = generate(type, [])

        then:
        !output['build.gradle'].contains('classpath "org.apache.grails.gradle:grails-publish"')
        !output['build.gradle'].contains('apply plugin: "org.apache.grails.gradle.grails-publish"')
        !output['build.gradle'].contains('grailsPublish')
        !output['README.md'].contains('Publishing the plugin')

        where:
        type << PLUGINS
    }

    @Unroll
    void "grails-publish is a feature of plugins: it is listed for a #type.name"() {
        when:
        Feature feature = beanContext.getBean(FeatureRegistry).find(type, 'grails-publish', true).orElse(null)

        then:
        feature != null
        feature.visible
        feature.category == 'Packaging'
        feature.title == 'Grails Publish'

        where:
        type << PLUGINS
    }

    @Unroll
    void "grails-publish is refused for a #type.name application"() {
        when:
        generate(type, ['grails-publish'])

        then:
        FeatureSelectionException e = thrown()
        e.message == 'The requested feature does not exist: grails-publish'

        and:
        !beanContext.getBean(FeatureRegistry).find(type, 'grails-publish', true).present

        where:
        type << [ApplicationType.WEB, ApplicationType.REST_API]
    }
}
