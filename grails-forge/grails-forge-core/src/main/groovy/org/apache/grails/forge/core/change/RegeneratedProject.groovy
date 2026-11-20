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
package org.apache.grails.forge.core.change

import groovy.transform.CompileStatic
import groovy.transform.PackageScope
import org.apache.grails.forge.core.state.ProjectState

/**
 * The application of a project as the generator makes it from the state of the project, in memory:
 * every file with its bytes and whether it can be executed, the files of the state under
 * {@code .grails} among them. It was held to the state of the project when it was made, see
 * {@link FeatureAddition#regenerate}.
 *
 * @since 9.0
 */
@CompileStatic
final class RegeneratedProject {

    /** The state of the project it was generated from. */
    final ProjectState state

    private final RenderedProject rendered
    private final List<String> paths

    @PackageScope
    RegeneratedProject(RenderedProject rendered, ProjectState state) {
        this.rendered = rendered
        this.state = state
        this.paths = rendered.paths().sort(false).asImmutable()
    }

    /**
     * @return the path of every file, with {@code /} between its steps, sorted
     */
    List<String> getPaths() {
        return paths
    }

    /**
     * @return the bytes of a file, or {@code null} for a path that is none of {@link #getPaths()}.
     * The array is not a copy and is not to be changed
     */
    byte[] content(String path) {
        return rendered.content(path)
    }

    /**
     * @return whether the file is one that can be executed
     */
    boolean isExecutable(String path) {
        return rendered.isExecutable(path)
    }
}
