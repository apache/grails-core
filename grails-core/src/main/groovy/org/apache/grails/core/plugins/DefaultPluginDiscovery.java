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

import grails.plugins.GrailsPluginSorter;
import grails.plugins.GrailsVersionUtils;
import grails.plugins.exceptions.PluginException;
import grails.util.Metadata;
import org.apache.grails.core.plugins.filters.PluginFilter;
import org.apache.grails.core.plugins.filters.PluginFilterRetriever;
import org.grails.core.io.CachingPathMatchingResourcePatternResolver;

/**
 * Default {@link PluginDiscovery} implementation used during Grails bootstrap.
 *
 * <p>This class discovers plugins from two sources:</p>
 * <ul>
 *     <li>classpath plugin descriptors discovered via {@link ClasspathPluginFinder}</li>
 *     <li>dynamically supplied plugin classes or Groovy resources configured through the setter methods</li>
 * </ul>
 *
 * <p>When {@link #init(Environment)} is invoked, the discovery process loads candidate plugins, applies the
 * configured {@link PluginFilter}, resolves load ordering and delayed dependencies, and exposes both the
 * load order and the topologically sorted order required by later bootstrap components.</p>
 */
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
    protected boolean initialized = false;

    public DefaultPluginDiscovery() {
        this(new PluginFilterRetriever());
    }

    public DefaultPluginDiscovery(PluginFilterRetriever filterRetriever) {
        this.filterRetriever = filterRetriever;
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

    public DefaultPluginDiscovery(Resource[] pluginFiles) {
        this();
        setPluginResources(pluginFiles);
    }

    @Override
    public void setPluginResources(String[] pluginResources) {
        var resolver = CachingPathMatchingResourcePatternResolver.INSTANCE;
        var resourceList = new ArrayList<Resource>();
        for (var resourcePath : pluginResources) {
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
        var resolver = CachingPathMatchingResourcePatternResolver.INSTANCE;
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

    @Override
    public boolean hasPlugin(String name) {
        return plugins.containsKey(PluginUtils.normalizePluginName(name));
    }

    @Override
    public PluginInfo findPlugin(String pluginName) {
        validateInitialized();
        return plugins.get(PluginUtils.normalizePluginName(pluginName));
    }

    @Override
    public PluginInfo findPlugin(String pluginName, Object version) {
        validateInitialized();
        var plugin = plugins.get(PluginUtils.normalizePluginName(pluginName));
        if (plugin != null && GrailsVersionUtils.isValidVersion(plugin.getPluginVersion(), version.toString())) {
            return plugin;
        }
        return null;
    }

    @Override
    public Collection<PluginInfo> findPluginObservers(PluginInfo plugin) {
        Objects.requireNonNull(plugin, "Argument [plugin] cannot be null");

        var observers = pluginToObserverMap.get(plugin.getName());

        // Add any wildcard observers.
        var wildcardObservers = pluginToObserverMap.get(PluginUtils.WILDCARD_OBSERVER_PATTERN);
        if (wildcardObservers != null) {
            if (observers != null) {
                observers.addAll(wildcardObservers);
            } else {
                observers = wildcardObservers;
            }
        }

        if (observers != null) {
            // Make sure this plugin is not observing itself!
            observers.remove(plugin);
            return observers;
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

    @Override
    public void init(Environment environment) {
        if (!initialized) {
            if (environment == null) {
                throw new IllegalArgumentException("Environment must be provided to determine plugin order");
            }
            populatePlugins(environment);
        }
    }

    /**
     * Resets all derived discovery state so plugin discovery can be performed again.
     *
     * <p>This is primarily intended for controlled scenarios such as tests.</p>
     */
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

    @Override
    public Collection<PluginInfo> getDynamicPlugins() {
        return Collections.unmodifiableList(dynamicPlugins);
    }

    @Override
    public Collection<PluginInfo> getFailedPlugins() {
        return Collections.unmodifiableCollection(failedPlugins.values());
    }

    @Override
    public PluginInfo getFailedPlugin(String name) {
        return failedPlugins.get(PluginUtils.normalizePluginName(name));
    }

    @Override
    public boolean hasFailedPlugin(String name) {
        return failedPlugins.containsKey(PluginUtils.normalizePluginName(name));
    }

    /**
     * Applies the configured plugin filter to the discovered plugins.
     *
     * <p>If no explicit filter has been set, the filter is resolved from the supplied environment through
     * {@link PluginFilterRetriever}.</p>
     *
     * @param plugins the discovered plugins to filter
     * @param environment the environment used to resolve a filter when necessary
     * @return the filtered plugins in their original encounter order
     */
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

    /**
     * Discovers, filters, and orders plugins for the current environment.
     *
     * <p>This method discovers classpath plugins, collects any dynamically supplied plugins, applies the
     * configured filter, resets previous discovery state, and then registers plugins immediately or defers
     * them until their dependencies can be satisfied.</p>
     *
     * @param environment the environment used to determine plugin filtering and load order
     */
    void populatePlugins(Environment environment) {
        var classLoader = resolveClassLoader();
        List<PluginInfo> classpathPlugins;
        if (loadClasspathPlugins) {
            classpathPlugins = new ClasspathPluginFinder()
                    .findClasspathPlugins(classLoader, applicationMeta.getGrailsVersion());
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

        var allPlugins = new ArrayList<>(classpathPlugins);
        allPlugins.addAll(dynamicPlugins);

        var filteredPlugins = filterPlugins(allPlugins, environment);

        reset();
        initialized = true;

        if (filteredPlugins.isEmpty()) {
            LOG.debug("All plugins were excluded by plugin filtering");
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
    }

    protected void evictPlugin(PluginInfo evictor, String evicteeName) {
        var pluginToEvict = plugins.get(evicteeName);
        if (pluginToEvict != null) {
            orderedPlugins.remove(pluginToEvict);
            loadOrderedPlugins.remove(pluginToEvict);
            plugins.remove(pluginToEvict.getName());

            if (LOG.isInfoEnabled()) {
                LOG.info("Grails plug-in {} was evicted by {}", pluginToEvict, evictor);
            }
        }
    }

    private void validateInitialized() {
        if (!initialized) {
            throw new IllegalStateException("init() must be called prior to fetching the plugin order.");
        }
    }

    private void processDelayedEvictions() {
        for (var entry : delayedEvictions.entrySet()) {
            var plugin = entry.getKey();
            for (var pluginName : entry.getValue()) {
                evictPlugin(plugin, pluginName);
            }
        }
    }

    /**
     * Attempts to register plugins that could not be loaded during the first registration pass.
     *
     * <p>Plugins remain delayed until their dependencies are available and any declared {@code loadAfter}
     * relationships can be satisfied. Plugins whose dependencies can never be resolved are moved to the
     * failed plugin collection.</p>
     */
    private void loadDelayedPlugins() {
        while (!delayedLoadPlugins.isEmpty()) {
            var plugin = delayedLoadPlugins.remove(0);
            if (areDependenciesResolved(plugin)) {
                if (!hasValidPluginsToLoadBefore(plugin)) {
                    registerPlugin(plugin);
                } else {
                    delayedLoadPlugins.add(plugin);
                }
            } else {
                // ok, it still hasn't resolved the dependency after the initial
                // load of all plugins. All hopes are not lost, however, so let's first
                // look inside the remaining delayed loads before giving up
                boolean foundInDelayed = false;
                for (var remainingPlugin : delayedLoadPlugins) {
                    if (isDependentOn(plugin, remainingPlugin)) {
                        foundInDelayed = true;
                        break;
                    }
                }
                if (foundInDelayed) {
                    delayedLoadPlugins.add(plugin);
                } else {
                    failedPlugins.put(plugin.getName(), plugin);
                    LOG.error(
                            "ERROR: Plugin [{}] cannot be loaded because its dependencies [{}}] cannot be resolved",
                            plugin.getName(),
                            plugin.getDependsOnNames()
                    );
                }
            }
        }
    }

    /**
     * Checks whether the first plugin is dependent on the second plugin.
     *
     * @param plugin the plugin to check
     * @param dependency the plugin that may satisfy one of {@code plugin}'s declared dependencies
     * @return {@code true} if {@code plugin} depends on {@code dependency}
     */
    private boolean isDependentOn(PluginInfo plugin, PluginInfo dependency) {
        for (var name : plugin.getDependsOnNames()) {
            var requiredVersion = plugin.getMetadata().getDependentVersion(name);
            if (name.equals(dependency.getName()) &&
                    GrailsVersionUtils.isValidVersion(dependency.getPluginVersion(), requiredVersion)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasValidPluginsToLoadBefore(PluginInfo plugin) {
        var loadAfterNames = plugin.getLoadAfterNames();
        for (var other : delayedLoadPlugins) {
            for (var name : loadAfterNames) {
                if (other.getName().equals(name)) {
                    return hasDelayedDependencies(other) || areDependenciesResolved(other);
                }
            }
        }
        return false;
    }

    private boolean hasDelayedDependencies(PluginInfo other) {
        var dependencyNames = other.getDependsOnNames();
        for (var dependencyName : dependencyNames) {
            for (var grailsPlugin : delayedLoadPlugins) {
                if (grailsPlugin.getName().equals(dependencyName)) {
                    return true;
                }
            }
        }
        return false;
    }

    private void attemptRegisterPlugins(List<PluginInfo> filteredPlugins) {
        for (var eligiblePlugin : filteredPlugins) {
            if (areDependenciesResolved(eligiblePlugin) && areNoneToLoadBefore(eligiblePlugin)) {
                registerPlugin(eligiblePlugin);
            } else {
                delayedLoadPlugins.add(eligiblePlugin);
            }
        }
    }

    /**
     * Registers a plugin that is eligible to be loaded.
     *
     * <p>This method skips disabled plugins, records eviction and observer relationships, and appends the
     * plugin to the discovered load order.</p>
     *
     * @param plugin the plugin to register
     */
    private void registerPlugin(PluginInfo plugin) {
        if (!plugin.getMetadata().canRegisterPlugin()) {
            if (LOG.isInfoEnabled()) {
                LOG.info("Grails plugin {} is disabled and was not loaded", plugin);
            }
            return;
        }

        if (LOG.isInfoEnabled()) {
            LOG.info(
                    "Grails plug-in [{}] with version [{}] loaded successfully",
                    plugin.getName(),
                    plugin.getPluginVersion()
            );
        }

        var evictionNames = plugin.getEvictions();
        if (evictionNames.length > 0) {
            delayedEvictions.put(plugin, evictionNames);
        }

        var observedPlugins = plugin.getObservedPluginNames();
        for (var observedPlugin : observedPlugins) {
            var observers = pluginToObserverMap.computeIfAbsent(observedPlugin, k -> new HashSet<>());
            observers.add(plugin);
        }
        loadOrderedPlugins.add(plugin);
        plugins.put(plugin.getName(), plugin);
    }

    /**
     * Returns true if there are no plugins left that should, if possible, be loaded before this plugin.
     *
     * @param plugin the plugin to check
     * @return {@code true} if every declared {@code loadAfter} plugin has already been registered
     */
    private boolean areNoneToLoadBefore(PluginInfo plugin) {
        for (var name : plugin.getLoadAfterNames()) {
            if (findPlugin(name) == null) {
                return false;
            }
        }
        return true;
    }

    private boolean areDependenciesResolved(PluginInfo plugin) {
        for (var name : plugin.getMetadata().getDependsOnNames()) {
            if (!hasGrailsPlugin(name, plugin.getMetadata().getDependentVersion(name))) {
                return false;
            }
        }
        return true;
    }

    private boolean hasGrailsPlugin(String name, String version) {
        return findPlugin(name, version) != null;
    }

    /**
     * Discovers dynamically supplied plugins from configured Groovy resources and explicit plugin classes.
     *
     * <p>Resource-based plugins are compiled with a {@link GroovyClassLoader}, while pre-supplied plugin
     * classes are converted directly into {@link PluginInfo} instances.</p>
     *
     * @param classLoader the base class loader used to discover and compile dynamic plugins
     * @return the dynamically discovered plugins
     */
    private List<PluginInfo> findDynamicPlugins(ClassLoader classLoader) {
        var discoveredPlugins = new ArrayList<PluginInfo>();
        var pluginResourceClassLoader = resolvePluginResourceClassLoader(classLoader);
        LOG.info("Attempting to load [{}] dynamically defined plugins", pluginResources.length);
        for (var resource : pluginResources) {
            var pluginClass = loadPluginClass(pluginResourceClassLoader, resource);
            if (PluginUtils.isGrailsPluginClassNamedCorrectly(pluginClass)) {
                try {
                    var pluginInfo = PluginUtils.createPluginInfo(pluginClass, resource, true);
                    pluginInfo.isGrailsVersionCompatible(applicationMeta.getGrailsVersion());
                    discoveredPlugins.add(pluginInfo);
                } catch (Exception e) {
                    logPluginLoadingError(pluginClass, e);
                }
            } else {
                LOG.warn(
                        "Class [{}] loaded from Resource [{}] not loaded as plug-in. Grails plug-ins must end with " +
                                "the convention 'GrailsPlugin'!",
                        pluginClass.getName(),
                        resource.getDescription()
                );
            }
        }

        for (var pluginClass : pluginClasses) {
            if (PluginUtils.isGrailsPluginClassNamedCorrectly(pluginClass)) {
                try {
                    var pluginInfo = PluginUtils.createPluginInfo(pluginClass, null, true);
                    pluginInfo.isGrailsVersionCompatible(applicationMeta.getGrailsVersion());
                    discoveredPlugins.add(pluginInfo);
                } catch (Exception e) {
                    logPluginLoadingError(pluginClass, e);
                }
            } else {
                LOG.warn("Class [{}] not loaded as plug-in. Grails plug-ins must end with the convention 'GrailsPlugin'!", pluginClass.getName());
            }
        }
        return discoveredPlugins;
    }

    private void logPluginLoadingError(Class<?> pluginClass, Exception e) {
        LOG.warn("Error loading plugin class [{}]; skipping", pluginClass.getName());
        if (LOG.isDebugEnabled()) {
            LOG.debug(e.getMessage(), e);
        }
    }

    /**
     * Resolves the base class loader for plugin discovery.
     *
     * @return the thread context class loader, or this class's loader if the thread context loader is not set
     */
    private ClassLoader resolveClassLoader() {
        var classLoader = Thread.currentThread().getContextClassLoader();
        if (classLoader == null) {
            classLoader = DefaultPluginDiscovery.class.getClassLoader();
        }
        return classLoader;
    }

    /**
     * Resolves a {@link GroovyClassLoader} for compiling plugin resources expressed as Groovy source files.
     *
     * @param classLoader the base class loader for plugin discovery
     * @return a Groovy-capable class loader backed by the supplied class loader
     */
    private GroovyClassLoader resolvePluginResourceClassLoader(ClassLoader classLoader) {
        if (classLoader instanceof GroovyClassLoader) {
            return (GroovyClassLoader) classLoader;
        }
        if (LOG.isDebugEnabled()) {
            LOG.debug("Wrapping class loader [{}] in GroovyClassLoader for dynamic plugin resource compilation", classLoader);
        }
        return new GroovyClassLoader(classLoader);
    }

    /**
     * Compiles a plugin class from a Groovy resource.
     *
     * @param cl the Groovy class loader used to compile the resource
     * @param r the plugin resource to compile
     * @return the compiled plugin class
     * @throws PluginException if the resource cannot be read or compiled
     */
    private Class<?> loadPluginClass(GroovyClassLoader cl, Resource r) {
        try {
            if (LOG.isInfoEnabled()) {
                LOG.info("Parsing & compiling {}", r.getFilename());
            }
            return cl.parseClass(IOGroovyMethods.getText(r.getInputStream(), "UTF-8"));
        } catch (CompilationFailedException e) {
            throw new PluginException("Error compiling plugin [" + r.getFilename() + "] " + e.getMessage(), e);
        } catch (IOException e) {
            throw new PluginException("Error reading plugin [" + r.getFilename() + "] " + e.getMessage(), e);
        }
    }
}
