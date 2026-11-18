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
package org.apache.grails.gradle.plugin.aot

import groovy.transform.CompileStatic
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property

/**
 * What {@code traceNativeMetadata} asks the application for.
 *
 * <p>Declared rather than discovered. The agent records what ran, so this list is the coverage: a
 * path nobody wrote down is a path missing from the image, and it will be missing in the way that
 * only shows up when somebody uses it.</p>
 *
 * @since 8.0
 */
@CompileStatic
abstract class NativeMetadataExtension {

    /** Paths to ask for. */
    abstract ListProperty<String> getPaths()

    /**
     * Pages whose form is to be submitted, which is not the same as asking for the page. Rendering a
     * form and binding one are different halves of the framework, and only the second one converts
     * anything.
     */
    abstract ListProperty<String> getForms()

    abstract ListProperty<String> getJvmArguments()

    abstract Property<Integer> getPort()

    abstract Property<Integer> getStartTimeoutSeconds()

    /**
     * A java from a GraalVM, which is where the agent lives. Left unset, the project's toolchain is
     * used -- which is a GraalVM already for a project that builds an image.
     */
    abstract Property<String> getJavaExecutable()

    /** Where what was recorded is merged, in the application's sources so it can be reviewed. */
    abstract DirectoryProperty getOutputDirectory()
}
