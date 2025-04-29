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
package example

import org.hibernate.Hibernate

import grails.gorm.transactions.Rollback
import grails.test.hibernate.HibernateSpec

/**
 * Tests Proxy with hibernate-groovy-proxy
 */

class ProxySpec extends HibernateSpec {

    @Rollback
    void "Test Proxy"() {
        when:
        new Customer(1, "Bob").save(failOnError: true, flush: true)
        hibernateDatastore.currentSession.clear()

        def proxy
        Customer.withNewSession {
            proxy = Customer.load(1)
        }

        then:
        //without ByteBuddyGroovyInterceptor this would normally cause the proxy to init
        proxy
        proxy.metaClass
        proxy.getMetaClass()
        !Hibernate.isInitialized(proxy)
        //id calls
        proxy.id == 1
        proxy.getId() == 1
        proxy["id"] == 1
        !Hibernate.isInitialized(proxy)
        // gorms trait implements in the class so no way to tell
        // proxy.toString() == "Customer : 1 (proxy)"
        // !Hibernate.isInitialized(proxy)
    }

}
