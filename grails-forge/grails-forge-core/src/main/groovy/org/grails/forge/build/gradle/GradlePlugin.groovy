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
package org.grails.forge.build.gradle

import groovy.transform.CompileStatic
import jakarta.annotation.Nonnull
import jakarta.annotation.Nullable
import org.grails.forge.build.BuildPlugin
import org.grails.forge.build.dependencies.Coordinate
import org.grails.forge.build.dependencies.CoordinateResolver
import org.grails.forge.build.dependencies.LookupFailedException
import org.grails.forge.build.dependencies.Scope
import org.grails.forge.options.BuildTool
import org.grails.forge.template.Writable

@CompileStatic
class GradlePlugin implements BuildPlugin {

    private final String id
    private final String version
    private final String artifactId
    private final Writable extension
    private final Writable settingsExtension
    private final boolean requiresLookup
    private final Set<String> buildImports
    private final int order
    private final boolean useApplyPlugin

    GradlePlugin(@Nonnull String id,
                        @Nullable String version,
                        @Nullable String artifactId,
                        @Nullable Writable extension,
                        @Nullable Writable settingsExtension,
                        boolean requiresLookup,
                        int order,
                        Set<String> buildImports) {
        this(id,
            version,
            artifactId,
            extension,
            settingsExtension,
            requiresLookup,
            order,
            buildImports,
            false)
    }

    GradlePlugin(@Nonnull String id,
                        @Nullable String version,
                        @Nullable String artifactId,
                        @Nullable Writable extension,
                        @Nullable Writable settingsExtension,
                        boolean requiresLookup,
                        int order,
                        Set<String> buildImports,
                        boolean useApplyPlugin) {
        this.id = id
        this.version = version
        this.artifactId = artifactId
        this.extension = extension
        this.settingsExtension = settingsExtension
        this.requiresLookup = requiresLookup
        this.order = order
        this.buildImports = buildImports
        this.useApplyPlugin = useApplyPlugin
    }

    @Nullable
    Set<String> getBuildImports() {
        return buildImports
    }

    @Nonnull
    String getId() {
        return id
    }

    @Nullable
    String getVersion() {
        return version
    }

    @Override
    @Nonnull
    BuildTool getBuildTool() {
        return null
    }

    @Override
    @Nullable
    Writable getExtension() {
        return extension
    }

    @Nullable
    Writable getSettingsExtension() {
        return this.settingsExtension
    }

    @Override
    int getOrder() {
        return this.order
    }

    @Override
    boolean requiresLookup() {
        return requiresLookup
    }

    boolean useApplyPlugin() {
        return useApplyPlugin
    }

    @Override
    BuildPlugin resolved(CoordinateResolver coordinateResolver) {
        Coordinate coordinate = coordinateResolver.resolve(artifactId)
                .orElseThrow(() -> new LookupFailedException(artifactId))
        return new GradlePlugin(id, coordinate.getVersion(), artifactId, extension, settingsExtension, false, order, buildImports)
    }

    @Override
    boolean equals(Object o) {
        if (this.is(o)) {
            return true
        }
        if (o == null || getClass() != o.getClass()) {
            return false
        }
        GradlePlugin that = (GradlePlugin) o
        return id.equals(that.id)
    }

    @Override
    int hashCode() {
        return Objects.hash(id)
    }

    static Builder builder() {
        return new Builder()
    }

    static final class Builder {

        private Scope scope = Scope.BUILD
        private String id
        private String artifactId
        private String version
        private Writable extension
        private Writable settingsExtension
        private boolean requiresLookup
        private boolean pom = false
        private int order = 0
        private boolean useApplyPlugin = false
        private boolean template = false
        private Set<String> buildImports = new HashSet<>()

        private Builder() { }

        @Nonnull
        GradlePlugin.Builder id(@Nonnull String id) {
            this.id = id
            return this
        }

        @Nonnull
        GradlePlugin.Builder buildImports(String... imports) {
            this.buildImports.addAll(Arrays.asList(imports))
            return this
        }

        @Nonnull
        GradlePlugin.Builder lookupArtifactId(@Nonnull String artifactId) {
            if (template) {
                return copy().lookupArtifactId(artifactId)
            } else {
                this.artifactId = artifactId
                this.requiresLookup = true
                return this
            }
        }

        @Nonnull
        GradlePlugin.Builder version(@Nullable String version) {
            this.version = version
            return this
        }

        @Nonnull
        GradlePlugin.Builder extension(@Nullable Writable extension) {
            this.extension = extension
            return this
        }

        @Nonnull
        GradlePlugin.Builder settingsExtension(@Nullable Writable settingsExtension) {
            this.settingsExtension = settingsExtension
            return this
        }

        @Nonnull
        GradlePlugin.Builder order(int order) {
            this.order = order
            return this
        }

        GradlePlugin.Builder template() {
            this.template = true
            return this
        }

        GradlePlugin.Builder pom(boolean pom) {
            this.pom = pom
            return this
        }

        GradlePlugin.Builder useApplyPlugin(boolean useApplyPlugin) {
            this.useApplyPlugin = useApplyPlugin
            return this
        }

        GradlePlugin build() {
            return new GradlePlugin(id, version, artifactId, extension, settingsExtension, requiresLookup, order, buildImports, useApplyPlugin)
        }

        private GradlePlugin.Builder copy() {
            GradlePlugin.Builder builder = new GradlePlugin.Builder()
            if (requiresLookup) {
                builder.lookupArtifactId(artifactId)
            } else {
                builder.id(id)
                builder.version(version)
            }
            if (extension != null) {
                builder.extension(extension)
            }
            return builder.order(order).pom(pom)
        }
    }

}
