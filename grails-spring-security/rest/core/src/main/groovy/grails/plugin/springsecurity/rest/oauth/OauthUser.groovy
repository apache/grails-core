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
package grails.plugin.springsecurity.rest.oauth

import groovy.transform.CompileStatic
import org.pac4j.core.profile.CommonProfile
import org.pac4j.core.profile.UserProfile
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetails

/**
 * A {@link UserDetails} implementation that holds the {@link CommonProfile} returned by the OAuth provider
 */

@CompileStatic
class OauthUser extends User implements Serializable {

    private static final long serialVersionUID = 1055519971558835240L

    CommonProfile userProfile

    OauthUser(String username, String password, Collection<? extends GrantedAuthority> authorities) {
        super(username, password, authorities)
    }

    OauthUser(String username, String password, Collection<? extends GrantedAuthority> authorities, UserProfile userProfile) {
        super(username, password, authorities)
        if(userProfile && !(userProfile instanceof CommonProfile)) {
            throw new IllegalStateException("The userProfile must be an instance of CommonProfile to support display name and email")
        }

        this.userProfile = userProfile as CommonProfile
    }

}
