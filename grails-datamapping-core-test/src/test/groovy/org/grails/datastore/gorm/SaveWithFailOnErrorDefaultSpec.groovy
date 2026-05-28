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

import org.springframework.validation.Errors
import org.springframework.validation.Validator

import grails.gorm.annotation.Entity
import grails.gorm.validation.ConstrainedProperty
import grails.validation.ValidationException
import org.apache.grails.data.simple.core.GrailsDataCoreTckManager
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec
import org.grails.datastore.gorm.validation.constraints.eval.DefaultConstraintEvaluator

class SaveWithFailOnErrorDefaultSpec extends GrailsDataTckSpec<GrailsDataCoreTckManager> {

    void setupSpec() {
        manager.domainClasses.addAll([TestProduct])
    }

    def setup() {
        def validator = [supports: { Class cls -> true },
                         validate: { Object target, Errors errors ->
                             def constrainedProperties = new DefaultConstraintEvaluator().evaluate(TestProduct)
                             for (ConstrainedProperty cp in constrainedProperties.values()) {
                                 cp.validate(target, target[cp.propertyName], errors)
                             }
                         }] as Validator

        final context = manager.session.datastore.mappingContext
        final entity = context.getPersistentEntity(TestProduct.name)
        context.addEntityValidator(entity, validator)
    }

    void "save(failOnError: true) throws ValidationException on invalid entity"() {
        when: "An invalid product is saved with failOnError: true"
        new TestProduct().save(failOnError: true)

        then: "ValidationException is thrown"
        thrown(ValidationException)
    }

    void "save(failOnError: false) returns null on invalid entity"() {
        when: "An invalid product is saved with failOnError: false"
        def result = new TestProduct().save(failOnError: false)

        then: "null is returned instead of throwing"
        result == null
    }

    void "save() default behaviour returns null on invalid entity"() {
        when: "An invalid product is saved with no explicit failOnError arg"
        def result = new TestProduct().save()

        then: "null is returned (default is false)"
        result == null
    }

    void "per-call failOnError: true overrides when entity is invalid"() {
        when: "save with explicit failOnError: true on an invalid entity"
        new TestProduct().save(failOnError: true)

        then:
        thrown(ValidationException)

        when: "save with explicit failOnError: false on an invalid entity"
        def result = new TestProduct().save(failOnError: false, flush: true)

        then:
        result == null
    }
}

@Entity
class TestProduct {

    Long id
    String name

    static constraints = {
        //noinspection GroovyAssignabilityCheck
        name(nullable: false)
    }
}
