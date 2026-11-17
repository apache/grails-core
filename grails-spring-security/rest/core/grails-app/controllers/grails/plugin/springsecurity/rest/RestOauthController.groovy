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
package grails.plugin.springsecurity.rest

import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap

import groovy.util.logging.Slf4j

import com.nimbusds.jwt.JWT
import org.apache.commons.codec.binary.Base64
import org.pac4j.core.client.IndirectClient
import org.pac4j.core.context.CallContext
import org.pac4j.core.context.session.SessionStore
import org.pac4j.core.exception.http.FoundAction
import org.pac4j.core.exception.http.RedirectionAction
import org.pac4j.jee.context.JEEContext
import org.pac4j.jee.context.session.JEESessionStoreFactory

import org.springframework.http.HttpStatus
import org.springframework.security.core.userdetails.User

import grails.core.GrailsApplication
import grails.plugin.springsecurity.annotation.Secured
import grails.plugin.springsecurity.rest.authentication.RestAuthenticationEventPublisher
import grails.plugin.springsecurity.rest.error.CallbackErrorHandler
import grails.plugin.springsecurity.rest.token.AccessToken
import grails.plugin.springsecurity.rest.token.generation.jwt.AbstractJwtTokenGenerator
import grails.plugin.springsecurity.rest.token.rendering.AccessTokenJsonRenderer
import grails.plugin.springsecurity.rest.token.storage.TokenStorageService
import org.grails.plugins.codecs.URLCodec

@Slf4j
@Secured(['permitAll'])
class RestOauthController {

    static allowedMethods = [accessToken: 'POST']
    private static final SessionStore SESSION_STORE = JEESessionStoreFactory.INSTANCE.newSessionStore(null)
    private static final String ALLOWED_CALLBACK_ORIGINS = 'grails.plugin.springsecurity.rest.oauth.allowedCallbackOrigins'
    final String CALLBACK_ATTR = 'spring-security-rest-callback'
    private final Set<String> invalidAllowedCallbackOrigins = ConcurrentHashMap.newKeySet()

    CallbackErrorHandler callbackErrorHandler
    RestOauthService restOauthService
    GrailsApplication grailsApplication

    JwtService jwtService
    TokenStorageService tokenStorageService
    def tokenGenerator
    AccessTokenJsonRenderer accessTokenJsonRenderer
    RestAuthenticationEventPublisher authenticationEventPublisher
    /**
     * Starts the OAuth authentication flow, redirecting to the provider's Login URL. An optional callback parameter
     * allows the frontend application to define the frontend callback URL on demand. It is only used when it is an
     * http or https URL whose origin is listed in {@code grails.plugin.springsecurity.rest.oauth.allowedCallbackOrigins}
     * and that has a path, query or fragment after the host.
     */
    def authenticate(String provider, String callback) {
        IndirectClient client = restOauthService.getClient(provider)

        session[CALLBACK_ATTR] = null
        if (callback) {
            if (Base64.isBase64(callback.getBytes())) {
                callback = new String(Base64.decodeBase64(callback), StandardCharsets.UTF_8)
            }
            if (isAllowedCallbackUrl(callback)) {
                callback = toAsciiUrl(callback)
                log.debug "Storing in the HTTP session a user specified callback URL: ${callback}"
                session[CALLBACK_ATTR] = callback
            } else {
                log.warn "The callback URL is not an http or https URL of an origin listed in ${ALLOWED_CALLBACK_ORIGINS}. Not storing it."
            }
        }

        CallContext callContext = new CallContext(new JEEContext(request, response), SESSION_STORE)
        RedirectionAction redirectAction = client.getRedirectionAction(callContext).get()
        if (redirectAction instanceof FoundAction) {
            log.debug "Redirecting to ${redirectAction.location}"
            redirect url: redirectAction.location
        } else {
            response.status = redirectAction.code
        }
    }

    /**
     * Handles the OAuth provider callback. It uses {@link RestOauthService} to generate and store a token for that user,
     * and finally redirects to the configured frontend callback URL, where the token is in the URL. That way, the
     * frontend application can store the REST API token locally for subsequent API calls.
     */
    def callback(String provider) {
        CallContext context = new CallContext(new JEEContext(request, response), SESSION_STORE)
        def frontendCallbackUrl
        final sessionCallbackUrl = session[CALLBACK_ATTR] as String
        if (sessionCallbackUrl && isAllowedCallbackUrl(sessionCallbackUrl)) {
            log.debug 'Found callback URL in the HTTP session'
            frontendCallbackUrl = toAsciiUrl(sessionCallbackUrl)
        } else {
            if (sessionCallbackUrl) {
                log.warn('The callback URL in the HTTP session is not an http or https URL of an origin listed in {}. Not using it.', ALLOWED_CALLBACK_ORIGINS)
            }
            log.debug 'Found callback URL in the configuration file'
            frontendCallbackUrl = grailsApplication.config['grails.plugin.springsecurity.rest.oauth.frontendCallbackUrl']
        }

        try {
            String tokenValue = restOauthService.storeAuthentication(provider, context)
            frontendCallbackUrl = getCallbackUrl(frontendCallbackUrl, tokenValue)

        } catch (Exception e) {
            def errorParams = new StringBuilder()

            Map params = callbackErrorHandler.convert(e)

            URLCodec urlCodec = new URLCodec()
            params.each { key, value ->
                errorParams << "&${key}=${urlCodec.encoder.encode(value)}"
            }

            frontendCallbackUrl = getCallbackUrl(frontendCallbackUrl, errorParams.toString())
        }

        log.debug "Redirecting to ${frontendCallbackUrl}"
        redirect url: frontendCallbackUrl
    }

