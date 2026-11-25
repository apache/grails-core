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
package org.grails.forge.cli.command

import java.nio.file.Path
import groovy.transform.CompileStatic
import org.apache.grails.forge.cli.console.ConsoleProject
import org.apache.grails.forge.core.pack.Pack
import org.apache.grails.forge.core.state.ProjectState
import org.grails.forge.application.ApplicationType
import org.grails.forge.application.generator.ProjectGenerator
import org.grails.forge.cli.ForgeContext
import org.grails.forge.feature.Feature
import org.grails.forge.io.FileSystemOutputHandler

/**
 * The names of the features {@code feature info} says something of, which the TAB key completes: in
 * the directory of an application the features of Grails for its type, whether it has them or not,
 * and anywhere else those of every type of application.
 *
 * @since 9.0
 */
@CompileStatic
class KnownFeatureCandidates implements Iterable<String> {

    private final ForgeContext context

    /**
     * @param context the Forge application, which is started when the names are asked for
     */
    KnownFeatureCandidates(ForgeContext context) {
        this.context = context
    }

    @Override
    Iterator<String> iterator() {
        try {
            return names().iterator()
        } catch (IOException | RuntimeException ignored) {
            // a generator that cannot be started, or a record that cannot be read: nothing to complete with is no reason for the command line to fail
            return Collections.<String>emptyIterator()
        }
    }

    private List<String> names() {
        ProjectGenerator generator = context.get().getBean(ProjectGenerator)
        Path current = FileSystemOutputHandler.getDefaultBaseDirectory().toPath()
        ConsoleProject project = ConsoleProject.find(current)
        Set<String> names = new TreeSet<>()
        List<ApplicationType> types = project != null && ProjectState.exists(project.directory) ?
                [ProjectState.readSelection(project.directory, true).manifest.applicationType] : ApplicationType.values().toList()
        for (ApplicationType type : types) {
            names.addAll(generator.availableFeatures(type, Collections.<Pack>emptyList()).getFeatures().map(Feature::getName).toList())
        }
        return new ArrayList<String>(names)
    }
}
