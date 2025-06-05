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
package grails.plugin.springsecurity.rest.token.storage

import grails.plugin.springsecurity.rest.token.AccessToken
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.Authentication

/**
 * Implementations of this trait are responsible to load user information from a token storage system, and to store
 * token information into it.
 */
trait TokenStorageService {
    /**
     * Returns a principal object given the passed token value
     * @throws TokenNotFoundException if no token is found in the storage
     */
    abstract UserDetails loadUserByToken(String tokenValue) throws TokenNotFoundException

    /**
     * Stores a token. It receives the principal to store any additional information together with the token,
     * like the username associated.
     *
     * @see Authentication#getPrincipal()
     */
    void storeToken(String tokenValue, UserDetails principal) {}

    /**
     * Stores the access token. Allows for handling of refresh token and other JWT claims as needed.
     */
    void storeToken(AccessToken accessToken) {
        storeToken(accessToken.accessToken, accessToken.principal)
    }

    /**
     * Removes a token from the storage.
     * @throws TokenNotFoundException if the given token is not found in the storage
     */
    abstract void removeToken(String tokenValue) throws TokenNotFoundException
}
