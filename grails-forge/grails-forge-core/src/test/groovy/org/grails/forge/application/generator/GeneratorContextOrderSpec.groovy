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
import org.grails.forge.build.gradle.GradlePlugin
import org.grails.forge.feature.config.Configuration
import org.grails.forge.fixture.ContextFixture
import org.grails.forge.fixture.ProjectFixture

/**
 * The lines of a build file are in an order that is said: the plugins by their order and then as the
 * features declared them, the dependencies sorted and, where two are the same to the sort, as the
 * features declared them. None is in the order a set happened to keep them in, which is another for
 * another number of them and can be another the next time the generator runs.
 */
class GeneratorContextOrderSpec extends ApplicationContextSpec implements ContextFixture, ProjectFixture {

    private static final List<String> IDS = ['zeta', 'alpha', 'mu', 'beta', 'omega', 'gamma', 'kappa', 'delta', 'tau', 'eta', 'pi', 'iota', 'rho',
                                             'nu', 'xi', 'chi', 'psi', 'phi'].collect { String name -> "com.acme.${name}".toString() }

    void "the plugins of a build are in the order they were declared in"() {
        given:
        GeneratorContext context = buildGeneratorContext([]) { GeneratorContext it ->
            IDS.each { String id -> it.addBuildPlugin(GradlePlugin.builder().id(id).build()) }
        }

        when:
        GradleBuild build = beanContext.getBean(GradleBuildCreator).create(context)

        then:
        build.plugins*.id.findAll { String id -> id.startsWith('com.acme.') } == IDS
    }

    void "a plugin that is declared changes the place of no other plugin, and one with an order is where its order puts it"() {
        given:
        List<String> without = beanContext.getBean(GradleBuildCreator).create(buildGeneratorContext([])).plugins*.id
        GeneratorContext context = buildGeneratorContext([]) { GeneratorContext it ->
            IDS.take(3).each { String id -> it.addBuildPlugin(GradlePlugin.builder().id(id).build()) }
            it.addBuildPlugin(GradlePlugin.builder().id('com.acme.first').order(-10).build())
            it.addBuildPlugin(GradlePlugin.builder().id('com.acme.last').order(10).build())
        }

        when:
        List<String> with = beanContext.getBean(GradleBuildCreator).create(context).plugins*.id

        then:
        without.size() > 2
        with.findAll { String id -> !id.startsWith('com.acme.') } == without
        with.first() == 'com.acme.first'
        with.last() == 'com.acme.last'
        with.findAll { String id -> id.startsWith('com.acme.') } == ['com.acme.first'] + IDS.take(3) + ['com.acme.last']
    }

    void "dependencies the sort does not tell apart are in the order they were declared in"() {
        given: 'the same artifacts for two configurations that are written at the same place of the file'
        List<String> artifacts = (1..16).collect { int number -> "acme-tool-${number}".toString() }
        GeneratorContext context = buildGeneratorContext([]) { GeneratorContext it ->
            artifacts.each { String artifact ->
                it.addDependency(Dependency.builder().groupId('com.acme').artifactId(artifact).developmentOnly())
                it.addDependency(Dependency.builder().groupId('com.acme').artifactId(artifact).testAndDevelopmentOnly())
            }
        }

        when:
        List<String> lines = beanContext.getBean(GradleBuildCreator).create(context).dependencies*.toSnippet().findAll { String line -> line.contains('com.acme:') }

        then:
        lines == artifacts.toSorted().collectMany { String artifact ->
            ["developmentOnly \"com.acme:${artifact}\"".toString(), "testAndDevelopmentOnly \"com.acme:${artifact}\"".toString()]
        }
    }

    void "the configuration files are added in the order their configurations were"() {
        given:
        List<String> keys = ['zeta', 'alpha', 'mu', 'beta', 'omega', 'gamma', 'kappa', 'delta', 'tau', 'eta', 'pi', 'iota', 'rho']
        GeneratorContext context = buildGeneratorContext([]) { GeneratorContext it ->
            keys.each { String key ->
                Configuration configuration = new Configuration('main', "application-${key}".toString(), "application-${key}-config".toString(), null)
                configuration.put('acme', key)
                it.addConfiguration(configuration)
            }
        }

        expect:
        context.getAllConfigurations().findAll { Configuration configuration -> configuration.get('acme') != null }*.get('acme') == keys
    }
}
