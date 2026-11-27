/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.grails.plugins;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import groovy.lang.ExpandoMetaClass;
import groovy.lang.GroovySystem;
import groovy.lang.MetaClassRegistry;
import groovy.util.ConfigObject;
import groovy.util.ConfigSlurper;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.io.FileSystemResource;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import grails.artefact.Enhanced;
import grails.core.GrailsApplication;
import grails.core.GrailsApplicationLifeCycle;
import grails.plugins.GrailsPlugin;
import grails.plugins.GrailsPluginManager;
import grails.util.Environment;
import grails.util.GrailsNameUtils;
import org.apache.grails.core.plugins.PluginDiscovery;
import org.apache.grails.core.plugins.PluginInfo;
import org.grails.io.support.GrailsResourceUtils;

/**
 * Abstract implementation of the GrailsPluginManager interface
 *
 * @author Graeme Rocher
 * @since 0.4
 */
public abstract class AbstractGrailsPluginManager implements GrailsPluginManager {

    private static final Log LOG = LogFactory.getLog(AbstractGrailsPluginManager.class);
    private static final String BLANK = "";
    public static final String CONFIG_FILE = "application.groovy";
    protected GrailsApplication application;
    protected PluginDiscovery pluginDiscovery;
    protected Map<String, GrailsPlugin> plugins = new HashMap<>();
    private volatile ArtefactIndex artefactIndex;
    protected boolean initialised = false;
    protected boolean shutdown = false;
    protected ApplicationContext applicationContext;

    private static final String CONFIG_BINDING_USER_HOME = "userHome";
    private static final String CONFIG_BINDING_APP_NAME = "appName";
    private static final String CONFIG_BINDING_APP_VERSION = "appVersion";

    public AbstractGrailsPluginManager(GrailsApplication application, PluginDiscovery pluginDiscovery) {
        Objects.requireNonNull(application, "Argument [application] cannot be null!");
        Objects.requireNonNull(pluginDiscovery, "Argument [pluginDiscovery] cannot be null!");
        this.application = application;
        this.pluginDiscovery = pluginDiscovery;
    }

    public GrailsPlugin[] getAllPlugins() {
        return pluginDiscovery.getPluginsInTopologicalOrder()
                .stream()
                .map(PluginInfo::getName)
                .map(plugins::get)
                .filter(Objects::nonNull)
                .toArray(GrailsPlugin[]::new);
    }

    /**
     * @return the initialised
     */
    public boolean isInitialised() {
        return initialised;
    }

    protected void checkInitialised() {
        Assert.state(initialised, "Must call loadPlugins() before invoking configurational methods on GrailsPluginManager");
    }

    /**
     * Base implementation that will simply go through each plugin and call doWithApplicationContext on each.
     */
    public void doPostProcessing(ApplicationContext ctx) {
        checkInitialised();

        for (PluginInfo pluginInfo : pluginDiscovery.getPluginsInTopologicalOrder()) {
            GrailsPlugin grailsPlugin = plugins.get(pluginInfo.getName());
            if (grailsPlugin.supportsCurrentScopeAndEnvironment()) {
                grailsPlugin.doWithApplicationContext(ctx);
            }
        }
    }

    public GrailsPlugin getGrailsPlugin(String name) {
        PluginInfo pluginInfo = pluginDiscovery.findPlugin(name);
        if (pluginInfo == null) {
            return null;
        }

        return plugins.get(pluginInfo.getName());
    }

    public GrailsPlugin getGrailsPluginForClassName(String name) {
        for (GrailsPlugin plugin : plugins.values()) {
            Class<?> pluginClass = plugin.getPluginClass();
            if (pluginClass != null && (pluginClass.getName().equals(name) || pluginClass.getSimpleName().equals(name))) {
                return plugin;
            }
        }
        return null;
    }

    public GrailsPlugin getGrailsPlugin(String name, Object version) {
        PluginInfo pluginInfo = pluginDiscovery.findPlugin(name, version);
        if (pluginInfo == null) {
            return null;
        }

        return plugins.get(pluginInfo.getName());
    }

