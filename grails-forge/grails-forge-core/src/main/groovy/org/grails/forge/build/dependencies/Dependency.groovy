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
import java.util.regex.Pattern

@CompileStatic
final class Dependency {

    /** A capability as a build file names it: {@code group:name}, of the characters of coordinates */
    private static final Pattern CAPABILITY = Pattern.compile(/[A-Za-z0-9][A-Za-z0-9_.\-]*:[A-Za-z0-9][A-Za-z0-9_.\-]*/)

    private final Scope scope
    private final String groupId
    private final String artifactId
    private final String version
    private final String versionProperty
    private final List<DependencyExclusion> exclusions
    private final List<String> capabilities
    private final boolean requiresLookup
    private final int order
    private final boolean annotationProcessorPriority
    private final boolean pom
    private final boolean enforced

    private Dependency(Scope scope,
                       String groupId,
                       String artifactId,
                       String version,
                       String versionProperty,
                       List<DependencyExclusion> exclusions,
                       List<String> capabilities,
                       boolean requiresLookup,
                       boolean annotationProcessorPriority,
                       int order,
                       boolean pom,
                       boolean enforced) {
        this.scope = scope
        this.groupId = groupId
        this.artifactId = artifactId
        this.version = version
        this.versionProperty = versionProperty
        this.exclusions = Collections.unmodifiableList(new ArrayList<DependencyExclusion>(exclusions))
        this.capabilities = Collections.unmodifiableList(new ArrayList<String>(capabilities))
        this.requiresLookup = requiresLookup
        this.annotationProcessorPriority = annotationProcessorPriority
        this.order = order
        this.pom = pom
        this.enforced = enforced
    }

    Scope getScope() {
        return scope
    }

    String getGroupId() {
        return groupId
    }

    String getArtifactId() {
        return artifactId
    }

    @Nullable
    String getVersion() {
        return version
    }

    @Nullable
    String getVersionProperty() {
        return versionProperty
    }

    int getOrder() {
        return order
    }

    /**
     * @return the transitive dependencies this dependency is declared without, in the order they were added
     */
    @Nonnull
    List<DependencyExclusion> getExclusions() {
        return exclusions
    }

    /**
     * @return the capabilities, each {@code group:name}, that the variant of this dependency is selected by, in the
     * order they were added; none for the variant Gradle selects by default
     */
    @Nonnull
    List<String> getCapabilities() {
        return capabilities
    }

    boolean isPom() {
        return pom
    }

    boolean isEnforced() {
        return enforced
    }

    static Builder builder() {
        return new Builder()
    }

    boolean requiresLookup() {
        return requiresLookup
    }

    Dependency resolved(Coordinate coordinate) {
        return new Dependency(
                scope,
                coordinate.getGroupId(),
                artifactId,
                coordinate.getVersion(),
                null,
                exclusions,
                capabilities,
                false,
                annotationProcessorPriority,
                order,
                coordinate.isPom(),
                coordinate.isEnforced())
    }

    Dependency scope(Scope newScope) {
        return new Dependency(
                newScope,
                groupId,
                artifactId,
                version,
                versionProperty,
                exclusions,
                capabilities,
                requiresLookup,
                annotationProcessorPriority,
                order,
                pom,
                enforced)
    }

    boolean isAnnotationProcessorPriority() {
        return annotationProcessorPriority
    }

    @Override
    boolean equals(Object o) {
        if (this.is(o)) {
            return true
        }
        if (o == null || getClass() != o.getClass()) {
            return false
        }
        Dependency that = (Dependency) o

        return Objects.equals(getGroupId(), that.getGroupId()) &&
                Objects.equals(getArtifactId(), that.getArtifactId()) &&
                Objects.equals(getScope(), that.getScope())
    }

    @Override
    int hashCode() {
        return Objects.hash(getGroupId(), getArtifactId(), getScope())
    }

    static class Builder {

        private Scope scope
        private String groupId
        private String artifactId
        private String version
        private String versionProperty
        private final List<DependencyExclusion> exclusions = new ArrayList<>()
        private final List<String> capabilities = new ArrayList<>()
        private boolean requiresLookup
        private int order = 0
        private boolean template = false
        private boolean annotationProcessorPriority = false
        private boolean pom = false
        private boolean enforced = false

        Builder scope(@Nonnull Scope scope) {
            if (template) {
                return copy().scope(scope)
            } else {
                this.scope = scope
                return this
            }
        }

        Builder buildSrc() {
            return scope(Scope.BUILD)
        }

        Builder implementation() {
            return scope(Scope.IMPLEMENTATION)
        }

        Builder console() {
            return scope(Scope.CONSOLE)
        }

        Builder compileOnly() {
            return scope(Scope.COMPILE_ONLY)
        }

        Builder developmentOnly() {
            return scope(Scope.DEVELOPMENT_ONLY)
        }

        Builder testAndDevelopmentOnly() {
            return scope(Scope.TEST_AND_DEVELOPMENT_ONLY)
        }

        Builder runtimeOnly() {
            return scope(Scope.RUNTIME_ONLY)
        }

