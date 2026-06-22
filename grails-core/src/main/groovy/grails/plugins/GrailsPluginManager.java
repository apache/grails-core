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

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.type.filter.TypeFilter;

import grails.core.GrailsApplication;
import grails.plugins.exceptions.PluginException;
import org.grails.spring.RuntimeSpringConfiguration;

/**
 * Handles the loading and management of plugins in the Grails framework.
 * <p>
 * A plugin is just like a normal Grails application, except it contains
 * a Groovy "plugin descriptor class" which extends {@link Plugin} and has a name
 * ending with {@code GrailsPlugin}.
 * <p>
 * This plugin descriptor class has a {@link GrailsPluginInfo#getVersion() version}
 * property and optionally Closure properties that are event handlers used to react to
 * {@link grails.core.GrailsApplicationLifeCycle Grails lifecycle} events,
 * like {@link grails.core.GrailsApplicationLifeCycle#doWithSpring doWithSpring} and
 * {@link grails.core.GrailsApplicationLifeCycle#doWithApplicationContext doWithApplicationContext}.
 * <p>
 * The {@link grails.core.GrailsApplicationLifeCycle#doWithSpring doWithSpring} Closure uses the
 * {@link grails.spring.BeanBuilder} DSL to provide runtime configuration of Grails via Spring.
 * <p>
 * The {@link grails.core.GrailsApplicationLifeCycle#doWithApplicationContext doWithApplicationContext}
 * Closure is called after the Spring {@link ApplicationContext} is built and accepts a single argument -
 * the application context.
 *
 * <p>
 * Example:
 * <pre>
 * class ClassEditorGrailsPlugin {
 *      def version = '1.1'
 *      def doWithSpring = { application ->
 *          classEditor(org.springframework.beans.propertyeditors.ClassEditor, application.classLoader)
 *      }
 * }
 * </pre>
 * <p>
 * The plugin descriptor class can also optionally define {@code dependsOn} and {@code evict} properties that
 * specify any other plugins that the plugin depends on and any plugins it is incompatible with and should evict.
 *
 * @author Graeme Rocher
 * @since 0.4
 */
public interface GrailsPluginManager extends ApplicationContextAware {

    String BEAN_NAME = "pluginManager";

    /**
     * Returns all loaded plugins.
     *
     * @return All loaded plugins
     */
    GrailsPlugin[] getAllPlugins();

    /**
     * Returns plugins installed by the user (e.g., not provided by the core framework).
     *
     * @return All user plugins
     */
    GrailsPlugin[] getUserPlugins();

    /**
     * Returns any plugins that failed to load due to dependency resolution errors.
     *
     * @return All plugins that failed to load
     */
    GrailsPlugin[] getFailedLoadPlugins();

    /**
     * Performs the initial load of plugins.
     *
     * @throws PluginException if any error occurs when loading the plugins
     */
    void loadPlugins() throws PluginException;

    /**
     * Executes the runtime configuration phase of the loaded plugins (e.g., {@code doWithSpring}).
     *
     * @param springConfig The {@link RuntimeSpringConfiguration} instance
     */
    void doRuntimeConfiguration(RuntimeSpringConfiguration springConfig);

    /**
     * Executes the pre-auto-configuration phase of the loaded plugins
     * ({@code doWithSpringBeforeAutoConfiguration}), so the resulting beans are registered ahead of
     * Spring Boot auto-configuration. Default no-op.
     *
     * @param springConfig The {@link RuntimeSpringConfiguration} instance
     * @since 8.0
     */
    default void doRuntimeConfigurationBeforeAutoConfiguration(RuntimeSpringConfiguration springConfig) {
        // no-op by default
    }

    /**
     * Performs post-initialization configuration for each plugin,
     * passing the built application context (e.g., {@code doWithApplicationContext}).
     *
     * @param applicationContext The {@link ApplicationContext Spring application context}
     */
    void doPostProcessing(ApplicationContext applicationContext);

