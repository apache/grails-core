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
import org.apache.grails.forge.core.pack.PackArtefact
import org.apache.grails.forge.core.state.LockedArtefact
import org.apache.grails.forge.core.state.ProjectLock

/**
 * A command a feature of the application gives the command line, with the feature it is of: what the
 * record of the project says of it, which is all the command line knows of it. Neither the feature
 * pack nor the project generator is asked.
 *
 * @since 9.0
 */
@CompileStatic
final class FeatureArtefact {

    /** The name of the feature */
    final String feature

    /** The command as the record has it */
    final LockedArtefact artefact

    FeatureArtefact(String feature, LockedArtefact artefact) {
        this.feature = feature
        this.artefact = artefact
    }

    /**
     * @param lock the lock of a project
     * @return the commands of its features, in the order of the features and, for each, of its pack
     */
    static List<FeatureArtefact> of(ProjectLock lock) {
        List<FeatureArtefact> all = []
        for (ProjectLock.LockedFeature feature : lock.features) {
            for (LockedArtefact artefact : feature.artefacts) {
                all.add(new FeatureArtefact(feature.name, artefact))
            }
        }
        return all
    }

    /**
     * @return the name of the command
     */
    String getCommandName() {
        return artefact.commandName
    }

    /**
     * @return the path of the template of the class in the project
     */
    String getTemplatePath() {
        return PackArtefact.projectPath(feature, artefact.template)
    }

    /**
     * @return the path of the template of the test in the project, or {@code null} for a command that writes no test
     */
    String getTestTemplatePath() {
        return artefact.testTemplate == null ? null : PackArtefact.projectPath(feature, artefact.testTemplate)
    }
}
