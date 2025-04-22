/*
 * Copyright 2010 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.plugins;

import grails.plugins.DefaultGrailsPluginManager;
import grails.plugins.GrailsPlugin;
import groovy.lang.GroovySystem;
import groovy.lang.MetaClassRegistry;
import grails.core.GrailsApplication;
import org.grails.core.exceptions.GrailsConfigurationException;
import org.grails.spring.RuntimeSpringConfiguration;
import grails.plugins.exceptions.PluginException;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.Resource;

/**
 * A GrailsPluginManager implementation that outputs profile data to a logger.
 *
 * @author Graeme Rocher
 * @since 2.0
 */
public class ProfilingGrailsPluginManager extends DefaultGrailsPluginManager {

    public ProfilingGrailsPluginManager(GrailsApplication application) {
        super(application);
    }

    public ProfilingGrailsPluginManager(Class<?>[] plugins, GrailsApplication application) {
        super(plugins, application);
    }

    public ProfilingGrailsPluginManager(Resource[] pluginFiles, GrailsApplication application) {
        super(pluginFiles, application);
    }

    public ProfilingGrailsPluginManager(String resourcePath, GrailsApplication application) {
        super(resourcePath, application);
    }

    public ProfilingGrailsPluginManager(String[] pluginResources, GrailsApplication application) {
        super(pluginResources, application);
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
        for (GrailsPlugin plugin : pluginList) {
            if (plugin.supportsCurrentScopeAndEnvironment()) {
                try {
                    long pluginTime = System.currentTimeMillis();
                    System.out.println("doWithDynamicMethods for plugin [" + plugin.getName() + "] started");

                    plugin.doWithDynamicMethods(applicationContext);

                    System.out.println("doWithDynamicMethods for plugin [" + plugin.getName() + "] took " + (System.currentTimeMillis() - pluginTime));
                }
                catch (Throwable t) {
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
        for (GrailsPlugin plugin : pluginList) {
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
        for (GrailsPlugin plugin : pluginList) {
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
