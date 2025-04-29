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
import grails.gorm.validation.ConstrainedProperty
import grails.validation.ValidationException
import org.apache.grails.data.simple.core.GrailsDataCoreTckManager
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec
import org.grails.datastore.gorm.validation.constraints.eval.DefaultConstraintEvaluator
import org.springframework.validation.Errors
import org.springframework.validation.Validator

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

    void "test save with fail on error default"() {
        when: "A product is saved with fail on error default true"
        GormEnhancer.findInstanceApi(TestProduct).failOnError = true
        def p = new TestProduct()
        p.save()

        then: "Validation exception thrown"
        thrown(ValidationException)

        when: "A product is saved with fail on error default false"
        GormEnhancer.findInstanceApi(TestProduct).failOnError = false
        p = new TestProduct()
        def result = p.save()

        then: "The save returns false"
        !result
    }

    void "test override fail on error default"() {
        when: "A product is saved with fail on error override to false"
        GormEnhancer.findInstanceApi(TestProduct).failOnError = true
        def p = new TestProduct()
        def result = p.save(failOnError: false, flush: true)

        then: "The save returns false"
        !result

        when: "A product is saved with fail on error override to true"
        GormEnhancer.findInstanceApi(TestProduct).failOnError = false
        p = new TestProduct()
        p.save(failOnError: true)

        then: "Validation exception thrown"
        thrown(ValidationException)
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
