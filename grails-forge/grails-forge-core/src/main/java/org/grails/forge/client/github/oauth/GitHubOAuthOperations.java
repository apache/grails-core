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

import io.micronaut.http.HttpHeaders;
import io.micronaut.http.annotation.Header;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.QueryValue;

/**
 * GitHub Oauth operations.
 *
 * @author Pavol Gressa
 * @since 6.0.0
 */
public interface GitHubOAuthOperations {

    @Post(value = "/access_token")
    AccessToken accessToken(@Header(HttpHeaders.USER_AGENT) String userAgent, @QueryValue("client_id") String clientId, @QueryValue("client_secret") String clientSecret, @QueryValue String code, @QueryValue String state);

}
