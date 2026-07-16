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

package demo

import grails.gorm.transactions.Transactional

class BootStrap {

    def init = {
        populate()
    }
    def destroy = {
    }

    @Transactional
    void populate() {
        Role roleAdmin = new Role(authority: 'ROLE_ADMIN')
        roleAdmin.save()
        Role roleDetective = new Role(authority: 'ROLE_DETECTIVE')
        roleDetective.save()
        User user = new User(username: 'sherlock', password: 'elementary')
        user.save()
        UserRole userRole = new UserRole(role: roleDetective, user: user)
        userRole.save()
    }
}
