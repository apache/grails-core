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
import org.apache.grails.core.plugins.DefaultPluginDiscovery
import org.apache.grails.core.plugins.PluginDiscovery
import org.apache.grails.forge.cli.StartupPreload
import org.apache.grails.forge.cli.console.GradleBuild
import org.apache.grails.forge.core.ForgeEnvironment
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
        // first: the classes the command is about to load are loaded beside it from here on, see StartupPreload
        StartupPreload.start(Application.classLoader, args)
        // Gradle is given the output of the process for as long as it is the output of the CLI
        GradleBuild.outputOfProcess(System.out, System.err)
        System.exit(ForgeCli.run(args))
    }

    /**
     * The application, ready to run: the specs run the same builder, so what they test is what
     * {@link #main} runs.
     *
     * @return the builder of a Grails application without a web server
     */
    static GrailsAppBuilder builder() {
        // Forge's own environment, not the user's: a grails.env or GRAILS_ENV that reaches this JVM is
        // meant for a project, which the commands give its Gradle build. Without production the
        // application would watch the user's current directory for source changes to reload.
        ForgeEnvironment.useProduction()
        return new GrailsAppBuilder(Application)
                .web(WebApplicationType.NONE)
                .bannerMode(Banner.Mode.OFF)
                .logStartupInfo(false)
                .addCommandLineProperties(false)
                // whoever starts the application closes it: a JVM that runs more than this is left no hook
                .registerShutdownHook(false)
                .properties('spring.config.location': 'classpath:/')
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
