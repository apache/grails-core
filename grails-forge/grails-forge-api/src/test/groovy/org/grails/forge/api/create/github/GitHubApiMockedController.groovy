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

package org.grails.forge.api.create.github

import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpHeaders
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Header
import io.micronaut.http.annotation.PathVariable
import io.micronaut.http.annotation.Post
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.transport.URIish
import org.grails.forge.client.github.v3.GitHubApiClient
import org.grails.forge.client.github.v3.GitHubApiOperations
import org.grails.forge.client.github.v3.GitHubRepository
import org.grails.forge.client.github.v3.GitHubSecret
import org.grails.forge.client.github.v3.GitHubSecretsPublicKey
import org.grails.forge.client.github.v3.GitHubUser
import org.grails.forge.client.github.v3.GitHubWorkflowRun
import org.grails.forge.client.github.v3.GitHubWorkflowRuns

import java.nio.file.Files
import java.nio.file.Path

@Requires(property = 'spec.name', value = 'GitHubCreateControllerSpec')
@Controller(value = "/")
class GitHubApiMockedController implements GitHubApiOperations {

    @Override
    @Post(value = "/user/repos", processes = [GitHubApiClient.GITHUB_V3_TYPE, MediaType.APPLICATION_JSON])
    GitHubRepository createRepository(
            @Header(HttpHeaders.USER_AGENT) String userAgent,
            @Header(HttpHeaders.AUTHORIZATION) String oauthToken,
            @Body GitHubRepository gitHubRepository) {
        Path temp = Files.createTempDirectory('test-github-create')
        Git repo = Git.init().setDirectory(temp.toFile()).setBare(true).call()
        URIish uri = new URIish(repo.getRepository().getDirectory().toURI().toURL())
        return new GitHubRepository(gitHubRepository.name, gitHubRepository.description, 'url', 'html_url', uri.toString())
    }

    @Override
    @Get(value = "/repos/{owner}/{repo}", processes = [GitHubApiClient.GITHUB_V3_TYPE])
    GitHubRepository getRepository(
            @Header(HttpHeaders.USER_AGENT) String userAgent,
            @Header(HttpHeaders.AUTHORIZATION) String oauthToken,
            @PathVariable String owner,
            @PathVariable String repo) {
        return repo == "existing" ? new GitHubRepository("existing", null) : null
    }

    @Override
    void deleteRepository(@Header(HttpHeaders.USER_AGENT) String userAgent, @Header(HttpHeaders.AUTHORIZATION) String oauthToken, @PathVariable String owner, @PathVariable String repo) {
        // no-op
    }

    @Override
    @Get(value = "/user", processes = [GitHubApiClient.GITHUB_V3_TYPE, MediaType.APPLICATION_JSON])
    GitHubUser getUser(@Header(HttpHeaders.USER_AGENT) String userAgent, @Header(HttpHeaders.AUTHORIZATION) String oauthToken) {
        return new GitHubUser("login", "email", "name")
    }

    @Override
    void createSecret(@Header(HttpHeaders.USER_AGENT) String userAgent, @Header(HttpHeaders.AUTHORIZATION) String oauthToken, @PathVariable String owner, @PathVariable String repo, @PathVariable String secretName, @Body GitHubSecret secret) {
        // no-op
    }

    @Override
    GitHubSecretsPublicKey getSecretPublicKey(@Header(HttpHeaders.USER_AGENT) String userAgent, @Header(HttpHeaders.AUTHORIZATION) String oauthToken, @PathVariable String owner, @PathVariable String repo) {
        return null
    }

    @Override
    GitHubWorkflowRuns listWorkflows(@Header(HttpHeaders.USER_AGENT) String userAgent, @Header(HttpHeaders.AUTHORIZATION) String oauthToken, @PathVariable String owner, @PathVariable String repo) {
        return null
    }

    @Override
    GitHubWorkflowRun getWorkflowRun(@Header(HttpHeaders.USER_AGENT) String userAgent, @Header(HttpHeaders.AUTHORIZATION) String oauthToken, @PathVariable String owner, @PathVariable String repo, @PathVariable Long runId) {
        return null
    }
}
