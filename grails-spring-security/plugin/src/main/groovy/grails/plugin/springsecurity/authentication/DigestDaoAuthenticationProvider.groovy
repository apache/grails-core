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
package grails.plugin.springsecurity.authentication

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

import groovy.transform.CompileStatic

import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.authentication.dao.DaoAuthenticationProvider
import org.springframework.security.core.Authentication
import org.springframework.security.core.AuthenticationException
import org.springframework.security.core.CredentialsContainer
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.userdetails.UserCache
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.crypto.password.NoOpPasswordEncoder

/**
 * The {@code daoAuthenticationProvider} the plugin defines with {@code useDigestAuth}, which compares what is presented
 * with what Digest authentication has a user store: the hash a browser computes from the username, the realm name and
 * the password, or with {@code digest.useCleartextPasswords} the password as it was typed.
 *
 * <p>A password that a form or Basic authentication sends is hashed as a browser hashes it before it is compared, so a
 * user signs in with their password in either mode. {@code DigestAuthenticationFilter} leaves a token whose principal
 * is the {@code UserDetails} it loaded and whose credentials are what is stored, for the security interceptor to
 * authenticate; only in a token whose principal is a {@code UserDetails} is what is stored accepted as it is. The
 * principal of what a form or Basic authentication sends is the username, so the stored hash does not sign anyone in
 * there.</p>
 *
 * <p>The {@code passwordEncoder} bean is not used to compare, so it stays as configured for everything else.</p>
 *
 * <p>With {@code cacheUsers}, the user this returns as the principal is the one the user cache holds, which
 * {@code DigestAuthenticationFilter} reads what is stored from on every request. Erasing the credentials of what this
 * returns, as the authentication manager does by default, erases them from that user only where it is not the one the
 * cache holds, so that the next request is answered from the cache rather than by loading the user again.</p>
 *
 * @since 9.0
 */
@CompileStatic
class DigestDaoAuthenticationProvider extends DaoAuthenticationProvider {

    private static final String BAD_CREDENTIALS = 'AbstractUserDetailsAuthenticationProvider.badCredentials'

    /** The realm name a browser hashes the password with, {@code digest.realmName}. */
    String realmName

    /**
     * Whether what is stored is the hash a browser computes rather than the password, as the filter's
     * {@code passwordAlreadyEncoded} is.
     */
    boolean passwordAlreadyEncoded = true

    DigestDaoAuthenticationProvider(UserDetailsService userDetailsService) {
        super(userDetailsService)
        // Not used to compare a password. A sign-in for a username nobody has is compared with what this encoder made,
        // so that it takes about as long as one for a user, and with what is compared here as cheap as it is, a slower
        // encoder would tell from the time a sign-in takes which usernames exist.
        setPasswordEncoder(NoOpPasswordEncoder.getInstance())
    }

    @Override
    protected void additionalAuthenticationChecks(UserDetails user, UsernamePasswordAuthenticationToken authentication)
            throws AuthenticationException {
        Object credentials = authentication.credentials
        if (credentials == null) {
            logger.debug('Failed to authenticate since no credentials provided')
            throw new BadCredentialsException(messages.getMessage(BAD_CREDENTIALS, 'Bad credentials'))
        }
        if (!matches(user, authentication.principal, credentials.toString())) {
            logger.debug('Failed to authenticate since password does not match stored value')
            throw new BadCredentialsException(messages.getMessage(BAD_CREDENTIALS, 'Bad credentials'))
        }
    }

    private boolean matches(UserDetails user, Object principal, String presented) {
        String stored = user.password
        if (stored == null) {
            return false
        }
        if (!passwordAlreadyEncoded) {
            return isEqual(presented, stored)
        }
        if (isEqual(md5Hex("${user.username}:${realmName}:${presented}"), stored)) {
            return true
        }
        // what the filter left, or a token authenticated before that still holds what it was authenticated with
        principal instanceof UserDetails && isEqual(presented, stored)
    }

    @Override
    protected Authentication createSuccessAuthentication(Object principal, Authentication authentication, UserDetails user) {
        Authentication authenticated = super.createSuccessAuthentication(principal, authentication, user)
        KeepsCachedUserToken result = new KeepsCachedUserToken(authenticated.principal, authenticated.credentials,
                authenticated.authorities, userCache)
        result.details = authenticated.details
        result
    }

    /**
     * An authenticated token whose erased credentials leave alone a principal the user cache holds, as that instance
     * is the one every later request is checked against.
     */
    private static class KeepsCachedUserToken extends UsernamePasswordAuthenticationToken {

        private final transient UserCache userCache
        private Object presented

        KeepsCachedUserToken(Object principal, Object credentials, Collection<? extends GrantedAuthority> authorities,
                UserCache userCache) {
            super(principal, null, authorities)
            this.presented = credentials
            this.userCache = userCache
        }

        @Override
        Object getCredentials() {
            presented
        }

        @Override
        void eraseCredentials() {
            presented = null
            Object principal = getPrincipal()
            if (principal instanceof CredentialsContainer && !cached(principal)) {
                ((CredentialsContainer) principal).eraseCredentials()
            }
            if (details instanceof CredentialsContainer) {
                ((CredentialsContainer) details).eraseCredentials()
            }
        }

        private boolean cached(Object principal) {
            principal instanceof UserDetails && userCache != null &&
                    userCache.getUserFromCache(((UserDetails) principal).username).is(principal)
        }
    }

    private static String md5Hex(String value) {
        HexFormat.of().formatHex(MessageDigest.getInstance('MD5').digest(value.getBytes(StandardCharsets.UTF_8)))
    }

    private static boolean isEqual(String presented, String stored) {
        MessageDigest.isEqual(presented.getBytes(StandardCharsets.UTF_8), stored.getBytes(StandardCharsets.UTF_8))
    }
}