    /**
     * Called on all plugins so that they can add new methods/properties/constructors etc.
     */
    void doDynamicMethods();

    /**
     * Executes the {@link Plugin#onStartup(Map)} hook for all plugins.
     *
     * @param event the Event
     */
    void onStartup(Map<String, Object> event);

    /**
     * Retrieves the Grails plugin instance with the given name.
     *
     * @param name The name of the plugin
     * @return The {@link GrailsPlugin} instance, or null if it doesn't exist
     */
    GrailsPlugin getGrailsPlugin(String name);

    /**
     * Retrieves the Grails plugin for the given class name.
     *
     * @param name The class name of the plugin
     * @return A {@link GrailsPlugin} instance, or null if it doesn't exist
     */
    GrailsPlugin getGrailsPluginForClassName(String name);

    /**
     * Checks whether the manager has a loaded plugin with the given name
     *
     * @param name The name of the plugin
     * @return true if the manager has a loaded plugin with the given name
     */
    boolean hasGrailsPlugin(String name);

    /**
     * Retrieves a plugin that failed to load, or null if it doesn't exist.
     *
     * @param name The name of the plugin
     * @return A {@link GrailsPlugin} instance, or null if it doesn't exist
     */
    GrailsPlugin getFailedPlugin(String name);

    /**
     * Retrieves a plugin with the given name and version.
     *
     * @param name The name of the plugin
     * @param version The version of the plugin
     * @return A {@link GrailsPlugin} instance, or null if it doesn't exist
     */
    GrailsPlugin getGrailsPlugin(String name, Object version);

    /**
     * Executes the runtime configuration for a specific plugin AND all its dependencies.
     *
     * @param pluginName The name of the plugin
     * @param springConfig The runtime spring config instance
     */
    void doRuntimeConfiguration(String pluginName, RuntimeSpringConfiguration springConfig);

    /**
     * Assigns the {@link GrailsApplication} instance to be used by this plugin manager.
     * @param application The {@link GrailsApplication} instance to use
     */
    void setApplication(GrailsApplication application);

    /**
     * Returns whether the manager has been initialised or not.
     *
     * @return the initialisation status of the manager
     */
    boolean isInitialised();

    /**
     * Refreshes the plugin with the given name.
     * <p>
     * A refresh will force the plugin to "touch" each of its watched resources
     * and fire modified events for each of them.
     *
     * @param name The name of the plugin to refresh
     */
    void refreshPlugin(String name);

    /**
     * Retrieves a collection of plugins that are observing the specified {@link GrailsPlugin plugin}.
     *
     * @param plugin The {@link GrailsPlugin plugin} to retrieve observers for
     * @return A collection of observers
     */
    @SuppressWarnings("rawtypes")
    Collection getPluginObservers(GrailsPlugin plugin);

    /**
     * Notify observers of the {@link GrailsPlugin plugin} with the given name
     * of the event described by the given {@link Map}.
     *
     * @param pluginName The name of the plugin
     * @param event The event
     */
    @SuppressWarnings("rawtypes")
    void informObservers(String pluginName, Map event);

    /**
     * Called prior to the initialisation of the {@link GrailsApplication} object
     * to allow registration of additional {@link grails.core.ArtefactHandler} objects.
     *
     * @see grails.core.ArtefactHandler
     */
    void doArtefactConfiguration();

    /**
     * Registers pre-compiled artefacts with the {@link GrailsApplication} instance,
     * only overriding if the application doesn't already provide an artefact of the same name.
     *
     * @param application The {@link GrailsApplication} instance
     */
    void registerProvidedArtefacts(GrailsApplication application);

    /**
     * Shuts down this plugin manager.
     */
    void shutdown();

    /**
     * Method for handling changes to a class and triggering {@link Plugin#onChange onChange} events etc.
     *
     * @param aClass The class that has changed
     */
    void informOfClassChange(Class<?> aClass);

