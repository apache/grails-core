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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.core.env.Environment;

import grails.plugins.GrailsPluginSorter;
import grails.plugins.GrailsVersionUtils;
import grails.plugins.exceptions.PluginException;
import grails.util.Metadata;
import org.apache.grails.core.plugins.filters.PluginFilter;
import org.apache.grails.core.plugins.filters.PluginFilterRetriever;

/**
 * Default {@link PluginDiscovery} implementation used during Grails bootstrap.
 *
 * <p>This class discovers plugins from two sources:</p>
 * <ul>
 *     <li>classpath plugin descriptors discovered via {@link ClasspathPluginFinder}</li>
 *     <li>plugin classes registered directly through {@link #setPluginClasses(Class[])}</li>
 * </ul>
 *
 * <p>When {@link #init(Environment)} is invoked, the discovery process loads candidate plugins, applies the
 * configured {@link PluginFilter}, leaves out the plugins that do not support the current Grails environment,
 * checks every plugin's dependencies, applies evictions, and puts the plugins in their one order with
 * {@link GrailsPluginSorter}. Configuration, bean registrars, hooks, artefacts, views and messages all follow
 * that order.</p>
 *
 * <p>Every {@code dependsOn} of every plugin must be met. A plugin whose dependency is missing, has a version
 * outside the required range, was left out by the plugin filter or by its environments, or cannot load itself,
 * stops discovery with one {@link PluginException} that names every unmet dependency of every such plugin.
 * Dependencies are checked before evictions are applied: a plugin depending on a plugin another plugin evicts
 * still loads, the evicting plugin standing in for the evicted one.</p>
 */
