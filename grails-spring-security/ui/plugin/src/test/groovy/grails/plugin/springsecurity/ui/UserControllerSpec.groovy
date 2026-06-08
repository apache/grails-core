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
package grails.plugin.springsecurity.ui

import grails.testing.web.controllers.ControllerUnitTest
import spock.lang.Specification
import spock.lang.Unroll

@Unroll
class UserControllerSpec extends Specification implements ControllerUnitTest<UserController> {
    static final Map ADMIN_ROLE = [authority: "ROLE_ADMIN"]
    static final Map SUPER_ADMIN_ROLE = [authority: "ROLE_SUPER_ADMIN"]
    static final Map USER_ROLE = [authority: "ROLE_USER"]

    void "verify proper construction of roleMap for user with roles #rolesAssignedToUser"() {
        given: "the authority name field has been set to the default name of 'authority'"
        controller.authorityNameField = "authority"

        and: "we mock the returning of all Role instances within the database"
        List sortedRoles = [ADMIN_ROLE, SUPER_ADMIN_ROLE, USER_ROLE]

        when: "we call buildRoleMap with the role names associated to the user"
        Map results = controller.buildRoleMap(rolesAssignedToUser, sortedRoles)

        then: "the user is only granted access to roles with which they are associated"
        results == expectedResults
        results instanceof LinkedHashMap

        where:
        rolesAssignedToUser                                | expectedResults
        [ADMIN_ROLE.authority, USER_ROLE.authority] as Set | [(ADMIN_ROLE): true, (SUPER_ADMIN_ROLE): false, (USER_ROLE): true]
        [] as Set                                          | [(ADMIN_ROLE): false, (SUPER_ADMIN_ROLE): false, (USER_ROLE): false]
        null                                               | [(ADMIN_ROLE): false, (SUPER_ADMIN_ROLE): false, (USER_ROLE): false]
    }
}
