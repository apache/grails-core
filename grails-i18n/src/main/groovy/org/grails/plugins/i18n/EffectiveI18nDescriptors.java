/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.grails.plugins.i18n;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.grails.core.plugins.PluginUtils;

/**
 * The message bundles that actually participate, in the order Spring Boot should consult them.
 *
 * <p>Filtering happens here, once, and both consumers read the result: the base-name list handed to
 * {@code spring.messages.basename}, and the locales {@link AvailableLocaleResolver} offers. Were they
 * to filter separately, a plugin excluded from message resolution could still advertise its language
 * in a locale picker — offering a translation whose messages cannot resolve.</p>
 *
 * <p>Only plugins the application actually discovered contribute. A descriptor whose plugin was
 * evicted, filtered out by environment, or failed to load is ignored even though its jar is still on
 * the classpath.</p>
 *
 * <h2>Why plugin base names are reversed</h2>
 *
 * <p>Spring's {@code ResourceBundleMessageSource} resolves a code against base names in order and
 * takes the first match. The message source Grails used previously merged plugin bundles with
 * {@code Map.putAll} while iterating {@code GrailsPluginManager.getAllPlugins()}, so the <em>last</em>
 * plugin in that iteration overwrote earlier ones. {@code getAllPlugins()} returns plugins in
 * <em>topological</em> order, so reversing that order and letting the first match win reproduces the
 * previous precedence exactly.</p>
 *
 * <p>Note this cannot be reasoned about by analogy with plugin <em>configuration</em>, which uses the
 * opposite convention: there, earlier plugins take precedence.</p>
 *
 * @since 8.0
 */
public final class EffectiveI18nDescriptors {

    private final List<String> basenames;

    private final List<String> locales;

    private EffectiveI18nDescriptors(List<String> basenames, List<String> locales) {
        this.basenames = List.copyOf(basenames);
        this.locales = List.copyOf(locales);
    }

    /**
     * Resolves the effective set.
     *
     * @param descriptors every descriptor found on the classpath, in any order
     * @param pluginNamesInTopologicalOrder the names of the plugins the application discovered, in
     *        the topological order {@code GrailsPluginManager.getAllPlugins()} uses
     * @param includePluginBundles whether plugin bundles participate at all
     * @return the effective base names and locales
     */
    public static EffectiveI18nDescriptors of(List<I18nDescriptor> descriptors,
            List<String> pluginNamesInTopologicalOrder, boolean includePluginBundles) {

        Map<String, I18nDescriptor> pluginDescriptors = new LinkedHashMap<>();
        List<I18nDescriptor> applications = new ArrayList<>();
        for (I18nDescriptor descriptor : descriptors) {
            if (descriptor.isApplication()) {
                applications.add(descriptor);
            }
            else {
                // Descriptors record the hyphenated plugin name, matching the bundle base-name
                // convention (spring-security-core), while a discovered plugin reports the logical
                // camel-case form (springSecurityCore). Normalising both sides is what lets the two
                // meet; comparing them raw silently drops every multi-word plugin's bundles.
                pluginDescriptors.put(PluginUtils.normalizePluginName(descriptor.name()), descriptor);
            }
        }

        List<I18nDescriptor> effectivePlugins = new ArrayList<>();
        if (includePluginBundles) {
            for (String pluginName : pluginNamesInTopologicalOrder) {
                I18nDescriptor descriptor = pluginDescriptors.get(PluginUtils.normalizePluginName(pluginName));
                if (descriptor != null) {
                    effectivePlugins.add(descriptor);
                }
            }
        }

        Set<String> basenames = new LinkedHashSet<>();
        Set<String> locales = new LinkedHashSet<>();
        for (I18nDescriptor application : applications) {
            basenames.addAll(application.basenames());
            locales.addAll(application.locales());
        }
        for (int i = effectivePlugins.size() - 1; i >= 0; i--) {
            basenames.addAll(effectivePlugins.get(i).basenames());
        }
        for (I18nDescriptor plugin : effectivePlugins) {
            locales.addAll(plugin.locales());
        }

        List<String> sortedLocales = new ArrayList<>(locales);
        sortedLocales.sort(null);
        return new EffectiveI18nDescriptors(new ArrayList<>(basenames), sortedLocales);
    }

    /**
     * Base names in precedence order: the application's own first, then each participating plugin's in
     * reverse topological order.
     *
     * @return the base names, never {@code null}
     */
    public List<String> basenames() {
        return this.basenames;
    }

    /**
     * The locale identifiers ({@code de}, {@code pt_BR}) the participating bundles are translated
     * into, deduplicated and sorted.
     *
     * @return the locale identifiers, never {@code null}
     */
    public List<String> locales() {
        return this.locales;
    }
}
