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
import java.util.Map;

import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import grails.core.GrailsApplication;
import grails.plugins.exceptions.PluginException;

/**
 * Handles the loading and management of plugins in the Grails framework.
 * <p>
 * A plugin is a concrete subclass of {@link Plugin} annotated with
 * {@link grails.plugins.metadata.GrailsPluginDescriptor}, which declares its name, ordering and other metadata.
 * The manager creates a {@link GrailsPlugin} for each plugin discovery finds, and drives it through the
 * application's lifecycle: {@link Plugin#beanRegistrar() beanRegistrar} contributes beans,
 * {@link Plugin#doWithApplicationContext() doWithApplicationContext} runs once the
 * {@link ApplicationContext} is refreshed, and {@link Plugin#onChange(Map) onChange},
 * {@link Plugin#onConfigChange(Map) onConfigChange} and {@link Plugin#onShutdown(Map) onShutdown} react to events.
 * <p>
 * Example:
 * <pre>
 * &#64;GrailsPluginDescriptor(dependsOn = 'core')
 * class ClassEditorGrailsPlugin extends Plugin {
 *     BeanRegistrar beanRegistrar() {
 *         ...
 *     }
 * }
 * </pre>
 *
 * @author Graeme Rocher
 * @since 0.4
 */
public interface GrailsPluginManager extends ApplicationContextAware {

    String BEAN_NAME = "pluginManager";

    /**
     * Returns all loaded plugins, in plugin order: a plugin comes after the plugins it depends on and those its
     * descriptor's {@code after} names. Where two plugins provide the same thing, such as a view, a static
     * resource or a message, the later one wins, so a lookup searches the plugins from the last one back.
     *
     * @return All loaded plugins
     */
    GrailsPlugin[] getAllPlugins();

    /**
     * Performs the initial load of plugins.
     *
     * @throws PluginException if any error occurs when loading the plugins
     */
    void loadPlugins() throws PluginException;

    /**
     * Performs post-initialization configuration for each plugin,
     * passing the built application context (e.g., {@code doWithApplicationContext}).
     *
     * @param applicationContext The {@link ApplicationContext Spring application context}
     */
    void doPostProcessing(ApplicationContext applicationContext);

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
     * Retrieves a plugin with the given name and version.
     *
     * @param name The name of the plugin
     * @param version The version of the plugin
     * @return A {@link GrailsPlugin} instance, or null if it doesn't exist
     */
    GrailsPlugin getGrailsPlugin(String name, Object version);

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
     * Looks up the {@link GrailsPlugin plugin} that provides the class of the given instance, an artefact its
     * descriptor lists. Null is returned for an instance of any other class.
     *
     * @param instance The instance
     * @return The {@link GrailsPlugin plugin} that defined the instance or null
     */
    GrailsPlugin getPluginForInstance(Object instance);

    /**
     * Returns the plugin path for the given {@link GrailsPlugin plugin} instance.
     * @param instance The {@link GrailsPlugin plugin} instance
     * @return The plugin path or {@code null} if no plugin provides the class of the instance
     */
    String getPluginPathForInstance(Object instance);

    /**
     * Returns the plugin path for the given {@link GrailsPlugin plugin} class.
     *
     * @param theClass The {@link GrailsPlugin plugin} class
     * @return The plugin path or {@code null} if no plugin provides the class
     */
    String getPluginPathForClass(Class<?> theClass);

    /**
     * Returns the views directory path for the given {@link GrailsPlugin plugin} instance.
     *
     * @param instance The {@link GrailsPlugin plugin}  instance
     * @return The plugin views directory path or {@code null} if no plugin provides the class of the instance
     */
    String getPluginViewsPathForInstance(Object instance);

    /**
     * Returns the views directory path for the given {@link GrailsPlugin plugin} class.
     *
     * @param theClass The {@link GrailsPlugin plugin} class
     * @return The plugin views directory path or {@code null} if no plugin provides the class
     */
    String getPluginViewsPathForClass(Class<? extends Object> theClass);

    /**
     * Returns the {@link GrailsPlugin plugin} for the given class.
     *
     * @param theClass The class to find the {@link GrailsPlugin plugin} for
     * @return The {@link GrailsPlugin plugin} whose descriptor lists the class as one of its artefacts, or null
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
