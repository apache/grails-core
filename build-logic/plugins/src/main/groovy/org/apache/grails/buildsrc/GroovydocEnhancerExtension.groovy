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
package org.apache.grails.buildsrc

import javax.inject.Inject

import groovy.transform.CompileStatic

import org.gradle.api.Project
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property

/**
 * Extension for configuring the Groovydoc Enhancer convention plugin.
 *
 * <p>This plugin replaces Gradle's built-in Groovydoc task execution with
 * a direct AntBuilder invocation of the Groovy {@code org.codehaus.groovy.ant.Groovydoc}
 * Ant task. This enables the {@code javaVersion} parameter (added in Groovy 4.0.27,
 * GROOVY-11668) which controls the JavaParser language level used when parsing
 * Java source files.</p>
 *
 * <p>When Gradle natively supports the {@code javaVersion} property
 * (see <a href="https://github.com/gradle/gradle/issues/33659">gradle#33659</a>),
 * set {@link #useAntBuilder} to {@code false} to revert to Gradle's built-in
 * Groovydoc task execution while retaining all other configuration (footer,
 * defaults, etc.).</p>
 *
 * @since 7.0.8
 */
@CompileStatic
class GroovydocEnhancerExtension {

    /**
     * The Java language level string passed to the groovydoc Ant task's
     * {@code javaVersion} parameter (e.g. {@code "JAVA_17"}, {@code "JAVA_21"}).
     *
     * <p>Defaults to {@code "JAVA_${javaVersion}"} where {@code javaVersion}
     * is read from the project property, falling back to {@code "JAVA_17"}.</p>
     */
    final Property<String> javaVersion

    /**
     * Whether to pass the {@code javaVersion} parameter to the groovydoc
     * Ant task. Set to {@code false} for projects using Groovy versions
     * older than 4.0.27 (which do not support the parameter).
     *
     * <p>Defaults to {@code true}.</p>
     */
    final Property<Boolean> javaVersionEnabled

    /**
     * Whether to replace Gradle's built-in Groovydoc task execution with
     * AntBuilder invocation. When {@code true} (default), the plugin clears
     * the task's actions and replaces them with a {@code doLast} that uses
     * AntBuilder. When {@code false}, the plugin only applies property
     * defaults (footer, etc.) and lets Gradle's built-in task run normally.
     *
     * <p>Set to {@code false} when Gradle adds native {@code javaVersion}
     * support (gradle/gradle#33659).</p>
     *
     * <p>Defaults to {@code true}.</p>
     */
    final Property<Boolean> useAntBuilder

    /**
     * HTML footer appended to every generated groovydoc page. Useful for
     * analytics scripts, copyright notices, or custom branding.
     *
     * <p>Defaults to an empty string (no footer).</p>
     */
    final Property<String> footer

    @Inject
    GroovydocEnhancerExtension(ObjectFactory objects, Project project) {
        javaVersion = objects.property(String).convention(
                "JAVA_${GradleUtils.findProperty(project, 'javaVersion') ?: '17'}" as String
        )
        javaVersionEnabled = objects.property(Boolean).convention(true)
        useAntBuilder = objects.property(Boolean).convention(true)
        footer = objects.property(String).convention('')
    }
}