        Builder testImplementation() {
            return scope(Scope.TEST_IMPLEMENTATION)
        }

        @SuppressWarnings('unused')
        Builder testCompileOnly() {
            return scope(Scope.TEST_COMPILE_ONLY)
        }

        Builder testRuntimeOnly() {
            return scope(Scope.TEST_RUNTIME_ONLY)
        }

        Builder annotationProcessor() {
            return scope(Scope.ANNOTATION_PROCESSOR)
        }

        Builder integrationTestImplementationTestFixtures() {
            return scope(Scope.INTEGRATION_TEST_IMPLEMENTATION_TEST_FIXTURES)
        }

        Builder classpath() {
            return scope(Scope.CLASSPATH)
        }

        Builder annotationProcessor(boolean requiresPriority) {
            this.annotationProcessorPriority = requiresPriority
            return annotationProcessor()
        }

        Builder testAnnotationProcessor() {
            return scope(Scope.TEST_ANNOTATION_PROCESSOR)
        }

        @SuppressWarnings('unused')
        Builder testAnnotationProcessor(boolean requiresPriority) {
            this.annotationProcessorPriority = requiresPriority
            return testAnnotationProcessor()
        }

        Builder groupId(@Nullable String groupId) {
            if (template) {
                return copy().groupId(groupId)
            } else {
                this.groupId = groupId
                return this
            }
        }

        Builder artifactId(@Nonnull String artifactId) {
            if (template) {
                return copy().artifactId(artifactId)
            } else {
                this.artifactId = artifactId
                return this
            }
        }

        Builder lookupArtifactId(@Nonnull String artifactId) {
            if (template) {
                return copy().lookupArtifactId(artifactId)
            } else {
                this.artifactId = artifactId
                this.requiresLookup = true
                return this
            }
        }

        Builder version(@Nullable String version) {
            if (template) {
                return copy().version(version)
            } else {
                this.version = version
                return this
            }
        }

        Builder versionProperty(@Nullable String versionProperty) {
            if (template) {
                return copy().versionProperty(versionProperty)
            } else {
                this.versionProperty = versionProperty
                return this
            }
        }

        /**
         * Excludes a transitive dependency of this dependency.
         *
         * @param groupId the group of the dependency to exclude
         * @param artifactId its artifact, or {@code null} to exclude every artifact of the group
         */
        @Nonnull
        Builder exclude(@Nonnull String groupId, @Nullable String artifactId) {
            if (template) {
                return copy().exclude(groupId, artifactId)
            }
            this.exclusions.add(new DependencyExclusion(groupId, artifactId))
            return this
        }

        /**
         * Selects the variant of this dependency that has the capability, as Gradle's
         * {@code capabilities { requireCapability('group:name') }} does: the Jakarta EE variant of a library that
         * publishes one beside its default variant, for example.
         *
         * @param capability the capability, {@code group:name}
         */
        @Nonnull
        Builder requireCapability(@Nonnull String capability) {
            if (template) {
                return copy().requireCapability(capability)
            }
            Objects.requireNonNull(capability, 'The capability of a dependency must be set')
            if (!CAPABILITY.matcher(capability).matches()) {
                throw new IllegalArgumentException("The capability of a dependency is 'group:name', but is '${capability}'")
            }
            this.capabilities.add(capability)
            return this
        }

        Builder order(int order) {
            if (template) {
                return copy().order(order)
            } else {
                this.order = order
                return this
            }
        }

        Builder template() {
            this.template = true
            return this
        }

        Builder pom(boolean pom) {
            this.pom = pom
            return this
        }

        Builder enforced(boolean enforced) {
            this.enforced = enforced
            return this
        }

        Dependency build() {
            Objects.requireNonNull(scope, 'The dependency scope must be set')
            Objects.requireNonNull(artifactId, 'The artifact id must be set')

            return buildInternal()
        }

        DependencyCoordinate buildCoordinate() {
            return buildCoordinate(false)
        }

        DependencyCoordinate buildCoordinate(boolean showVersionProperty) {
            Objects.requireNonNull(artifactId, 'The artifact id must be set')

            return new DependencyCoordinate(buildInternal(), showVersionProperty)
        }

        private Dependency buildInternal() {
            return new Dependency(
                    scope,
                    groupId,
                    artifactId,
                    version,
                    versionProperty,
                    exclusions,
                    capabilities,
                    requiresLookup,
                    annotationProcessorPriority,
                    order,
                    pom,
                    enforced)
        }

        private Builder copy() {
            Builder builder = new Builder().scope(scope)
            if (requiresLookup) {
                builder.lookupArtifactId(artifactId)
            } else {
                builder.groupId(groupId).artifactId(artifactId)
                if (versionProperty != null) {
                    builder.versionProperty(versionProperty)
                } else {
                    builder.version(version)
                }
            }
            builder.exclusions.addAll(exclusions)
            builder.capabilities.addAll(capabilities)
            return builder.order(order).pom(pom).enforced(enforced)
        }
    }
}
