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
package grails.plugin.springsecurity.rest.token.generation

import grails.plugin.springsecurity.rest.token.AccessToken
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.apache.commons.lang3.RandomStringUtils
import org.springframework.security.core.userdetails.UserDetails

import java.security.SecureRandom

/**
 * A {@link TokenGenerator} implementation using {@link java.security.SecureRandom}
 */
@Slf4j
@CompileStatic
class SecureRandomTokenGenerator implements TokenGenerator {

    SecureRandom random = new SecureRandom()

    /**
     * Generates a token using {@link java.security.SecureRandom}, a cryptographically strong random number generator.
     *
     * @return a String token of 32 alphanumeric characters.
     */
    @Override
    AccessToken generateAccessToken(UserDetails principal) {
        String token = new BigInteger(160, this.random).toString(32)
        def tokenSize = token.size()
        if (tokenSize < 32) token += RandomStringUtils.randomAlphanumeric(32 - tokenSize)
        return new AccessToken(principal, principal.authorities, token)
    }

    @Override
    AccessToken generateAccessToken(UserDetails principal, Integer expiration) {
        AccessToken accessToken = generateAccessToken(principal)
        accessToken.expiration = expiration
        return accessToken
    }
}
