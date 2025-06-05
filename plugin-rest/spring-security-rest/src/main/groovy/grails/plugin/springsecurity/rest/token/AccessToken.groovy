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
package grails.plugin.springsecurity.rest.token

import com.nimbusds.jwt.JWT
import groovy.transform.CompileStatic
import groovy.transform.ToString
import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.userdetails.UserDetails

/**
 * Encapsulates an OAuth 2.0 access token.
 */
@ToString(includeNames = true, includeSuper = true, includes = ['principal', 'accessToken', 'accessTokenJwt', 'refreshToken', 'refreshTokenJwt', 'expiration'])
@CompileStatic
class AccessToken extends AbstractAuthenticationToken {

    static final long serialVersionUID = 6369159577577756817L

    String accessToken
    JWT accessTokenJwt

    Integer expiration

    String refreshToken
    JWT refreshTokenJwt

    /** The username */
    UserDetails principal

    AccessToken(Collection<? extends GrantedAuthority> authorities) {
        super(authorities)
        super.setAuthenticated(true)
    }

    AccessToken(UserDetails principal, Collection<? extends GrantedAuthority> authorities, String accessToken, String refreshToken = null, Integer expiration = null, JWT accessTokenJwt = null, JWT refreshTokenJwt = null) {
        this(authorities)
        this.principal = principal
        this.accessToken = accessToken
        this.refreshToken = refreshToken
        this.expiration = expiration
        this.accessTokenJwt = accessTokenJwt
        this.refreshTokenJwt = refreshTokenJwt
    }

    AccessToken(String accessToken, String refreshToken = null, Integer expiration = null) {
        super(null)
        this.accessToken = accessToken
        this.refreshToken = refreshToken
        this.expiration = expiration
        super.setAuthenticated(false)
    }

    Object getCredentials() {
        return null
    }

    void setAuthenticated(boolean isAuthenticated) throws IllegalArgumentException {
        if (isAuthenticated) {
            throw new IllegalArgumentException("Cannot set this token to trusted - use constructor which takes a GrantedAuthority list instead")
        }

        super.setAuthenticated(false)
    }

}
