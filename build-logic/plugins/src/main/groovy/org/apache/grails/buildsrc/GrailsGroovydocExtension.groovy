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
 * Extension for configuring the Grails Groovydoc convention plugin.
 *
 * <p>Allows per-project control over the {@code javaVersion} parameter
 * passed to the Groovy Ant groovydoc task. The {@code javaVersion}
 * parameter was added in Groovy 4.0.27 (GROOVY-11668) and controls
 * the JavaParser language level used when parsing Java sources.</p>
 *
 * @since 7.0.8
 */
@CompileStatic
class GrailsGroovydocExtension {

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

    @Inject
    GrailsGroovydocExtension(ObjectFactory objects, Project project) {
        javaVersion = objects.property(String).convention(
                "JAVA_${GradleUtils.findProperty(project, 'javaVersion') ?: '17'}" as String
        )
        javaVersionEnabled = objects.property(Boolean).convention(true)
    }
}
