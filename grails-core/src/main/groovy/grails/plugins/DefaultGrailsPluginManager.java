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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.GenericApplicationContext;

import grails.core.GrailsApplication;
import grails.plugins.exceptions.PluginException;
import org.apache.grails.core.plugins.PluginDiscovery;
import org.apache.grails.core.plugins.PluginInfo;
import org.grails.plugins.AbstractGrailsPluginManager;
import org.grails.plugins.DefaultGrailsPlugin;
import org.grails.spring.DefaultRuntimeSpringConfiguration;
import org.grails.spring.RuntimeSpringConfiguration;

/**
 * @author Graeme Rocher
 * @since 0.4
 * @see GrailsPluginManager
 */
public class DefaultGrailsPluginManager extends AbstractGrailsPluginManager {

    private static final Log LOG = LogFactory.getLog(DefaultGrailsPluginManager.class);

    private ApplicationContext parentCtx;

    public DefaultGrailsPluginManager(GrailsApplication application, PluginDiscovery pluginDiscovery) {
        super(application, pluginDiscovery);
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
        for (GrailsPlugin observingPlugin : getPluginObservers(plugin)) {

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

        List<String> loadedPlugins = new ArrayList<>();
        // Note: the environment is null here since the plugins should have always been populated in the bootstrap phase
        pluginDiscovery.getPluginsInLoadOrder().forEach(pluginInfo -> {
            GrailsPlugin plugin = createGrailsPlugin(pluginInfo);

            if (LOG.isDebugEnabled()) {
                LOG.debug("Grails plug-in [" + plugin.getName() + "] with version [" + plugin.getVersion() + "] loaded successfully");
            }

            // plugin is always ApplicationContextAware
            plugin.setApplicationContext(applicationContext);
            plugin.setManager(this);
            plugins.put(plugin.getName(), plugin);
            loadedPlugins.add(plugin.getName() + " (" + plugin.getVersion() + ")");
        });

        if (LOG.isInfoEnabled()) {
            LOG.info("Loaded " + loadedPlugins.size() + " Grails plugins in load order: [" +
                    String.join(", ", loadedPlugins) + "]");
        }

        initialised = true;
    }

    /**
     * Creates the runtime plugin for a discovered plugin.
     *
     * @param pluginInfo the plugin, as discovery found it
     * @return the plugin
     */
    protected GrailsPlugin createGrailsPlugin(PluginInfo pluginInfo) {
        return new DefaultGrailsPlugin(pluginInfo, application);
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
        RuntimeSpringConfiguration springConfig = new DefaultRuntimeSpringConfiguration(parentCtx);

        doRuntimeConfiguration(plugin.getName(), springConfig);
        springConfig.registerBeansWithContext((GenericApplicationContext) applicationContext);

        plugin.doWithApplicationContext(applicationContext);
    }
}
