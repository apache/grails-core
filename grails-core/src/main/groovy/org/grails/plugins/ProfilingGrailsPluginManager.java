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
package org.grails.plugins;

import groovy.lang.GroovySystem;
import groovy.lang.MetaClassRegistry;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.context.ApplicationContext;
import org.springframework.core.io.Resource;

import grails.core.GrailsApplication;
import grails.plugins.DefaultGrailsPluginManager;
import grails.plugins.GrailsPlugin;
import grails.plugins.exceptions.PluginException;
import org.apache.grails.core.plugins.PluginDiscovery;
import org.grails.core.exceptions.GrailsConfigurationException;
import org.grails.spring.RuntimeSpringConfiguration;

/**
 * A GrailsPluginManager implementation that outputs profile data to a logger.
 *
 * @author Graeme Rocher
 * @since 2.0
 */
public class ProfilingGrailsPluginManager extends DefaultGrailsPluginManager {

    private static final Log LOG = LogFactory.getLog(DefaultGrailsPluginManager.class);

    public ProfilingGrailsPluginManager(GrailsApplication application, PluginDiscovery pluginDiscovery) {
        super(application, pluginDiscovery);
    }

    public ProfilingGrailsPluginManager(Class<?>[] plugins, GrailsApplication application) {
        this(application, reinitDiscovery(application, plugins));
    }

    public ProfilingGrailsPluginManager(Resource[] pluginFiles, GrailsApplication application) {
        this(application, reinitDiscovery(application, pluginFiles));
    }

    public ProfilingGrailsPluginManager(String resourcePath, GrailsApplication application) {
        this(application, reinitDiscovery(application, resourcePath));
    }

    public ProfilingGrailsPluginManager(String[] pluginResources, GrailsApplication application) {
        this(application, reinitDiscovery(application, pluginResources));
    }

    private static PluginDiscovery reinitDiscovery(GrailsApplication application, Class<?>[] plugins) {
        PluginDiscovery discovery = resolveAndResetDiscovery(application);
        discovery.setPluginClasses(plugins);
        discovery.init(application.getMainContext().getEnvironment());
        return discovery;
    }

    private static PluginDiscovery reinitDiscovery(GrailsApplication application, String[] pluginResources) {
        PluginDiscovery discovery = resolveAndResetDiscovery(application);
        discovery.setPluginResources(pluginResources);
        discovery.init(application.getMainContext().getEnvironment());
        return discovery;
    }

    private static PluginDiscovery reinitDiscovery(GrailsApplication application, Resource[] pluginResources) {
        PluginDiscovery discovery = resolveAndResetDiscovery(application);
        discovery.setPluginResources(pluginResources);
        discovery.init(application.getMainContext().getEnvironment());
        return discovery;
    }

    private static PluginDiscovery reinitDiscovery(GrailsApplication application, String resourcePath) {
        PluginDiscovery discovery = resolveAndResetDiscovery(application);
        discovery.setPluginResources(resourcePath);
        discovery.init(application.getMainContext().getEnvironment());
        return discovery;
    }

    /**
     * Resolves the {@link PluginDiscovery} bean from the application context,
     * resets it.
     */
    private static PluginDiscovery resolveAndResetDiscovery(GrailsApplication application) {
        ApplicationContext ctx = application.getMainContext();
        PluginDiscovery discovery = (PluginDiscovery) ctx.getBean(PluginDiscovery.BEAN_NAME);
        LOG.warn("Using deprecated DefaultGrailsPluginManager constructor. " +
                "Plugin discovery should be configured through the GrailsPluginDiscovery bean. " +
                "Reinitializing plugin discovery.");
        discovery.reset();
        return discovery;
    }

    @Override
    public void loadPlugins() throws PluginException {
        long time = System.currentTimeMillis();
        System.out.println("Loading plugins started");
        super.loadPlugins();
        System.out.println("Loading plugins took " + (System.currentTimeMillis() - time));
    }

    @Override
    public void doDynamicMethods() {
        long time = System.currentTimeMillis();
        System.out.println("doWithDynamicMethods started");
        checkInitialised();
        // remove common meta classes just to be sure
        MetaClassRegistry registry = GroovySystem.getMetaClassRegistry();
        for (Class<?> COMMON_CLASS : COMMON_CLASSES) {
            registry.removeMetaClass(COMMON_CLASS);
        }
        for (GrailsPlugin plugin : getAllPlugins()) {
            if (plugin.supportsCurrentScopeAndEnvironment()) {
                try {
                    long pluginTime = System.currentTimeMillis();
                    System.out.println("doWithDynamicMethods for plugin [" + plugin.getName() + "] started");

                    plugin.doWithDynamicMethods(applicationContext);

                    System.out.println("doWithDynamicMethods for plugin [" + plugin.getName() + "] took " + (System.currentTimeMillis() - pluginTime));
                } catch (Throwable t) {
                    throw new GrailsConfigurationException("Error configuring dynamic methods for plugin " + plugin + ": " + t.getMessage(), t);
                }
            }
        }
        System.out.println("doWithDynamicMethods took " + (System.currentTimeMillis() - time));
    }

    @Override
    public void doRuntimeConfiguration(RuntimeSpringConfiguration springConfig) {
        long time = System.currentTimeMillis();

        System.out.println("doWithSpring started");
        checkInitialised();
        for (GrailsPlugin plugin : getAllPlugins()) {
            if (plugin.supportsCurrentScopeAndEnvironment()) {
                long pluginTime = System.currentTimeMillis();
                System.out.println("doWithSpring for plugin [" + plugin.getName() + "] started");
                plugin.doWithRuntimeConfiguration(springConfig);
                System.out.println("doWithSpring for plugin [" + plugin.getName() + "] took " + (System.currentTimeMillis() - pluginTime));
            }
        }
        System.out.println("doWithSpring took " + (System.currentTimeMillis() - time));
    }

    @Override
    public void doPostProcessing(ApplicationContext ctx) {
        long time = System.currentTimeMillis();
        System.out.println("doWithApplicationContext started");
        checkInitialised();
        for (GrailsPlugin plugin : getAllPlugins()) {
            if (plugin.supportsCurrentScopeAndEnvironment()) {
                long pluginTime = System.currentTimeMillis();
                System.out.println("doWithApplicationContext for plugin [" + plugin.getName() + "] started");
                plugin.doWithApplicationContext(ctx);
                System.out.println("doWithApplicationContext for plugin [" + plugin.getName() + "] took " + (System.currentTimeMillis() - pluginTime));
            }
        }
        System.out.println("doWithApplicationContext took " + (System.currentTimeMillis() - time));
    }

    @Override
    public void doArtefactConfiguration() {
        long time = System.currentTimeMillis();
        System.out.println("doArtefactConfiguration started");
        super.doArtefactConfiguration();
        System.out.println("doArtefactConfiguration took " + (System.currentTimeMillis() - time));
    }
}
