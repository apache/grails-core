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
package org.grails.forge.build.dependencies

import groovy.transform.CompileStatic
import jakarta.annotation.Nonnull
import jakarta.annotation.Nullable

/**
 * A transitive dependency that a {@link Dependency} is declared without.
 *
 * @since 9.0
 */
@CompileStatic
final class DependencyExclusion {

    private final String groupId
    private final String artifactId

    /**
     * @param groupId the group of the excluded dependency
     * @param artifactId its artifact, or {@code null} to exclude every artifact of the group
     */
    DependencyExclusion(@Nonnull String groupId, @Nullable String artifactId) {
        Objects.requireNonNull(groupId, 'The group id of an exclusion must be set')
        if (groupId.isBlank() || (artifactId != null && artifactId.isBlank())) {
            throw new IllegalArgumentException('The group id and artifact id of an exclusion must not be blank')
        }
        this.groupId = groupId
        this.artifactId = artifactId
    }

    @Nonnull
    String getGroupId() {
        return groupId
    }

    @Nullable
    String getArtifactId() {
        return artifactId
    }

    @Override
    boolean equals(Object o) {
        if (this.is(o)) {
            return true
        }
        if (o == null || getClass() != o.getClass()) {
            return false
        }
        DependencyExclusion that = (DependencyExclusion) o
        return Objects.equals(groupId, that.groupId) && Objects.equals(artifactId, that.artifactId)
    }

    @Override
    int hashCode() {
        return Objects.hash(groupId, artifactId)
    }

    @Override
    String toString() {
        return artifactId == null ? groupId : groupId + ':' + artifactId
    }
}
