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

import com.example.pages.LoginPage
import com.example.pages.LogoutPage
import com.example.pages.CommunityUserListPage

import grails.plugin.geb.ContainerGebConfiguration
import grails.plugin.geb.ContainerGebSpec
import grails.testing.mixin.integration.Integration

@Integration
@ContainerGebConfiguration(reporting = true)
class UserCommunityControllerSpec extends ContainerGebSpec {

    void setup() {
        clearCookiesQuietly()
    }

    void cleanup() {
        try {
            to(LogoutPage).logout()
        } catch (Exception ignore) {
            // ignore any exceptions that occur during logout
        }
    }

    void "User list"() {
        when: 'an unauthenticated user requests the community user list and signs in when prompted'
        via(CommunityUserListPage)
        at(LoginPage).login()

        then: 'the saved request redirects to the community user list'
        def page = at(CommunityUserListPage)
        !page.scaffoldTable
    }
}
