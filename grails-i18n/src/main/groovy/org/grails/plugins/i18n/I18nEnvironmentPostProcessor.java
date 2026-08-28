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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.bootstrap.ConfigurableBootstrapContext;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;

import grails.config.Settings;
import grails.util.Environment;
import org.apache.grails.core.plugins.PluginDiscovery;
import org.apache.grails.core.plugins.PluginInfo;

/**
 * Composes {@code spring.messages.basename} from the build-time i18n descriptors, so that Spring
 * Boot's own {@code MessageSourceAutoConfiguration} can own the {@code messageSource} bean outright.
 *
 * <p>Spring replaces list properties rather than accumulating them, so plugins cannot each contribute
 * a fragment of the base-name list through their own configuration files. Something has to compute
 * one combined value, and doing it here rather than in the application's build is what keeps the
 * result correct when the runtime classpath differs from what the build resolved — an integration
 * test that pulls in an extra plugin, for instance.</p>
 *
 * <h2>Ordering</h2>
 *
 * <p>This runs after {@code ConfigDataEnvironmentPostProcessor}, so the application's own
 * {@code spring.messages.basename} is visible and can be merged rather than replaced; and after
 * {@code GrailsEnvironmentPostProcessor}, whose plugin configuration can influence the environment
 * this reads. It does not, however, depend on that processor having initialised plugin discovery:
 * {@link PluginDiscovery#init} is idempotent, so this calls it too rather than leaving an invisible
 * precondition that would break quietly if the other call ever moved.</p>
 *
 * @since 8.0
 */
