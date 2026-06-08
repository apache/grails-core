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
package grails.plugin.springsecurity.rest.error

import org.springframework.security.core.userdetails.UsernameNotFoundException

import static org.springframework.http.HttpStatus.FORBIDDEN
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR

/**
 * Error handler that's backwardly compatible with the behaviour that was embedded within the callback action of
 * RestOauthController up to and including version 1.5.2
 *
 * @author Dónal Murtagh
 */

class DefaultCallbackErrorHandler implements CallbackErrorHandler {

    @Override
    Map convert(Exception e) {

        Map params = [:]

        if (e instanceof UsernameNotFoundException) {
            params.error = FORBIDDEN.value()

        } else {
            params.error = e.cause?.hasProperty('code') ? e.cause.code : INTERNAL_SERVER_ERROR.value()
        }

        // Add the error message under the keys 'error_description' and 'message' - the former for compatibility with
        // the RFC and the latter for backwards compatibility with plugin versions <= 1.5.2
        params.error_description = params.message = e.message ?: ''
        params.error_code = e.cause ? e.cause.class.simpleName : e.class.simpleName
        params
    }
}
