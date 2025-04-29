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

import grails.testing.gorm.DataTest
import spock.lang.Specification
import grails.persistence.Entity
import spock.lang.Issue

/**
 */
class DomainClassDeepValidationSpec extends Specification implements DataTest {

    void setupSpec() {
        mockDomains ExampleParent, ExampleManyChild, ExampleSingleChild
    }

    @Issue('GRAILS-8738')
    void "Test that validation cascades to the child from the owner"() {
        when:"a domain model with invalid children is created"
            def testObj = new ExampleParent()
            testObj.title = 'Testing'
            def oneChild = new ExampleSingleChild()
            testObj.oneChild = oneChild
            def manyChildren = [new ExampleManyChild(), new ExampleManyChild(), new ExampleManyChild(), new ExampleManyChild()]

        then: "The children have validation errors"
            !oneChild.validate()
            !manyChildren[0].validate()

        when: "When the children are assigned to the parent"
            testObj.manyChildren = manyChildren
        then: "The parent has validation errors"
            !testObj.validate(deepValidate: true)
            testObj.validate(deepValidate: false)

        when:"The children are made valid"
            oneChild.singleName = 'foo'
            testObj.manyChildren.each { it.childName = "stuff" }

        then: "The parent has no validation errors"
            testObj.validate()
            testObj.validate(deepValidate: false)
            testObj.validate(deepValidate: true)

    }
}

@Entity
class ExampleManyChild {

    static constraints = {
        childName(minSize: 1)
    }

    String childName

    static belongsTo = ExampleParent
}

@Entity
class ExampleParent {


    static constraints = {
        title(minSize: 1)
        manyChildren(minSize: 1)
        oneChild(nullable: false)
    }

    static hasMany = [manyChildren: ExampleManyChild]
    static hasOne = [oneChild: ExampleSingleChild]

    String title


}

@Entity
class ExampleSingleChild {

    static constraints = {
        singleName(minSize: 1)
    }

    String singleName

    static belongsTo = ExampleParent
}
