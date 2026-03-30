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
package org.apache.grails.core.plugins;

import java.util.Collection;
import java.util.List;

import org.springframework.core.env.Environment;
import org.springframework.core.io.Resource;

import org.apache.grails.core.plugins.filters.PluginFilter;

/**
 * Defines a strategy for discovering the Grails plugins that should participate in application bootstrap.
 *
 * <p>A {@code PluginDiscovery} implementation is initialized early in the application lifecycle, typically from
 * {@link grails.boot.config.GrailsEnvironmentPostProcessor}, and later reused by the plugin manager and other
 * bootstrap components. Implementations are expected to discover candidate plugins, apply filtering, resolve
 * ordering, and expose the resulting plugin metadata through the methods in this interface.</p>
 *
 * @since 7.1
 */
public interface PluginDiscovery {

    /**
     * The bean name used when the bootstrap {@code PluginDiscovery} is promoted into the application context.
     */
    String BEAN_NAME = "grailsPluginDiscovery";

    /**
     * Initializes plugin discovery for the supplied environment.
     *
     * <p>This method is typically invoked during
     * {@link grails.boot.config.GrailsEnvironmentPostProcessor} so filtering and ordering are available before
     * the main application context finishes bootstrapping.</p>
     *
     * @param environment the environment used to determine plugin filtering and ordering
     */
    void init(Environment environment);

    /**
     * Returns plugins supplied dynamically instead of being discovered from classpath descriptors.
     *
     * <p>This is most commonly used by tests that configure plugin classes or resources directly.</p>
     *
     * @return dynamically configured plugins discovered outside the standard classpath descriptor scan
     */
    Collection<PluginInfo> getDynamicPlugins();

    /**
     * Determines whether a plugin failed during discovery or dependency resolution.
     *
     * @param name the name of the plugin; implementations may normalize the name before lookup
     * @return {@code true} if the named plugin could not be loaded successfully
     */
    boolean hasFailedPlugin(String name);

    /**
     * Returns a plugin that failed during discovery or dependency resolution.
     *
     * @param name the name of the plugin; implementations may normalize the name before lookup
     * @return the failed plugin, or {@code null} if no matching failed plugin exists
     */
    PluginInfo getFailedPlugin(String name);

    /**
     * Returns every plugin that failed during discovery or dependency resolution.
     *
     * @return the plugins that could not be loaded successfully
     */
    Collection<PluginInfo> getFailedPlugins();

    /**
     * Finds a discovered plugin by name.
     *
     * @param pluginName a plugin name to search for; implementations may normalize the name before lookup
     * @return the plugin information if found, otherwise {@code null}
     */
    PluginInfo findPlugin(String pluginName);

    /**
     * Finds a discovered plugin by name and required version.
     *
     * @param pluginName a plugin name to search for; implementations may normalize the name before lookup
     * @param version the required version of the plugin
     * @return the matching plugin if it satisfies the required version, otherwise {@code null}
     */
    PluginInfo findPlugin(String pluginName, Object version);

    /**
     * Returns the discovered plugins in topological order.
     *
     * @return plugins ordered by dependency and declared load-before/load-after relationships
     */
    List<PluginInfo> getPluginsInTopologicalOrder();

    /**
     * Returns the discovered plugins in their effective load order.
     *
     * @return the order in which plugins should be loaded
     */
    List<PluginInfo> getPluginsInLoadOrder();

    /**
     * Determines whether a plugin with the given name has been discovered.
     *
     * @param name the name of the plugin; implementations may normalize the name before lookup
     * @return {@code true} if a plugin with the given name exists, otherwise {@code false}
     */
    boolean hasPlugin(String name);

    /**
     * Finds plugins that observe the supplied plugin.
     *
     * @param plugin the plugin to get the observers for
     * @return the plugins observing the given plugin, or an empty collection if there are none
     */
    Collection<PluginInfo> findPluginObservers(PluginInfo plugin);

    /**
     * Enables or disables discovery of plugins from classpath descriptors.
     *
     * @param loadClasspathPlugins true if plugins should be loaded from the classpath,
     *                             otherwise no classpath plugins will be searched
     */
    void setLoadPluginsFromClasspath(boolean loadClasspathPlugins);

    /**
     * Controls whether at least one classpath plugin is required.
     *
     * @param requireClasspathPlugin if true, classpath plugins will be required to be present
     */
    void setClasspathPluginsRequired(boolean requireClasspathPlugin);

    /**
     * Overrides the plugin filter used during discovery.
     *
     * @param filter an override to filter plugins that are found during the discovery process
     */
    void setPluginFilter(PluginFilter filter);

    /**
     * Configures Groovy plugin resources to be treated as dynamic plugins.
     *
     * @param pluginResources the resources containing dynamically defined plugin classes
     */
    void setPluginResources(Resource[] pluginResources);

    /**
     * Configures resource locations to search for dynamically defined plugins.
     *
     * @param pluginResources the resource location patterns containing dynamically defined plugins
     */
    void setPluginResources(String[] pluginResources);

    /**
     * Configures a single resource location to search for dynamically defined plugins.
     *
     * @param resourcePath the resource location containing dynamically defined plugins
     */
    void setPluginResources(String resourcePath);

    /**
     * Configures explicit plugin classes to be treated as dynamic plugins.
     *
     * @param pluginClasses the dynamically supplied plugin classes
     */
    void setPluginClasses(Class<?>[] pluginClasses);

    /**
     * Returns the configured dynamic plugin resources.
     *
     * @return the dynamic plugin resources currently configured for discovery
     */
    Resource[] getPluginResources();

    /**
     * Resets the discovery state so plugins can be discovered again.
     *
     * <p><strong>Warning:</strong> This should only be done in controlled environments, usually during testing.</p>
     */
    void reset();
}
