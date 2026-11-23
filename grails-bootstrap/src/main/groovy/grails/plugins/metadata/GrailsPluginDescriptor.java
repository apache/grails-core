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
package grails.plugins.metadata;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares strongly-typed metadata for a Grails plugin descriptor class.
 *
 * <p>Classes annotated with {@code @GrailsPluginDescriptor} may be written in Java or statically-compiled
 * Groovy and do not require the legacy {@code *GrailsPlugin} class name suffix. Build-time tooling reads
 * the annotation attributes directly from bytecode into
 * {@code META-INF/grails/plugins/<name>/plugin.properties}, and runtime discovery extracts the metadata
 * without instantiating the class via reflection.</p>
 *
 * @since 9.0
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface GrailsPluginDescriptor {

    /**
     * The logical name of the plugin (for example {@code myPlugin} or {@code my-plugin}).
     * When omitted, the logical name is derived from the annotated class name.
     *
     * @return the logical plugin name, or an empty string to derive from the class name
     */
    String name() default "";

    /**
     * The plugin version. When omitted, the build's project version is recorded at build time.
     *
     * @return the plugin version, or an empty string to inherit the project version
     */
    String version() default "";

    /**
     * The supported Grails version range expression (for example {@code "9.0.0 > *"}).
     *
     * @return the Grails version range, or an empty string if unconstrained
     */
    String grailsVersion() default "";

    /**
     * Logical names of plugins that this plugin prefers to load after.
     *
     * @return plugin names to load after
     */
    String[] loadAfter() default {};

    /**
     * Logical names of plugins that this plugin prefers to load before.
     *
     * @return plugin names to load before
     */
    String[] loadBefore() default {};

    /**
     * Required plugin dependencies, formatted as {@code "<pluginName>:<versionRange>"}
     * (for example {@code "core:9.0.0 > *"}) or {@code "<pluginName>"} when any version is accepted.
     *
     * @return required plugin dependency declarations
     */
    String[] dependsOn() default {};

    /**
     * Logical names of plugins evicted by this plugin.
     *
     * @return evicted plugin names
     */
    String[] evict() default {};

    /**
     * Logical names of plugins observed by this plugin (or {@code "*"} for all plugins).
     *
     * @return observed plugin names
     */
    String[] observe() default {};

    /**
     * Grails environments in which this plugin is enabled. When empty, all environments are allowed
     * unless excluded via {@link #environmentsExcludes()}.
     *
     * @return included environment names
     */
    String[] environmentsIncludes() default {};

    /**
     * Grails environments in which this plugin is disabled.
     *
     * @return excluded environment names
     */
    String[] environmentsExcludes() default {};

    /**
     * Ant-style resource patterns excluded from the plugin's packaged artefact list.
     *
     * @return excluded artefact path patterns
     */
    String[] pluginExcludes() default {};

    /**
     * Whether the plugin is enabled by default.
     *
     * @return {@code true} if enabled
     */
    boolean enabled() default true;
}
