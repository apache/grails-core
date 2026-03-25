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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import groovy.lang.GroovyClassLoader;
import org.codehaus.groovy.control.CompilationFailedException;
import org.codehaus.groovy.runtime.IOGroovyMethods;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.core.env.Environment;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import grails.plugins.GrailsPluginSorter;
import grails.plugins.GrailsVersionUtils;
import grails.plugins.exceptions.PluginException;
import grails.util.Metadata;
import org.apache.grails.core.plugins.filters.PluginFilter;
import org.apache.grails.core.plugins.filters.PluginFilterRetriever;
import org.grails.core.io.CachingPathMatchingResourcePatternResolver;
import org.grails.io.support.GrailsResourceUtils;

public class DefaultPluginDiscovery implements PluginDiscovery {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultPluginDiscovery.class);

    protected Metadata applicationMeta = Metadata.getCurrent();
    protected Resource[] pluginResources = new Resource[0];
    protected Class<?>[] pluginClasses = new Class[0];
    protected LinkedHashMap<String, PluginInfo> plugins;
    protected List<PluginInfo> orderedPlugins;
    protected List<PluginInfo> loadOrderedPlugins;
    protected List<PluginInfo> dynamicPlugins;
    protected Map<String, Set<PluginInfo>> pluginToObserverMap;
    protected List<PluginInfo> delayedLoadPlugins;
    protected Map<String, PluginInfo> failedPlugins;
    protected Map<PluginInfo, String[]> delayedEvictions;
    protected PluginFilter pluginFilter;
    protected boolean loadClasspathPlugins = true;
    protected boolean requireClasspathPlugin = true;
    protected final PluginFilterRetriever filterRetriever;
    private boolean initialized = false;

    public DefaultPluginDiscovery() {
        this(new PluginFilterRetriever());
    }

    public DefaultPluginDiscovery(String resourcePath) {
        this();
        setPluginResources(resourcePath);
    }

    public DefaultPluginDiscovery(Class<?>[] pluginClasses) {
        this();
        setPluginClasses(pluginClasses);
    }

    public DefaultPluginDiscovery(String[] pluginResources) {
        this();
        setPluginResources(pluginResources);
    }

    public DefaultPluginDiscovery(PluginFilterRetriever filterRetriever) {
        this.filterRetriever = filterRetriever;
    }

    public DefaultPluginDiscovery(Resource[] pluginFiles) {
        this();
        setPluginResources(pluginFiles);
    }

    @Override
    public void setPluginResources(String[] pluginResources) {
        PathMatchingResourcePatternResolver resolver = CachingPathMatchingResourcePatternResolver.INSTANCE;

        List<Resource> resourceList = new ArrayList<>();
        for (String resourcePath : pluginResources) {
            try {
                resourceList.addAll(Arrays.asList(resolver.getResources(resourcePath)));
            } catch (IOException ioe) {
                LOG.debug("Unable to load plugins for resource path {}", resourcePath, ioe);
            }
        }

        this.pluginResources = resourceList.toArray(new Resource[0]);
    }

    @Override
    public void setPluginClasses(Class<?>[] pluginClasses) {
        this.pluginClasses = pluginClasses;
    }

    @Override
    public void setPluginResources(String resourcePath) {
        PathMatchingResourcePatternResolver resolver = CachingPathMatchingResourcePatternResolver.INSTANCE;
        try {
            pluginResources = resolver.getResources(resourcePath);
        } catch (IOException ioe) {
            LOG.debug("Unable to load plugins for resource path {}", resourcePath, ioe);
        }
    }

    @Override
    public void setPluginResources(Resource[] pluginResources) {
        this.pluginResources = pluginResources;
    }

    public Collection<PluginInfo> getDynamicPlugins() {
        return Collections.unmodifiableList(dynamicPlugins);
    }

    public Collection<PluginInfo> getFailedPlugins() {
        return Collections.unmodifiableCollection(failedPlugins.values());
    }

    public PluginInfo getFailedPlugin(String name) {
        return failedPlugins.get(PluginUtils.normalizePluginName(name));
    }

    public boolean hasFailedPlugin(String name) {
        return failedPlugins.containsKey(PluginUtils.normalizePluginName(name));
    }

    public void init(Environment environment) {
        if (!initialized) {
            if (environment == null) {
                throw new IllegalArgumentException("Environment must be provided to determine plugin order");
            }
            populatePlugins(environment);
        }
    }

    private void validateInitialized() {
        if (!initialized) {
            throw new IllegalStateException("init() must be called prior to fetching the plugin order.");
        }
    }

    @Override
    public boolean hasPlugin(String name) {
        return plugins.containsKey(PluginUtils.normalizePluginName(name));
    }

    public PluginInfo findPlugin(String pluginName) {
        validateInitialized();

        return plugins.get(PluginUtils.normalizePluginName(pluginName));
    }

    @Override
    public PluginInfo findPlugin(String pluginName, Object version) {
        validateInitialized();

        PluginInfo plugin = plugins.get(PluginUtils.normalizePluginName(pluginName));
        if (plugin != null && GrailsVersionUtils.isValidVersion(plugin.getPluginVersion(), version.toString())) {
            return plugin;
        }
        return null;
    }

    @Override
    public Collection<PluginInfo> findPluginObservers(PluginInfo plugin) {
        Objects.requireNonNull(plugin, "Argument [plugin] cannot be null");

        Collection<PluginInfo> c = pluginToObserverMap.get(plugin.getName());

        // Add any wildcard observers.
        Collection<PluginInfo> wildcardObservers = pluginToObserverMap.get(PluginUtils.WILDCARD_OBSERVER_PATTERN);
        if (wildcardObservers != null) {
            if (c != null) {
                c.addAll(wildcardObservers);
            } else {
                c = wildcardObservers;
            }
        }

        if (c != null) {
            // Make sure this plugin is not observing itself!
            c.remove(plugin);
            return c;
        }

        return Collections.emptySet();
    }

    @Override
    public List<PluginInfo> getPluginsInTopologicalOrder() {
        validateInitialized();
        return Collections.unmodifiableList(orderedPlugins);
    }

    @Override
    public List<PluginInfo> getPluginsInLoadOrder() {
        validateInitialized();
        return Collections.unmodifiableList(loadOrderedPlugins);
    }

    @Override
    public void setLoadPluginsFromClasspath(boolean loadClasspathPlugins) {
        this.loadClasspathPlugins = loadClasspathPlugins;
    }

    @Override
    public void setClasspathPluginsRequired(boolean requireClasspathPlugin) {
        this.requireClasspathPlugin = requireClasspathPlugin;
    }

    @Override
    public void setPluginFilter(PluginFilter filter) {
        this.pluginFilter = filter;
    }

    @Override
    public Resource[] getPluginResources() {
        validateInitialized();
        return pluginResources;
    }

    List<PluginInfo> filterPlugins(List<PluginInfo> plugins, Environment environment) {
        if (pluginFilter == null) {
            pluginFilter = filterRetriever.getPluginFilter(environment);
        }

        var filteredMetadataSet = new HashSet<>(pluginFilter.filterPluginList(
                plugins.stream()
                        .map(PluginInfo::getMetadata)
                        .toList()
        ));

        return plugins.stream()
                .filter(p -> filteredMetadataSet.contains(p.getMetadata()))
                .toList();
    }

    void populatePlugins(Environment environment) {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        List<PluginInfo> classpathPlugins;
        if (loadClasspathPlugins) {
            ClasspathPluginFinder finder = new ClasspathPluginFinder();
            classpathPlugins = finder.findClasspathPlugins(classLoader, applicationMeta.getGrailsVersion());
        } else {
            classpathPlugins = Collections.emptyList();
        }

        if (loadClasspathPlugins && requireClasspathPlugin && classpathPlugins.isEmpty()) {
            LOG.debug("No Grails plugin classes found in META-INF/grails-plugin.xml descriptors");
            throw new IllegalStateException(
                    "Grails was unable to load plugins dynamically. This is normally a " +
                            "problem with the container class loader configuration, see " +
                            "troubleshooting and FAQ for more info.");
        }

        // TODO: These were previously never filtered, and continue to behave this way
        dynamicPlugins = findDynamicPlugins(classLoader);

        List<PluginInfo> allPlugins = new ArrayList<>(classpathPlugins);
        allPlugins.addAll(dynamicPlugins);

        List<PluginInfo> filteredPlugins = filterPlugins(allPlugins, environment);

        reset();

        if (filteredPlugins.isEmpty()) {
            LOG.debug("All plugins were excluded by plugin filtering");
            initialized = true;
            return;
        }

        attemptRegisterPlugins(filteredPlugins);

        if (!delayedLoadPlugins.isEmpty()) {
            loadDelayedPlugins();
        }
        if (!delayedEvictions.isEmpty()) {
            processDelayedEvictions();
        }

        // TODO: why do we sort if we know the order it had to load in?
        orderedPlugins = GrailsPluginSorter.sort(
                loadOrderedPlugins,
                PluginInfo::getName,
                PluginInfo::getLoadAfterNames,
                PluginInfo::getLoadBeforeNames
        );
        initialized = true;
    }

    private void processDelayedEvictions() {
        for (Map.Entry<PluginInfo, String[]> entry : delayedEvictions.entrySet()) {
            PluginInfo plugin = entry.getKey();
            for (String pluginName : entry.getValue()) {
                evictPlugin(plugin, pluginName);
            }
        }
    }

    protected void evictPlugin(PluginInfo evictor, String evicteeName) {
        PluginInfo pluginToEvict = plugins.get(evicteeName);
        if (pluginToEvict != null) {
            orderedPlugins.remove(pluginToEvict);
            loadOrderedPlugins.remove(pluginToEvict);
            plugins.remove(pluginToEvict.getName());

            if (LOG.isInfoEnabled()) {
                LOG.info("Grails plug-in {} was evicted by {}", pluginToEvict, evictor);
            }
        }
    }

    /**
     * This method will attempt to load that plug-ins not loaded in the first pass
     */
    private void loadDelayedPlugins() {
        while (!delayedLoadPlugins.isEmpty()) {
            PluginInfo plugin = delayedLoadPlugins.remove(0);
            if (areDependenciesResolved(plugin)) {
                if (!hasValidPluginsToLoadBefore(plugin)) {
                    registerPlugin(plugin);
                } else {
                    delayedLoadPlugins.add(plugin);
                }
            } else {
                // ok, it still hasn't resolved the dependency after the initial
                // load of all plugins. All hope is not lost, however, so let's first
                // look inside the remaining delayed loads before giving up
                boolean foundInDelayed = false;
                for (PluginInfo remainingPlugin : delayedLoadPlugins) {
                    if (isDependentOn(plugin, remainingPlugin)) {
                        foundInDelayed = true;
                        break;
                    }
                }
                if (foundInDelayed) {
                    delayedLoadPlugins.add(plugin);
                } else {
                    failedPlugins.put(plugin.getName(), plugin);
                    LOG.error("ERROR: Plugin [{}] cannot be loaded because its dependencies [{}}] cannot be resolved", plugin.getName(), plugin.getDependsOnNames());
                }
            }
        }
    }

    /**
     * Checks whether the first plugin is dependent on the second plugin.
     *
     * @param plugin     The plugin to check
     * @param dependency The plugin which the first argument may be dependent on
     * @return true if it is
     */
    private boolean isDependentOn(PluginInfo plugin, PluginInfo dependency) {
        for (String name : plugin.getDependsOnNames()) {
            String requiredVersion = plugin.getMetadata().getDependentVersion(name);

            if (name.equals(dependency.getName()) &&
                    GrailsVersionUtils.isValidVersion(dependency.getPluginVersion(), requiredVersion)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasValidPluginsToLoadBefore(PluginInfo plugin) {
        String[] loadAfterNames = plugin.getLoadAfterNames();
        for (PluginInfo other : delayedLoadPlugins) {
            for (String name : loadAfterNames) {
                if (other.getName().equals(name)) {
                    return hasDelayedDependencies(other) || areDependenciesResolved(other);
                }
            }
        }
        return false;
    }

    private boolean hasDelayedDependencies(PluginInfo other) {
        String[] dependencyNames = other.getDependsOnNames();
        for (String dependencyName : dependencyNames) {
            for (PluginInfo grailsPlugin : delayedLoadPlugins) {
                if (grailsPlugin.getName().equals(dependencyName)) {
                    return true;
                }
            }
        }
        return false;
    }

    private void attemptRegisterPlugins(List<PluginInfo> filteredPlugins) {
        for (PluginInfo eligiblePlugin : filteredPlugins) {
            if (areDependenciesResolved(eligiblePlugin) && areNoneToLoadBefore(eligiblePlugin)) {
                registerPlugin(eligiblePlugin);
            } else {
                delayedLoadPlugins.add(eligiblePlugin);
            }
        }
    }

    private void registerPlugin(PluginInfo plugin) {
        if (!plugin.getMetadata().canRegisterPlugin()) {
            if (LOG.isInfoEnabled()) {
                LOG.info("Grails plugin {} is disabled and was not loaded", plugin);
            }
            return;
        }

        if (LOG.isInfoEnabled()) {
            LOG.info("Grails plug-in [" + plugin.getName() + "] with version [" + plugin.getPluginVersion() + "] loaded successfully");
        }

        String[] evictionNames = plugin.getEvictions();
        if (evictionNames.length > 0) {
            delayedEvictions.put(plugin, evictionNames);
        }

        String[] observedPlugins = plugin.getObservedPluginNames();
        for (String observedPlugin : observedPlugins) {
            Set<PluginInfo> observers = pluginToObserverMap.computeIfAbsent(observedPlugin, k -> new HashSet<>());
            observers.add(plugin);
        }
        loadOrderedPlugins.add(plugin);
        plugins.put(plugin.getName(), plugin);
    }

    /**
     * Returns true if there are no plugins left that should, if possible, be loaded before this plugin.
     *
     * @param plugin The plugin
     * @return true if there are
     */
    private boolean areNoneToLoadBefore(PluginInfo plugin) {
        for (String name : plugin.getLoadAfterNames()) {
            if (findPlugin(name) == null) {
                return false;
            }
        }
        return true;
    }

    private boolean areDependenciesResolved(PluginInfo plugin) {
        for (String name : plugin.getMetadata().getDependsOnNames()) {
            if (!hasGrailsPlugin(name, plugin.getMetadata().getDependentVersion(name))) {
                return false;
            }
        }
        return true;
    }

    private boolean hasGrailsPlugin(String name, String version) {
        return findPlugin(name, version) != null;
    }

    private List<PluginInfo> findDynamicPlugins(ClassLoader classLoader) {
        List<PluginInfo> discoveredPlugins = new ArrayList<>();
        LOG.info("Attempting to load [{}] dynamically defined plugins", pluginResources.length);
        for (Resource r : pluginResources) {
            Class<?> pluginClass = loadPluginClass(classLoader, r);
            if (PluginUtils.isGrailsPluginClassNamedCorrectly(pluginClass)) {
                try {
                    PluginInfo pluginInfo = PluginUtils.createPluginInfo(pluginClass, r, true);
                    pluginInfo.isGrailsVersionCompatible(applicationMeta.getGrailsVersion());
                    discoveredPlugins.add(pluginInfo);
                } catch (Exception e) {
                    LOG.warn("Error loading plugin class [{}]; skipping", pluginClass.getName());
                    if (LOG.isDebugEnabled()) {
                        LOG.debug(e.getMessage(), e);
                    }
                }
            } else {
                LOG.warn("Class [{}] loaded from Resource [{}] not loaded as plug-in. Grails plug-ins must end with the convention 'GrailsPlugin'!", pluginClass.getName(), r.getDescription());
            }
        }

        for (Class<?> pluginClass : pluginClasses) {
            if (PluginUtils.isGrailsPluginClassNamedCorrectly(pluginClass)) {
                try {
                    PluginInfo pluginInfo = PluginUtils.createPluginInfo(pluginClass, null, true);
                    pluginInfo.isGrailsVersionCompatible(applicationMeta.getGrailsVersion());
                    discoveredPlugins.add(pluginInfo);
                } catch (Exception e) {
                    LOG.warn("Error loading plugin class [{}]; skipping", pluginClass.getName());
                    if (LOG.isDebugEnabled()) {
                        LOG.debug(e.getMessage(), e);
                    }
                }
            } else {
                LOG.warn("Class [{}] not loaded as plug-in. Grails plug-ins must end with the convention 'GrailsPlugin'!", pluginClass.getName());
            }
        }
        return discoveredPlugins;
    }

    private Class<?> loadPluginClass(ClassLoader cl, Resource r) {
        Class<?> pluginClass;
        if (cl instanceof GroovyClassLoader) {
            try {
                if (LOG.isInfoEnabled()) {
                    LOG.info("Parsing & compiling {}", r.getFilename());
                }
                pluginClass = ((GroovyClassLoader) cl).parseClass(IOGroovyMethods.getText(r.getInputStream(), "UTF-8"));
            } catch (CompilationFailedException e) {
                throw new PluginException("Error compiling plugin [" + r.getFilename() + "] " + e.getMessage(), e);
            } catch (IOException e) {
                throw new PluginException("Error reading plugin [" + r.getFilename() + "] " + e.getMessage(), e);
            }
        } else {
            String className = null;
            try {
                className = GrailsResourceUtils.getClassName(r.getFile().getAbsolutePath());
            } catch (IOException e) {
                throw new PluginException("Cannot find plugin class [" + className + "] resource: [" + r.getFilename() + "]", e);
            }
            try {
                pluginClass = Class.forName(className, true, cl);
            } catch (ClassNotFoundException e) {
                throw new PluginException("Cannot find plugin class [" + className + "] resource: [" + r.getFilename() + "]", e);
            }
        }
        return pluginClass;
    }

    @Override
    public void reset() {
        initialized = false;
        plugins = new LinkedHashMap<>();
        loadOrderedPlugins = new ArrayList<>();
        pluginToObserverMap = new HashMap<>();
        delayedLoadPlugins = new LinkedList<>();
        failedPlugins = new HashMap<>();
        delayedEvictions = new HashMap<>();
        orderedPlugins = new ArrayList<>();
    }
}
