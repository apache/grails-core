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

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.event.ApplicationEventPublisher;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import jakarta.inject.Singleton;
import org.grails.forge.api.GrailsForgeConfiguration;
import org.grails.forge.api.TestFramework;
import org.grails.forge.api.create.AbstractCreateController;
import org.grails.forge.application.ApplicationType;
import org.grails.forge.application.generator.GeneratorContext;
import org.grails.forge.application.generator.ProjectGenerator;
import org.grails.forge.client.github.oauth.AccessToken;
import org.grails.forge.client.github.oauth.GitHubOAuthClient;
import org.grails.forge.client.github.v3.GitHubApiClient;
import org.grails.forge.client.github.v3.GitHubRepository;
import org.grails.forge.client.github.v3.GitHubUser;
import org.grails.forge.io.ConsoleOutput;
import org.grails.forge.io.FileSystemOutputHandler;
import org.grails.forge.io.OutputHandler;
import org.grails.forge.options.BuildTool;
import org.grails.forge.options.GormImpl;
import org.grails.forge.options.JdkVersion;
import org.grails.forge.options.ServletImpl;
import org.grails.forge.util.GitHubUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.validation.constraints.NotNull;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * GitHub create service.
 *
 * @author Pavol Gressa
 * @since 6.0.0
 */
@Singleton
@Requires(beans = GitHubOAuthClient.class)
public class GitHubCreateService extends AbstractCreateController {

    private static final Logger LOG = LoggerFactory.getLogger(GitHubCreateService.class);
    private static final String TOKEN_PREFIX = "Bearer ";
    private static final String REPO_PREFIX = "generated";
    private static final String TMP_DIR = "/tmp";

    private final GitHubOAuthClient gitHubOAuthClient;
    private final GitHubApiClient gitHubApiClient;
    private final GrailsForgeConfiguration.GitHubConfiguration gitHubConfiguration;

    public GitHubCreateService(
            @NotNull ProjectGenerator projectGenerator,
            @NotNull ApplicationEventPublisher eventPublisher,
            @NotNull GitHubOAuthClient gitHubOAuthClient,
            @NotNull GitHubApiClient gitHubApiClient,
            @NotNull GrailsForgeConfiguration.GitHubConfiguration gitHubConfiguration) {
        super(projectGenerator, eventPublisher);
        this.gitHubOAuthClient = gitHubOAuthClient;
        this.gitHubApiClient = gitHubApiClient;
        this.gitHubConfiguration = gitHubConfiguration;
    }

    protected GitHubRepository creatApp(
            @NonNull ApplicationType type,
            @NonNull String name,
            @Nullable List<String> features,
            @Nullable BuildTool build,
            @Nullable TestFramework test,
            @Nullable GormImpl gorm,
            @Nullable ServletImpl servlet,
            @Nullable JdkVersion javaVersion,
            @NonNull String code,
            @NonNull String state,
            @Nullable String userAgent) {
        AccessToken accessToken = getGitHubAccessToken(code, state);
        String authToken = TOKEN_PREFIX + accessToken.getAccessToken();

        GitHubUser gitHubUser = getGitHubUser(authToken);

        GeneratorContext generatorContext = createProjectGeneratorContext(
                type, name, features, build, test, gorm, servlet, javaVersion, userAgent);

        String repoName = generatorContext.getProject().getName();
        String repoDescription = String.format("Grails %s Application", generatorContext.getProject().getNaturalName());
        GitHubRepository githubRepository = createGitHubRepository(authToken, repoName, repoDescription, gitHubUser);

        pushToGithubRepository(generatorContext, gitHubUser, githubRepository, accessToken);

        return githubRepository;
    }

    private void pushToGithubRepository(GeneratorContext generatorContext, GitHubUser gitHubUser,
                                        GitHubRepository githubRepository, AccessToken accessToken) {
        Path repoPath = null;
        try {
            repoPath = Files.createTempDirectory(Paths.get(TMP_DIR), REPO_PREFIX);
            generateAppLocally(generatorContext, repoPath);
            GitHubUtil.initAndPushToGitHubRepository(
                    githubRepository, gitHubUser, repoPath, accessToken.getAccessToken());

            if (LOG.isDebugEnabled()) {
                LOG.debug("Successfully pushed application to " + githubRepository);
            }
        } catch (Exception e) {
            LOG.error(e.getMessage(), e);
            throw new RuntimeException("Failed to push to created repository: " + githubRepository.getUrl());
        } finally {
            try {
                if (repoPath != null) {
                    deleteDirectory(repoPath);
                }
            } catch (IOException e) {
                LOG.error("Error cleaning up temporary project directory: " + e.getMessage(), e);
            }
        }
    }

    private GitHubRepository createGitHubRepository(String authToken, String repoName, String repoDescription,
                                                    GitHubUser gitHubUser) {
        try {
            if (gitHubApiClient.getRepository(gitHubConfiguration.getUserAgent(), authToken, gitHubUser.getLogin(), repoName) != null) {
                throw new IllegalArgumentException("Repository " + repoName + " already exists.");
            }

            GitHubRepository githubRepository = gitHubApiClient.createRepository(gitHubConfiguration.getUserAgent(),
                    authToken,
                    new GitHubRepository(repoName, repoDescription));

            if (LOG.isDebugEnabled()) {
                LOG.debug("Created repository " + githubRepository);
            }
            return githubRepository;
        } catch (HttpClientResponseException e) {
            LOG.error(e.getMessage(), e);
            throw new RuntimeException("Failed to create repository " + repoName);
        }
    }

    private AccessToken getGitHubAccessToken(String code, String state) {
        try {
            return gitHubOAuthClient.accessToken(gitHubConfiguration.getUserAgent(), gitHubConfiguration.getClientId(),
                    gitHubConfiguration.getClientSecret(), code, state);
        } catch (HttpClientResponseException e) {
            LOG.error(e.getMessage(), e);
            throw new RuntimeException("Failed to get user access token.");
        }
    }

    private GitHubUser getGitHubUser(String authToken) {
        try {
            GitHubUser gitHubUser = gitHubApiClient.getUser(gitHubConfiguration.getUserAgent(), authToken);
            if (LOG.isDebugEnabled()) {
                LOG.debug("Fetched user " + gitHubUser);
            }
            return gitHubUser;
        } catch (HttpClientResponseException e) {
            LOG.error(e.getMessage(), e);
            throw new RuntimeException("Failed to get user.");
        }
    }

    /**
     * Generates the micronaut application into specified {@code repoPath} directory
     *
     * @param generatorContext context
     * @param repoPath         path
     * @throws IOException if an I/O error occurs
     */
    protected void generateAppLocally(@NotNull GeneratorContext generatorContext, @NotNull Path repoPath) throws IOException {
        try {
            if (!Files.isDirectory(repoPath)) {
                throw new IllegalArgumentException(String.format("The path %s must be a directory!", repoPath));
            }

            OutputHandler outputHandler = new FileSystemOutputHandler(repoPath.toFile(), ConsoleOutput.NOOP);
            projectGenerator.generate(generatorContext.getApplicationType(),
                    generatorContext.getProject(),
                    outputHandler,
                    generatorContext);
        } catch (Exception e) {
            LOG.error("Error generating application: " + e.getMessage(), e);
            throw new IOException(e.getMessage(), e);
        }
    }

    private static void deleteDirectory(Path dir) throws IOException {
        try (Stream<Path> entries = Files.walk(dir)) {
            entries.sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
        }
    }
}
