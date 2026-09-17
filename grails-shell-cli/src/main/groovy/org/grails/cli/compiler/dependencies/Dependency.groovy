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
package org.grails.cli.compiler.dependencies

import groovy.transform.CompileStatic
import org.springframework.util.Assert

/**
 * A single dependency.
 *
 * @author Phillip Webb
 * @since 1.3.0
 */
@CompileStatic
final class Dependency {

    private final String groupId

    private final String artifactId

    private final String version

    private final List<Exclusion> exclusions

    /**
     * Create a new {@link Dependency} instance.
     * @param groupId the group ID
     * @param artifactId the artifact ID
     * @param version the version
     */
    Dependency(String groupId, String artifactId, String version) {
        this(groupId, artifactId, version, Collections.emptyList())
    }

    /**
     * Create a new {@link Dependency} instance.
     * @param groupId the group ID
     * @param artifactId the artifact ID
     * @param version the version
     * @param exclusions the exclusions
     */
    Dependency(String groupId, String artifactId, String version, List<Exclusion> exclusions) {
        Assert.notNull(groupId, 'GroupId must not be null')
        Assert.notNull(artifactId, 'ArtifactId must not be null')
        Assert.notNull(version, 'Version must not be null')
        Assert.notNull(exclusions, 'Exclusions must not be null')
        this.groupId = groupId
        this.artifactId = artifactId
        this.version = version
        this.exclusions = Collections.unmodifiableList(exclusions)
    }

    /**
     * Return the dependency group id.
     * @return the group ID
     */
    String getGroupId() {
        return this.groupId
    }

    /**
     * Return the dependency artifact id.
     * @return the artifact ID
     */
    String getArtifactId() {
        return this.artifactId
    }

    /**
     * Return the dependency version.
     * @return the version
     */
    String getVersion() {
        return this.version
    }

    /**
     * Return the dependency exclusions.
     * @return the exclusions
     */
    List<Exclusion> getExclusions() {
        return this.exclusions
    }

    @Override
    boolean equals(Object obj) {
        if (this.is(obj)) {
            return true
        }
        if (obj == null) {
            return false
        }
        if (getClass() == obj.getClass()) {
            Dependency other = (Dependency) obj
            boolean result = true
            result = result && this.groupId.equals(other.groupId)
            result = result && this.artifactId.equals(other.artifactId)
            result = result && this.version.equals(other.version)
            result = result && this.exclusions.equals(other.exclusions)
            return result
        }
        return false
    }

    @Override
    int hashCode() {
        final int prime = 31
        int result = 1
        result = prime * result + this.groupId.hashCode()
        result = prime * result + this.artifactId.hashCode()
        result = prime * result + this.version.hashCode()
        result = prime * result + this.exclusions.hashCode()
        return result
    }

    @Override
    String toString() {
        return this.groupId + ':' + this.artifactId + ':' + this.version
    }

    /**
     * A dependency exclusion.
     */
    public static final class Exclusion {

        private final String groupId

        private final String artifactId

        Exclusion(String groupId, String artifactId) {
            Assert.notNull(groupId, 'GroupId must not be null')
            Assert.notNull(artifactId, 'ArtifactId must not be null')
            this.groupId = groupId
            this.artifactId = artifactId
        }

        /**
         * Return the exclusion artifact ID.
         * @return the exclusion artifact ID
         */
        String getArtifactId() {
            return this.artifactId
        }

        /**
         * Return the exclusion group ID.
         * @return the exclusion group ID
         */
        String getGroupId() {
            return this.groupId
        }

        @Override
        boolean equals(Object obj) {
            if (this.is(obj)) {
                return true
            }
            if (obj == null) {
                return false
            }
            if (getClass() == obj.getClass()) {
                Exclusion other = (Exclusion) obj
                boolean result = true
                result = result && this.groupId.equals(other.groupId)
                result = result && this.artifactId.equals(other.artifactId)
                return result
            }
            return false
        }

        @Override
        int hashCode() {
            return this.groupId.hashCode() * 31 + this.artifactId.hashCode()
        }

        @Override
        String toString() {
            return this.groupId + ':' + this.artifactId
        }

    }

}
