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
package grails.test.mixin

import grails.persistence.Entity
import grails.testing.gorm.DomainUnitTest
import spock.lang.Specification

class DomainClassWithCustomValidatorTests extends Specification implements DomainUnitTest<Uniqueable> {

    void testThereCanBeOnlyOneSomething() {
        when:
        def uni = new Uniqueable()

        then:
        uni.save(flush:true)

        when:
        def uni2 = new Uniqueable()

        then:
        // checks there is no stack over flow
        uni2.save() == null
    }
}

@Entity
class Uniqueable {
    String word = "something"

    static constraints = {
        word validator: Uniqueable.onlyOneSomething
    }

    static onlyOneSomething = { value, obj ->
        if (value == "something" && Uniqueable.countByWord("something")) {
            return "unique"
        }
    }
}

