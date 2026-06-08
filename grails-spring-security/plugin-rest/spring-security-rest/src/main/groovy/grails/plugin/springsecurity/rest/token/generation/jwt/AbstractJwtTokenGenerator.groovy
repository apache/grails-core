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
package grails.plugin.springsecurity.rest.token.generation.jwt

import com.nimbusds.jwt.JWT
import com.nimbusds.jwt.JWTClaimsSet
import grails.plugin.springsecurity.rest.JwtService
import grails.plugin.springsecurity.rest.token.AccessToken
import grails.plugin.springsecurity.rest.token.generation.TokenGenerator
import grails.plugin.springsecurity.rest.token.storage.jwt.JwtTokenStorageService
import groovy.time.TimeCategory
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.springframework.security.core.userdetails.UserDetails

@Slf4j
@CompileStatic
abstract class AbstractJwtTokenGenerator implements TokenGenerator {
    static String REFRESH_ONLY_CLAIM = "refresh_only"

    Integer defaultExpiration
    Integer defaultRefreshExpiration

    JwtTokenStorageService jwtTokenStorageService

    List<CustomClaimProvider> customClaimProviders

    @Override
    AccessToken generateAccessToken(UserDetails details) {
        log.debug "Generating an access token with default expiration: ${this.defaultExpiration}"
        generateAccessToken(details, this.defaultExpiration)
    }

    @Override
    AccessToken generateAccessToken(UserDetails details, Integer expiration) {
        generateAccessToken(details, true, expiration)
    }

    AccessToken generateAccessToken(UserDetails details, boolean withRefreshToken, Integer expiration = this.defaultExpiration, Integer refreshExpiration = this.defaultRefreshExpiration) {
        log.debug "Serializing the principal received"
        String serializedPrincipal = serializePrincipal(details)

        JWTClaimsSet.Builder builder = generateClaims(details, serializedPrincipal, expiration)

        log.debug "Generating access token..."
        JWT accessTokenJwt = generateAccessToken(builder.build())
        String accessToken = accessTokenJwt.serialize()

        JWT refreshTokenJwt = null
        String refreshToken = null
        if (withRefreshToken) {
            log.debug "Generating refresh token..."
            refreshTokenJwt = generateRefreshToken(details, serializedPrincipal, refreshExpiration)
            refreshToken = refreshTokenJwt.serialize()
        }

        return new AccessToken(details, details.authorities, accessToken, refreshToken, expiration, accessTokenJwt, refreshTokenJwt)
    }

    @CompileDynamic
    JWTClaimsSet.Builder generateClaims(UserDetails details, String serializedPrincipal, Integer expiration) {
        JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder()
        builder.subject(details.username)

        Date now = new Date()
        builder.issueTime(now)

        if (expiration) {
            log.debug "Setting expiration to ${expiration}"
            use(TimeCategory) {
                builder.expirationTime(now + expiration.seconds)
            }
        }

        builder.claim('roles', details.authorities?.collect { it.authority })
        builder.claim('principal', serializedPrincipal)

        customClaimProviders.each { CustomClaimProvider customClaimProvider ->
            customClaimProvider.provideCustomClaims(builder, details, serializedPrincipal, expiration)
        }

        log.debug "Generated claim set: {}", builder.build().toJSONObject().toString()
        return builder
    }

    protected String serializePrincipal(UserDetails principal) {
        try {
            return JwtService.serialize(principal)
        } catch (exception) {
            log.debug(exception.message)
            log.debug "The principal class (${principal.class}) is not serializable. Object: ${principal}"
            return null
        }
    }

    protected abstract JWT generateAccessToken(JWTClaimsSet claimsSet)

    protected JWT generateRefreshToken(UserDetails principal, String serializedPrincipal, Integer expiration) {
        JWTClaimsSet.Builder builder = generateClaims(principal, serializedPrincipal, expiration)

        // Flag this token as a refresh token
        builder.claim(REFRESH_ONLY_CLAIM, true)

        return generateAccessToken(builder.build())
    }
}
