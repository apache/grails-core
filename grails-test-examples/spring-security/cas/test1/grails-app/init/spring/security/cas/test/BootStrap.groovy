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

package spring.security.cas.test

import com.test.Role
import com.test.User
import com.test.UserRole
import groovy.transform.CompileStatic

@CompileStatic
class BootStrap {

    def init = {
        Role roleAdmin
        Role roleUser
        User user
        User admin
        Role.withTransaction {
            roleAdmin = new Role('ROLE_ADMIN').save()
            roleUser = new Role('ROLE_USER').save()
        }
        User.withTransaction {
            user = new User('user', 'user').save()
            admin = new User('admin', 'admin').save()
        }
        UserRole.withTransaction {
            UserRole.create user, roleUser
            UserRole.create admin, roleUser
            UserRole.create admin, roleAdmin, true
        }
    }
}
