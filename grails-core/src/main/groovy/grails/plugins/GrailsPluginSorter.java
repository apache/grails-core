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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Canonical topological sort for Grails plugins based on {@code loadAfter}
 * and {@code loadBefore} declarations.
 *
 * <p>This class provides the single shared implementation of plugin ordering that
 * is used by both {@link grails.plugins.DefaultGrailsPluginManager} (at runtime, operating on
 * {@link grails.plugins.GrailsPlugin} instances) and
 * {@link grails.boot.config.GrailsEnvironmentPostProcessor} (early in the
 * lifecycle, operating on lightweight {@code PluginInfo} records before the
 * ApplicationContext is available).</p>
 *
 * <p>The algorithm is a DFS-based topological sort:</p>
 * <ol>
 *   <li>{@code loadAfter} creates edges from a plugin to its
 *       dependencies (the plugin must load <em>after</em> the named plugins).</li>
 *   <li>{@code loadBefore} creates reverse edges (the <em>named</em> plugin must
 *       load after <em>this</em> plugin).</li>
 *   <li>A recursive DFS visits dependencies first, then adds the current node,
 *       producing a valid topological order.</li>
 * </ol>
 *
 * <p><strong>Note:</strong> {@code dependsOn} is intentionally <em>not</em> used
 * for ordering. In the original {@link grails.plugins.DefaultGrailsPluginManager}, {@code dependsOn}
 * is only used for dependency <em>resolution</em> (checking that required plugins
 * are present), not for determining load order. Load order is controlled exclusively
 * by {@code loadAfter} and {@code loadBefore}.</p>
 *
 * <p>The sort is generic: callers provide accessor functions so the same algorithm
 * works with any plugin representation (full {@link grails.plugins.GrailsPlugin} objects, lightweight
 * metadata records, etc.).</p>
 *
 * @since 7.1
 */
public final class GrailsPluginSorter {

    private GrailsPluginSorter() {
        // utility class
    }

    /**
     * Performs a topological sort of the given plugins.
     *
     * <p>This is the single canonical sort algorithm used across the framework to
     * ensure that plugin load order is identical whether plugins are loaded early
     * (by the {@code grails.boot.config.GrailsEnvironmentPostProcessor} for configuration)
     * or later (by the {@code grails.plugins.DefaultGrailsPluginManager} for full lifecycle).</p>
     *
     * @param <T>               the plugin type
     * @param plugins           the plugins to sort
     * @param loadAfterExtractor extracts the {@code loadAfter} names from a plugin
     * @param loadBeforeExtractor extracts the {@code loadBefore} names from a plugin
     * @param pluginLookup      resolves a plugin by its logical name (returns {@code null}
     *                          if the named plugin is not present)
     * @return a new list with the plugins in topological order
     */
    static <T> List<T> sortPlugins(
            List<T> plugins,
            Function<T, String[]> loadAfterExtractor,
            Function<T, String[]> loadBeforeExtractor,
            Function<String, T> pluginLookup) {

        Map<T, List<T>> loadOrderDependencies = resolveLoadDependencies(
                plugins, loadAfterExtractor, loadBeforeExtractor, pluginLookup);

        List<T> sorted = new ArrayList<>(plugins.size());
        Set<T> visited = new HashSet<>();

        for (T plugin : plugins) {
            visitTopologicalSort(plugin, sorted, visited, loadOrderDependencies);
        }

        return sorted;
    }

    /**
     * Overload that builds the lookup function from the plugin list itself.
     *
     * <p>This is convenient when there is no external registry (e.g., in the
     * {@code EnvironmentPostProcessor} where plugins are represented as lightweight
     * records rather than managed by a plugin manager).</p>
     *
     * @param <T>               the plugin type
     * @param plugins           the plugins to sort
     * @param nameExtractor     extracts the logical plugin name from a plugin
     * @param loadAfterExtractor extracts the {@code loadAfter} names
     * @param loadBeforeExtractor extracts the {@code loadBefore} names
     * @return a new list with the plugins in topological order
     */
    public static <T> List<T> sort(
            List<T> plugins,
            Function<T, String> nameExtractor,
            Function<T, String[]> loadAfterExtractor,
            Function<T, String[]> loadBeforeExtractor) {

        Map<String, T> pluginsByName = new HashMap<>();
        for (T plugin : plugins) {
            pluginsByName.putIfAbsent(nameExtractor.apply(plugin), plugin);
        }

        return sortPlugins(plugins, loadAfterExtractor, loadBeforeExtractor, pluginsByName::get);
    }

    /**
     * Resolves load-order dependencies for all plugins, building an adjacency map
     * for the topological sort.
     *
     * <p>Two types of ordering declarations are handled:</p>
     * <ul>
     *   <li>{@code loadAfter}: this plugin should load after the named plugins</li>
     *   <li>{@code loadBefore}: the named plugins should load after this plugin
     *       (creates reverse edges)</li>
     * </ul>
     *
     * <p>{@code dependsOn} is intentionally not included here. It is used for
     * dependency resolution (ensuring required plugins are present) but does not
     * affect load ordering. This matches the original behavior in
     * {@link grails.plugins.DefaultGrailsPluginManager}.</p>
     */
    private static <T> Map<T, List<T>> resolveLoadDependencies(
            List<T> plugins,
            Function<T, String[]> loadAfterExtractor,
            Function<T, String[]> loadBeforeExtractor,
            Function<String, T> pluginLookup) {

        Map<T, List<T>> loadOrderDependencies = new HashMap<>();

        for (T plugin : plugins) {
            // loadAfter: this plugin should load after the named plugins
            addEdges(loadOrderDependencies, loadAfterExtractor, plugin, pluginLookup, true);
            // loadBefore: the named plugins should load after this plugin
            addEdges(loadOrderDependencies, loadBeforeExtractor, plugin, pluginLookup, false);
        }

        return loadOrderDependencies;
    }

    /**
     * Adds dependency edges for a single plugin based on an extractor function.
     *
     * <p>When {@code forwardEdge} is {@code true} (loadAfter), the edge points from
     * {@code plugin} to the looked-up plugin (this plugin depends on the named one).
     * When {@code false} (loadBefore), the edge points from the looked-up plugin to
     * {@code plugin} (the named plugin depends on this one).</p>
     */
    private static <T> void addEdges(
            Map<T, List<T>> loadOrderDependencies,
            Function<T, String[]> extractor,
            T plugin,
            Function<String, T> pluginLookup,
            boolean forwardEdge) {

        String[] names = extractor.apply(plugin);
        if (names != null) {
            for (String name : names) {
                T other = pluginLookup.apply(name);
                if (other != null) {
                    T dependent = forwardEdge ? plugin : other;
                    T dependency = forwardEdge ? other : plugin;
                    loadOrderDependencies.computeIfAbsent(dependent, k -> new ArrayList<>()).add(dependency);
                }
            }
        }
    }

    /**
     * Recursive DFS visit for topological sort.
     *
     * <p>Visits all dependencies of the given plugin first, then adds the plugin
     * itself to the sorted list. This produces a valid topological ordering where
     * dependencies appear before the plugins that depend on them.</p>
     */
    private static <T> void visitTopologicalSort(
            T plugin, List<T> sorted, Set<T> visited,
            Map<T, List<T>> loadOrderDependencies) {

        if (plugin != null && !visited.contains(plugin)) {
            visited.add(plugin);
            List<T> deps = loadOrderDependencies.get(plugin);
            if (deps != null) {
                for (T dep : deps) {
                    visitTopologicalSort(dep, sorted, visited, loadOrderDependencies);
                }
            }
            sorted.add(plugin);
        }
    }
}
