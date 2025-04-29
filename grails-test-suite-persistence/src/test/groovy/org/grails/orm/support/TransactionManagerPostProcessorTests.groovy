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
package org.grails.orm.support

import grails.spring.BeanBuilder
import grails.transaction.TransactionManagerAware
import org.grails.transaction.TransactionManagerPostProcessor
import org.junit.jupiter.api.Test
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.transaction.PlatformTransactionManager

import static org.junit.jupiter.api.Assertions.assertNotNull

/**
 * @author Graeme Rocher
 * @since 1.0
 */
class TransactionManagerPostProcessorTests {

    @Test
    void testTransactionManagerPostProccessor() {
        def bb = new BeanBuilder()

        bb.beans {
            myBean(MyBean) { bean ->
                bean.lazyInit = true
            }
            dataSource(DriverManagerDataSource) {
                url = "jdbc:h2:mem:tmpptDB"
                driverClassName = "org.h2.Driver"
                username = "sa"
                password = ""
            }
            transactionManager(DataSourceTransactionManager) {
                dataSource = dataSource
            }
            transactionManagerPostProcessor(TransactionManagerPostProcessor)
        }

        def ctx = bb.createApplicationContext()

        MyBean bean = ctx.getBean("myBean")
        assertNotNull bean
        assertNotNull bean.tm
    }
}

class MyBean implements TransactionManagerAware {

    PlatformTransactionManager tm

    void setTransactionManager(PlatformTransactionManager transactionManager) {
        tm = transactionManager
    }
}
