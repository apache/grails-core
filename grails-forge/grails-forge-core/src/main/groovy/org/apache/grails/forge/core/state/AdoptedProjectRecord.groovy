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
 * be, written by {@code project migrate} and not when the application was generated. There are two
 * of them. The one of {@link #adopted} has the baseline the generator writes for what the application
 * is taken to be, and says so with {@code generationState: adopted}: a feature is added against that
 * baseline. The one of {@link #files} is written where no such baseline can be generated: its manifest
 * says {@code generationState: untracked} and its baseline has no file, so nothing can be merged
 * against it, no feature is added to such an application and it is not generated again. The commands
 * that create the classes of an application read from both what they read from the record of a
 * generated one.
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

    /**
     * The manifest and the lock the generator writes for an application, as those of an application
     * that was there before it had a record and whose baseline is what the generator writes for what
     * it is taken to be: the manifest says {@code generationState: adopted} and is the same otherwise,
     * and the lock has the digest of that manifest. The baseline and its index are those the generator
     * wrote.
     *
     * @param manifestBytes the manifest the generator wrote
     * @param lockBytes the lock the generator wrote with it
     * @return the manifest and the lock of the adopted record, by their paths in the project
     * @throws ProjectStateException when the two are not a manifest and a lock this version reads
     * @since 9.0
     */
    static Map<String, byte[]> adopted(byte[] manifestBytes, byte[] lockBytes) {
        ProjectManifest written = ProjectManifest.read(manifestBytes)
        ProjectLock lock = ProjectLock.read(lockBytes)
        byte[] manifest = new ProjectManifest(written.grailsVersion, written.applicationType, written.name, written.defaultPackage, written.options,
                ProjectManifest.ADOPTED, written.requestedFeatures, written.excludedFeatures, written.parameters, written.preset).toBytes()
        byte[] sealed = new ProjectLock(StateYaml.sha256(manifest), lock.generatorName, lock.generatorVersion, lock.generatorSha256, lock.grailsVersion, lock.templateApi,
                lock.applicationType, lock.options, lock.lineEndings, lock.repositories, lock.repositoriesNotNamed, lock.features, lock.parameters,
                lock.baselineIndexSha256).toBytes()
        Map<String, byte[]> files = new LinkedHashMap<>()
        files.put(ProjectState.MANIFEST, manifest)
        files.put(ProjectState.LOCK, sealed)
        return files
    }
}
