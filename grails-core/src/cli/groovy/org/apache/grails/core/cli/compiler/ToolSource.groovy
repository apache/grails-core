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
package org.apache.grails.core.cli.compiler

import groovy.transform.CompileStatic
import groovy.transform.PackageScope

/**
 * Which folder of a project the source of a class is in, which decides what a tool there is compiled
 * against and how it runs. The folder is the nearest directory above the file that is the root of the
 * sources of one: {@code src/<source set>/<language>}, or {@code grails-app/<kind of artefact>}.
 *
 * @since 9.0
 */
@PackageScope
@CompileStatic
final class ToolSource {

    /**
     * The folders a source of a project can be in.
     */
    enum Folder {

        /** {@code src/cli}: the tools the build runs. */
        CLI,

        /** {@code src/standalone}: the tools that run as a process of their own. */
        STANDALONE,

        /** {@code grails-app/commands}, the deprecated folder of the tools the build runs. */
        LEGACY,

        /** Another folder of {@code grails-app}. */
        GRAILS_APP,

        /** {@code src/main}. */
        MAIN,

        /** A folder of tests: {@code src/test*}, {@code src/integration-test*}, {@code src/testFixtures}. */
        TEST,

        /** None of these. */
        OTHER
    }

    private static final String SRC = 'src'

    private static final String GRAILS_APP = 'grails-app'

    private static final String COMMANDS = 'commands'

    /** The folder the source is in. */
    final Folder folder

    /** The path of the source from the directory of its project, with {@code /} between the names. */
    final String path

    private ToolSource(Folder folder, String path) {
        this.folder = folder
        this.path = path
    }

    /**
     * @param url where the source of a compilation is
     * @return the folder it is in
     */
    static ToolSource of(URL url) {
        File file
        try {
            file = 'file' == url.protocol ? new File(url.toURI()) : new File(url.path)
        } catch (URISyntaxException | IllegalArgumentException ignored) {
            file = new File(url.path)
        }
        of(file)
    }

    /**
     * @param file a source file
     * @return the folder it is in
     */
    static ToolSource of(File file) {
        File directory = file.parentFile
        while (directory != null) {
            File parent = directory.parentFile
            if (parent == null) {
                break
            }
            if (parent.name == GRAILS_APP) {
                return new ToolSource(directory.name == COMMANDS ? Folder.LEGACY : Folder.GRAILS_APP, relative(parent.parentFile, file))
            }
            File sources = parent.parentFile
            Folder folder = sources != null && sources.name == SRC ? sourceSet(parent.name) : null
            if (folder != null) {
                return new ToolSource(folder, relative(sources.parentFile, file))
            }
            directory = parent
        }
        new ToolSource(Folder.OTHER, file.path)
    }

    /**
     * @return whether the source is one the rules of the folders of tools are not applied to: a test
     */
    boolean isExempt() {
        folder == Folder.TEST
    }

    private static Folder sourceSet(String name) {
        switch (name) {
            case 'cli':
                return Folder.CLI
            case 'standalone':
                return Folder.STANDALONE
            case 'main':
                return Folder.MAIN
            default:
                return name.startsWith('test') || name.startsWith('integration-test') ? Folder.TEST : null
        }
    }

    private static String relative(File project, File file) {
        project == null ? file.path : project.toPath().relativize(file.toPath()).toString().replace(File.separatorChar, '/' as char)
    }
}