    private String getCallbackUrl(baseUrl, String queryStringSuffix) {
        session[CALLBACK_ATTR] = null
        baseUrl instanceof Closure ? baseUrl(queryStringSuffix) : baseUrl + queryStringSuffix
    }

    private boolean isAllowedCallbackUrl(String url) {
        final uri = parseHttpUrl(url)
        // The token is appended to the callback URL, so a path, query or fragment must follow the host and port.
        if (!uri || !(uri.rawPath || uri.rawQuery != null || uri.rawFragment != null)) {
            return false
        }
        final callbackOrigin = originOf(uri)
        return grailsApplication.config.getProperty(ALLOWED_CALLBACK_ORIGINS, List, []).any { entry ->
            final allowedUri = parseHttpUrl(entry?.toString())
            if (!allowedUri || !isOriginOnly(allowedUri)) {
                if (invalidAllowedCallbackOrigins.add(String.valueOf(entry))) {
                    log.warn('Ignoring {} in {}: it is not an origin, an http or https URL with only a scheme, host and port', entry, ALLOWED_CALLBACK_ORIGINS)
                }
                return false
            }
            originOf(allowedUri) == callbackOrigin
        }
    }

    /**
     * Whether a URL that {@link #parseHttpUrl} accepted has nothing but an origin: no user info, no path other than
     * {@code /}, no query and no fragment. The path of a callback URL is not compared, so an entry with a path would
     * allow every path of its origin.
     */
    private static boolean isOriginOnly(URI uri) {
        uri.rawUserInfo == null && uri.rawPath in ['', '/'] && uri.rawQuery == null && uri.rawFragment == null
    }

    /**
     * Returns a URL that {@link #isAllowedCallbackUrl} accepted with every character outside US-ASCII percent-encoded
     * as UTF-8, as browsers send it. A redirect URL has to be ASCII, because servlet containers drop or change other
     * characters in the {@code Location} header.
     */
    private static String toAsciiUrl(String url) {
        new URI(url).toASCIIString()
    }

    /**
     * Parses an absolute http or https URL with a host, or returns {@code null} for any other value.
     */
    private static URI parseHttpUrl(String url) {
        if (!url) {
            return null
        }
        URI uri
        try {
            uri = new URI(url)
        } catch (URISyntaxException ignored) {
            return null
        }
        final scheme = uri.scheme?.toLowerCase(Locale.ROOT)
        // Browsers reject an IPv6 address with a zone ID in a URL.
        return (scheme in ['http', 'https'] && uri.host && !uri.host.contains('%')) ? uri : null
    }

    /**
     * Returns the origin (scheme, host and port) of a URL that {@link #parseHttpUrl} accepted.
     */
    private static String originOf(URI uri) {
        final scheme = uri.scheme.toLowerCase(Locale.ROOT)
        final port = uri.port == -1 ? (scheme == 'https' ? 443 : 80) : uri.port
        return "${scheme}://${hostOf(uri)}:${port}"
    }

    /**
     * Returns the host of a URL that {@link #parseHttpUrl} accepted, written one way: a name in lower case, and an
     * IPv6 address in one notation, so that {@code [::1]} and {@code [0:0:0:0:0:0:0:1]} are the same host.
     */
    private static String hostOf(URI uri) {
        if (!uri.host.startsWith('[')) {
            return uri.host.toLowerCase(Locale.ROOT)
        }
        // URI has checked the address, so InetAddress reads it without a lookup. InetAddress returns an IPv4-mapped
        // address as an IPv4 address, and the brackets keep it apart from that IPv4 host, as browsers do.
        return "[${InetAddress.getByName(uri.host).hostAddress}]"
    }

    /**
     * Generates a new access token given the refresh token passed
     */
    def accessToken() {
        String grantType = params['grant_type']
        if (!grantType || grantType != 'refresh_token') {
            render status: HttpStatus.BAD_REQUEST, text: 'Invalid grant_type'
            return
        }

        String refreshToken = params['refresh_token']
        log.debug "Trying to generate an access token for the refresh token: ${refreshToken}"
        if (!refreshToken) {
            log.debug 'Refresh token is missing. Replying with bad request'
            render status: HttpStatus.BAD_REQUEST, text: 'Refresh token is required'
            return
        }

        // only JWT tokens can be refreshed
        if (!AbstractJwtTokenGenerator.isAssignableFrom(tokenGenerator.getClass())) {
            log.debug('Token type does not support refresh tokens')
            render status: HttpStatus.FORBIDDEN
            return
        }

        try {
            JWT jwt = jwtService.parse(refreshToken)
            if (!jwt || !jwt.JWTClaimsSet.getBooleanClaim(AbstractJwtTokenGenerator.REFRESH_ONLY_CLAIM)) {
                log.debug("Token ${refreshToken} is not a refresh token")
                render status: HttpStatus.FORBIDDEN
                return
            }
        }
        catch (e) {
            log.debug("Invalid refresh token: ${refreshToken}", e)
            render status: HttpStatus.FORBIDDEN
            return
        }

        try {
            def user = tokenStorageService.loadUserByToken(refreshToken)
            User principal = user ? user as User : null
            log.debug "Principal found for refresh token: ${principal}"

            AccessToken accessToken = (tokenGenerator as AbstractJwtTokenGenerator).generateAccessToken(principal, false)
            accessToken.refreshToken = refreshToken

            tokenStorageService.storeToken(accessToken)
            authenticationEventPublisher.publishTokenCreation(accessToken)

            response.addHeader 'Cache-Control', 'no-store'
            response.addHeader 'Pragma', 'no-cache'
            render contentType: 'application/json', encoding: 'UTF-8', text: accessTokenJsonRenderer.generateJson(accessToken)
        } catch (e) {
            log.debug('Could not load by refresh token', e)
            render status: HttpStatus.FORBIDDEN
        }
    }
}