public class I18nEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    /**
     * Later than {@code ConfigDataEnvironmentPostProcessor.ORDER} so {@code application.yml} is
     * loaded, and later than {@code GrailsEnvironmentPostProcessor} so plugin configuration is in
     * place.
     */
    public static final int ORDER = Ordered.HIGHEST_PRECEDENCE + 16;

    /** Property holding the composed, ordered base-name list. */
    public static final String BASENAME_PROPERTY = "spring.messages.basename";

    /** Set to {@code false} to leave plugin bundles out of both resolution and native-image hints. */
    public static final String INCLUDE_PLUGIN_BUNDLES_PROPERTY = "grails.i18n.include-plugin-bundles";

    private static final String PROPERTY_SOURCE_NAME = "grailsI18nBasenames";

    private static final String DEFAULTS_PROPERTY_SOURCE_NAME = "grailsI18nDefaults";

    private static final String ENCODING_PROPERTY = "spring.messages.encoding";

    private static final String FALLBACK_TO_SYSTEM_LOCALE_PROPERTY = "spring.messages.fallback-to-system-locale";

    private static final String GSP_ENCODING_PROPERTY = "grails.views.gsp.encoding";

    private static final String GSP_ENABLE_RELOAD_PROPERTY = "grails.gsp.enable.reload";

    private static final String CACHE_DURATION_PROPERTY = "spring.messages.cache-duration";

    /** Short enough to feel immediate while editing, long enough not to re-read on every lookup. */
    private static final String DEVELOPMENT_CACHE_DURATION = "5s";

    private static final Logger logger = LoggerFactory.getLogger(I18nEnvironmentPostProcessor.class);

    private final ConfigurableBootstrapContext bootstrapContext;

    I18nEnvironmentPostProcessor(ConfigurableBootstrapContext bootstrapContext) {
        this.bootstrapContext = bootstrapContext;
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        MutablePropertySources propertySources = environment.getPropertySources();

        // Contributed even when nothing was discovered: an application may point
        // spring.messages.basename at a bundle outside grails-app/i18n and so have a message source
        // but no descriptor, and Grails' documented defaults should still apply to it.
        propertySources.addLast(new MapPropertySource(DEFAULTS_PROPERTY_SOURCE_NAME, defaults(environment)));

        List<I18nDescriptor> descriptors = I18nDescriptors.load(application.getClassLoader());
        if (descriptors.isEmpty()) {
            return;
        }

        boolean includePluginBundles = environment.getProperty(INCLUDE_PLUGIN_BUNDLES_PROPERTY, Boolean.class,
                Boolean.TRUE);
        EffectiveI18nDescriptors effective = EffectiveI18nDescriptors.of(descriptors,
                pluginNamesInTopologicalOrder(environment, descriptors, includePluginBundles), includePluginBundles);

        List<String> composed = compose(environment, effective.basenames());
        if (!composed.isEmpty()) {
            Map<String, Object> source = new LinkedHashMap<>();
            source.put(BASENAME_PROPERTY, String.join(",", composed));
            propertySources.addFirst(new MapPropertySource(PROPERTY_SOURCE_NAME, source));
        }
    }

    /**
     * Merges the application's declared base names with the discovered ones.
     *
     * <p>A value the application set itself comes first and is never dropped, because Spring would
     * otherwise replace the whole list and silently discard whichever side lost.</p>
     */
    private List<String> compose(ConfigurableEnvironment environment, List<String> discovered) {
        Set<String> composed = new LinkedHashSet<>(declaredBasenames(environment));
        composed.addAll(discovered);
        return new ArrayList<>(composed);
    }

    private List<String> declaredBasenames(ConfigurableEnvironment environment) {
        String declared = environment.getProperty(BASENAME_PROPERTY);
        if (declared == null || declared.isBlank()) {
            return List.of();
        }
        List<String> basenames = new ArrayList<>();
        for (String basename : declared.split(",")) {
            String trimmed = basename.trim();
            if (!trimmed.isEmpty()) {
                basenames.add(trimmed);
            }
        }
        return basenames;
    }

    /**
     * Grails defaults for Boot's message-source properties, contributed at the lowest precedence so an
     * application can still override them.
     */
    private Map<String, Object> defaults(ConfigurableEnvironment environment) {
        Map<String, Object> defaults = new LinkedHashMap<>();
        defaults.put(FALLBACK_TO_SYSTEM_LOCALE_PROPERTY, Boolean.FALSE);
        String gspEncoding = environment.getProperty(GSP_ENCODING_PROPERTY);
        if (gspEncoding != null && !gspEncoding.isBlank()) {
            defaults.put(ENCODING_PROPERTY, gspEncoding);
        }
        String legacyCacheDuration = legacyCacheDuration(environment);
        if (reloadEnabled(environment)) {
            defaults.put(CACHE_DURATION_PROPERTY,
                    (legacyCacheDuration != null) ? legacyCacheDuration : DEVELOPMENT_CACHE_DURATION);
        }
        return defaults;
    }

    /**
     * Translates the removed {@code grails.i18n.cache.seconds} into a duration, easing upgrades for an
     * application that tuned it.
     *
     * <p>Deliberately faithful to the old behaviour rather than to the property's name: the previous
     * message source applied this setting <em>only</em> when reload was enabled, so honouring it in
     * production now would quietly start re-reading bundles in applications that have cached them
     * forever for years. The caller therefore applies the returned value only in reload mode, while the
     * warning is emitted wherever the property is set so the migration is visible either way.</p>
     *
     * @return the equivalent {@code spring.messages.cache-duration}, or {@code null} when the removed
     * property is not set
     * @deprecated since 8.0, for removal. Configure {@code spring.messages.cache-duration} instead.
     */
    @Deprecated(since = "8.0", forRemoval = true)
    @SuppressWarnings("removal")
    private String legacyCacheDuration(ConfigurableEnvironment environment) {
        Integer cacheSeconds = environment.getProperty(Settings.I18N_CACHE_SECONDS, Integer.class);
        if (cacheSeconds == null) {
            return null;
        }
        logger.warn("'{}' is deprecated and will be removed. Spring Boot owns the message source now, " +
                "so use '{}={}s' instead. As before, the setting applies only when reload is enabled.",
                Settings.I18N_CACHE_SECONDS, CACHE_DURATION_PROPERTY, cacheSeconds);
        return cacheSeconds + "s";
    }

    /**
     * Whether bundles should be re-read while the application runs.
     *
     * <p>Without a cache duration {@code ResourceBundleMessageSource} caches every bundle forever in a
     * map of its own — "cache forever: prefer locale cache over repeated getBundle calls" — which
     * {@code ResourceBundle.clearCache} cannot reach. Editing a bundle would then have no effect until
     * a restart, where the message source Grails used previously reloaded automatically in development.
     * Setting a short duration switches Spring to a fresh {@code ResourceBundle.getBundle} per lookup,
     * which honours both the time-to-live and an explicit cache flush.</p>
     *
     * <p>Contributed at the lowest precedence, so an application that sets
     * {@code spring.messages.cache-duration} itself still wins.</p>
     */
    private boolean reloadEnabled(ConfigurableEnvironment environment) {
        return Environment.getCurrent().isReloadEnabled() ||
                environment.getProperty(GSP_ENABLE_RELOAD_PROPERTY, Boolean.class, Boolean.FALSE);
    }

    /**
     * The discovered plugins in topological order — the order
     * {@code GrailsPluginManager.getAllPlugins()} uses, and therefore the order whose reverse
     * reproduces the message precedence Grails had before Boot owned the message source.
     */
    private List<String> pluginNamesInTopologicalOrder(ConfigurableEnvironment environment,
            List<I18nDescriptor> descriptors, boolean includePluginBundles) {

        boolean pluginBundlesPresent = descriptors.stream().anyMatch(descriptor -> !descriptor.isApplication());
        if (!includePluginBundles || !pluginBundlesPresent) {
            return List.of();
        }

        if (this.bootstrapContext == null || !this.bootstrapContext.isRegistered(PluginDiscovery.class)) {
            throw new IllegalStateException("Plugin message bundles are on the classpath but Grails plugin discovery " +
                    "is unavailable, so their base names cannot be ordered. Set " +
                    INCLUDE_PLUGIN_BUNDLES_PROPERTY + "=false to exclude plugin bundles deliberately.");
        }

        PluginDiscovery pluginDiscovery = this.bootstrapContext.get(PluginDiscovery.class);
        pluginDiscovery.init(environment);
        return pluginDiscovery.getPluginsInTopologicalOrder().stream().map(PluginInfo::getName).toList();
    }
}
