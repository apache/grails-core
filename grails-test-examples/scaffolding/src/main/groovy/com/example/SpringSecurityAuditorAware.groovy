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

import groovy.transform.CompileStatic
import org.grails.datastore.gorm.timestamp.AuditorAware
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder

/**
 * AuditorAware implementation that retrieves the current auditor from Spring Security's authentication context.
 * This is used to automatically populate @CreatedBy and @LastModifiedBy annotated fields in domain classes.
 */
@CompileStatic
class SpringSecurityAuditorAware implements AuditorAware<String> {

    @Override
    Optional<String> getCurrentAuditor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication()

        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty()
        }

        String username = authentication.getName()

        // Don't set auditor for anonymous users
        if (username == 'anonymousUser') {
            return Optional.empty()
        }

        return Optional.of(username)
    }
}
