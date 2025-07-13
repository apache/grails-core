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
package org.grails.forge.build.gradle;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import org.grails.forge.build.BuildPlugin;
import org.grails.forge.build.dependencies.*;
import org.grails.forge.options.BuildTool;
import org.grails.forge.template.Writable;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class GradlePlugin implements BuildPlugin {

    private final String id;
    private final String version;
    private final String artifactId;
    private final Writable extension;
    private final Writable settingsExtension;
    private final boolean requiresLookup;
    private final Set<String> buildImports;
    private final int order;
    private final boolean useApplyPlugin;

    public GradlePlugin(@NonNull String id,
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
            false);
    }

    public GradlePlugin(@NonNull String id,
                        @Nullable String version,
                        @Nullable String artifactId,
                        @Nullable Writable extension,
                        @Nullable Writable settingsExtension,
                        boolean requiresLookup,
                        int order,
                        Set<String> buildImports,
                        boolean useApplyPlugin) {
        this.id = id;
        this.version = version;
        this.artifactId = artifactId;
        this.extension = extension;
        this.settingsExtension = settingsExtension;
        this.requiresLookup = requiresLookup;
        this.order = order;
        this.buildImports = buildImports;
        this.useApplyPlugin = useApplyPlugin;
    }

    @Nullable
    public Set<String> getBuildImports() {
        return buildImports;
    }

    @NonNull
    public String getId() {
        return id;
    }

    @Nullable
    public String getVersion() {
        return version;
    }

    @Override
    @NonNull
    public BuildTool getBuildTool() {
        return null;
    }

    @Override
    @Nullable
    public Writable getExtension() {
        return extension;
    }

    @Nullable
    public Writable getSettingsExtension() {
        return this.settingsExtension;
    }

    @Override
    public int getOrder() {
        return this.order;
    }

    @Override
    public boolean requiresLookup() {
        return requiresLookup;
    }

    public boolean useApplyPlugin() {
        return useApplyPlugin;
    }

    @Override
    public BuildPlugin resolved(CoordinateResolver coordinateResolver) {
        Coordinate coordinate = coordinateResolver.resolve(artifactId)
                .orElseThrow(() -> new LookupFailedException(artifactId));
        return new GradlePlugin(id, coordinate.getVersion(), artifactId, extension, settingsExtension, false, order, buildImports);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        GradlePlugin that = (GradlePlugin) o;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private Scope scope = Scope.BUILD;
        private String id;
        private String artifactId;
        private String version;
        private Writable extension;
        private Writable settingsExtension;
        private boolean requiresLookup;
        private boolean pom = false;
        private int order = 0;
        private boolean useApplyPlugin = false;
        private boolean template = false;
        private Set<String> buildImports = new HashSet<>();

        private Builder() { }

        @NonNull
        public GradlePlugin.Builder id(@NonNull String id) {
            this.id = id;
            return this;
        }

        @NonNull
        public GradlePlugin.Builder buildImports(String ...imports) {
            this.buildImports.addAll(Arrays.asList(imports));
            return this;
        }

        @NonNull
        public GradlePlugin.Builder lookupArtifactId(@NonNull String artifactId) {
            if (template) {
                return copy().lookupArtifactId(artifactId);
            } else {
                this.artifactId = artifactId;
                this.requiresLookup = true;
                return this;
            }
        }

        @NonNull
        public GradlePlugin.Builder version(@Nullable String version) {
            this.version = version;
            return this;
        }

        @NonNull
        public GradlePlugin.Builder extension(@Nullable Writable extension) {
            this.extension = extension;
            return this;
        }

        @NonNull
        public GradlePlugin.Builder settingsExtension(@Nullable Writable settingsExtension) {
            this.settingsExtension = settingsExtension;
            return this;
        }

        @NonNull
        public GradlePlugin.Builder order(int order) {
            this.order = order;
            return this;
        }

        public GradlePlugin.Builder template() {
            this.template = true;
            return this;
        }

        public GradlePlugin.Builder pom(boolean pom) {
            this.pom = pom;
            return this;
        }

        public GradlePlugin.Builder useApplyPlugin(boolean useApplyPlugin) {
            this.useApplyPlugin = useApplyPlugin;
            return this;
        }

        public GradlePlugin build() {
            return new GradlePlugin(id, version, artifactId, extension, settingsExtension, requiresLookup, order, buildImports, useApplyPlugin);
        }

        private GradlePlugin.Builder copy() {
            GradlePlugin.Builder builder = new GradlePlugin.Builder();
            if (requiresLookup) {
                builder.lookupArtifactId(artifactId);
            } else {
                builder.id(id);
                builder.version(version);
            }
            if (extension != null) {
                builder.extension(extension);
            }
            return builder.order(order).pom(pom);
        }
    }

}
