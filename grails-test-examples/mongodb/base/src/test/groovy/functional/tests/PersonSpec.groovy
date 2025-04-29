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

import grails.test.mongodb.MongoSpec
import org.apache.grails.testing.mongo.AbstractMongoGrailsExtension
import org.testcontainers.containers.MongoDBContainer
import org.testcontainers.utility.DockerImageName
import spock.lang.AutoCleanup
import spock.lang.Shared

/**
 * Created by graemerocher on 17/10/16.
 */
class PersonSpec extends MongoSpec implements EmbeddedMongoClient {

    @Shared
    @AutoCleanup
    final MongoDBContainer mongoDBContainer = new MongoDBContainer(AbstractMongoGrailsExtension.desiredMongoDockerName)

    void "Test codecs work for custom properties"() {
        when:"An an instance with a custom codec is saved"
        def dob = new Date()
        new Person(name: "Fred", birthday: new Birthday(dob)).save(flush:true)
        Person person = Person.first()

        then:"The result is correct"
        person.name == "Fred"
        person.birthday.date == dob
    }
}
