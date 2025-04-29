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

import grails.gorm.annotation.Entity
import grails.gorm.transactions.Rollback
import org.grails.orm.hibernate.HibernateDatastore
import org.springframework.transaction.PlatformTransactionManager
import spock.lang.*

/**
 * Created by graemerocher on 01/03/2017.
 */
@Ignore
class SubclassMultipleListCollectionSpec extends Specification {

    @AutoCleanup @Shared HibernateDatastore hibernateDatastore
    @Shared PlatformTransactionManager transactionManager


    void setupSpec() {
        hibernateDatastore = new HibernateDatastore(
                SuperProduct, Product, Iteration
        )
        transactionManager = hibernateDatastore.getTransactionManager()
    }

    @Ignore // not yet implemented
    @Rollback
    @Issue('https://github.com/grails/grails-data-mapping/issues/882')
    void "test inheritance with multiple list collections"() {
        when:
        Iteration iter = new Iteration()
        iter.addToProducts(new Product())
        iter.addToOtherProducts(new SuperProduct())
        iter.save(flush:true)

        then:
        Iteration.count == 1
    }
}

@Entity
class Iteration {
    List products

    static hasMany = [products: Product, otherProducts: SuperProduct]
    // uncommenting this line resolves the issue
//    static mappedBy = [products: 'iteration', otherProducts: 'none']
}

@Entity
class Product extends SuperProduct {

    static belongsTo = [iteration: Iteration]
}

@Entity
class SuperProduct {

    static constraints = {
    }
}
