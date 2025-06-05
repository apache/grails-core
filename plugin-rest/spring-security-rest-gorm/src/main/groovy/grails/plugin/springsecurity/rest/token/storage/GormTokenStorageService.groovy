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

import grails.core.GrailsApplication
import grails.plugin.springsecurity.SpringSecurityUtils
import grails.util.Holders
import groovy.util.logging.Slf4j
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService

/**
 * GORM implementation for token storage. It will look for tokens on the DB using a domain class that will contain the
 * generated token and the username associated.
 *
 * Once the username is found, it will delegate to the configured {@link UserDetailsService} for obtaining authorities
 * information.
 */
@Slf4j
class GormTokenStorageService implements TokenStorageService {

    GrailsApplication grailsApplication

    UserDetailsService userDetailsService

    public GormTokenStorageService() {
        this.grailsApplication = Holders.grailsApplication
    }

    UserDetails loadUserByToken(String tokenValue) throws TokenNotFoundException {
        log.debug "Finding token ${tokenValue} in GORM"
        def conf = SpringSecurityUtils.securityConfig
        String usernamePropertyName = conf.rest.token.storage.gorm.usernamePropertyName
        def existingToken = findExistingToken(tokenValue)

        if (existingToken) {
            def username = existingToken."${usernamePropertyName}"
            return userDetailsService.loadUserByUsername(username)
        }

        throw new TokenNotFoundException("Token ${tokenValue} not found")

    }

    void storeToken(String tokenValue, UserDetails principal) {
        log.debug "Storing principal for token: ${tokenValue}"
        log.debug "Principal: ${principal}"

        def conf = SpringSecurityUtils.securityConfig
        String tokenClassName = conf.rest.token.storage.gorm.tokenDomainClassName
        String tokenValuePropertyName = conf.rest.token.storage.gorm.tokenValuePropertyName
        String usernamePropertyName = conf.rest.token.storage.gorm.usernamePropertyName
        def dc = grailsApplication.getClassForName(tokenClassName)

        //TODO check at startup, not here
        if (!dc) {
            throw new IllegalArgumentException("The specified token domain class '$tokenClassName' is not a domain class ")
        }

        dc.withTransaction { status ->
            def newTokenObject = dc.newInstance((tokenValuePropertyName): tokenValue, (usernamePropertyName): principal.username)
            newTokenObject.save()
        }
    }

    void removeToken(String tokenValue) throws TokenNotFoundException {
        log.debug "Removing token ${tokenValue} from GORM"
        def conf = SpringSecurityUtils.securityConfig
        String tokenClassName = conf.rest.token.storage.gorm.tokenDomainClassName
        def existingToken = findExistingToken(tokenValue)
        if (existingToken) {
            def dc = grailsApplication.getClassForName(tokenClassName)
            dc.withTransaction() {
                existingToken.delete()
            }
        } else {
            throw new TokenNotFoundException("Token ${tokenValue} not found")
        }

    }

    private findExistingToken(String tokenValue) {
        log.debug "Searching in GORM for UserDetails of token ${tokenValue}"
        def conf = SpringSecurityUtils.securityConfig
        String tokenClassName = conf.rest.token.storage.gorm.tokenDomainClassName
        String tokenValuePropertyName = conf.rest.token.storage.gorm.tokenValuePropertyName
        def dc = grailsApplication.getClassForName(tokenClassName)

        //TODO check at startup, not here
        if (!dc) {
            throw new IllegalArgumentException("The specified token domain class '$tokenClassName' is not a domain class")
        }

        dc.withTransaction() { status ->
            return dc.findWhere((tokenValuePropertyName): tokenValue)
        }
    }

}