public class DefaultPluginDiscovery implements PluginDiscovery {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultPluginDiscovery.class);

    protected Metadata applicationMeta = Metadata.getCurrent();
    protected Class<?>[] pluginClasses = new Class[0];
    protected LinkedHashMap<String, PluginInfo> plugins;
    protected List<PluginInfo> orderedPlugins;
    protected Map<String, PluginInfo> failedPlugins;
    protected Map<String, List<String>> unmetDependencies;
    protected Map<String, PluginInfo> filteredOutPlugins;
    protected Map<String, PluginInfo> environmentExcludedPlugins;
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

    public DefaultPluginDiscovery(Class<?>[] pluginClasses) {
        this();
        setPluginClasses(pluginClasses);
    }

    @Override
    public void setPluginClasses(Class<?>[] pluginClasses) {
        this.pluginClasses = pluginClasses;
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

    /**
     * The plugins that observe the given one, by name or with {@code *}, in plugin order. A plugin never observes
     * itself.
     */
    @Override
    public Collection<PluginInfo> findPluginObservers(PluginInfo plugin) {
        Objects.requireNonNull(plugin, "Argument [plugin] cannot be null");
        validateInitialized();
        var observers = new ArrayList<PluginInfo>();
        for (var candidate : orderedPlugins) {
            if (!candidate.equals(plugin)) {
                var observed = Arrays.asList(candidate.getObservedPluginNames());
                if (observed.contains(plugin.getName()) || observed.contains(PluginUtils.WILDCARD_OBSERVER_PATTERN)) {
                    observers.add(candidate);
                }
            }
        }
        return Collections.unmodifiableList(observers);
    }

    @Override
    public List<PluginInfo> getPluginsInTopologicalOrder() {
        validateInitialized();
        return Collections.unmodifiableList(orderedPlugins);
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
        orderedPlugins = new ArrayList<>();
        failedPlugins = new LinkedHashMap<>();
        unmetDependencies = new LinkedHashMap<>();
        filteredOutPlugins = new HashMap<>();
        environmentExcludedPlugins = new HashMap<>();
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

        var filtered = new HashSet<>(pluginFilter.filterPluginList(plugins));
        return plugins.stream()
                .filter(filtered::contains)
                .toList();
    }

    /**
     * Discovers, filters, checks and orders plugins for the current environment.
     *
     * <p>This method discovers classpath plugins, adds the plugin classes registered directly, applies the
     * configured filter, resets previous discovery state, leaves out the plugins that do not support the current
     * Grails environment, checks the dependencies of the others, applies their evictions and sorts them.</p>
     *
     * @param environment the environment used to determine plugin filtering and load order
     * @throws PluginException if a {@code dependsOn} of any plugin is not met, or the plugins' order is circular
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
            LOG.debug("No Grails plugins found via META-INF/grails/plugins.imports descriptors");
            throw new IllegalStateException(
                    "Grails was unable to load plugins dynamically. This is normally a " +
                            "problem with the container class loader configuration, see " +
                            "troubleshooting and FAQ for more info.");
        }

        var allPlugins = new ArrayList<>(classpathPlugins);
        allPlugins.addAll(registeredPlugins());

        var filteredPlugins = filterPlugins(allPlugins, environment);

        reset();
        initialized = true;

        for (var plugin : allPlugins) {
            if (!filteredPlugins.contains(plugin)) {
                filteredOutPlugins.putIfAbsent(plugin.getName(), plugin);
            }
        }
        var candidates = supportedInCurrentEnvironment(filteredPlugins);

        if (candidates.isEmpty()) {
            LOG.debug("All plugins were excluded by plugin filtering or by their environments");
            return;
        }

        for (var candidate : candidates) {
            registerPlugin(candidate);
        }
        removePluginsWithUnmetDependencies();
        if (!unmetDependencies.isEmpty()) {
            initialized = false;
            throw new PluginException(unmetDependenciesMessage());
        }
        applyEvictions();

        try {
            orderedPlugins = GrailsPluginSorter.sort(plugins.values(), PluginInfo::getName,
                    plugin -> plugin.getDefinition().getOrderedAfter(), plugin -> plugin.getDefinition().getBefore());
        }
        catch (PluginException e) {
            initialized = false;
            throw e;
        }
    }

    /**
     * The plugins that support the current Grails environment. The others are recorded, so a plugin that depends on
     * one of them is reported as depending on a plugin its environments leave out.
     */
    private List<PluginInfo> supportedInCurrentEnvironment(List<PluginInfo> filteredPlugins) {
        var current = grails.util.Environment.getCurrent();
        var supported = new ArrayList<PluginInfo>();
        for (var plugin : filteredPlugins) {
            if (plugin.supportsEnvironment(current)) {
                supported.add(plugin);
            } else {
                environmentExcludedPlugins.putIfAbsent(plugin.getName(), plugin);
                LOG.info("Grails plugin {} does not support the [{}] environment and was not loaded", plugin, current.getName());
            }
        }
        return supported;
    }

    private void validateInitialized() {
        if (!initialized) {
            throw new IllegalStateException("init() must be called prior to fetching the plugin order.");
        }
    }

    /**
     * Removes every plugin a dependency of which is not met, recording why. A plugin depending on one removed
     * this way cannot load either, so removing repeats until every remaining plugin has its dependencies. Each
     * round checks every plugin against the same plugins, so what is reported does not depend on the order the
     * plugins were found in.
     */
    private void removePluginsWithUnmetDependencies() {
        boolean removed = true;
        while (removed) {
            var unmetThisRound = new LinkedHashMap<PluginInfo, List<String>>();
            for (var plugin : plugins.values()) {
                var unmet = unresolvedDependencies(plugin);
                if (!unmet.isEmpty()) {
                    unmetThisRound.put(plugin, unmet);
                }
            }
            unmetThisRound.forEach((plugin, unmet) -> {
                plugins.remove(plugin.getName());
                failedPlugins.put(plugin.getName(), plugin);
                unmetDependencies.put(plugin.getName(), unmet);
            });
            removed = !unmetThisRound.isEmpty();
        }
    }

    /**
     * Applies the evictions of the plugins that remain. Evictions are applied together, after the dependencies
     * were checked, so a plugin that evicts another one stands in for it as a dependency.
     */
    private void applyEvictions() {
        var evictions = new LinkedHashMap<PluginInfo, String[]>();
        for (var plugin : plugins.values()) {
            if (plugin.getEvictions().length > 0) {
                evictions.put(plugin, plugin.getEvictions());
            }
        }
        evictions.forEach((evictor, names) -> {
            for (var name : names) {
                var evicted = plugins.remove(PluginUtils.normalizePluginName(name));
                if (evicted != null && LOG.isInfoEnabled()) {
                    LOG.info("Grails plug-in {} was evicted by {}", evicted, evictor);
                }
            }
        });
    }

    /**
     * The message of the exception that stops discovery: one line for every plugin that cannot be loaded, naming
     * each of its unmet dependencies.
     */
    private String unmetDependenciesMessage() {
        var message = new StringBuilder("Grails plug-ins cannot be loaded because their dependencies are not met:");
        unmetDependencies.forEach((name, unmet) -> {
            var plugin = failedPlugins.get(name);
            message.append(System.lineSeparator())
                    .append("- Grails plug-in [").append(name).append(']');
            if (plugin.getPluginVersion() != null) {
                message.append(" with version [").append(plugin.getPluginVersion()).append(']');
            }
            message.append(" cannot be loaded: ").append(String.join("; ", unmet));
        });
        return message.toString();
    }

    private List<String> unresolvedDependencies(PluginInfo plugin) {
        var unresolvedDependencies = new ArrayList<String>();
        for (var name : plugin.getDependsOnNames()) {
            var requiredVersion = plugin.getDependentVersion(name);
            var normalizedName = PluginUtils.normalizePluginName(name);
            var dependency = plugins.get(normalizedName);
            if (dependency != null) {
                if (!GrailsVersionUtils.isValidVersion(dependency.getPluginVersion(), requiredVersion)) {
                    unresolvedDependencies.add(
                            "dependency [" + name + "] has version [" + dependency.getPluginVersion() +
                                    "] but requires [" + requiredVersion + "]"
                    );
                }
            } else if (unmetDependencies.containsKey(normalizedName)) {
                unresolvedDependencies.add(
                        "dependency [" + name + "] with required version [" + requiredVersion + "] failed to load"
                );
            } else if (environmentExcludedPlugins.containsKey(normalizedName)) {
                unresolvedDependencies.add(
                        "dependency [" + name + "] does not support the [" + grails.util.Environment.getCurrent().getName() +
                                "] environment; leave [" + plugin.getName() + "] out of that environment too, with " +
                                "its environmentsIncludes or environmentsExcludes"
                );
            } else if (filteredOutPlugins.containsKey(normalizedName)) {
                unresolvedDependencies.add(
                        "dependency [" + name + "] is left out by the plugin filter (grails.plugin.excludes or " +
                                "grails.plugin.includes); leave out [" + plugin.getName() + "] too"
                );
            } else {
                unresolvedDependencies.add(
                        "dependency [" + name + "] with required version [" + requiredVersion + "] is missing"
                );
            }
        }
        return unresolvedDependencies;
    }

    /**
     * Registers a candidate plugin, skipping one already registered under the same name (a plugin may be
     * declared by more than one descriptor on the classpath): the first one found wins.
     *
     * @param plugin the plugin to register
     */
    private void registerPlugin(PluginInfo plugin) {
        var registeredPlugin = plugins.get(plugin.getName());
        if (registeredPlugin != null) {
            LOG.warn(
                    "Grails plug-in [{}] with version [{}] is already registered; " +
                            "skipping duplicate declaration with version [{}]",
                    plugin.getName(),
                    registeredPlugin.getPluginVersion(),
                    plugin.getPluginVersion()
            );
            return;
        }

        if (LOG.isDebugEnabled()) {
            LOG.debug(
                    "Grails plug-in [{}] with version [{}] loaded successfully",
                    plugin.getName(),
                    plugin.getPluginVersion()
            );
        }
        plugins.put(plugin.getName(), plugin);
    }

    /**
     * The plugins whose classes were registered directly, each described by its {@code @GrailsPluginDescriptor}.
     */
    private List<PluginInfo> registeredPlugins() {
        var registered = new ArrayList<PluginInfo>();
        for (var pluginClass : pluginClasses) {
            var pluginInfo = PluginUtils.createPluginInfo(pluginClass);
            pluginInfo.isGrailsVersionCompatible(applicationMeta.getGrailsVersion());
            registered.add(pluginInfo);
        }
        return registered;
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
}
