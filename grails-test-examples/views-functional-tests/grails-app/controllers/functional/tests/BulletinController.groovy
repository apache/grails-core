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

package functional.tests

import grails.gorm.transactions.Transactional

class BulletinController {

    @Transactional
    def index() {
        User newUser = new  User(username: 'user1', publicId: 'user1').save()
        User newUser2 = new User(username: 'user2', publicId: 'user2').save()
        User newUser3 = new User(username: 'user3', publicId: 'user3').save()

        Bulletin bulletin = new Bulletin(name: 'The bulletin', content: 'Hi everyone!')
        bulletin.addToContactUsers(newUser)
        bulletin.addToContactUsers(newUser2)
        bulletin.addToContactUsers(newUser3)
        bulletin.addToTargetUsers(newUser)
        bulletin.addToTargetUsers(newUser2)
        bulletin.save(flush: true)

        respond(bulletin: bulletin)
    }
}
