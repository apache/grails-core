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
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;

import grails.plugins.exceptions.PluginException;

/**
 * Puts Grails plugins in their one order.
 *
 * <p>A plugin comes after every plugin it depends on and every plugin its {@code after} names, and before
 * every plugin its {@code before} names. A named plugin that is absent is ignored. Plugins the declarations
 * leave unordered relative to each other come in the order of their names, so the order never depends on
 * the order of the class path. A circular order cannot be met, and fails with a {@link PluginException}
 * that names the plugins of the circle.</p>
 *
 * <p>Every part of Grails follows this order: the plugins' configuration, bean registrars and hooks, their
 * artefacts, views, static resources and messages. Where two plugins provide the same thing, the later one
 * wins.</p>
 *
 * @since 7.1
 */
public final class GrailsPluginSorter {

    private GrailsPluginSorter() {
    }

    /**
     * Sorts plugins topologically (Kahn's algorithm), taking the plugin with the lowest name whenever more
     * than one may come next.
     *
     * @param <T> the plugin type
     * @param plugins the plugins to sort, each with a distinct name
     * @param name the logical name of a plugin
     * @param after the names of the plugins a plugin comes after: those it depends on and those its
     *        {@code after} names
     * @param before the names of the plugins a plugin comes before
     * @return a new list with the plugins in their order
     * @throws PluginException if the declarations are circular, or two plugins share a name
     */
    public static <T> List<T> sort(Collection<T> plugins, Function<T, String> name,
            Function<T, ? extends Collection<String>> after, Function<T, ? extends Collection<String>> before) {
        Map<String, T> byName = new TreeMap<>();
        for (T plugin : plugins) {
            T previous = byName.put(name.apply(plugin), plugin);
            if (previous != null) {
                throw new PluginException("Two Grails plugins are named [" + name.apply(plugin) + "]: " + previous +
                        " and " + plugin);
            }
        }

        // successors.get(a) holds the plugins that come after a, and why
        Map<String, Map<String, String>> successors = new HashMap<>();
        Map<String, Integer> predecessorCounts = new HashMap<>();
        byName.keySet().forEach(pluginName -> {
            successors.put(pluginName, new TreeMap<>());
            predecessorCounts.put(pluginName, 0);
        });
        byName.forEach((pluginName, plugin) -> {
            for (String other : after.apply(plugin)) {
                addEdge(successors, predecessorCounts, other, pluginName, pluginName + " comes after " + other);
            }
            for (String other : before.apply(plugin)) {
                addEdge(successors, predecessorCounts, pluginName, other, pluginName + " comes before " + other);
            }
        });

        PriorityQueue<String> ready = new PriorityQueue<>(Comparator.naturalOrder());
        predecessorCounts.forEach((pluginName, count) -> {
            if (count == 0) {
                ready.add(pluginName);
            }
        });
        List<T> sorted = new ArrayList<>(byName.size());
        while (!ready.isEmpty()) {
            String next = ready.poll();
            sorted.add(byName.get(next));
            for (String successor : successors.get(next).keySet()) {
                if (predecessorCounts.merge(successor, -1, Integer::sum) == 0) {
                    ready.add(successor);
                }
            }
        }
        if (sorted.size() < byName.size()) {
            throw new PluginException(circleMessage(successors, predecessorCounts));
        }
        return sorted;
    }

    private static void addEdge(Map<String, Map<String, String>> successors, Map<String, Integer> predecessorCounts,
            String from, String to, String reason) {
        if (from.equals(to)) {
            throw new PluginException("Grails plugin [" + from + "] cannot be ordered: " + reason);
        }
        Map<String, String> edges = successors.get(from);
        // a plugin that is absent is ignored, and a second declaration of an edge adds nothing
        if (edges != null && successors.containsKey(to) && edges.putIfAbsent(to, reason) == null) {
            predecessorCounts.merge(to, 1, Integer::sum);
        }
    }

    /**
     * Describes one circle among the plugins Kahn's algorithm could not place: each of them still has a
     * predecessor among them, so walking back from any one of them returns to a plugin already seen.
     */
    private static String circleMessage(Map<String, Map<String, String>> successors, Map<String, Integer> predecessorCounts) {
        Map<String, String> predecessorOf = new TreeMap<>();
        successors.forEach((from, edges) -> {
            if (predecessorCounts.get(from) > 0) {
                edges.keySet().forEach(to -> {
                    if (predecessorCounts.get(to) > 0) {
                        predecessorOf.putIfAbsent(to, from);
                    }
                });
            }
        });
        Set<String> walked = new LinkedHashSet<>();
        String current = predecessorOf.keySet().iterator().next();
        while (walked.add(current)) {
            current = predecessorOf.get(current);
        }
        List<String> circle = new ArrayList<>();
        boolean inCircle = false;
        for (String pluginName : walked) {
            inCircle = inCircle || pluginName.equals(current);
            if (inCircle) {
                circle.add(pluginName);
            }
        }
        // walked follows predecessors; the circle reads in plugin order the other way round
        Collections.reverse(circle);
        Map<String, String> reasons = new LinkedHashMap<>();
        for (int i = 0; i < circle.size(); i++) {
            String from = circle.get(i);
            String to = circle.get((i + 1) % circle.size());
            reasons.put(from + " -> " + to, successors.get(from).get(to));
        }
        StringBuilder message = new StringBuilder("Grails plugins cannot be ordered, because their order is circular: ")
                .append(String.join(" -> ", circle)).append(" -> ").append(circle.get(0)).append(" (")
                .append(String.join("; ", reasons.values())).append(')');
        return message.toString();
    }
}
