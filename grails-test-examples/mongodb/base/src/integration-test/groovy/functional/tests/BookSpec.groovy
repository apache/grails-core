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

import com.mongodb.client.MongoClient
import grails.testing.mixin.integration.Integration

import grails.validation.ValidationException
import org.springframework.beans.factory.annotation.Autowired
import spock.lang.Specification

/**
 * Created by graemerocher on 12/09/2016.
 */
@Integration(applicationClass = Application)
class BookSpec extends Specification {

    @Autowired
    MongoClient mongoClient

    void "Test low-level API extensions"() {

        when:
        def db = mongoClient.getDatabase("test")
        db.drop()
// Insert a document
        db['languages'].insert([name: 'Groovy'])
// A less verbose way to do it
        db.languages.insert(name: 'Ruby')
// Yet another way
        db.languages << [name: 'Python']

        then:
        db.languages.count() == 3

    }

    void "test fail on error"() {
        when:
        def invalid = new Book(title: "")
        invalid.save()

        then:
        thrown ValidationException
        invalid.hasErrors()
    }
}
