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
package org.apache.grails.forge.core.state

import groovy.transform.CompileStatic
import org.apache.grails.forge.core.pack.PackDescriptor
import org.apache.grails.forge.core.state.BaselineIndex.BaselineFile
import org.apache.grails.forge.core.state.ProjectLock.LockedFeature
import org.apache.grails.forge.core.state.ProjectLock.LockedRepository
import org.apache.grails.forge.core.state.ProjectManifest.RequestedFeature
import org.grails.forge.application.ApplicationType
import org.grails.forge.util.VersionInfo
import java.nio.charset.StandardCharsets

/**
 * The record of an application that was there before it had one: what the application is taken to
 * be, written by {@code project migrate} and not by the generator. Its manifest says so with
 * {@code generationState: untracked}, and its baseline has no file, because the generator wrote none
 * of the files of the application as they are: nothing can be merged against it, so no feature is
 * added to such an application and it is not generated again. The commands that create the classes
 * of an application read from it what they read from the record of a generated one.
 *
 * @since 9.0
 */
@CompileStatic
final class AdoptedProjectRecord {

    private AdoptedProjectRecord() {
    }

    /**
     * @param applicationType the type of the application
     * @param name the name of the application, as a create command takes it
     * @param defaultPackage the package its classes are created in
     * @param options the options it is taken to have
     * @param requested the names of the features that were stated; each is among {@code features}
     * @param excluded the names of the features that were said not to be there; none is among {@code features}
     * @param features the features the application is taken to have, in the order they are applied in
     * @return the content of each file of the record by its path in the project, in the order the
     * files are written in, the manifest last
     * @throws ProjectStateException when what was given does not make a record this version reads,
     * with what is wrong in one line
     */
    static Map<String, byte[]> files(ApplicationType applicationType, String name, String defaultPackage, GenerationOptions options,
                                     List<String> requested, List<String> excluded, List<LockedFeature> features) {
        byte[] indexBytes = new BaselineIndex(Collections.<BaselineFile>emptyList()).toBytes()
        String grailsVersion = VersionInfo.getGrailsVersion()
        byte[] manifestBytes = new ProjectManifest(
                grailsVersion,
                applicationType,
                name,
                defaultPackage,
                options,
                ProjectManifest.UNTRACKED,
                requested.toSorted().unique().collect { String each -> new RequestedFeature(each) },
                excluded.toSorted().unique().collect { String each -> new RequestedFeature(each) },
                [:]).toBytes()
        byte[] lockBytes = new ProjectLock(
                StateYaml.sha256(manifestBytes),
                ProjectLock.GENERATOR,
                VersionInfo.getVersion(),
                // the build that wrote the record, as for a generated application
                VersionInfo.getGeneratorSha256(),
                grailsVersion,
                PackDescriptor.TEMPLATE_API,
                applicationType,
                options,
                ProjectLock.LINE_ENDINGS_OF_TEMPLATES,
                Collections.<LockedRepository>emptyList(),
                0,
                features,
                [:],
                StateYaml.sha256(indexBytes)).toBytes()
        // what is written is what the reader takes
        ProjectState.of(manifestBytes, lockBytes, indexBytes)
        Map<String, byte[]> files = new LinkedHashMap<>()
        files.put(ProjectState.IGNORE, ProjectState.IGNORE_CONTENT.getBytes(StandardCharsets.UTF_8))
        files.put(ProjectState.ATTRIBUTES, '* -text\n'.getBytes(StandardCharsets.UTF_8))
        files.put(ProjectState.BASELINE_INDEX, indexBytes)
        files.put(ProjectState.LOCK, lockBytes)
        files.put(ProjectState.MANIFEST, manifestBytes)
        return files
    }
}
