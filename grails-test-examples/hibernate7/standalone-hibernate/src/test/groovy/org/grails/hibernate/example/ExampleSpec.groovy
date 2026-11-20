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
package org.grails.hibernate.example

import grails.gorm.annotation.Entity
import grails.gorm.services.Service
import grails.gorm.transactions.Rollback
import org.grails.orm.hibernate.HibernateDatastore
import org.springframework.transaction.PlatformTransactionManager
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

/**
 * Created by graemerocher on 13/07/2016.
 */
class ExampleSpec extends Specification {

    @Shared @AutoCleanup HibernateDatastore hibernateDatastore
    @Shared PlatformTransactionManager transactionManager

    void setupSpec() {
       hibernateDatastore = new HibernateDatastore(Example)
       transactionManager = hibernateDatastore.getTransactionManager()
    }

    @Rollback
    void "test execute Hibernate standalone in a unit test"() {
        when:
        new Example(name: "Fred").save(flush:true)
        ExampleService exampleService = hibernateDatastore.getService(ExampleService)
        then:
        exampleService.count("Fred") == 1
        Example.count() == 1
    }
}

@Service(Example)
interface ExampleService {
    Number count(String name)
}
@Entity
class Example {
    String name
}
