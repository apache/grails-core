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

package grails.gorm.specs.validation

import grails.gorm.annotation.Entity
import grails.gorm.specs.HibernateGormDatastoreSpec
import grails.gorm.transactions.Rollback
import jakarta.validation.constraints.Digits
import org.hibernate.validator.constraints.NotBlank

/**
 * Created by graemerocher on 07/04/2017.
 */
class BeanValidationSpec extends HibernateGormDatastoreSpec {

    def setupSpec() {
        manager.addAllDomainClasses([Bean])
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
}

@Entity
class Bean {
    @NotBlank
    String name
    @Digits(integer = 6, fraction = 2)
    BigDecimal price
}
