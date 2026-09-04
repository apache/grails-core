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

import java.util.List;

/**
 * The message bundles one application or plugin contributes, as recorded in its
 * {@code META-INF/grails/i18n.properties} at build time.
 *
 * <p>Base names and locales are already normalised by the Grails Gradle plugin, so nothing here
 * re-parses file names. That is deliberate: the base-name list handed to Spring Boot, the locale list
 * offered by {@link AvailableLocaleResolver}, and the native-image resource hints all read the same
 * pre-computed values and therefore cannot disagree about what a file name means.</p>
 *
 * @param type either {@link #TYPE_APPLICATION} or {@link #TYPE_PLUGIN}
 * @param name the application name, or the Grails plugin name (e.g. {@code spring-security-core})
 * @param version the artifact version; recorded for diagnostics only, never for ordering
 * @param basenames base names for {@code spring.messages.basename}
 * @param locales locale identifiers ({@code de}, {@code pt_BR}) the bundles are translated into
 * @since 8.0
 */
public record I18nDescriptor(String type, String name, String version, List<String> basenames, List<String> locales) {

    /** An application's descriptor. At most one may be present on a classpath. */
    public static final String TYPE_APPLICATION = "application";

    /** A plugin's descriptor. */
    public static final String TYPE_PLUGIN = "plugin";

    public I18nDescriptor {
        basenames = List.copyOf(basenames);
        locales = List.copyOf(locales);
    }

    /**
     * Whether this descriptor belongs to the application rather than a plugin.
     *
     * @return {@code true} for the application descriptor
     */
    public boolean isApplication() {
        return TYPE_APPLICATION.equals(this.type);
    }
}
