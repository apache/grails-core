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

package grails.gorm.tck

import grails.gorm.tck.ChildEntity
import grails.gorm.tests.GormDatastoreSpec
import grails.gorm.tck.TestEntity
import grails.validation.ValidationException
import spock.lang.IgnoreRest

/**
 * @author graemerocher
 */
class CrudOperationsSpec extends GormDatastoreSpec {

    void "Test get using a string-based key"() {
        given:

            def t = new TestEntity(name:"Bob", child:new ChildEntity(name:"Child"))
            t.save(flush:true)

        when:
            t = TestEntity.get("${t.id}")

        then:
            t != null
    }

    void "Test get returns null of non-existent entity"() {
        given:
            def t
        when:
            t = TestEntity.get(1)
        then:
            t == null
    }

    @IgnoreRest
    void "Test basic CRUD operations"() {
        given:

            def t = new TestEntity(name:"Bob", child:new ChildEntity(name:"Child"))
            t.save(flush:true)

        when:
            def results = TestEntity.list()
            t = TestEntity.get(t.id)

        then:
            t != null
            t.id != null
            "Bob" == t.name
            1 == results.size()
            "Bob" == results[0].name
    }

    void "Test save method that takes a map"() {

        given:
            def t = new TestEntity(name:"Bob", child:new ChildEntity(name:"Child"))
            t.save(param:"one", flush: true)
        when:
            t = TestEntity.get(t.id)
        then:
            t.id != null
    }

    void "Test failOnError"() {
        given:
            def t = new TestEntity(child: new ChildEntity(name:"Child"))

        when:
            t.save(failOnError: true)

        then:
            thrown ValidationException
            t.id == null
    }
}
