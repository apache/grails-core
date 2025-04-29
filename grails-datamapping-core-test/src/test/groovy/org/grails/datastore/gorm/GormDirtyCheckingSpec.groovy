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
package org.grails.datastore.gorm

import grails.gorm.annotation.Entity
import org.apache.grails.data.simple.core.GrailsDataCoreTckManager
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec
import spock.lang.Issue

class GormDirtyCheckingSpec extends GrailsDataTckSpec<GrailsDataCoreTckManager> {
    void setupSpec() {
        manager.domainClasses.addAll([Student, BooleanTest])
    }

    void "test a new instance is dirty by default"() {
        when:
        Student student = new Student(name: "JD")

        then:
        student.isDirty()
    }

    @Issue('https://github.com/grails/grails-core/issues/12453')
    void "test Boolean property getters"() {

        when:
        BooleanTest student = new BooleanTest(property1: true, property2: true)

        then: "Same behaviour of getters for boolean and Boolean"
        student.isProperty1()
        student.getProperty1()
        student.isProperty2()
        student.getProperty2()
    }

}

@Entity
class Student {
    String name
}

@Entity
class BooleanTest {
    Boolean property1
    boolean property2
}

