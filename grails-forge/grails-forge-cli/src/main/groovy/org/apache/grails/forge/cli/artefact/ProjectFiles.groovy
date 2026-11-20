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
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import org.grails.forge.feature.FeatureSelectionException
import org.grails.forge.io.OutputFailedException
import org.grails.forge.io.OutputHandler
import org.grails.forge.template.StringTemplate

/**
 * Writes the files of one command into a project, all of them or none: when one cannot be written,
 * what the command wrote before it is taken back, and a file it replaced is what it was again.
 *
 * @since 9.0
 */
@CompileStatic
final class ProjectFiles {

    // a file the command replaces is kept to be put back, unless it is larger than a source file is
    private static final int MAX_KEPT = 16 * 1024 * 1024

    private ProjectFiles() {
    }

    /**
     * A file could not be written. The message is one line with the path of the file in the project
     * and what is wrong, and names nothing outside the project.
     */
    static class Failed extends RuntimeException {

        Failed(String message, Throwable cause) {
            super(message, cause)
        }
    }

    /**
     * @param project the directory of the project
     * @param paths paths in the project, with {@code /} between the steps
     * @return for each path nothing can be written at, the sentence that says why, by the path
     */
    static Map<String, String> refusals(File project, Collection<String> paths) {
        Map<String, String> refusals = new LinkedHashMap<>()
        for (String path : paths) {
            String refusal = ProjectOutputHandler.refusal(project, path)
            if (refusal != null) {
                refusals.put(path, said(path, refusal))
            }
        }
        return refusals
    }

    /**
     * @param project the directory of the project
     * @param path a path in the project
     * @return whether there is a file, or a link, a file written there would replace
     */
    static boolean isThere(File project, String path) {
        return Files.exists(project.toPath().resolve(path), LinkOption.NOFOLLOW_LINKS)
    }

    /**
     * Writes files, replacing what is there under their paths.
     *
     * @param project the directory of the project
     * @param output what writes a file of the project
     * @param texts the text of each file, by its path in the project
     * @throws Failed when a file cannot be written; none of the files is written then
     */
    static void write(File project, OutputHandler output, Map<String, String> texts) {
        Map<String, String> refused = refusals(project, texts.keySet())
        if (refused) {
            throw new Failed(refused.values().first(), null)
        }
        List<Before> written = []
        for (Map.Entry<String, String> each : texts.entrySet()) {
            Before before = new Before(project.toPath(), each.key)
            written.add(before)
            try {
                output.write(each.key, new StringTemplate(each.key, each.value))
            } catch (Exception e) {
                written.reverse().each { Before made -> made.putBack() }
                // what the file system says has the path of the file on this computer in it, and is not repeated
                String reason = e instanceof ProjectOutputHandler.Refused ? e.getMessage() :
                        e instanceof IOException ? OutputFailedException.reason((IOException) e) : "it failed (${e.getClass().getSimpleName()})".toString()
                throw new Failed(said(each.key, reason) + (texts.size() > 1 ? '. No file of the command was written' : ''), e)
            }
        }
    }

    private static String said(String path, String reason) {
        return "Cannot write ${FeatureSelectionException.shown(path)}: ${reason}"
    }

    /**
     * What there was where a file is written, to put it back.
     */
    private static final class Before {

        private final Path file
        private final Path link
        private final byte[] content
        private final boolean absent
        private final List<Path> missing = []

        Before(Path project, String path) {
            file = project.resolve(path)
            Path target = null
            byte[] bytes = null
            try {
                if (Files.isSymbolicLink(file)) {
                    target = Files.readSymbolicLink(file)
                } else if (Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) && Files.size(file) <= MAX_KEPT) {
                    bytes = Files.readAllBytes(file)
                }
            } catch (IOException ignored) {
                // what cannot be read cannot be put back, and is most likely not replaced either
            }
            link = target
            content = bytes
            absent = !Files.exists(file, LinkOption.NOFOLLOW_LINKS)
            for (Path directory = file.getParent(); directory != null && directory.startsWith(project) && directory != project &&
                    !Files.exists(directory, LinkOption.NOFOLLOW_LINKS); directory = directory.getParent()) {
                missing.add(directory)
            }
        }

        void putBack() {
            try {
                if (link != null) {
                    Files.deleteIfExists(file)
                    Files.createSymbolicLink(file, link)
                } else if (content != null) {
                    Files.write(file, content)
                } else if (absent) {
                    Files.deleteIfExists(file)
                }
                // the deepest first, and only those that are empty again
                for (Path directory : missing) {
                    Files.deleteIfExists(directory)
                }
            } catch (IOException ignored) {
                // what cannot be taken back stays; the failure that is reported is the one of the write
            }
        }
    }
}
