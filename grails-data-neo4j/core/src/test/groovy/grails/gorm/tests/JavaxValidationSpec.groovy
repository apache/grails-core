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


import org.apache.grails.data.neo4j.core.Neo4jGormDatastoreSpec
import grails.gorm.annotation.Entity

import jakarta.validation.constraints.Digits

/**
 * Created by graemerocher on 30/12/2016.
 */
class JavaxValidationSpec extends Neo4jGormDatastoreSpec {

    void "test javax.validator validation"() {
        when:"An invalid entity is created"
        JavaxProduct p = new JavaxProduct(name:"MacBook", price: "bad")
        p.save()

        then:"The are errors"
        p.hasErrors()
        p.errors.getFieldError('price')
    }

    void setupSpec() {
        manager.registerDomainClasses(JavaxProduct)
    }
}

@Entity
class JavaxProduct {
    @Digits(integer = 6, fraction = 2)
    String price
    String name
}
