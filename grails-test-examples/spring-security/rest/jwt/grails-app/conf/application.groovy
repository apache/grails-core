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

import org.pac4j.oauth.client.GenericOAuth20Client

String statelessFilters = 'JOINED_FILTERS,-anonymousAuthenticationFilter,-exceptionTranslationFilter,-authenticationProcessingFilter,-securityContextPersistenceFilter,-rememberMeAuthenticationFilter'

grails {
    plugin {
        springsecurity {

            useSecurityEventListener = true

            controllerAnnotations {
                staticRules = [
                        // Where a response.sendError() of a filter of the plugin is dispatched to
                        [pattern: '/error', access: ['permitAll']]
                ]
            }

            filterChain {
                chainMap = [
                        [pattern: '/api/**', filters: statelessFilters],
                        [pattern: '/secured/**', filters: statelessFilters],
                        [pattern: '/jwt/**', filters: statelessFilters],
                        [pattern: '/anonymous/**', filters: 'anonymousAuthenticationFilter,restTokenValidationFilter,restExceptionTranslationFilter,filterInvocationInterceptor'],
                        [pattern: '/**', filters: 'JOINED_FILTERS,-restTokenValidationFilter,-restExceptionTranslationFilter']
                ]
            }

            rest {
                token {
                    validation {
                        enableAnonymousAccess = true
                        useBearerToken = false
                    }
                    storage {
                        jwt {
                            secret = 'foobar123' * 4
                        }
                    }
                }

                oauth {
                    frontendCallbackUrl = { String tokenValue -> "http://example.org#token=${tokenValue}" }

                    // The authUrl, tokenUrl and profileUrl of the provider are test properties: the specs point
                    // them at the Ersatz server that plays the provider (rest.MockOauthProvider).
                    mock {
                        client = GenericOAuth20Client
                        key = 'rest-test-client'
                        secret = 'rest-test-secret'
                        scope = 'profile email'
                        withState = true
                        defaultRoles = ['ROLE_USER', 'ROLE_MOCK']
                    }
                }
            }
        }
    }
}
