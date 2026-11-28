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
package org.apache.grails.forge.core.pack

import groovy.transform.CompileStatic

/**
 * The published jar a feature pack was read from: what was asked for, where it was found and the digest
 * of the bytes that were read. It says what was used; it is no proof of who wrote it.
 *
 * @since 9.0
 */
@CompileStatic
final class PackArtifact {

    /** The name of the pack the jar holds */
    final String name

    /** The coordinates the jar was asked for with, as {@code group:artifact:version} */
    final String coordinates

    /** The address of the repository the jar was found in */
    final String repository

    /** The name of the file in the repository, which for a snapshot is that of one build of it */
    final String file

    /** The SHA-256 of the bytes of the jar, in lowercase hexadecimal */
    final String sha256

    /** The kind of checksum of the repository the jar was checked against, or {@code null} when it publishes none */
    final String checkedAgainst

    /** Whether the digest is the one that was asked for */
    final boolean pinned

    /** The address of the catalog that gave the coordinates for the name of a feature, or {@code null} when they were given */
    final String catalog

    /** The SHA-256 of the bytes of that catalog as it was read, in lowercase hexadecimal, or {@code null} */
    final String catalogSha256

    PackArtifact(String name, String coordinates, String repository, String file, String sha256, String checkedAgainst, boolean pinned) {
        this(name, coordinates, repository, file, sha256, checkedAgainst, pinned, null, null)
    }

    private PackArtifact(String name, String coordinates, String repository, String file, String sha256, String checkedAgainst, boolean pinned,
                         String catalog, String catalogSha256) {
        this.catalog = catalog
        this.catalogSha256 = catalogSha256
        this.name = name
        this.coordinates = coordinates
        this.repository = repository
        this.file = file
        this.sha256 = sha256
        this.checkedAgainst = checkedAgainst
        this.pinned = pinned
    }

    /**
     * The repository as it may be written into a project, which others read: an https address, without
     * a user in it. A directory of this computer is not written, since its path names the computer and
     * often the user.
     *
     * @return the address, or {@code null} when it is not one to write
     */
    String getRecordableRepository() {
        return recordable(repository)
    }

    /**
     * @param catalog the address of the catalog that gave the coordinates of this jar for the name of a feature
     * @param catalogSha256 the SHA-256 of the bytes of the catalog as it was read
     * @return this, as the jar of a pack that was found by its name in that catalog
     */
    PackArtifact withCatalog(String catalog, String catalogSha256) {
        return new PackArtifact(name, coordinates, repository, file, sha256, checkedAgainst, pinned, catalog, catalogSha256)
    }

    /**
     * @return the address of the catalog as it may be written into a project, by the rule of
     * {@link #getRecordableRepository()}, or {@code null}
     */
    String getRecordableCatalog() {
        return recordable(catalog)
    }

    private static String recordable(String given) {
        if (given == null) {
            return null
        }
        try {
            URI address = new URI(given)
            return 'https'.equalsIgnoreCase(address.scheme) && address.host != null && address.rawUserInfo == null ? given : null
        } catch (URISyntaxException ignored) {
            return null
        }
    }
}
