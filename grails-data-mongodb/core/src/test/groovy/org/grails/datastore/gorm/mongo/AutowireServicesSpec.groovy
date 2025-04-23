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

package org.grails.datastore.gorm.mongo

import grails.gorm.tests.GormDatastoreSpec
import grails.persistence.Entity

import org.springframework.context.support.GenericApplicationContext

class AutowireServicesSpec extends GormDatastoreSpec {

    void "Test that services can be autowired"() {
        given:"A service registered in the application context"
            GenericApplicationContext context = new GenericApplicationContext()

            session.datastore.applicationContext = context
            context.beanFactory.registerSingleton("orderService", new OrderService())
            context.refresh()

        when:"An instance is created and saved"
            OrderService orderService = context.getBean("orderService")
            def p = new Pizza(name:"Ham and Cheese", orderService: orderService)
            p.save flush:true

        then:"The service is called correctly"
            orderService.orders.size() == 1

        when:"The instance is loaded"
            session.clear()
            p = Pizza.get(p.id)

        then:"The order service is autowired"
            p.orderService != null

        when:"The entity is deleted"
            p.delete flush:true

        then:"The order is correctly removed"
            orderService.orders.size() == 0
    }

    @Override
    List getDomainClasses() {
        [Pizza]
    }
}

@Entity
class Pizza {
    String id
    String name
    OrderService orderService
    def afterInsert() {
        orderService.placeOrder(this.name)
    }
    def afterDelete() {
        orderService.removeOrder(this.name)
    }

    static mapping = {
        autowire true
    }
}

class OrderService {
    def orders = []
    def removeOrder(String name) { orders.remove(name)}
    def placeOrder(String name) { orders << name }
}
