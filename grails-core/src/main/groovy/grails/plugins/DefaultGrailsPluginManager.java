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

import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URI;
import java.net.URL;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;

import groovy.lang.GroovySystem;
import groovy.lang.MetaClassRegistry;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.io.Resource;

import grails.core.GrailsApplication;
import grails.core.support.ParentApplicationContextAware;
import grails.plugins.exceptions.PluginException;
import org.apache.grails.core.plugins.PluginDescriptor;
import org.apache.grails.core.plugins.PluginDiscovery;
import org.apache.grails.core.plugins.PluginInfo;
import org.grails.core.exceptions.GrailsConfigurationException;
import org.grails.plugins.AbstractGrailsPluginManager;
import org.grails.plugins.BinaryGrailsPlugin;
import org.grails.plugins.DefaultGrailsPlugin;
import org.grails.spring.DefaultRuntimeSpringConfiguration;
import org.grails.spring.RuntimeSpringConfiguration;

/**
 * <p>Handles the loading and management of plug-ins in the Grails system.
 * A plugin is just like a normal Grails application except that it contains a file ending
 * in *Plugin.groovy in the root of the directory.
 * <p>A Plugin class is a Groovy class that has a version and optionally closures
 * called doWithSpring, doWithContext and doWithWebDescriptor
 * <p>The doWithSpring closure uses the BeanBuilder syntax (@see grails.spring.BeanBuilder) to
 * provide runtime configuration of Grails via Spring
 * <p>The doWithContext closure is called after the Spring ApplicationContext is built and accepts
 * a single argument (the ApplicationContext)
 * <p>The doWithWebDescriptor uses mark-up building to provide additional functionality to the web.xml
 * file
 * <p> Example:
 * <pre>
 * class ClassEditorGrailsPlugin {
 *      def version = '1.1'
 *      def doWithSpring = { application ->
 *          classEditor(org.springframework.beans.propertyeditors.ClassEditor, application.classLoader)
 *      }
 * }
 * </pre>
 * <p>A plugin can also define "dependsOn" and "evict" properties that specify what plugins the plugin
 * depends on and which ones it is incompatible with and should evict
 *
 * @author Graeme Rocher
 * @since 0.4
 */
public class DefaultGrailsPluginManager extends AbstractGrailsPluginManager {

    protected static final Class<?>[] COMMON_CLASSES = {
        Boolean.class, Byte.class, Character.class, Class.class, Double.class, Float.class,
        Integer.class, Long.class, Number.class, Short.class, String.class, BigInteger.class,
        BigDecimal.class, URL.class, URI.class
    };
    private static final Log LOG = LogFactory.getLog(DefaultGrailsPluginManager.class);

    private ApplicationContext parentCtx;

    public DefaultGrailsPluginManager(GrailsApplication application, PluginDiscovery pluginDiscovery) {
        super(application, pluginDiscovery);
    }

    /**
     * @deprecated Use {@link #DefaultGrailsPluginManager(GrailsApplication, PluginDiscovery)} instead.
     * Plugin discovery is now handled by {@link PluginDiscovery}. This constructor fetches the
     * existing discovery bean, resets and reinitializes it. Will be removed in Grails 8.0.0.
     */
    @Deprecated(forRemoval = true, since = "7.1")
    public DefaultGrailsPluginManager(String resourcePath, GrailsApplication application) {
        this(application, reinitDiscovery(application, resourcePath));
    }

    /**
     * @deprecated Use {@link #DefaultGrailsPluginManager(GrailsApplication, PluginDiscovery)} instead.
     * Plugin discovery is now handled by {@link PluginDiscovery}. This constructor fetches the
     * existing discovery bean, resets and reinitializes it. Will be removed in Grails 8.0.0.
     */
    @Deprecated(forRemoval = true, since = "7.1")
    public DefaultGrailsPluginManager(String[] pluginResources, GrailsApplication application) {
        this(application, reinitDiscovery(application, pluginResources));
    }

