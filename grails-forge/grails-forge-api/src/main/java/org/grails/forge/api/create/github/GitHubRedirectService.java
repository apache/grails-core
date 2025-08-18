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

import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.uri.UriBuilder;
import org.grails.forge.api.RequestInfo;
import org.grails.forge.api.GrailsForgeConfiguration;
import org.grails.forge.client.github.oauth.GitHubOAuthClient;
import org.grails.forge.client.github.v3.GitHubRepository;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.validation.constraints.NotNull;
import java.net.URI;
import java.util.UUID;

/**
 * Redirect service.
 *
 * @author Pavol Gressa
 * @since 6.0.0
 */
@Singleton
@Requires(property = GitHubRedirectService.OAUTH_URL)
public class GitHubRedirectService {

    public static final String OAUTH_URL = "micronaut.http.services." + GitHubOAuthClient.SERVICE_ID + ".url";
    private static final Logger LOG = LoggerFactory.getLogger(GitHubRedirectService.class);
    private static final String AUTHORIZE_PATH = "/login/oauth/authorize";

    private final String githubOAuthUrl;
    private final GrailsForgeConfiguration starterConfiguration;
    private final GrailsForgeConfiguration.GitHubConfiguration gitHubConfiguration;

    public GitHubRedirectService(
            @NotNull @Property(name = OAUTH_URL) String githubOAuthUrl,
            @NotNull GrailsForgeConfiguration starterConfiguration,
            @NotNull GrailsForgeConfiguration.GitHubConfiguration gitHubConfiguration) {
        this.githubOAuthUrl = githubOAuthUrl + AUTHORIZE_PATH;
        this.starterConfiguration = starterConfiguration;
        this.gitHubConfiguration = gitHubConfiguration;
    }

    /**
     * Creates redirect URI to github oauth auhtorise in order to receive the user access code.
     *
     * @param requestInfo origin request info
     * @return URI to github oauth authorise
     */
    protected URI constructOAuthRedirectUrl(RequestInfo requestInfo) {
        try {
            UriBuilder uriBuilder = UriBuilder.of(requestInfo.getServerURL()).path(requestInfo.getPath());
            requestInfo.getParameters().forEachValue(uriBuilder::queryParam);
            URI redirectUri = uriBuilder.build();

            return UriBuilder.of(githubOAuthUrl)
                    .queryParam("scope", gitHubConfiguration.getTokenPermissions())
                    .queryParam("client_id", gitHubConfiguration.getClientId())
                    .queryParam("redirect_uri", redirectUri.toString())
                    .queryParam("state", UUID.randomUUID().toString())
                    .build();
        } catch (Exception e) {
            String msg = "Failed to construct redirect URI using request " + requestInfo + " to GiHub OAuth: " + e.getMessage();
            LOG.error(msg, e);
            throw new RuntimeException(msg);
        }
    }

    /**
     * Creates redirect URI back to Grails Application Forge with details about created github repository in query parameters.
     *
     * @param gitHubRepository GitHub Repository
     * @return URI or null if the Grails Application Forge URL is missing
     */
    protected URI constructGrailsForgeRedirectUrl(GitHubRepository gitHubRepository) {
        URI redirectUri = getLauncherURI();
        if (redirectUri == null) {
            if (LOG.isWarnEnabled()) {
                LOG.warn("Can't construct redirect back to Grails Application Forge as configuration is missing redirect uri.");
            }
            return null;
        }

        try {
            return UriBuilder.of(redirectUri)
                    .queryParam("url", gitHubRepository.getUrl())
                    .queryParam("cloneUrl", gitHubRepository.getCloneUrl())
                    .queryParam("htmlUrl", gitHubRepository.getHtmlUrl())
                    .build();
        } catch (Exception e) {
            String msg = "Failed to construct redirect to URI back to Grails Application Forge: " + e.getMessage();
            LOG.error(msg, e);
            throw new RuntimeException(msg);
        }
    }

    /**
     * Creates redirect URI back to Grails app forge with error message.
     *
     * @param error message
     * @return URI or null if the Grails Forge url is missing
     */
    protected URI constructGrailsForgeErrorRedirectUrl(String error) {
        URI redirectUri = getLauncherURI();
        if (redirectUri == null) {
            if (LOG.isWarnEnabled()) {
                LOG.warn("Can't construct redirect back to Grails Application Forge as configuration is missing redirect uri.");
            }
            return null;
        }

        try {
            return UriBuilder.of(redirectUri)
                    .queryParam("error", error)
                    .build();
        } catch (Exception e) {
            String msg = "Failed to construct error redirect to URI back to Grails Application Forge: " + e.getMessage();
            LOG.error(msg, e);
            throw new RuntimeException(msg);
        }
    }

    /**
     * Returns launcher uri.
     * @return uri
     */
    protected URI getLauncherURI() {
        return starterConfiguration.getRedirectUri().orElse(null);
    }
}
