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
package org.grails.forge.util;

import io.micronaut.core.util.StringUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.grails.forge.client.github.v3.GitHubRepository;
import org.grails.forge.client.github.v3.GitHubUser;

import javax.validation.constraints.NotNull;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Utility class for GitHub operations.
 *
 * @author Pavol Gressa
 * @since 6.0.0
 */
public class GitHubUtil {

    public static final String INIT_COMMIT_MESSAGE = "Initial commit";

    /**
     * Push files in {@code localPath} to the GitHub repository {@code repository}.
     *
     * @param repository github repository
     * @param user       github user
     * @param localPath  local directry with generated content
     * @param authToken  github user access authToken
     * @throws IOException if the push to repo fails
     */
    public static void initAndPushToGitHubRepository(
            @NotNull GitHubRepository repository,
            @NotNull GitHubUser user,
            @NotNull Path localPath,
            @NotNull String authToken) throws IOException {
        try {
            final String name = StringUtils.isEmpty(user.getName()) ? user.getLogin() : user.getName();
            final String email = StringUtils.isEmpty(user.getEmail()) ? user.getLogin() : user.getEmail();

            Git gitRepo = Git.init().setDirectory(localPath.toFile())
                    .call();
            gitRepo.add()
                    .addFilepattern(".")
                    .call();

            gitRepo.commit()
                    .setMessage(INIT_COMMIT_MESSAGE)
                    .setAuthor(name, email)
                    .setCommitter(name, email)
                    .setSign(false).call();

            Iterable<PushResult> pushResults = gitRepo.push()
                    .setRemote(repository.getCloneUrl())
                    .setCredentialsProvider(new UsernamePasswordCredentialsProvider(user.getLogin(), authToken))
                    .call();

            List<RemoteRefUpdate> failedRefUpdates = StreamSupport.stream(pushResults.spliterator(), false)
                    .flatMap(pushResult -> pushResult.getRemoteUpdates().stream())
                    .filter(remoteRefUpdate -> remoteRefUpdate.getStatus() != RemoteRefUpdate.Status.OK)
                    .collect(Collectors.toList());

            if (!failedRefUpdates.isEmpty()) {
                throw new IOException(String.format("Failed to push to %s repository.", repository.getName()));
            }
        } catch (GitAPIException e) {
            throw new IOException(e.getMessage(), e);
        }
    }
}
