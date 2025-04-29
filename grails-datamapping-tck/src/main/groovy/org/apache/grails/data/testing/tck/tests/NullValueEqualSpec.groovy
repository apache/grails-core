/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.grails.data.testing.tck.tests

import org.apache.grails.data.testing.tck.domains.TestEntity
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec
import spock.lang.IgnoreIf

class NullValueEqualSpec extends GrailsDataTckSpec {

    void "test null value in equal"() {
        when:
        new TestEntity(name: "Fred", age: null).save(failOnError: true)
        new TestEntity(name: "Bob", age: 11).save(failOnError: true)
        new TestEntity(name: "Jack", age: null).save(flush: true, failOnError: true)

        then:
        TestEntity.countByAge(11) == 1
        TestEntity.findAllByAge(null).size() == 2
        TestEntity.countByAge(null) == 2
    }

    @IgnoreIf({ System.getProperty('hibernate5.gorm.suite') })
    void "test null value in not equal"() {
        when:
        new TestEntity(name: "Fred", age: null).save(failOnError: true)
        new TestEntity(name: "Bob", age: 11).save(failOnError: true)
        new TestEntity(name: "Jack", age: null).save(flush: true, failOnError: true)

        then:
        TestEntity.countByAgeNotEqual(11) == 2
        TestEntity.countByAgeNotEqual(null) == 1
    }
}
