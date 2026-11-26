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
package grails.plugins;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.BeanRegistrar;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import grails.core.GrailsApplication;
import grails.util.Environment;
import org.grails.plugins.support.WatchPattern;

/**
 * A loaded Grails plugin: an instance of the plugin's {@link Plugin} subclass, which the
 * {@link GrailsPluginManager} drives through the application's lifecycle, together with what its
 * {@link grails.plugins.metadata.GrailsPluginDescriptor} declares about it.
 *
 * @author Steven Devijver
 * @author Graeme Rocher
 * @since 0.2
 * @see GrailsPluginManager
 */
@SuppressWarnings("rawtypes")
public interface GrailsPlugin extends ApplicationContextAware {

    int EVENT_ON_CHANGE = 0;
    int EVENT_ON_CONFIG_CHANGE = 1;
    int EVENT_ON_SHUTDOWN = 2;

    /**
     * The key below which a plugin's configuration holds per-environment values
     */
    String ENVIRONMENTS = "environments";

    /**
     * The prefix used in plug-ins paths
     */
    String PLUGINS_PATH = "/plugins";

    /**
     * @return The logical name of the plugin, for example {@code core} or {@code springSecurityCore}
     */
    String getName();

    /**
     * @return The version of the plugin, or {@code null} for a plugin compiled outside a build that sets one
     */
    String getVersion();

    /**
     * @return The name of the plugin followed by {@code -<version>}, or the name alone for a plugin without a version
     */
    String getFullName();

    /**
     * Calls the plugin's {@link Plugin#doWithApplicationContext()} once the application context is refreshed.
     * @param applicationContext The Spring ApplicationContext instance
     */
    void doWithApplicationContext(ApplicationContext applicationContext);

    /**
     * Returns the {@link BeanRegistrar} exposed by this plugin's
     * {@link grails.core.GrailsApplicationLifeCycle#beanRegistrar()} hook, if any. The registrar
     * is applied to the bean definition registry before Spring Boot auto-configuration is
     * processed, plugin by plugin in load order, so its beans take precedence over Boot's
     * {@code @ConditionalOnMissingBean} defaults and over a bean of the same name that an earlier
     * plugin registered.
     * @return The plugin's {@link BeanRegistrar}, or {@code null} if the plugin does not define one
     * @since 8.0
     */
    default BeanRegistrar getBeanRegistrar() {
        return null;
    }

    /**
     * Returns whether this plugin supports the given environment
     * @param environment The environment
     * @return true if it does
     */
    boolean supportsEnvironment(Environment environment);

    /**
     * @return true if the current plugin supports the current Environment
     */
    boolean supportsCurrentScopeAndEnvironment();

    /**
     * Returns the path of the plug-in
     * @return A String that makes up the path to the plug-in in the format /plugins/plugin-name-PLUGIN_VERSION
     */
    String getPluginPath();

    /**
     * Returns the path of the plug-in using camel case
     * @return A String that makes up the path to the plug-in in the format /plugins/pluginName-PLUGIN_VERSION
     */
    String getPluginPathCamelCase();

    /**
     * @return The names of the plugins this plugin depends on
     */
    String[] getDependencyNames();

    /**
     * Retrieves the names of plugins that this plugin should be loaded after. This differs
     * from dependencies in that if that plugin doesn't exist, this plugin will still be loaded.
     * It is a way of enforcing plugins are loaded before, but not necessarily needed
     * @return The names of the plugins that this plugin should be loaded after
     */
    String[] getLoadAfterNames();

    /**
     * Retrieves the names of plugins that this plugin should be loaded before.
     * As with getLoadAfterNames(), it is not a requirement that the specified
     * plugins exist
     * @return The names of the plugins that this plugin should load before
     */
    String[] getLoadBeforeNames();

    /**
     * The version of the specified dependency
     * @param name the name of the dependency
     * @return The version
     */
    String getDependentVersion(String name);

    /**
     * Retrieves the plugin manager if known, otherwise returns null
     * @return The PluginManager or null
     */
    GrailsPluginManager getManager();

    /**
     * Retrieves the instance of the plugin's {@link Plugin} subclass.
     * @return The plugin instance
     * @since 9.0
     */
    Plugin getPluginInstance();

    /**
     * Sets the plugin manager for this plugin
     * @param manager A GrailsPluginManager instance
     */
    void setManager(GrailsPluginManager manager);

    void setApplication(GrailsApplication application);

    /**
     * Retrieve the plugin names that this plugin is observing for changes
     * @return The names of the observed plugins
     */
    String[] getObservedPluginNames();

    /**
     * Notifies this plugin of the specified Event calling the onChange listener
     * @param event The event to listen for
     */
    void notifyOfEvent(Map event);

    /**
     * Notifies the plugin of a specific event for the given event id, which is one of ON_CHANGE, ON_CONFIG_CHANGE
     * @param eventKind The event kind
     * @param source The source of the event
     * @return a Map that represents the event
     */
    Map notifyOfEvent(int eventKind, Object source);

    /**
     * Retrieves an array of provided Artefacts that are pre-compiled additions to the GrailsApplication object
     * but are overridable by the end-user
     * @return A list of provided artefacts
     */
    Class<?>[] getProvidedArtefacts();

    /**
     * Returns the name of the plugin as represented in the file system, including the version.
     * For example, TagLibGrailsPlugin would result in "tag-lib-0.1"
     * @return The file system representation of the plugin name
     */
    String getFileSystemName();

    /**
     * Returns the name of the plugin as represented on the file system without the version.
     * For example, TagLibGrailsPlugin would result in "tag-lib"
     * @return The file system name
     */
    String getFileSystemShortName();

    /**
     * @return The plugin's {@link Plugin} subclass
     */
    Class<? extends Plugin> getPluginClass();

    /**
     * Returns whether this plugin is the project being run: when you execute grails run-app from a plugin project,
     * the plugin whose classes are that project's is the base plugin.
     * @return true if it is the base plugin
     */
    boolean isBasePlugin();

    /**
     * Resources that this plugin watches
     * @return The watch resource patterns
     */
    List<WatchPattern> getWatchedResourcePatterns();

    /**
     * Whether the plugin is interested in a particular change
     * @param path The path to the resource that changed
     * @return true if it is
     */
    boolean hasInterestInChange(String path);
}