    /**
     * Get all the {@link TypeFilter} definitions defined by the plugins.
     *
     * @return A list of {@link TypeFilter} definitions
     */
    List<TypeFilter> getTypeFilters();

    /**
     * Returns the plugin path for the {@link GrailsPlugin plugin} with the given name.
     *
     * @param name The plugin name
     * @return the context path
     */
    String getPluginPath(String name);

    /**
     * Returns the plugin path for the {@link GrailsPlugin plugin} with the given name.
     * <p>
     * Will optionally convert the plugin name in the returned path to {@code camelCase} instead
     * of {@code kebab-case}. For example, {@code my-plug-web} would resolve to {@code myPlugWeb}
     * if {@code forceCamelCase} is {@code true}.
     *
     * @param name The plugin name
     * @param forceCamelCase Convert the name in the retured path to {@code camelCase}
     * @return the plugin path
     */
    String getPluginPath(String name, boolean forceCamelCase);

    /**
     * Looks up the {@link GrailsPlugin plugin} that defined the given instance.
     * If the passed in instance is not an instance of a class annotated with
     * {@link grails.plugins.metadata.GrailsPlugin}, null is returned.
     *
     * @param instance The instance
     * @return The {@link GrailsPlugin plugin} that defined the instance or null
     */
    GrailsPlugin getPluginForInstance(Object instance);

    /**
     * Returns the plugin path for the given {@link GrailsPlugin plugin} instance.
     * @param instance The {@link GrailsPlugin plugin} instance
     * @return The plugin path or {@code null} if the instance is not an instance of a class
     *         annotated with {@link grails.plugins.metadata.GrailsPlugin}
     */
    String getPluginPathForInstance(Object instance);

    /**
     * Returns the plugin path for the given {@link GrailsPlugin plugin} class.
     *
     * @param theClass The {@link GrailsPlugin plugin} class
     * @return The plugin path or {@code null} if the class is not annotated with
     *         {@link grails.plugins.metadata.GrailsPlugin}
     */
    String getPluginPathForClass(Class<?> theClass);

    /**
     * Returns the views directory path for the given {@link GrailsPlugin plugin} instance.
     *
     * @param instance The {@link GrailsPlugin plugin}  instance
     * @return The plugin views directory path or {@code null} if the instance is not
     *         an instance of a class annotated with {@link grails.plugins.metadata.GrailsPlugin}
     */
    String getPluginViewsPathForInstance(Object instance);

    /**
     * Returns the views directory path for the given {@link GrailsPlugin plugin} class.
     *
     * @param theClass The {@link GrailsPlugin plugin} class
     * @return The plugin views directory path or {@code null} if the class is not annotated with
     *         {@link grails.plugins.metadata.GrailsPlugin}
     */
    String getPluginViewsPathForClass(Class<? extends Object> theClass);

    /**
     * Returns the {@link GrailsPlugin plugin} for the given class.
     *
     * @param theClass The class to find the {@link GrailsPlugin plugin} for
     * @return The {@link GrailsPlugin plugin} or null if the class is not an instance of a class annotated with
     *         {@link grails.plugins.metadata.GrailsPlugin}
     */
    GrailsPlugin getPluginForClass(Class<?> theClass);

    /**
     * Informs the plugins of a configuration change event.
     */
    void informPluginsOfConfigChange();

    /**
     * Inform the plugins that a particular {@link File file} has changed.
     *
     * @param file The {@link File file} that changed
     * @since 2.0
     */
    void informOfFileChange(File file);

    /**
     * Inform the plugins that a particular {@link File file} and it's resulting
     * compiled class has changed.
     *
     * @param file The {@link File file} that changed
     * @param cls The class that changed
     */
    void informOfClassChange(File file, @SuppressWarnings("rawtypes") Class cls);

    /**
     * Indicates whether this plugin manager has been shutdown or not.
     *
     * @return True if it has been shutdown
     */
    boolean isShutdown();

}
