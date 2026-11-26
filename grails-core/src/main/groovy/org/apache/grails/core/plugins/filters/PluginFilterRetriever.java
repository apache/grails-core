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
package org.apache.grails.core.plugins.filters;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

import grails.config.Settings;

/**
 * Resolves the appropriate {@link PluginFilter} implementation from Grails plugin filter configuration.
 *
 * <p>Resolution is driven by the supplied {@link Environment} using the following precedence:</p>
 * <ol>
 *     <li>Bind {@link Settings#PLUGIN_INCLUDES} as a list and create an {@link IncludingPluginFilter} if present.</li>
 *     <li>Fall back to the scalar {@link Settings#PLUGIN_INCLUDES} property for comma-delimited string compatibility.</li>
 *     <li>Bind {@link Settings#PLUGIN_EXCLUDES} as a list and create an {@link ExcludingPluginFilter} if present.</li>
 *     <li>Fall back to the scalar {@link Settings#PLUGIN_EXCLUDES} property for comma-delimited string compatibility.</li>
 *     <li>Return a {@link NoOpPluginFilter} when no include or exclude settings are defined.</li>
 * </ol>
 *
 * <p>The resolved filter is cached per retriever instance, so repeated lookups return the same filter.</p>
 */
public class PluginFilterRetriever {

    private PluginFilter filter;

    /**
     * Returns the cached plugin filter for this retriever, resolving it from the environment on first access.
     *
     * @param environment the environment containing plugin include and exclude configuration
     * @return the resolved plugin filter
     */
    public PluginFilter getPluginFilter(Environment environment) {
        if (filter != null) {
            return filter;
        }

        filter = findPluginFilter(environment);
        return filter;
    }

    /**
     * Resolves the plugin filter to use for the supplied environment.
     *
     * <p>Include settings take precedence over exclude settings. Both list-based binding and scalar
     * comma-delimited compatibility lookups are supported.</p>
     *
     * @param environment the environment containing plugin filter settings
     * @return the resolved plugin filter
     * @throws IllegalArgumentException if {@code environment} is {@code null}
     */
    private PluginFilter findPluginFilter(Environment environment) {
        if (environment == null) {
            throw new IllegalArgumentException("Environment cannot be null");
        }

        var binder = Binder.get(environment);
        var includes = binder.bind(Settings.PLUGIN_INCLUDES, Bindable.listOf(String.class)).orElse(null);
        if (includes != null && !includes.isEmpty()) {
            return new IncludingPluginFilter(toSet(includes));
        }
        var includesCompatibility = environment.getProperty(Settings.PLUGIN_INCLUDES);
        if (StringUtils.hasText(includesCompatibility)) {
            return new IncludingPluginFilter(toSet(StringUtils.commaDelimitedListToSet(includesCompatibility)));
        }

        var excludes = binder.bind(Settings.PLUGIN_EXCLUDES, Bindable.listOf(String.class)).orElse(null);
        if (excludes != null && !excludes.isEmpty()) {
            return new ExcludingPluginFilter(toSet(excludes));
        }
        var excludesCompatibility = environment.getProperty(Settings.PLUGIN_EXCLUDES);
        if (StringUtils.hasText(excludesCompatibility)) {
            return new ExcludingPluginFilter(toSet(StringUtils.commaDelimitedListToSet(excludesCompatibility)));
        }

        return new NoOpPluginFilter();
    }

    /**
     * Normalizes a collection of configured plugin names into an ordered set.
     *
     * <p>Values are trimmed, blank entries are discarded, and the insertion order is preserved.</p>
     *
     * @param values the configured plugin names to normalize
     * @return an ordered set containing the normalized plugin names
     */
    private static Set<String> toSet(Collection<String> values) {
        var set = new LinkedHashSet<String>();
        for (var v : values) {
            if (v == null) {
                continue;
            }

            var s = v.trim();
            if (!s.isEmpty()) {
                set.add(s);
            }
        }
        return set;
    }
}
