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
import java.util.function.Function
import java.nio.file.Files
import java.nio.file.LinkOption
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
     * Writes files, replacing what is there under their paths. What is there is moved aside under
     * another name first, and has its name again when a file cannot be written, so it is what it
     * was, whatever its size, its mode and its other names.
     *
     * @param project the directory of the project
     * @param output what writes a file of the project
     * @param texts the text of each file, by its path in the project
     * @throws Failed when a file cannot be written. What was written before is taken back, and the
     * message says what of it could not be
     */
    static void write(File project, OutputHandler output, Map<String, String> texts) {
        write(project, output, texts, { String path -> ProjectPlace.open(project, path) } as Function<String, ProjectPlace>)
    }

    /**
     * @param places what opens the place of a file by its path in the project, see {@link ProjectPlace#open}
     * @see #write(File, OutputHandler, Map)
     */
    static void write(File project, OutputHandler output, Map<String, String> texts, Function<String, ProjectPlace> opener) {
        Map<String, String> refused = refusals(project, texts.keySet())
        if (refused) {
            throw new Failed(refused.values().first(), null)
        }
        List<ProjectPlace> places = []
        try {
            for (Map.Entry<String, String> each : texts.entrySet()) {
                try {
                    ProjectPlace place = opener.apply(each.key)
                    places.add(place)
                    place.moveAside()
                    output.write(each.key, new StringTemplate(each.key, each.value))
                } catch (Exception e) {
                    List<String> left = places.reverse().collect { ProjectPlace made -> made.restore() }.findAll { String sentence -> sentence != null }
                    // what the file system says has the path of the file on this computer in it, and is not repeated
                    String reason = e instanceof ProjectOutputHandler.Refused ? e.getMessage() :
                            e instanceof IOException ? OutputFailedException.reason((IOException) e) : "it failed (${e.getClass().getSimpleName()})".toString()
                    String after = left ? ". Not everything the command did could be taken back: ${left.join('; ')}" : '. The project is as it was before the command'
                    throw new Failed(said(each.key, reason) + after, e)
                }
            }
            places.each { ProjectPlace place -> place.keep() }
        } finally {
            places.each { ProjectPlace place -> place.close() }
        }
    }

    private static String said(String path, String reason) {
        return "Cannot write ${FeatureSelectionException.shown(path)}: ${reason}"
    }
}
