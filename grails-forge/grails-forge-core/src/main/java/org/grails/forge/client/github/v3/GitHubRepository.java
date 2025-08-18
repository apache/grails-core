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
package org.grails.forge.client.github.v3;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.Nullable;

/**
 * GitHub repository.
 *
 * @author Pavol Gressa
 * @see <a href="https://docs.github.com/en/rest/reference/repos">Api reference repositories</a>
 * @since 6.0.0
 */
@Introspected
public class GitHubRepository {
    private final String name;
    private final String description;
    private final String url;
    private final String htmlUrl;
    private final String cloneUrl;

    public GitHubRepository(String name, String description) {
        this(name, description, null, null, null);
    }

    @JsonCreator
    public GitHubRepository(
            @JsonProperty("name") String name,
            @JsonProperty("description") String description,
            @JsonProperty("url") @Nullable String url,
            @JsonProperty("html_url") @Nullable String htmlUrl,
            @JsonProperty("clone_url") @Nullable String cloneUrl) {
        this.name = name;
        this.description = description;
        this.url = url;
        this.htmlUrl = htmlUrl;
        this.cloneUrl = cloneUrl;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getCloneUrl() {
        return cloneUrl;
    }

    public String getUrl() {
        return url;
    }

    public String getHtmlUrl() {
        return htmlUrl;
    }

    @Override
    public String toString() {
        return "GitHubRepository{" +
                "name='" + name + '\'' +
                ", description='" + description + '\'' +
                ", url='" + url + '\'' +
                ", htmlUrl='" + htmlUrl + '\'' +
                ", cloneUrl='" + cloneUrl + '\'' +
                '}';
    }
}
