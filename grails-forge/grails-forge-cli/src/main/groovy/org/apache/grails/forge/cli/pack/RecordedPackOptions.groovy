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
package org.apache.grails.forge.cli.pack

import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.function.Consumer
import groovy.transform.CompileStatic
import org.apache.grails.forge.core.change.ProjectChangeException
import org.apache.grails.forge.core.pack.Pack
import org.apache.grails.forge.core.pack.PackArtifact
import org.apache.grails.forge.core.pack.PackDescriptorException
import org.apache.grails.forge.core.pack.PackLoader
import org.apache.grails.forge.core.state.ProjectLock
import org.apache.grails.forge.core.state.ProjectLock.LockedFeature
import org.apache.grails.forge.core.state.ProjectState
import picocli.CommandLine

/**
 * The options of a command that generates an application again, to add a feature to it or to compare
 * it, by which the feature packs that are not part of Grails the application was generated with, the
 * preset it was made from among them, are given again; and the packs they give. The pack of a jar that was taken from a repository is fetched
 * again by the coordinates the record of the application has, held to the SHA-256 it has; a pack that
 * was given as a directory is given again with {@code --feature-pack-dir}, and is held to the digest
 * of its content the record has. Nothing is read or asked for an application without such packs.
 *
 * @since 9.0
 */
@CompileStatic
class RecordedPackOptions {

    @CommandLine.Option(names = ['--feature-pack-dir'], paramLabel = 'DIRECTORY',
            description = 'The directory of a feature pack the application was generated with, which is not part of Grails and was given to the ' +
                    'create command as a directory. Its content has to have the SHA-256 the record of the application has. Can be given more than once.')
    List<File> directories = new ArrayList<>()

    // not shown, as for the create commands: the jar of a plugin the application was generated with, as a file
    @CommandLine.Option(names = ['--feature-pack-jar'], paramLabel = 'JAR', hidden = true,
            description = 'The jar of a plugin with a feature pack the application was generated with: one with the SHA-256 the record of the ' +
                    'application has for the jar, or a jar that was given as a file, whose pack has the content the record has. Can be given more than once.')
    List<File> jars = new ArrayList<>()

    @CommandLine.Option(names = ['--feature-pack-repository'], paramLabel = 'URL',
            description = 'A Maven repository to fetch the jar of a plugin from, whose feature pack the application was generated with. When the option ' +
                    'is given, only the repositories it names are asked; without it, those of GRAILS_REPO_URL, Maven Central and then the repository ' +
                    'the record of the application names for the jar. Can be given more than once.')
    List<String> repositories = new ArrayList<>()

    @CommandLine.Option(names = ['--offline'],
            description = 'Ask nothing of the network: the jar of a plugin whose feature pack the application has is taken only from a repository that ' +
                    'is a directory, or from --feature-pack-jar')
    boolean offline

