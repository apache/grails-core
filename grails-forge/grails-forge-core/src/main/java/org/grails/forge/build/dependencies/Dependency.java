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

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import org.grails.forge.template.Writable;

import java.util.Objects;

public final class Dependency {

    private final Scope scope;
    private final String groupId;
    private final String artifactId;
    private final String version;
    private final String versionProperty;
    private final Writable extension;
    private final boolean requiresLookup;
    private final int order;
    private final boolean annotationProcessorPriority;
    private final boolean pom;

    private Dependency(Scope scope,
                       String groupId,
                       String artifactId,
                       String version,
                       String versionProperty,
                       Writable extension,
                       boolean requiresLookup,
                       boolean annotationProcessorPriority,
                       int order,
                       boolean pom) {
        this.scope = scope;
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
        this.versionProperty = versionProperty;
        this.extension = extension;
        this.requiresLookup = requiresLookup;
        this.annotationProcessorPriority = annotationProcessorPriority;
        this.order = order;
        this.pom = pom;
    }

    private Dependency(Scope scope,
                       String groupId,
                       String artifactId,
                       String version,
                       String versionProperty,
                       boolean requiresLookup,
                       boolean annotationProcessorPriority,
                       int order,
                       boolean pom) {
        this(scope, groupId, artifactId, version, versionProperty, null, requiresLookup, annotationProcessorPriority, order, pom);
    }

    public Scope getScope() {
        return scope;
    }

    public String getGroupId() {
        return groupId;
    }

    public String getArtifactId() {
        return artifactId;
    }

    @Nullable
    public String getVersion() {
        return version;
    }

    @Nullable
    public String getVersionProperty() {
        return versionProperty;
    }

    public int getOrder() {
        return order;
    }

    public boolean isPom() {
        return pom;
    }

    public static Builder builder() {
        return new Builder();
    }

    public boolean requiresLookup() {
        return requiresLookup;
    }

    public Dependency resolved(Coordinate coordinate) {
        return new Dependency(
                scope,
                coordinate.getGroupId(),
                artifactId,
                coordinate.getVersion(),
                null,
                false,
                annotationProcessorPriority,
                order,
                coordinate.isPom());
    }

    public Dependency scope(Scope newScope) {
        return new Dependency(
                newScope,
                groupId,
                artifactId,
                version,
                versionProperty,
                requiresLookup,
                annotationProcessorPriority,
                order,
                pom);
    }

