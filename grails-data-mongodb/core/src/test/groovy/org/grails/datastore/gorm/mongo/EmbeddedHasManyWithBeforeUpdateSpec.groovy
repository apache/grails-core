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
package org.grails.datastore.gorm.mongo

import grails.persistence.Entity
import org.apache.grails.data.mongo.core.GrailsDataMongoTckManager
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec
import org.bson.types.ObjectId

class EmbeddedHasManyWithBeforeUpdateSpec extends GrailsDataTckSpec<GrailsDataMongoTckManager> {

    void setupSpec() {
        manager.domainClasses += [User, UserAddress]
    }

    void "Test embedded hasMany with beforeUpdate event"() {
        given:
        def user = User.findByName("Ratler")
        if (!user) {
            user = new User(name: "Ratler")
        }
        def address = new UserAddress(type: "home")
        user.addresses = [address]
        user.save(flush: true)
        manager.session.clear()

        when:
        user = User.findByName("Ratler")

        then:
        user != null
        user.addresses.size() == 1
        user.addresses[0].type == 'home'

        when:
        user.name = "Bob"
        user.save(flush: true)
        manager.session.clear()
        user = User.findByName("bob")

        then:
        user != null
        user.addresses.size() == 1
        user.addresses[0].type == 'home'
    }
}

@Entity
class User {
    ObjectId id
    String name
    List<UserAddress> addresses

    static embedded = ['addresses']
    static hasMany = [addresses: UserAddress]

    def beforeUpdate() {
        this.name = name.toLowerCase()
    }
}

@Entity
class UserAddress {
    ObjectId id
    String type
}