    public boolean hasGrailsPlugin(String name) {
        return pluginDiscovery.hasPlugin(name);
    }

    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
        if (applicationContext != null) {
            for (GrailsPlugin plugin : getOrderedPlugins()) {
                plugin.setApplicationContext(applicationContext);
            }
        }
    }

    public void setApplication(GrailsApplication application) {
        Assert.notNull(application, "Argument [application] cannot be null");
        this.application = application;

        for (GrailsPlugin plugin : getOrderedPlugins()) {
            plugin.setApplication(application);
        }
    }

    protected List<GrailsPlugin> getOrderedPlugins() {
        List<PluginInfo> orderedPluginInfos = pluginDiscovery.getPluginsInTopologicalOrder();
        if (orderedPluginInfos == null) {
            return new ArrayList<>();
        }

        return orderedPluginInfos.stream()
                .map(PluginInfo::getName)
                .map(plugins::get)
                .filter(Objects::nonNull)
                .toList();
    }

    public void registerProvidedArtefacts(GrailsApplication app) {
        checkInitialised();

        // since plugin classes are added as overridable artefacts, which are added as the first
        // item in the list of artefacts, we have to iterate in reverse order to ensure plugin
        // load sequence is maintained
        ArrayList<GrailsPlugin> toProcess = new ArrayList<>(getOrderedPlugins());
        Collections.reverse(toProcess);
        for (GrailsPlugin plugin : toProcess) {
            if (plugin.supportsCurrentScopeAndEnvironment()) {
                for (Class<?> artefact : plugin.getProvidedArtefacts()) {
                    String shortName = GrailsNameUtils.getShortName(artefact);
                    if (artefact.getName().equals(shortName)) {
                        LOG.warn("Plugin " + plugin.getName() + " has an artefact " + shortName + " without a package name " +
                                "This could lead to artefacts being excluded from the application");
                        if (app.getClassForName(shortName) != null) {
                            LOG.error("Plugin " + plugin.getName() + " has an artefact " + shortName + " that is being excluded from " +
                                    "the application because another artefact exists with the same name without a package defined.");
                        }
                    }
                    if (!isAlreadyRegistered(app, artefact)) {
                        app.addOverridableArtefact(artefact);
                    }
                }
            }
        }
    }

    private boolean isAlreadyRegistered(GrailsApplication app, Class<?> artefact) {
        return app.getClassForName(artefact.getName()) != null;
    }

    public void onStartup(Map<String, Object> event) {
        for (GrailsPlugin plugin : getOrderedPlugins()) {
            if (!plugin.supportsCurrentScopeAndEnvironment()) {
                continue;
            }
            Object instance = plugin.getPluginInstance();
            if (instance instanceof GrailsApplicationLifeCycle lifeCycle) {
                lifeCycle.onStartup(event);
            }
        }
    }

    public void shutdown() {
        checkInitialised();
        try {
            // Shutdown plugins in reverse dependency order
            List<GrailsPlugin> reversePluginList = new ArrayList<>(getOrderedPlugins());
            Collections.reverse(reversePluginList);

            for (GrailsPlugin plugin : reversePluginList) {
                if (plugin.supportsCurrentScopeAndEnvironment()) {
                    plugin.notifyOfEvent(GrailsPlugin.EVENT_ON_SHUTDOWN, plugin);
                }
            }
        } finally {
            shutdown = true;
        }

    }

    public boolean isShutdown() {
        return shutdown;
    }

    public String getPluginPath(String name) {
        return getPluginPath(name, false);
    }

    public String getPluginPath(String name, boolean forceCamelCase) {
        GrailsPlugin plugin = getGrailsPlugin(name);
        if (plugin != null && !plugin.isBasePlugin()) {
            if (forceCamelCase) {
                return plugin.getPluginPathCamelCase();
            } else {
                return plugin.getPluginPath();
            }
        }
        return BLANK;
    }

    public String getPluginPathForInstance(Object instance) {
        if (instance != null) {
            return getPluginPathForClass(instance.getClass());
        }
        return null;
    }

    public GrailsPlugin getPluginForInstance(Object instance) {
        if (instance != null) {
            return getPluginForClass(instance.getClass());
        }
        return null;
    }

    /**
     * The plugin whose descriptor lists the class among its artefacts. Classes are matched by name, so a class
     * reloaded in development still finds its plugin. View resolution asks on every render, so the answer comes
     * from an index of the plugins' artefacts, built again only when the loaded plugins change.
     */
    public GrailsPlugin getPluginForClass(Class<?> theClass) {
        if (theClass == null) {
            return null;
        }
        return artefactIndex().get(theClass.getName());
    }

    private Map<String, GrailsPlugin> artefactIndex() {
        ArtefactIndex index = artefactIndex;
        if (index == null || !index.describes(plugins)) {
            index = new ArtefactIndex(plugins);
            artefactIndex = index;
        }
        return index.pluginsByArtefact;
    }

    /** The plugins' artefacts by class name, with the plugins it was built from to tell when it is stale. */
    private static final class ArtefactIndex {

        private final Map<String, GrailsPlugin> plugins;
        private final Map<String, GrailsPlugin> pluginsByArtefact;

        ArtefactIndex(Map<String, GrailsPlugin> plugins) {
            this.plugins = Map.copyOf(plugins);
            Map<String, GrailsPlugin> byArtefact = new HashMap<>();
            for (GrailsPlugin plugin : this.plugins.values()) {
                for (Class<?> artefact : plugin.getProvidedArtefacts()) {
                    byArtefact.putIfAbsent(artefact.getName(), plugin);
                }
            }
            this.pluginsByArtefact = Map.copyOf(byArtefact);
        }

        boolean describes(Map<String, GrailsPlugin> current) {
            if (current.size() != plugins.size()) {
                return false;
            }
            for (Map.Entry<String, GrailsPlugin> entry : current.entrySet()) {
                if (plugins.get(entry.getKey()) != entry.getValue()) {
                    return false;
                }
            }
            return true;
        }
    }

    @Override
    public void informPluginsOfConfigChange() {
        for (GrailsPlugin plugin : getOrderedPlugins()) {
            if (plugin.supportsCurrentScopeAndEnvironment()) {
                plugin.notifyOfEvent(GrailsPlugin.EVENT_ON_CONFIG_CHANGE, application.getConfig());
            }
        }
    }

    public void informOfFileChange(File file) {
        String className = GrailsResourceUtils.getClassName(file.getAbsolutePath());
        Class<?> cls = null;
        if (className != null) {
            cls = loadApplicationClass(className);
        }
        informOfClassChange(file, cls);
    }

    static ConfigSlurper getConfigSlurper(GrailsApplication application) {
        String environment = Environment.getCurrent().getName();
        ConfigSlurper configSlurper = new ConfigSlurper(environment);
        final Map<String, Object> binding = new HashMap<>();
        // configure config slurper binding
        binding.put(CONFIG_BINDING_USER_HOME, System.getProperty("user.home"));
        if (application != null) {
            binding.put(CONFIG_BINDING_APP_NAME, application.getMetadata().getApplicationName());
            binding.put(CONFIG_BINDING_APP_VERSION, application.getMetadata().getApplicationVersion());
            binding.put(GrailsApplication.APPLICATION_ID, application);
        }
        configSlurper.setBinding(binding);
        return configSlurper;
    }

    public void informOfClassChange(File file, @SuppressWarnings("rawtypes") Class cls) {
        if (file.getName().equals(CONFIG_FILE)) {
            ConfigSlurper configSlurper = getConfigSlurper(application);
            ConfigObject c;
            try {
                c = configSlurper.parse(file.toURI().toURL());
                application.getConfig().merge(c);
                final Map flat = c.flatten();
                application.getConfig().merge(flat);
                application.configChanged();
                informPluginsOfConfigChange();
            } catch (Exception e) {
                // ignore
                LOG.debug("Error in changing Config", e);
            }
        } else {

            if (cls != null) {
                MetaClassRegistry registry = GroovySystem.getMetaClassRegistry();
                registry.removeMetaClass(cls);
                ExpandoMetaClass newMc = new ExpandoMetaClass(cls, true, true);
                newMc.initialize();
                registry.setMetaClass(cls, newMc);

                Enhanced en = AnnotationUtils.findAnnotation(cls, Enhanced.class);
                if (en != null) {
                    Class<?>[] mixinClasses = en.mixins();
                    if (mixinClasses != null) {
                        DefaultGroovyMethods.mixin(newMc, mixinClasses);
                    }
                }
            }

            for (GrailsPlugin grailsPlugin : getOrderedPlugins()) {
                if (grailsPlugin.hasInterestInChange(file.getAbsolutePath())) {
                    try {
                        if (cls == null) {
                            grailsPlugin.notifyOfEvent(GrailsPlugin.EVENT_ON_CHANGE, new FileSystemResource(file));
                        } else {
                            grailsPlugin.notifyOfEvent(GrailsPlugin.EVENT_ON_CHANGE, cls);
                        }
                        Environment.setCurrentReloadError(null);
                    } catch (Exception e) {
                        LOG.error("Plugin " + grailsPlugin + " could not reload changes to file [" +
                                file + "]: " + e.getMessage(), e);
                        Environment.setCurrentReloadError(e);
                    }
                }
            }
        }
    }

    private Class<?> loadApplicationClass(String className) {
        Class<?> cls = null;
        try {
            cls = application.getClassLoader().loadClass(className);
        } catch (ClassNotFoundException e) {
            // ignore
        }
        return cls;
    }

    public String getPluginPathForClass(Class<?> theClass) {
        GrailsPlugin plugin = getPluginForClass(theClass);
        return plugin != null ? getPluginPath(plugin.getName()) : null;
    }

    public String getPluginViewsPathForInstance(Object instance) {
        if (instance != null) {
            return getPluginViewsPathForClass(instance.getClass());
        }
        return null;
    }

    public String getPluginViewsPathForClass(Class<?> theClass) {
        if (theClass != null) {
            final String path = getPluginPathForClass(theClass);
            if (StringUtils.hasText(path)) {
                return path + '/' + GrailsResourceUtils.GRAILS_APP_DIR + "/views";
            }
        }
        return null;
    }
}
