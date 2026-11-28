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
 * Declares a Grails plugin and all of its metadata.
 *
 * <p>A Grails plugin is a concrete subclass of {@code grails.plugins.Plugin} carrying this annotation,
 * written in Groovy or Java. Nothing else is a plugin: the class name does not matter, and a class
 * without the annotation is never discovered. The plugin's behaviour is its overridden {@code Plugin}
 * methods; everything known before the plugin runs is declared here, as constants.</p>
 *
 * <p>The compiler writes the annotation, with the plugin's version and the artefacts compiled with it,
 * to {@code META-INF/grails/plugins/<name>/plugin.properties} and lists the name in
 * {@code META-INF/grails/plugins.imports}. Discovery reads those files, so plugins are filtered and
 * ordered without being loaded or created.</p>
 *
 * <p>The plugin's version is not declared: it is the version of the build that compiles it. A
 * dependency range may refer to it as {@code ${plugin.version}}, for example
 * {@code dependsOn = "core:${plugin.version}"} for a plugin released with the Grails version it
 * requires.</p>
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
     * The supported Grails version range expression (for example {@code "9.0.0 > *"}).
     *
     * @return the Grails version range, or an empty string for the version of Grails the plugin is
     *         compiled against and any later version
     */
    String grailsVersion() default "";

    /**
     * Logical names of plugins this plugin comes after, when they are present. A named plugin that is
     * absent is ignored.
     *
     * <p>Plugins are put in one order, and every part of Grails follows it: the order the plugins'
     * hooks are called in, their bean registrars, their generated auto-configurations, their
     * configuration and messages, and the order their views, static resources and URL mappings are
     * searched in. Where two plugins provide the same thing, the later one wins (the application
     * still beats every plugin).</p>
     *
     * @return plugin names to come after
     */
    String[] after() default {};

    /**
     * Logical names of plugins this plugin comes before, when they are present. A named plugin that
     * is absent is ignored.
     *
     * @return plugin names to come before
     * @see #after()
     */
    String[] before() default {};

    /**
     * Required plugin dependencies, formatted as {@code "<pluginName>:<versionRange>"}
     * (for example {@code "core:9.0.0 > *"}) or {@code "<pluginName>"} when any version is accepted.
     *
     * <p>A dependency means the plugin cannot work without the other one: the application does not
     * start when it is missing, outside the version range, or left out. The plugin also comes after
     * the plugins it depends on, as if {@link #after()} named them. The Grails Gradle plugin checks
     * when the plugin is built that a dependency of its project provides each plugin named here; a
     * plugin that only needs to come after another one, when that one is present, names it in
     * {@link #after()} instead.</p>
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
     * Ant-style patterns of the files whose changes are passed to {@code Plugin.onChange} while the
     * application runs in development, for example {@code "file:./grails-app/controllers/**}{@code /*Controller.groovy"}.
     *
     * @return watched resource patterns
     */
    String[] watchedResources() default {};

    /**
     * Artefact classes the plugin contributes in addition to those compiled with it, such as a tag
     * library from a library it depends on.
     *
     * @return additional artefact classes
     */
    Class<?>[] providedArtefacts() default {};

    /**
     * Ant-style patterns of the artefact classes left out of the plugin's descriptor, written as class names with
     * {@code /} between the parts, for example {@code "com/example/demo/**"}. A pattern of source files, one
     * starting with {@code grails-app/} or {@code src/} or naming a file extension, is an error.
     *
     * @return excluded artefact path patterns
     */
    String[] pluginExcludes() default {};
}
