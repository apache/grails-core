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
package org.apache.grails.core.aot;

import org.jspecify.annotations.Nullable;

import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.core.SpringProperties;
import org.springframework.util.StringUtils;

/**
 * The resources a Grails application reads by name at run time, which an image cannot prove are
 * reached.
 *
 * <p>An image includes what it can prove is used. A compiled asset is asked for by request path and
 * a message bundle by locale, so nothing in the code names either, and an image built without them
 * starts and then serves every page with a missing stylesheet and an untranslated string.</p>
 *
 * <p>Registered as hints rather than passed as {@code -H:IncludeResources}: that option is
 * experimental and GraalVM now warns it will require unlocking, while hints are the supported way
 * and are what Spring writes the image's resource configuration from.</p>
 *
 * <p>Bounded to where those two are actually looked for. A pattern that matched every properties
 * file at every depth would carry the whole classpath into the image -- every dependency's
 * configuration, and whatever else happened to be packaged beside it -- to keep the few that are
 * message bundles.</p>
 *
 * @since 8.0
 */
public class GrailsResourceRuntimeHints implements RuntimeHintsRegistrar {

    /**
     * Where a message bundle is looked for: the root of the classpath, which is what
     * {@code PluginAwareResourceBundleMessageSource} scans and where a plugin's own bundles land.
     * Not below it, because nothing reads them there.
     */
    private static final String MESSAGE_BUNDLES = "*.properties";

    /** Compiled assets, which asset-pipeline serves from the classpath by the path asked for. */
    private static final String[] ASSETS = { "assets/*", "assets/**" };

    /**
     * Where an application says what else it reads by name, as a comma-separated list of patterns.
     * Read as a property rather than from configuration because this runs while the code is being
     * generated rather than while the application is running, so it is set on the task that does
     * the generating:
     *
     * <pre>
     * tasks.named('processAot') {
     *     systemProperty 'grails.aot.resource-patterns', 'db/migration/&#42;&#42;,templates/&#42;.vm'
     * }
     * </pre>
     */
    public static final String ADDITIONAL_PATTERNS_PROPERTY = "grails.aot.resource-patterns";

    @Override
    public void registerHints(RuntimeHints hints, @Nullable ClassLoader classLoader) {
        for (String pattern : ASSETS) {
            hints.resources().registerPattern(pattern);
        }
        hints.resources().registerPattern(MESSAGE_BUNDLES);
        for (String pattern : additionalPatterns()) {
            hints.resources().registerPattern(pattern);
        }
    }

    /**
     * What the application asked for on top of the above, or none where it asked for nothing.
     *
     * <p>Tokenized rather than split, so that a list written with spaces after its commas is the
     * list it looks like: a pattern carrying a leading space matches nothing, and does so silently,
     * leaving out exactly the resource the application asked to keep.</p>
     */
    private String[] additionalPatterns() {
        String configured = SpringProperties.getProperty(ADDITIONAL_PATTERNS_PROPERTY);
        if (!StringUtils.hasText(configured)) {
            return new String[0];
        }
        return StringUtils.tokenizeToStringArray(configured, ",");
    }

}
