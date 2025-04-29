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
package grails.mongodb.bootstrap

import grails.gorm.annotation.Entity
import grails.mongodb.MongoEntity
import org.apache.grails.testing.mongo.AutoStartedMongoSpec
import org.grails.datastore.mapping.mongo.MongoDatastore
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

/**
 * Created by graemerocher on 05/10/2016.
 */
class EventsSetupSpec extends AutoStartedMongoSpec {

    @AutoCleanup
    @Shared
    MongoDatastore mongoDatastore

    void setupSpec() {
        mongoDatastore = new MongoDatastore(['grails.mongodb.host':mongoHost, 'grails.mongodb.port': mongoPort], MyEventSender)
    }

    void 'test events get triggered'() {
        setup:
        MyEventSender.DB.drop()
        when:
        new MyEventSender(name: "fred").save(flush:true)

        then:
        MyEventSender.first().name == 'FRED'
    }
}

@Entity
class MyEventSender implements MongoEntity<MyEventSender> {
    String name

    def beforeInsert() {
        name = name.toUpperCase()
    }
}
