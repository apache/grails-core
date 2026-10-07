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
package org.grails.forge.cli

import groovy.transform.CompileStatic
import org.springframework.boot.Banner
import org.springframework.boot.WebApplicationType
import org.springframework.boot.bootstrap.BootstrapContext
import org.springframework.boot.bootstrap.BootstrapRegistry
import org.springframework.context.annotation.ComponentScan

import grails.boot.GrailsAppBuilder
import grails.boot.config.GrailsAutoConfiguration
import grails.util.Environment
import org.apache.grails.core.plugins.DefaultPluginDiscovery
import org.apache.grails.core.plugins.PluginDiscovery
import org.grails.forge.ForgeGrailsPlugin
import org.grails.plugins.CoreGrailsPlugin

/**
 * The Forge command line generator as a Grails application: a {@link grails.boot.GrailsApp} without
 * a web server, hosting the Forge plugin that provides the feature catalogue and the project
 * generator, and the picocli commands of this package as its own beans.
 *
 * <p>The application is assembled by the {@code grails} launcher into one jar with the shell CLI
 * and the framework modules the shell needs, so the plugins it runs are declared here rather than
 * discovered on the class path: the core plugin and the Forge plugin, and nothing the shell's
 * dependencies bring along.</p>
 */
@CompileStatic
@ComponentScan(basePackageClasses = ForgeCommand)
class Application extends GrailsAutoConfiguration {

    static void main(String[] args) {
        System.exit(ForgeCli.run(args))
    }

    /**
     * The application, ready to run: the specs run the same builder, so what they test is what
     * {@link #main} runs.
     *
     * @return the builder of a Grails application without a web server
     */
    static GrailsAppBuilder builder() {
        if (!Environment.isSystemSet()) {
            // A generator is not a project under development: without this the environment defaults
            // to development and the application would watch the user's current directory for
            // source changes to reload.
            System.setProperty(Environment.KEY, Environment.PRODUCTION.name)
        }
        return new GrailsAppBuilder(Application)
                .web(WebApplicationType.NONE)
                .bannerMode(Banner.Mode.OFF)
                .logStartupInfo(false)
                .addCommandLineProperties(false)
                .addBootstrapRegistryInitializer(Application::declarePlugins)
    }

    private static void declarePlugins(BootstrapRegistry registry) {
        registry.register(PluginDiscovery, { BootstrapContext context ->
            DefaultPluginDiscovery discovery = new DefaultPluginDiscovery([CoreGrailsPlugin, ForgeGrailsPlugin] as Class<?>[])
            discovery.loadPluginsFromClasspath = false
            discovery
        } as BootstrapRegistry.InstanceSupplier<PluginDiscovery>)
    }

}