    /**
     * @deprecated Use {@link #DefaultGrailsPluginManager(GrailsApplication, PluginDiscovery)} instead.
     * Plugin discovery is now handled by {@link PluginDiscovery}. This constructor fetches the
     * existing discovery bean, resets and reinitializes it. Will be removed in Grails 8.0.0.
     */
    @Deprecated(forRemoval = true, since = "7.1")
    public DefaultGrailsPluginManager(Class<?>[] plugins, GrailsApplication application) {
        this(application, reinitDiscovery(application, plugins));
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

    private static PluginDiscovery reinitDiscovery(GrailsApplication application, String resourcePath) {
        PluginDiscovery discovery = resolveAndResetDiscovery(application);
        discovery.setPluginResources(resourcePath);
        discovery.init(application.getMainContext().getEnvironment());
        return discovery;
    }

    /**
     * Resolves the {@link PluginDiscovery} bean from the application context,
     * resets and reinitializes it. The GrailsApplication always has an application context
     * set when these deprecated constructors are called.
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

    public GrailsPlugin[] getUserPlugins() {
        return pluginDiscovery.getDynamicPlugins().stream()
                .map(PluginInfo::getName)
                .map(plugins::get)
                .filter(Objects::nonNull)
                .toArray(GrailsPlugin[]::new);
    }

    public void refreshPlugin(String name) {
        if (hasGrailsPlugin(name)) {
            getGrailsPlugin(name).refresh();
        }
    }

    public Collection<GrailsPlugin> getPluginObservers(GrailsPlugin grailsPlugin) {
        Objects.requireNonNull(grailsPlugin, "Argument [plugin] cannot be null");
        PluginInfo pluginInfo = this.pluginDiscovery.findPlugin(grailsPlugin.getName());
        return pluginDiscovery.findPluginObservers(pluginInfo)
                .stream()
                .map(PluginInfo::getName)
                .map(plugins::get)
                .filter(Objects::nonNull)
                .toList();
    }

    @SuppressWarnings("rawtypes")
    public void informObservers(String pluginName, Map event) {
        GrailsPlugin plugin = getGrailsPlugin(pluginName);
        if (plugin == null) {
            return;
        }
        if (!plugin.isEnabled(applicationContext.getEnvironment().getActiveProfiles())) return;

        for (GrailsPlugin observingPlugin : getPluginObservers(plugin)) {

            if (!observingPlugin.isEnabled(applicationContext.getEnvironment().getActiveProfiles())) continue;

            observingPlugin.notifyOfEvent(event);
        }
    }

    /* (non-Javadoc)
     * @see grails.plugins.GrailsPluginManager#loadPlugins()
     */
    public void loadPlugins() throws PluginException {
        if (initialised) {
            return;
        }

        // Note: the environment is null here since the plugins should have always been populated in the bootstrap phase
        pluginDiscovery.getPluginsInLoadOrder().forEach(pluginInfo -> {
            GrailsPlugin plugin;
            if (pluginInfo.isDynamic()) {
                plugin = createGrailsPlugin(pluginInfo.getPluginClass(), pluginInfo.getPluginDescriptor().getResource());
            } else {
                plugin = createBinaryGrailsPlugin(pluginInfo.getPluginClass(), pluginInfo.getPluginDescriptor());
            }

            if (LOG.isInfoEnabled()) {
                LOG.info("Grails plug-in [" + plugin.getName() + "] with version [" + plugin.getVersion() + "] loaded successfully");
            }

            // plugin is always ApplicationContextAware
            plugin.setApplicationContext(applicationContext);
            if (plugin instanceof ParentApplicationContextAware) {
                ((ParentApplicationContextAware) plugin).setParentApplicationContext(parentCtx);
            }
            plugin.setManager(this);
            plugins.put(plugin.getName(), plugin);
        });

        initialised = true;
    }

    private GrailsPlugin createBinaryGrailsPlugin(Class<?> pluginClass, PluginDescriptor binaryDescriptor) {
        return new BinaryGrailsPlugin(pluginClass, binaryDescriptor, application);
    }

    protected GrailsPlugin createGrailsPlugin(Class<?> pluginClass, Resource resource) {
        return new DefaultGrailsPlugin(pluginClass, resource, application);
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
        for (GrailsPlugin plugin : getOrderedPlugins()) {
            plugin.setApplicationContext(applicationContext);
        }
    }

    public void setParentApplicationContext(ApplicationContext parent) {
        parentCtx = parent;
    }

    public void reloadPlugin(GrailsPlugin plugin) {
        plugin.doArtefactConfiguration();

        RuntimeSpringConfiguration springConfig = new DefaultRuntimeSpringConfiguration(parentCtx);

        doRuntimeConfiguration(plugin.getName(), springConfig);
        springConfig.registerBeansWithContext((GenericApplicationContext) applicationContext);

        plugin.doWithApplicationContext(applicationContext);
        plugin.doWithDynamicMethods(applicationContext);
    }

    @Override
    public void doDynamicMethods() {
        checkInitialised();
        // remove common meta classes just to be sure
        MetaClassRegistry registry = GroovySystem.getMetaClassRegistry();
        for (Class<?> COMMON_CLASS : COMMON_CLASSES) {
            registry.removeMetaClass(COMMON_CLASS);
        }
        for (GrailsPlugin plugin : getOrderedPlugins()) {
            if (plugin.supportsCurrentScopeAndEnvironment()) {
                try {
                    plugin.doWithDynamicMethods(applicationContext);
                } catch (Throwable t) {
                    throw new GrailsConfigurationException("Error configuring dynamic methods for plugin " + plugin + ": " + t.getMessage(), t);
                }
            }
        }
    }
}
