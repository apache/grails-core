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
package org.grails.forge

import org.springframework.boot.Banner
import org.springframework.boot.WebApplicationType
import org.springframework.boot.web.server.context.WebServerApplicationContext
import org.springframework.context.ConfigurableApplicationContext
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import grails.boot.GrailsAppBuilder
import grails.plugins.GrailsPlugin
import grails.plugins.GrailsPluginManager
import grails.util.Environment
import grails.util.Holders
import org.grails.forge.application.ApplicationType
import org.grails.forge.application.ContextFactory
import org.grails.forge.application.generator.ProjectGenerator
import org.grails.forge.feature.FeatureRegistry
import org.grails.forge.feature.validation.FeatureValidator
import org.grails.forge.io.ConsoleOutput
import org.grails.forge.io.MapOutputHandler
import org.grails.forge.options.Options
import org.grails.forge.plugintest.ForgePluginTestApplication
import org.grails.forge.util.NameUtils

/**
 * The plugin as its two applications see it: started by a {@link grails.boot.GrailsApp} without a web
 * server, which is how the Forge CLI hosts it.
 */
class ForgeGrailsPluginSpec extends Specification {

    @Shared
    @AutoCleanup
    ConfigurableApplicationContext context

    void setupSpec() {
        if (!Environment.isSystemSet()) {
            System.setProperty(Environment.KEY, Environment.TEST.name)
        }
        context = new GrailsAppBuilder(ForgePluginTestApplication)
                .web(WebApplicationType.NONE)
                .bannerMode(Banner.Mode.OFF)
                .logStartupInfo(false)
                .run()
    }

    void cleanupSpec() {
        context?.close()
        Holders.clear()
        Environment.setInitializing(false)
    }

    void 'the plugin is loaded by the plugin manager of a Grails application without a web server'() {
        given:
        GrailsPluginManager pluginManager = context.getBean(GrailsPluginManager)

        expect:
        !(context instanceof WebServerApplicationContext)
        pluginManager.hasGrailsPlugin('forge')

        and: 'the descriptor carries the plugin metadata'
        GrailsPlugin plugin = pluginManager.getGrailsPlugin('forge')
        plugin.instance instanceof ForgeGrailsPlugin
        plugin.version
    }

    void 'the generator beans come from the plugin'() {
        expect:
        context.getBean(FeatureRegistry).all()
        context.getBean(ProjectGenerator)
        context.getBean(ContextFactory)
        context.getBean(FeatureValidator)

        and: 'the descriptor is the auto-configuration, registered once and not as a scanned component'
        context.getBeansOfType(ForgeGrailsPlugin).size() == 1
    }

    void 'the plugin generates an application in the Grails context'() {
        given:
        MapOutputHandler output = new MapOutputHandler()
        Options options = new Options()

        when:
        context.getBean(ProjectGenerator).generate(ApplicationType.WEB,
                NameUtils.parse('example.grails.plugintest'),
                options,
                options.operatingSystem,
                [],
                output,
                ConsoleOutput.NOOP)

        then:
        output.project['build.gradle'].contains('org.apache.grails.gradle.grails-web')
        output.project['grails-app/init/example/grails/Application.groovy']
    }

}
