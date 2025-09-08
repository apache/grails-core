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
package org.grails.forge.client.github.oauth;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Header;
import io.micronaut.http.client.annotation.Client;

/**
 * GitHub OAuth client.
 *
 * @author Pavol Gressa
 * @since 6.0.0
 */
@Client(id = GitHubOAuthClient.SERVICE_ID, path = "/login/oauth")
@Requires(property = "micronaut.http.services." + GitHubOAuthClient.SERVICE_ID)
@Header(name = "Accept", value = MediaType.APPLICATION_JSON)
public interface GitHubOAuthClient extends GitHubOAuthOperations {
    String SERVICE_ID = "github-oauth";
}
