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
package org.grails.gradle.plugin.core

import java.nio.charset.StandardCharsets
import java.nio.file.FileSystemNotFoundException
import java.nio.file.Paths
import java.security.MessageDigest

import groovy.transform.CompileStatic

import org.gradle.api.artifacts.ArtifactRepositoryContainer
import org.gradle.api.artifacts.repositories.MavenArtifactRepository

/**
 * The repositories the applications of {@code forgePackTest} get when the build names none: those of
 * the Maven repositories of the build script that can be given to a generated application by an
 * address alone, and what is said of the others.
 *
 * <p>A generated application is given a repository as the commands that create one are, by an entry
 * of {@code GRAILS_REPO_URL}: an {@code https} address, the path of a directory, or one of the two
 * repositories Gradle has a name for. An entry is written into the build files of the application as
 * it is. So a repository whose address has a user or a password is not taken from the build script,
 * where nobody chose to have it copied, and neither is one the generator would refuse for its
 * protocol.</p>
 *
 * @since 9.0
 */
@CompileStatic
class ForgePackFixtureRepositories implements Serializable {

    private static final long serialVersionUID = 1L

    /** The entry for the local Maven repository */
    public static final String MAVEN_LOCAL = 'mavenLocal()'

    /** The entry for Maven Central */
    public static final String MAVEN_CENTRAL = 'mavenCentral()'

    // the class of Gradle whose instances are the local Maven repository, wherever it is and whatever its name
    private static final String LOCAL_REPOSITORY_CLASS = 'DefaultMavenLocalArtifactRepository'

    /** The entries, in the order of the repositories, each once */
    final List<String> used

    /** What to say of each repository that is left out, on a line, without a user or a password */
    final List<String> leftOut

    ForgePackFixtureRepositories(List<String> used, List<String> leftOut) {
        this.used = new ArrayList<String>(used)
        this.leftOut = new ArrayList<String>(leftOut)
    }

    /**
     * @param repositories the Maven repositories of a build script
     * @return what of them the applications get, and what is said of the rest
     */
    static ForgePackFixtureRepositories of(Collection<MavenArtifactRepository> repositories) {
        List<String> used = []
        List<String> leftOut = []
        for (MavenArtifactRepository repository : repositories) {
            URI url = repository.url
            if (isLocal(repository)) {
                used << MAVEN_LOCAL
            } else if (url == null) {
                leftOut << ("The repository ${repository.name} of the build has no address, and the applications of forgePackTest " +
                        'do not get it').toString()
            } else if (sameAddress(url, URI.create(ArtifactRepositoryContainer.MAVEN_CENTRAL_URL))) {
                used << MAVEN_CENTRAL
            } else if ('file'.equalsIgnoreCase(url.scheme)) {
                String path = localPath(url)
                if (path == null) {
                    leftOut << ("The repository of the build at ${url} is a file address that is no path of this computer, and the " +
                            'applications of forgePackTest do not get it').toString()
                } else {
                    used << path
                }
            } else if (url.rawUserInfo != null || (url.rawAuthority ?: '').contains('@')) {
                leftOut << ("The repository of the build at ${url.host ?: 'a host that cannot be read'} has a user or a password in its " +
                        'address, and the applications of forgePackTest do not get it: an address is written into their build files as ' +
                        'it is. To use it all the same, name it in grails { forgePackTest { repositories = [...] } }').toString()
            } else if (!'https'.equalsIgnoreCase(url.scheme)) {
                leftOut << ("The repository of the build at ${url} is not one a generated application can be given, which are those " +
                        'with an https address and directories, and the applications of forgePackTest do not get it').toString()
            } else {
                used << url.toString()
            }
        }
        return new ForgePackFixtureRepositories(used.toUnique(), leftOut.toUnique())
    }

    /**
     * @param entries the entries the applications get
     * @return what stands for them as an input of the task: they can hold a password, and an input is kept as text
     */
    static String digest(List<String> entries) {
        MessageDigest digest = MessageDigest.getInstance('SHA-256')
        for (String entry : entries) {
            byte[] bytes = entry.getBytes(StandardCharsets.UTF_8)
            digest.update("${bytes.length}:".toString().getBytes(StandardCharsets.UTF_8))
            digest.update(bytes)
        }
        return digest.digest().encodeHex().toString()
    }

    // the path of a file address: one with a host is a share of Windows, which is no path on another system
    private static String localPath(URI url) {
        try {
            return Paths.get(url).toFile().absolutePath
        } catch (IllegalArgumentException | FileSystemNotFoundException ignored) {
            return null
        }
    }

    private static boolean isLocal(MavenArtifactRepository repository) {
        for (Class<?> type = repository.getClass(); type != null; type = type.superclass) {
            if (type.simpleName == LOCAL_REPOSITORY_CLASS) {
                return true
            }
        }
        return false
    }

    private static boolean sameAddress(URI one, URI other) {
        return one.scheme?.equalsIgnoreCase(other.scheme) && one.rawUserInfo == null && one.host?.equalsIgnoreCase(other.host) &&
                one.port == other.port && withoutSlash(one.rawPath) == withoutSlash(other.rawPath) && one.rawQuery == null
    }

    private static String withoutSlash(String path) {
        String given = path ?: ''
        return given.endsWith('/') ? given.substring(0, given.length() - 1) : given
    }
}
