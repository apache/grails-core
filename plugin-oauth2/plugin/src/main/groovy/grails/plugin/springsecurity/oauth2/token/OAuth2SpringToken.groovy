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
package grails.plugin.springsecurity.oauth2.token

import com.github.scribejava.core.model.OAuth2AccessToken
import grails.converters.JSON
import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.core.GrantedAuthority

/**
 * This is a Spring Security authentication token for OAuth providers.
 * It will be saved in SecurityContextHolder.context.authentication when user
 * complete the authentication process.
 * It must be initialised with a
 * {@link com.github.scribejava.core.model.OAuth2AccessToken https://github.com/fernandezpablo85/scribe-java} access token
 * from which dedicated provider.
 * Tokens can extract extra information such as the
 * principal.
 *
 * @author <a href='mailto:cazacugmihai@gmail.com'>Mihai Cazacu</a>
 */
abstract class OAuth2SpringToken extends AbstractAuthenticationToken {

    private static final long serialVersionUID = 1

    protected OAuth2AccessToken accessToken
    protected Map tokenParams
    Object principal
    Collection<GrantedAuthority> authorities

    /**
     * Initialises the token from an access token.
     */
    OAuth2SpringToken(OAuth2AccessToken accessToken, boolean forceJsonExtractor = false) {
        super(Collections.EMPTY_LIST)
        this.accessToken = accessToken
        this.tokenParams = extractParameters(accessToken.rawResponse,
            forceJsonExtractor ?: accessToken.rawResponse?.trim()?.startsWith('{'))
    }

    Object getPrincipal() {
        return principal
    }

    /**
     * Returns the raw response from the OAuth provider as a string.
     */
    def getCredentials() {
        return accessToken.rawResponse
    }

    /**
     * Returns the name of the OAuth provider for this token.
     */
    abstract String getProviderName()

    abstract String getSocialId()

    abstract String getScreenName()

    /**
     * Returns the parameters in the OAuth access token as a map.
     */
    protected final Map getParameters() { return Collections.unmodifiableMap(tokenParams) }

    private Map extractParameters(String data, boolean json) {
        if (json) {
          // don't return JSON data types because they can't serialize properly in sessions
          return JSON.parse(data) as HashMap<String,String>
        }
        return data?.split('&')?.collectEntries { it.split('=') as List }
    }
}
