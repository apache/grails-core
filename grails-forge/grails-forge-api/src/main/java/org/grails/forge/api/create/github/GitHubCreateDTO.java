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
package org.grails.forge.api.create.github;

import io.micronaut.core.annotation.Introspected;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Create application in GitHub repository.
 *
 * @author Pavol Gressa
 * @since 6.0.0
 */
@Introspected
@Schema(name = "GitHubCreate", description = "Details of created GitHub repository with Micronaut application.")
public class GitHubCreateDTO {
    private final String url;
    private final String cloneUrl;
    private final String htmlUrl;

    public GitHubCreateDTO(String url, String cloneUrl, String htmlUrl) {
        this.url = url;
        this.cloneUrl = cloneUrl;
        this.htmlUrl = htmlUrl;
    }

    @Schema(description = "Repository url")
    public String getUrl() {
        return url;
    }

    @Schema(description = "Repository clone url")
    public String getCloneUrl() {
        return cloneUrl;
    }

    @Schema(description = "Repository html url")
    public String getHtmlUrl() {
        return htmlUrl;
    }
}
