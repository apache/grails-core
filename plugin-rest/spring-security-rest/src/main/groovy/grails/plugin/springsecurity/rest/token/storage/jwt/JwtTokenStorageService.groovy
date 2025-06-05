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
package grails.plugin.springsecurity.rest.token.storage.jwt

import com.nimbusds.jose.JOSEException
import com.nimbusds.jwt.JWT
import grails.plugin.springsecurity.rest.JwtService
import grails.plugin.springsecurity.rest.token.generation.jwt.AbstractJwtTokenGenerator
import grails.plugin.springsecurity.rest.token.storage.TokenNotFoundException
import grails.plugin.springsecurity.rest.token.storage.TokenStorageService
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService

import java.text.ParseException

/**
 * Re-hydrates JWT's with HMAC protection or JWE encryption
 */
@Slf4j
@CompileStatic
class JwtTokenStorageService implements TokenStorageService {

    JwtService jwtService
    UserDetailsService userDetailsService

    @Override
    UserDetails loadUserByToken(String tokenValue) throws TokenNotFoundException {
        Date now = new Date()
        try {
            JWT jwt = jwtService.parse(tokenValue)

            if (jwt.JWTClaimsSet.expirationTime?.before(now)) {
                throw new TokenNotFoundException("Token ${tokenValue} has expired")
            }

            boolean isRefresh = jwt.JWTClaimsSet.getBooleanClaim(AbstractJwtTokenGenerator.REFRESH_ONLY_CLAIM) || jwt.JWTClaimsSet.expirationTime == null
            if(isRefresh) {
                return loadUserFromRefreshToken(jwt)
            }

            return loadUserFromAccessToken(jwt)

        } catch (ParseException ignored) {
            throw new TokenNotFoundException("Token ${tokenValue} is not valid")
        } catch (JOSEException ignored) {
            throw new TokenNotFoundException("Token ${tokenValue} has an invalid signature")
        }
    }

    /**
     * Load user details for an access token
     */
    protected UserDetails loadUserFromAccessToken(JWT jwt) {
        log.debug "Verified JWT, trying to deserialize the principal object"
        try {
            UserDetails details = JwtService.deserialize(jwt.JWTClaimsSet.getStringClaim('principal'))
            log.debug "UserDetails deserialized: {}", details
            if (details) {
                return details
            }
        } catch (exception) {
            log.debug(exception.message)
        }

        log.debug "Returning a org.springframework.security.core.userdetails.User instance"

        List<SimpleGrantedAuthority> roles = jwt.JWTClaimsSet.getStringArrayClaim('roles')?.collect { String role -> new SimpleGrantedAuthority(role) }
        return new User(jwt.JWTClaimsSet.subject, 'N/A', roles)
    }

    /**
     * Load user details for a refresh token
     *
     * @param jwt the refresh token
     * @return
     */
    protected UserDetails loadUserFromRefreshToken(JWT jwt) {
        UserDetails principal = userDetailsService.loadUserByUsername(jwt.JWTClaimsSet.subject)

        if(!principal){
            throw new TokenNotFoundException("Token no longer valid, principal not found")
        }
        if(!principal.enabled){
            throw new TokenNotFoundException("Token no longer valid, account disabled")
        }
        if(!principal.accountNonExpired){
            throw new TokenNotFoundException("Token no longer valid, account expired")
        }
        if(!principal.accountNonLocked){
            throw new TokenNotFoundException("Token no longer valid, account locked")
        }
        if(!principal.credentialsNonExpired){
            throw new TokenNotFoundException("Token no longer valid, credentials expired")
        }

        return principal
    }

    @Override
    void storeToken(String tokenValue, UserDetails principal) {
        log.debug "Nothing to store as this is a stateless implementation"
    }

    @Override
    void removeToken(String tokenValue) throws TokenNotFoundException {
        log.debug "Nothing to remove as this is a stateless implementation"
        throw new TokenNotFoundException("Token ${tokenValue} cannot be removed as this is a stateless implementation")
    }

}