    public boolean isAnnotationProcessorPriority() {
        return annotationProcessorPriority;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Dependency that = (Dependency) o;

        return Objects.equals(getGroupId(), that.getGroupId()) &&
                Objects.equals(getArtifactId(), that.getArtifactId()) &&
                Objects.equals(getScope(), that.getScope());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getGroupId(), getArtifactId(), getScope());
    }

    public static class Builder {

        private Scope scope;
        private String groupId;
        private String artifactId;
        private String version;
        private String versionProperty;
        private Writable extension;
        private boolean requiresLookup;
        private int order = 0;
        private boolean template = false;
        private boolean annotationProcessorPriority = false;
        private boolean pom = false;

        public Builder scope(@NonNull Scope scope) {
            if (template) {
                return copy().scope(scope);
            } else {
                this.scope = scope;
                return this;
            }
        }

        public Builder buildSrc() {
            return scope(Scope.BUILD);
        }

        public Builder implementation() {
            return scope(Scope.IMPLEMENTATION);
        }

        public Builder console() {
            return scope(Scope.CONSOLE);
        }

        public Builder compileOnly() {
            return scope(Scope.COMPILE_ONLY);
        }

        public Builder developmentOnly() {
            return scope(Scope.DEVELOPMENT_ONLY);
        }

        public Builder testAndDevelopmentOnly() {
            return scope(Scope.TEST_AND_DEVELOPMENT_ONLY);
        }

        public Builder runtimeOnly() {
            return scope(Scope.RUNTIME_ONLY);
        }

        public Builder testImplementation() {
            return scope(Scope.TEST_IMPLEMENTATION);
        }

        @SuppressWarnings("unused")
        public Builder testCompileOnly() {
            return scope(Scope.TEST_COMPILE_ONLY);
        }

        public Builder testRuntimeOnly() {
            return scope(Scope.TEST_RUNTIME_ONLY);
        }

        public Builder annotationProcessor() {
            return scope(Scope.ANNOTATION_PROCESSOR);
        }

        public Builder profile() {
            return scope(Scope.PROFILE);
        }

        public Builder integrationTestImplementationTestFixtures() {
            return scope(Scope.INTEGRATION_TEST_IMPLEMENTATION_TEST_FIXTURES);
        }
        
       public Builder classpath() {
            return scope(Scope.CLASSPATH);
       }

        public Builder annotationProcessor(boolean requiresPriority) {
            this.annotationProcessorPriority = requiresPriority;
            return annotationProcessor();
        }

        public Builder testAnnotationProcessor() {
            return scope(Scope.TEST_ANNOTATION_PROCESSOR);
        }

        @SuppressWarnings("unused")
        public Builder testAnnotationProcessor(boolean requiresPriority) {
            this.annotationProcessorPriority = requiresPriority;
            return testAnnotationProcessor();
        }

        public Builder groupId(@Nullable String groupId) {
            if (template) {
                return copy().groupId(groupId);
            } else {
                this.groupId = groupId;
                return this;
            }
        }

        public Builder artifactId(@NonNull String artifactId) {
            if (template) {
                return copy().artifactId(artifactId);
            } else {
                this.artifactId = artifactId;
                return this;
            }
        }

        public Builder lookupArtifactId(@NonNull String artifactId) {
            if (template) {
                return copy().lookupArtifactId(artifactId);
            } else {
                this.artifactId = artifactId;
                this.requiresLookup = true;
                return this;
            }
        }

        public Builder version(@Nullable String version) {
            if (template) {
                return copy().version(version);
            } else {
                this.version = version;
                return this;
            }
        }

        public Builder versionProperty(@Nullable String versionProperty) {
            if (template) {
                return copy().versionProperty(versionProperty);
            } else {
                this.versionProperty = versionProperty;
                return this;
            }
        }

        @NonNull
        public Builder extension(@Nullable Writable extension) {
            this.extension = extension;
            return this;
        }

        public Builder order(int order) {
            if (template) {
                return copy().order(order);
            } else {
                this.order = order;
                return this;
            }
        }

        public Builder template() {
            this.template = true;
            return this;
        }

        public Builder pom(boolean pom) {
            this.pom = pom;
            return this;
        }

        public Dependency build() {
            Objects.requireNonNull(scope, "The dependency scope must be set");
            Objects.requireNonNull(artifactId, "The artifact id must be set");

            return buildInternal();
        }

        public DependencyCoordinate buildCoordinate() {
            return buildCoordinate(false);
        }

        public DependencyCoordinate buildCoordinate(boolean showVersionProperty) {
            Objects.requireNonNull(artifactId, "The artifact id must be set");

            return new DependencyCoordinate(buildInternal(), showVersionProperty);
        }

        private Dependency buildInternal() {
            return new Dependency(
                    scope,
                    groupId,
                    artifactId,
                    version,
                    versionProperty,
                    requiresLookup,
                    annotationProcessorPriority,
                    order,
                    pom);
        }

        private Builder copy() {
            Builder builder = new Builder().scope(scope);
            if (requiresLookup) {
                builder.lookupArtifactId(artifactId);
            } else {
                builder.groupId(groupId).artifactId(artifactId);
                if (versionProperty != null) {
                    builder.versionProperty(versionProperty);
                } else {
                    builder.version(version);
                }
            }
            if (extension != null) {
                builder.extension(extension);
            }
            return builder.order(order).pom(pom);
        }
    }
}
