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

package grails.gorm.tests

import grails.gorm.annotation.Entity
import grails.neo4j.Relationship
import grails.neo4j.mapping.MappingBuilder
import spock.lang.Issue

class ManyToManyQuerySpec extends GormDatastoreSpec {

    @Override
    List getDomainClasses() {
        [TestA, TestB, TestC]
    }

    @Issue('309')
    def "many-to-many relationships are queried correctly"() {
        setup:
        def a = new TestA(testA: "aaa").save(flush: true, failOnError: true)
        def c = new TestC(testC: "ccc").save(flush: true, failOnError: true)
        new TestB(from: a, to: c, testB: "bbb").save(flush: true, failOnError: true)

        when:
        def result = TestA.createCriteria().list {
            ccc {
                eq("testB", "bbb")
                to {
                    eq("testC", "ccc")
                }
            }
        }

        then:
        result.size() == 1
        result[0].testA == a.testA
    }
}

@Entity
class TestA {
    String testA
    static hasMany = [ccc: TestB]
}

@Entity
class TestC {
    String testC
    static hasMany = [aaa: TestB]
}

@Entity
class TestB implements Relationship<TestA, TestC> {
    String testB
    static mapping = MappingBuilder.relationship {
        type "A_REL_C"
        direction Direction.OUTGOING
    }
}
