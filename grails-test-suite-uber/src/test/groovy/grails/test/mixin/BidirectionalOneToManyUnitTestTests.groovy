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
import grails.testing.gorm.DataTest
import spock.lang.Specification

class BidirectionalOneToManyUnitTestTests extends Specification implements DataTest {

    Class[] getDomainClassesToMock() {
        [Parent, Child]
    }

    // test for GRAILS-8030
    void testRelationship() {
        when:
        def parent = new Parent()
        def child = new Child()
        parent.addToChildren child

        then:
        parent.save()
    }
}

@Entity
class Parent {

    List children
    String name

    static hasMany = [ children: Child ]

    static constraints = {
        name nullable: true
    }
}

@Entity
class Child {
    static belongsTo = [ parent: Parent ]
}
