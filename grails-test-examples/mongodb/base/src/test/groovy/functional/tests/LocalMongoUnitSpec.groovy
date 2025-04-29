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

//tag::structure[]
import grails.test.mongodb.MongoSpec
import grails.validation.ValidationException
import org.apache.grails.testing.mongo.AbstractMongoGrailsExtension
import org.testcontainers.containers.MongoDBContainer
import org.testcontainers.utility.DockerImageName
import spock.lang.AutoCleanup
import spock.lang.PendingFeature
import spock.lang.Shared

class LocalMongoUnitSpec extends MongoSpec implements EmbeddedMongoClient {

    @Shared
    @AutoCleanup
    final MongoDBContainer mongoDBContainer = new MongoDBContainer(AbstractMongoGrailsExtension.desiredMongoDockerName)

//end::structure[]
    void "Test GORM access"(){
        when:
        Book book = new Book(title: 'El Quijote').save(flush: true)

        then:
        Book.count() == 1

        when:
        book = Book.findByTitle('El Quijote')

        then:
        book.id
    }

//tag::structure[]
    @PendingFeature(reason = 'A ValidationException is not thrown')
    void "test fail on error"() {

        when:
        def invalid = new Book(title: "")
        invalid.save()

        then:
        thrown ValidationException
        invalid.hasErrors()
    }
}
//end::structure[]
