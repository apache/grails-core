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

package grails.gorm.tests.validation

import grails.gorm.annotation.Entity
import grails.gorm.tests.HibernateGormDatastoreSpec
import grails.gorm.transactions.Rollback
import grails.gorm.validation.ConstrainedEntity
import jakarta.validation.constraints.Digits
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size

/**
 * Created by graemerocher on 07/04/2017.
 */
class BeanValidationSpec extends HibernateGormDatastoreSpec {

    def setupSpec() {
        manager.registerDomainClasses(Bean, BeanWithConstraintsBlock)
    }

    @Rollback
    void "test bean validation API validate on save"() {
        given:"A an invalid instance"
        Bean bean = new Bean(name:"", price:600.12034)
        when:"the bean is saved"
        bean.save()

        then:"the errors are correct"
        bean.hasErrors()
        bean.errors.allErrors.size() == 2
        bean.errors.hasFieldErrors("price")
        bean.errors.hasFieldErrors("name")
    }

    @Rollback
    void "test a null value is reported by the bean validation API"() {
        given: "an instance with a null property annotated @NotBlank"
        Bean bean = new Bean(price: 1.0)

        expect:
        !bean.validate()
        bean.errors.getFieldErrorCount("name") == 1
        bean.errors.getFieldError("name").code == "NotBlank"
        bean.save() == null
    }

    @Rollback
    void "test the constraints block is validated together with the bean validation annotations"() {
        given: "an instance violating annotations and the constraints block"
        BeanWithConstraintsBlock bean = new BeanWithConstraintsBlock(code: "toolong", isbn: "")

        expect: "each violation is reported once"
        !bean.validate()
        bean.errors.errorCount == 3
        bean.errors.getFieldError("name").code == "NotNull"
        bean.errors.getFieldError("code").code == "Size"
        bean.errors.getFieldError("isbn").code == "blank"

        when: "the instance is valid"
        bean = new BeanWithConstraintsBlock(name: "name", code: "code", isbn: "isbn")

        then:
        bean.validate()
        bean.save(flush: true) != null
    }

    void "test the constraints of an annotated entity are exposed"() {
        given:
        def entity = BeanWithConstraintsBlock.gormPersistentEntity
        def validator = entity.mappingContext.getEntityValidator(entity)

        expect:
        validator instanceof ConstrainedEntity
        def constraints = ((ConstrainedEntity) validator).constrainedProperties
        !constraints.name.nullable
        constraints.code.maxSize == 5
        !constraints.isbn.nullable
        !constraints.isbn.blank
    }
}

@Entity
class Bean {
    @NotBlank
    String name
    @Digits(integer = 6, fraction = 2)
    BigDecimal price
}

@Entity
class BeanWithConstraintsBlock {
    @NotNull
    String name
    @Size(max = 5)
    String code
    String isbn

    static constraints = {
        isbn nullable: false, blank: false
    }
}
