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

import grails.gorm.tests.GormDatastoreSpec
import grails.persistence.Entity
import org.bson.types.ObjectId
import spock.lang.Issue

/**
 * @author Graeme Rocher
 */
class BeforeInsertUpdateSpec extends GormDatastoreSpec{

    @Issue('GPMONGODB-251')
    void "Test that before insert and update events are triggered without issue"() {
        when:"A user is persisted"
            def u = new BeforeInsertUser(login: "fred", password: "bar")
            u.save(flush:true)
            session.clear()
            u = BeforeInsertUser.findByLogin("fred")

        then:"The before insert event was triggered and the password encoded"
            u != null
            u.password == 'foo'

        when:"A user is updated"
            u.password = "bar"
            u.save(flush: true)
            session.clear()
            u = BeforeInsertUser.findByLogin("fred")

        then:"The before update event was triggered"
            u != null
            u.password == 'foo'

    }

    @Override
    List getDomainClasses() {
        [BeforeInsertUser]
    }
}

@Entity
class BeforeInsertUser {

    ObjectId id
    String login
    String password

    transient isPasswordEncoded = false

    def beforeInsert() {
        if (!isPasswordEncoded) {
            encodePassword()
            isPasswordEncoded = true
        }
    }

    def beforeUpdate() {
        if (!isPasswordEncoded) {
            encodePassword()
            isPasswordEncoded = true
        }
    }

    protected void encodePassword() {
        password = "foo"
    }
}