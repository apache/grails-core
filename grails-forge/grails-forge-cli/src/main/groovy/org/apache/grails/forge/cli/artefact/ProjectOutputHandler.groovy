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
package org.apache.grails.forge.cli.artefact

import groovy.transform.CompileStatic
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import org.grails.forge.feature.FeatureSelectionException
import org.grails.forge.io.ConsoleOutput
import org.grails.forge.io.FileSystemOutputHandler
import org.grails.forge.template.Writable

/**
 * Writes a file into a project that is there already, and only into it. A project is whatever a
 * directory holds, of a repository that was cloned as well, and such a repository can have a link
 * where a directory of the sources is expected. So, other than where a whole application is generated,
 * a file is written only when every directory from the project down to it is a directory and not a
 * link, wherever the link leads, and has the very name that is asked for.
 *
 * @since 9.0
 */
@CompileStatic
class ProjectOutputHandler extends FileSystemOutputHandler {

    /** The most bytes a file system takes for the name of a file or a directory */
    public static final int MAX_NAME_BYTES = 255

    ProjectOutputHandler(File directory, ConsoleOutput console) throws IOException {
        super(directory, console)
    }

    @Override
    File write(String path, Writable contents) throws IOException {
        String refusal = refusal(applicationDirectory, path)
        if (refusal != null) {
            throw new Refused(refusal)
        }
        return super.write(path, contents)
    }

    /**
     * A file is not written because of what the project has on the way to it. The message is the
     * reason of {@link #refusal(File, String)}.
     */
    static class Refused extends IOException {

        Refused(String message) {
            super(message)
        }
    }

    /**
     * Why a file cannot be written at a path of a project, in words that name nothing outside the project.
     *
     * @param project the directory of the project
     * @param path the path of the file in the project, with {@code /} between its steps
     * @return the reason, as the end of a sentence, or null when the file can be written. A file or a
     * link that is there under the path is no reason: the file takes its place, and a link is not
     * written through
     */
    static String refusal(File project, String path) {
        List<String> steps = path.split('/', -1).toList()
        for (String step : steps) {
            if (step.isEmpty() || step == '.' || step == '..' || step.contains('\\')) {
                return 'it is not a path below the project'
            }
            if (step.getBytes(StandardCharsets.UTF_8).length > MAX_NAME_BYTES) {
                return "the name ${FeatureSelectionException.shown(step)} has more than the ${MAX_NAME_BYTES} bytes a file system takes for a name"
            }
        }
        Path directory = project.toPath()
        for (int i = 0; i < steps.size() - 1; i++) {
            String step = steps[i]
            String said = FeatureSelectionException.shown(steps.subList(0, i + 1).join('/'))
            Path next = directory.resolve(step)
            if (!Files.exists(next, LinkOption.NOFOLLOW_LINKS)) {
                return writable(directory, steps, i)
            }
            if (Files.isSymbolicLink(next)) {
                return "${said} is a link, and a file of the project is not written through a link"
            }
            if (!Files.isDirectory(next, LinkOption.NOFOLLOW_LINKS)) {
                return "${said} is a file where a directory is needed"
            }
            if (!named(directory, step)) {
                return "the project has ${said} under a name that is written differently, as in the case of its letters"
            }
            directory = next
        }
        Path file = directory.resolve(steps.last())
        if (Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
            if (Files.isDirectory(file, LinkOption.NOFOLLOW_LINKS)) {
                return 'it is a directory'
            }
            if (!Files.isSymbolicLink(file) && !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
                return 'there is something of its name that is not a file'
            }
            if (!named(directory, steps.last())) {
                return 'the project has a file of that name that is written differently, as in the case of its letters'
            }
        }
        return writable(directory, steps, steps.size() - 1)
    }

    // a directory that is there has to take a new entry
    private static String writable(Path directory, List<String> steps, int below) {
        if (Files.isWritable(directory)) {
            return null
        }
        return "the directory ${below == 0 ? 'of the project' : FeatureSelectionException.shown(steps.subList(0, below).join('/'))} cannot be written to"
    }

    // whether the directory has an entry of exactly this name: a file system that takes one name for
    // another, in another case or another way of writing a letter, finds an entry under either
    private static boolean named(Path directory, String name) {
        String[] names = directory.toFile().list()
        return names == null || name in names
    }
}
