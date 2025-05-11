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
import grails.gorm.transactions.Rollback
import org.grails.datastore.mapping.reflect.EntityReflector
import org.grails.orm.hibernate.HibernateDatastore
import spock.lang.AutoCleanup
import spock.lang.Issue
import spock.lang.Shared
import spock.lang.Specification

@Rollback
class UniqueInheritanceSpec extends Specification {

    @Shared @AutoCleanup HibernateDatastore hibernateDatastore = new HibernateDatastore(Item, ConcreteProduct, Product, Book)

    void "unique constraint works directly"() {
        setup:
        Product i = new ConcreteProduct(name: '123')
        i.save(flush: true)

        expect:
        !i.hasErrors()

        when:
        i.save()

        then:
        !i.hasErrors()
    }

    void "unique constraint works on cascade"() {
        setup:
        Item i = new Item(product: new ConcreteProduct(name: '123'))
        i.save(flush: true)

        expect:
        !i.hasErrors()

        when:
        i.save()

        then:
        !i.hasErrors() // item.product.name is not unique
    }

    @Issue('https://github.com/grails/gorm-hibernate5/issues/32')
    void "test save multiple book instances with unique constraint applied"() {
        when:
        def book1=new Book(title: "one")
        book1.save()
        def book2=new Book(title: "one")
        book2.save()

        then:
        book2.hasErrors()
    }
}

@Entity
class Book {
    String title
    static constraints = {
        title (nullable: false,size: 0..200, unique: true, blank:false)
    }
}

@Entity
class Item {
    Product product
}

@Entity
class ConcreteProduct extends Product {

}

@Entity
abstract class Product {
    String name

    static constraints = {
        name unique: true
    }
}
