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

package com.testapp

import grails.plugin.springsecurity.annotation.Secured
import grails.validation.Validateable

/**
 * This controller is used to verify that form parameters on PUT and PATCH requests are available
 */
class TestFormParamsController {

    static allowedMethods = [
            permitAll  : ["PUT", "PATCH"],
            permitAdmin: ["PUT", "PATCH"]
    ]

    @Secured(['permitAll'])
    def permitAll(TestFormCommand cmd) {
        render "username: ${cmd.username}, password: ${cmd.password}"
    }

    @Secured(['ROLE_ADMIN'])
    def permitAdmin(TestFormCommand cmd) {
        render "username: ${cmd.username}, password: ${cmd.password}"
    }
}

class TestFormCommand implements Validateable {
    String username
    String password

    static constraints = {
        username(nullable: true)
        password(nullable: true)
    }
}