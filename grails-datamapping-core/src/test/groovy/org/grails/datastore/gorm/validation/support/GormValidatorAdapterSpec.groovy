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
package org.grails.datastore.gorm.validation.support

import org.grails.datastore.gorm.GormValidateable
import org.grails.datastore.gorm.validation.jakarta.GormValidatorAdapter
import spock.lang.Specification

import jakarta.validation.Validation
import jakarta.validation.Validator
import jakarta.validation.constraints.Digits

/**
 * Created by graemerocher on 30/12/2016.
 */
class GormValidatorAdapterSpec extends Specification {


    void "test propagate jakarta.valdiation errors to gorm object"() {

        given:
        def factory = Validation.byDefaultProvider().configure().buildValidatorFactory()

        Validator v = factory.getValidator()
        def adapter = new GormValidatorAdapter(v)


        when:
        def product = new Product(price: "foo")
        adapter.validate(product)

        then:
        adapter.forExecutables()
        product.errors.hasErrors()
        product.errors.getFieldError('price')
    }
}

class Product implements GormValidateable {
    @Digits(integer = 6, fraction = 2)
    String price
}
