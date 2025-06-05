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
package grails.plugin.springsecurity.rest.token.rendering

import grails.converters.JSON
import grails.plugin.springsecurity.rest.oauth.OauthUser
import grails.plugin.springsecurity.rest.token.AccessToken
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.pac4j.core.profile.CommonProfile
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.util.Assert

/**
 * Generates a JSON response like the following: <code>{"username":"john.doe","roles":["USER","ADMIN"],"access_token":"1a2b3c4d"}</code>.
 * If the principal is an instance of {@link grails.plugin.springsecurity.rest.oauth.OauthUser}, also "email" ({@link CommonProfile#getEmail()}) and
 * "displayName" ({@link CommonProfile#getDisplayName()}) will be rendered
 */
@Slf4j
@CompileStatic
class DefaultAccessTokenJsonRenderer implements AccessTokenJsonRenderer {

    String usernamePropertyName
    String tokenPropertyName
    String authoritiesPropertyName

    Boolean useBearerToken

    String generateJson(AccessToken accessToken) {
        Assert.isInstanceOf(UserDetails, accessToken.principal, "A UserDetails implementation is required")
        UserDetails userDetails = accessToken.principal as UserDetails

        Map result = [
            (usernamePropertyName) : userDetails.username,
            (authoritiesPropertyName) : accessToken.authorities.collect { GrantedAuthority role -> role.authority }
        ]

        if (useBearerToken) {
            result.token_type = 'Bearer'
            result.access_token = accessToken.accessToken

            if (accessToken.expiration) {
                result.expires_in = accessToken.expiration
            }

            if (accessToken.refreshToken) result.refresh_token = accessToken.refreshToken

        } else {
                result["$tokenPropertyName".toString()] = accessToken.accessToken
        }

        if (userDetails instanceof OauthUser) {
            CommonProfile profile = (userDetails as OauthUser).userProfile
            if(profile) {
                result.email = profile.email
                result.displayName = profile.displayName
            }
        }

        JSON jsonResult = result as JSON

        log.debug "Generated JSON:\n${jsonResult.toString(true)}"

        return jsonResult.toString()
    }
}
