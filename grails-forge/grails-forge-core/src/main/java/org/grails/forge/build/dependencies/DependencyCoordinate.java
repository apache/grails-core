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
package org.grails.forge.build.dependencies;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.order.Ordered;

import java.util.Objects;

@Introspected
public class DependencyCoordinate implements Coordinate, Ordered {

    private final String groupId;
    private final String artifactId;
    @Nullable
    private final String version;
    private final int order;
    private final boolean pom;

    public DependencyCoordinate(Dependency dependency) {
        this(dependency, false);
    }

    public DependencyCoordinate(Dependency dependency, boolean showVersionProperty) {
        groupId = dependency.getGroupId();
        artifactId = dependency.getArtifactId();
        if (showVersionProperty && dependency.getVersionProperty() != null) {
            version = "${" + dependency.getVersionProperty() + "}";
        } else {
            version = dependency.getVersion();
        }
        order = dependency.getOrder();
        pom = dependency.isPom();
    }

    public DependencyCoordinate(String groupId,
                                String artifactId,
                                @Nullable String version,
                                int order,
                                boolean pom) {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
        this.order = order;
        this.pom = pom;
    }

    @Override
    public int getOrder() {
        return order;
    }

    @NonNull
    @Override
    public String getGroupId() {
        return groupId;
    }

    @NonNull
    @Override
    public String getArtifactId() {
        return artifactId;
    }

    @Nullable
    @Override
    public String getVersion() {
        return version;
    }

    @Override
    public boolean isPom() {
        return pom;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        DependencyCoordinate that = (DependencyCoordinate) o;

        return Objects.equals(getGroupId(), that.getGroupId()) &&
                Objects.equals(getArtifactId(), that.getArtifactId()) &&
                isPom() == that.isPom();
    }

    @Override
    public int hashCode() {
        return Objects.hash(getGroupId(), getArtifactId(), isPom());
    }
}
