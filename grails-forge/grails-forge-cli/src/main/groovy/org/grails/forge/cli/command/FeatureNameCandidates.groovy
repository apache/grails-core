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
import org.apache.grails.forge.core.state.ProjectState
import org.grails.forge.application.ApplicationType
import org.grails.forge.application.generator.ProjectGenerator
import org.grails.forge.cli.ForgeContext
import org.grails.forge.feature.Feature
import org.grails.forge.io.FileSystemOutputHandler

/**
 * What the name of a feature is completed with for {@code feature add}: in a project that has a
 * record, the features of Grails for its type of application that it does not have; elsewhere, as for
 * the completion script of the shell, which is written without a project, the features of every type
 * of application. The features are those of the generator, which is started when they are first asked
 * for; where it cannot be, nothing is offered.
 *
 * @since 9.0
 */
@CompileStatic
class FeatureNameCandidates implements Iterable<String> {

    private final ForgeContext context

    FeatureNameCandidates(ForgeContext context) {
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
        if (project != null && ProjectState.exists(project.directory)) {
            ProjectState state = ProjectState.readSelection(project.directory, true)
            List<String> has = state.lock.featureNames
            return visible(generator, state.manifest.applicationType).findAll { String name -> !has.contains(name) }
        }
        Set<String> every = new TreeSet<>()
        for (ApplicationType type : ApplicationType.values()) {
            every.addAll(visible(generator, type))
        }
        return new ArrayList<String>(every)
    }

    private static List<String> visible(ProjectGenerator generator, ApplicationType type) {
        return generator.availableFeatures(type, []).getFeatures().map(Feature::getName).sorted().toList()
    }
}
