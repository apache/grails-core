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
package org.grails.gradle.plugin.core

import javax.inject.Inject

import groovy.transform.CompileStatic

import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property

/**
 * Lazy opt-ins for compiling Grails artefacts with {@code @GrailsCompileStatic} automatically,
 * configured through the nested {@code grails { compileStatic { } }} block:
 *
 * <pre>
 * grails {
 *     compileStatic {
 *         controllers = true
 *         services = true
 *         tagLibs = true
 *     }
 * }
 * </pre>
 *
 * <p>Every flag is a lazy {@link Property} that defaults to {@code false} and is read when the Groovy
 * compile task runs (not at configuration time), so the values reflect the user's {@code grails { }}
 * block regardless of configuration ordering. The {@link #getAll() all} flag is a shortcut that enables
 * controllers, services and tag libraries together.</p>
 *
 * <p>A class that declares its own {@code @CompileDynamic} (or {@code @CompileStatic} /
 * {@code @GrailsCompileStatic} / {@code @TypeChecked} / {@code @GrailsTypeChecked}) annotation always
 * keeps that setting; these build-wide opt-ins never override an explicit per-class choice.</p>
 *
 * @since 8.0
 */
@CompileStatic
class GrailsCompileStaticOptions implements Serializable {

    private static final long serialVersionUID = 0L

    /**
     * Whether every controller, service and tag library should be compiled with {@code @GrailsCompileStatic}.
     * A shortcut equivalent to enabling {@link #getControllers() controllers}, {@link #getServices() services}
     * and {@link #getTagLibs() tagLibs} together. Disabled by default.
     */
    final Property<Boolean> all

    /**
     * Whether every controller under {@code grails-app/controllers} should be compiled with
     * {@code @GrailsCompileStatic}. Disabled by default.
     */
    final Property<Boolean> controllers

    /**
     * Whether every service under {@code grails-app/services} should be compiled with
     * {@code @GrailsCompileStatic}. Disabled by default.
     */
    final Property<Boolean> services

    /**
     * Whether every tag library under {@code grails-app/taglib} should be compiled with
     * {@code @GrailsCompileStatic}. Disabled by default.
     */
    final Property<Boolean> tagLibs

    @Inject
    GrailsCompileStaticOptions(ObjectFactory objects) {
        this.all = objects.property(Boolean).convention(false)
        this.controllers = objects.property(Boolean).convention(false)
        this.services = objects.property(Boolean).convention(false)
        this.tagLibs = objects.property(Boolean).convention(false)
    }
}
