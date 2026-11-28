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

package org.grails.forge.application.generator

import org.grails.forge.ApplicationContextSpec
import org.grails.forge.build.dependencies.Dependency
import org.grails.forge.build.gradle.GradleBuild
import org.grails.forge.build.gradle.GradleBuildCreator
import org.grails.forge.fixture.ContextFixture
import org.grails.forge.fixture.ProjectFixture

class GeneratorContextDependencySpec extends ApplicationContextSpec implements ContextFixture, ProjectFixture {

    void "the exclusions of a dependency reach the dependencies of the build"() {
        given:
        GeneratorContext context = buildGeneratorContext([]) { GeneratorContext it ->
            it.addDependency(Dependency.builder().groupId('com.acme').artifactId('acme-audit').implementation()
                    .exclude('org.slf4j', 'slf4j-simple'))
        }

        when:
        GradleBuild build = beanContext.getBean(GradleBuildCreator).create(context)

        then:
        build.dependencies*.toSnippet().contains(
                'implementation("com.acme:acme-audit") { exclude group: "org.slf4j", module: "slf4j-simple" }')
    }

    void "the exclusions of a dependency whose coordinates are looked up are kept"() {
        given:
        GeneratorContext context = buildGeneratorContext([]) { GeneratorContext it ->
            it.addDependency(Dependency.builder().lookupArtifactId('grails-web-console').implementation()
                    .exclude('org.slf4j', null))
        }

        expect:
        context.dependencies.find { it.artifactId == 'grails-web-console' }.exclusions*.toString() == ['org.slf4j']
    }

    void "the exclusions of a buildscript dependency reach the classpath of the build"() {
        given:
        GeneratorContext context = buildGeneratorContext([]) { GeneratorContext it ->
            it.addBuildscriptDependency(Dependency.builder().groupId('com.acme').artifactId('acme-gradle').buildSrc()
                    .exclude('org.slf4j', 'slf4j-simple'))
        }

        when:
        GradleBuild build = beanContext.getBean(GradleBuildCreator).create(context)

        then:
        build.buildscriptDependencies*.toSnippet().contains(
                'classpath("com.acme:acme-gradle") { exclude group: "org.slf4j", module: "slf4j-simple" }')
    }

    void "a dependency declared twice with the same exclusions is declared once"() {
        when:
        GeneratorContext context = buildGeneratorContext([]) { GeneratorContext generatorContext ->
            2.times {
                generatorContext.addDependency(Dependency.builder().groupId('com.acme').artifactId('acme-audit').implementation()
                        .exclude('org.slf4j', 'slf4j-simple'))
            }
        }

        then:
        context.dependencies.count { it.artifactId == 'acme-audit' } == 1
    }

    void "a dependency declared twice with different exclusions is an error"() {
        when:
        buildGeneratorContext([]) { GeneratorContext it ->
            it.addDependency(Dependency.builder().groupId('com.acme').artifactId('acme-audit').implementation())
            it.addDependency(Dependency.builder().groupId('com.acme').artifactId('acme-audit').implementation()
                    .exclude('org.slf4j', 'slf4j-simple'))
        }

        then:
        IllegalArgumentException e = thrown()
        e.message == 'The dependency com.acme:acme-audit is declared twice with different exclusions: [] and [org.slf4j:slf4j-simple]'
    }

    void "the capabilities of a dependency reach the dependencies of the build"() {
        given:
        GeneratorContext context = buildGeneratorContext([]) { GeneratorContext it ->
            it.addDependency(Dependency.builder().groupId('org.ehcache').artifactId('ehcache').implementation()
                    .requireCapability('org.ehcache:ehcache-jakarta'))
        }

        when:
        GradleBuild build = beanContext.getBean(GradleBuildCreator).create(context)

        then:
        build.dependencies*.toSnippet().contains(
                'implementation("org.ehcache:ehcache") { capabilities { requireCapability("org.ehcache:ehcache-jakarta") } }')
    }

    void "the capabilities of a dependency whose coordinates are looked up are kept"() {
        given:
        GeneratorContext context = buildGeneratorContext([]) { GeneratorContext it ->
            it.addDependency(Dependency.builder().lookupArtifactId('grails-web-console').implementation()
                    .requireCapability('org.grails.plugins:grails-web-console-jakarta'))
        }

        expect:
        context.dependencies.find { it.artifactId == 'grails-web-console' }.capabilities == ['org.grails.plugins:grails-web-console-jakarta']
    }

    void "a dependency declared twice with the same capabilities is declared once"() {
        when:
        GeneratorContext context = buildGeneratorContext([]) { GeneratorContext generatorContext ->
            2.times {
                generatorContext.addDependency(Dependency.builder().groupId('org.ehcache').artifactId('ehcache').implementation()
                        .requireCapability('org.ehcache:ehcache-jakarta'))
            }
        }

        then:
        context.dependencies.count { it.artifactId == 'ehcache' } == 1
    }

    void "a dependency declared twice with different capabilities is an error, as one of its variants would be dropped"() {
        when:
        buildGeneratorContext([]) { GeneratorContext it ->
            it.addDependency(Dependency.builder().groupId('org.ehcache').artifactId('ehcache').implementation())
            it.addDependency(Dependency.builder().groupId('org.ehcache').artifactId('ehcache').implementation()
                    .requireCapability('org.ehcache:ehcache-jakarta'))
        }

        then:
        IllegalArgumentException e = thrown()
        e.message == 'The dependency org.ehcache:ehcache is declared twice with different capabilities: [] and [org.ehcache:ehcache-jakarta]'
    }
}
