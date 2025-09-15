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
package com.example

import grails.gorm.annotation.AutoTimestamp
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.UserDetails

import java.time.LocalDateTime

class User implements UserDetails {

    String firstName
    String lastName
    String email
    String password
    String roles = ''

    boolean accountNonExpired = true
    boolean accountNonLocked = true
    boolean credentialsNonExpired = true
    boolean enabled = true

    @AutoTimestamp(AutoTimestamp.EventType.CREATED) LocalDateTime created
    @AutoTimestamp LocalDateTime modified

    static constraints = {
        firstName blank: false
        lastName blank: false
        email email: true, blank: false
        password password:true
        created nullable: true  // Grails 7 Bug
        modified nullable: true // Grails 7 Bug
    }

    static mapping = {
        table 'users'
    }

    @Override
    Collection<? extends GrantedAuthority> getAuthorities() {
        roles.split('').collect { new SimpleGrantedAuthority(it) }
    }

    @Override
    String getUsername() { email }
}