    /**
     * @param projectDirectory the directory of the application
     * @param artifacts what fetches a jar
     * @param grailsRepoUrl the value of {@code GRAILS_REPO_URL}, or {@code null}
     * @param said told each line that says which jar is fetched again, and from where
     * @param warned told each entry of {@code GRAILS_REPO_URL} that is not asked
     * @return the packs that were given, and the packs of the jars the record names that were fetched
     * again. They are held to the record of the application by whatever generates it again
     * @throws IllegalArgumentException when a directory or a file holds no pack that can be used, or
     * a repository that was given is none
     * @throws ProjectChangeException when the jar of a plugin could not be fetched again, or is not the
     * one of the record: {@link ProjectChangeException#PACK_MISSING}, with what the repositories answered
     */
    List<Pack> packs(Path projectDirectory, PackArtifacts artifacts, String grailsRepoUrl, Consumer<String> said, Consumer<String> warned) {
        PackLoader loader = new PackLoader()
        List<Pack> packs = []
        for (File directory : directories) {
            packs << loaded { loader.fromDirectory(directory.toPath().toAbsolutePath().normalize()) }
        }
        ProjectState state = state(projectDirectory)
        ProjectLock lock = state?.lock
        String preset = state?.manifest?.preset
        List<LockedFeature> ofJars = lock == null ? Collections.<LockedFeature>emptyList() :
                lock.features.findAll { LockedFeature feature -> feature.source == LockedFeature.PACK && feature.coordinates != null && feature.sha256 != null }
        for (File jar : jars) {
            Path path = jar.toPath().toAbsolutePath().normalize()
            byte[] bytes
            try {
                bytes = Files.readAllBytes(path)
            } catch (IOException e) {
                throw new IllegalArgumentException("The jar ${path} cannot be read: ${e.class.simpleName}")
            }
            Pack pack = loaded { loader.fromJarBytes(bytes, bytes.length, path.toString()) }
            String digest = HexFormat.of().formatHex(MessageDigest.getInstance('SHA-256').digest(bytes))
            // the jar of a repository the record names, which was kept as a file: it is that jar by its digest
            LockedFeature recorded = ofJars.find { LockedFeature feature -> feature.name == pack.descriptor.name && feature.sha256 == digest }
            packs << (recorded == null ? pack : pack.withArtifact(new PackArtifact(pack.descriptor.name, "${recorded.coordinates}:${recorded.version}".toString(),
                    null, path.fileName.toString(), digest, null, true)))
        }
        List<LockedFeature> toFetch = ofJars.findAll { LockedFeature feature -> !packs.any { Pack pack -> pack.descriptor.name == feature.name } }
        if (toFetch.isEmpty()) {
            return packs
        }
        artifacts.offline = offline
        List<String> unused = []
        List<URI> asked = PackArtifacts.repositories(repositories, grailsRepoUrl, unused)
        for (String each : unused) {
            warned.accept(each)
        }
        for (LockedFeature feature : toFetch) {
            String coordinates = "${feature.coordinates}:${feature.version}".toString()
            List<URI> where = new ArrayList<>(asked)
            URI ofTheRecord = repositories.isEmpty() ? origin(feature.origin) : null
            if (ofTheRecord != null && !where.contains(ofTheRecord)) {
                where << ofTheRecord
            }
            String kind = feature.name == preset ? 'preset' : 'feature pack'
            said.accept(("The ${kind} ${feature.name} of the application is fetched again as ${coordinates}, with the SHA-256 ${feature.sha256} its record has. " +
                    "Asking ${where.take(4).collect { URI each -> PackArtifacts.shownAddress(each) }.join(', ')}${where.size() > 4 ? " and ${where.size() - 4} more" : ''}").toString())
            try {
                packs << artifacts.resolve(PackArtifacts.coordinates(coordinates), where, feature.sha256)
            } catch (PackArtifactException e) {
                // the pack the application was generated with is not to be had: a refusal of the command, as a pack that was not given is
                throw new ProjectChangeException("The ${kind} ${feature.name} of the " +
                        "application could not be had again: ${e.message}", ProjectChangeException.PACK_MISSING, e)
            }
        }
        return packs
    }

    // the record as far as it can be read: what is wrong with it is said by what generates the application again
    private static ProjectState state(Path projectDirectory) {
        try {
            return ProjectState.read(projectDirectory)
        } catch (IOException | RuntimeException ignored) {
            return null
        }
    }

    // the repository the record names for a jar, asked as a catalog's is: an https address of the internet
    private static URI origin(String recorded) {
        if (recorded == null) {
            return null
        }
        try {
            return PackArtifacts.repositoryOfCatalog(recorded)
        } catch (IllegalArgumentException ignored) {
            return null
        }
    }

    private static Pack loaded(Closure<Pack> load) {
        try {
            return load.call()
        } catch (PackDescriptorException e) {
            throw new IllegalArgumentException(e.message)
        }
    }
}
