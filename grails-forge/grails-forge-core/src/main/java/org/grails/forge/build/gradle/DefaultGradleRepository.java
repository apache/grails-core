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

import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class DefaultGradleRepository implements GradleRepository {
    private final int order;
    private final String url;
    private final String name;
    private final List<VersionRegexRepoFilter> versionFilters;

    public DefaultGradleRepository(int order, String url) {
        this(order, url, null, Collections.emptyList());
    }

    public DefaultGradleRepository(int order, String url, String name, List<VersionRegexRepoFilter> versionFilters) {
        this.order = order;
        this.url = url;
        this.name = name;
        this.versionFilters = versionFilters;
    }

    public int getOrder() {
        return order;
    }

    public String getUrl() {
        return url;
    }

    public String getName() {
        return name;
    }

    public List<VersionRegexRepoFilter> getVersionFilters() {
        return versionFilters;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != this.getClass()) {
            return false;
        }
        var that = (DefaultGradleRepository) obj;
        return this.order == that.order &&
                Objects.equals(this.url, that.url) &&
                Objects.equals(this.name, that.name) &&
                Objects.equals(this.versionFilters, that.versionFilters);
    }

    @Override
    public int hashCode() {
        return Objects.hash(order, url, name, versionFilters);
    }

    @Override
    @NonNull
    public String toSnippet(String basePadding) {
        String indent = "   ";
        StringBuilder snippet = new StringBuilder();
        snippet.append("maven {\n");
        snippet.append(basePadding).append(indent).append("url = '").append(url).append("'\n");
        if (name != null && !name.isEmpty()) {
            snippet.append(basePadding).append(indent).append("name = '").append(name).append("'\n");
        }
        for (VersionRegexRepoFilter filter : versionFilters) {
            snippet.append(basePadding).append(indent).append("content {\n")
                    .append(basePadding).append(indent).append(indent).append("includeVersionByRegex('")
                    .append(filter.groupRegex()).append("', '")
                    .append(filter.artifactRegex()).append("', '")
                    .append(filter.versionRegex())
                    .append("')").append("\n")
                    .append(basePadding).append(indent).append("}\n");
        }
        snippet.append(basePadding).append("}");
        return snippet.toString();
    }

    @Override
    public String toString() {
        return "GradleRepository[" +
                "order=" + order + ", " +
                "url=" + url + ", " +
                "name=" + name + ", " +
                "versionFilters=" + versionFilters + ']';
    }
}
