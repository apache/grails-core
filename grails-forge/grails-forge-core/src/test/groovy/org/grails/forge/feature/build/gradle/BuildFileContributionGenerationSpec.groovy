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

package org.grails.forge.feature.build.gradle

import org.grails.forge.ApplicationContextSpec
import org.grails.forge.application.ApplicationType
import org.grails.forge.application.generator.GeneratorContext
import org.grails.forge.build.gradle.BuildFileAnchor
import org.grails.forge.build.gradle.GradleBuild
import org.grails.forge.build.gradle.GradleBuildCreator
import org.grails.forge.build.gradle.GradlePlugin
import org.grails.forge.fixture.CommandOutputFixture
import org.grails.forge.fixture.ContextFixture
import org.grails.forge.fixture.ProjectFixture
import org.grails.forge.options.DevelopmentReloading
import org.grails.forge.options.Options
import org.grails.forge.template.Writable

class BuildFileContributionGenerationSpec extends ApplicationContextSpec implements ContextFixture, ProjectFixture, CommandOutputFixture {

    void "the blocks of the features are in the build file, in the order the features are applied"() {
        when:
        String buildGradle = generate(ApplicationType.WEB, new Options(DevelopmentReloading.JREBEL),
                ['grails-compile-static', 'geb-with-local-browsers', 'openapi'])['build.gradle']
        List<String> blocks = ['assets {', 'grails {', "tasks.named('bootRun') {", 'tasks.withType(Test).configureEach {']
        List<Integer> positions = blocks.collect { buildGradle.indexOf('\n' + it + '\n') }

        then:
        blocks.every { buildGradle.count('\n' + it + '\n') == 1 }
        positions == positions.toSorted()

        and: 'after the dependencies and the Java release'
        buildGradle.indexOf('compileJava.options.release') < positions.first()

        and: 'with what the other selected features add to them'
        buildGradle.contains("            'webjars/swagger-ui/**',\n")
        buildGradle.contains('        tagLibs = true\n        gsp = true\n    }\n}\n')
        buildGradle.contains("    useJUnitPlatform()\n    def gebEnv = providers.systemProperty('geb.env')\n")

        and: 'each as a whole block, with one empty line between two of them and after the last'
        buildGradle.contains('''\
            }

            tasks.named('bootRun') {
                dependsOn(generateRebel)
                if (project.hasProperty("rebelAgent")) {
                    jvmArgs(rebelAgent)
                }
            }

            tasks.withType(Test).configureEach {
            '''.stripIndent())
        buildGradle.endsWith('    }\n}\n\n')
        !buildGradle.contains('\n\n\n')
    }

    void "a block a feature adds after the plugins comes before the repositories"() {
        when:
        String buildGradle = generate(['asciidoctor'])['build.gradle']

        then:
        buildGradle.contains('\n\napply from: "gradle/asciidoc.gradle"\n\nrepositories {\n')
    }

    void "a build file without those features has none of their blocks"() {
        when:
        String buildGradle = generate(ApplicationType.REST_API, new Options(DevelopmentReloading.NONE), [])['build.gradle']

        then:
        !buildGradle.contains('assets {')
        !buildGradle.contains('grails {')
        !buildGradle.contains('bootRun')
        !buildGradle.contains('apply from:')
        !buildGradle.contains('gebEnv')
        buildGradle.contains('tasks.withType(Test).configureEach {\n    useJUnitPlatform()\n}\n')
    }

    void "a web plugin packages its assets"() {
        expect:
        generate(ApplicationType.WEB_PLUGIN, [])['build.gradle'].contains('assets {\n    packagePlugin = true\n    excludes = [\n')
    }

    void "what a second contributor adds to an extension lands in the same block"() {
        given:
        GeneratorContext context = buildGeneratorContext([]) { GeneratorContext generatorContext ->
            generatorContext.buildContributions.extension('assets', 'minifyJs = false')
            generatorContext.buildContributions.block(BuildFileAnchor.END, 'println "configured"')
        }

        when:
        GradleBuild build = beanContext.getBean(GradleBuildCreator).create(context)
        String extensions = build.renderContributedExtensions()

        then:
        extensions.count('assets {') == 1
        extensions.startsWith('assets {\n    minifyJs = false\n    excludes = [\n')
        build.renderBlocks(BuildFileAnchor.END) == 'println "configured"\n\n'
    }

    void "blocks at every anchor and the extensions of plugins are in the build file, in their places"() {
        given:
        GeneratorContext context = buildGeneratorContext([]) { GeneratorContext generatorContext ->
            generatorContext.buildContributions.block(BuildFileAnchor.AFTER_PLUGINS, 'ext.first = 1')
            generatorContext.buildContributions.block(BuildFileAnchor.AFTER_DEPENDENCIES, 'ext.second = 2')
            generatorContext.buildContributions.block(BuildFileAnchor.END, 'ext.fourth = 4')
            generatorContext.addBuildPlugin(GradlePlugin.builder().id('com.acme.audit')
                    .extension({ OutputStream out -> out.write('acmeAudit {\n    third = 3\n}\n'.bytes) } as Writable)
                    .build())
        }

        when:
        String buildGradle = generate(ApplicationType.WEB, context)['build.gradle']
        List<String> landmarks = ['group = ', 'ext.first = 1\n\nrepositories {', 'compileJava.options.release',
                                  'ext.second = 2\n\nassets {', 'tasks.withType(Test).configureEach {',
                                  'acmeAudit {', 'ext.fourth = 4\n\n']
        List<Integer> positions = landmarks.collect { buildGradle.indexOf(it) }

        then:
        positions.every { it >= 0 }
        positions == positions.toSorted()
        buildGradle.endsWith('ext.fourth = 4\n\n')
    }

    void "a contribution made once the build file is rendered is an error"() {
        given:
        GeneratorContext context = buildGeneratorContext([])

        when:
        context.buildContributions.extension('assets', 'minifyJs = false')

        then:
        thrown(IllegalStateException)
    }

    void "applying the features of a context a second time does not apply them again"() {
        given:
        GeneratorContext context = buildGeneratorContext([])
        int templates = context.templates.size()

        when:
        context.applyFeatures()

        then:
        noExceptionThrown()
        context.templates.size() == templates

        and:
        generate(ApplicationType.WEB, context)['build.gradle'].count('\nassets {\n') == 1
    }
}
