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
package org.grails.transaction

import grails.core.GrailsApplication
import grails.transaction.TransactionManagerAware
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory
import org.springframework.transaction.PlatformTransactionManager
import spock.lang.Specification

class TransactionManagerPostProcessorSpec extends Specification {

    void 'setBeanFactory rejects a bean factory that is not configurable-listable'() {
        given:
        TransactionManagerPostProcessor processor = new TransactionManagerPostProcessor()

        when:
        processor.setBeanFactory(Mock(org.springframework.beans.factory.BeanFactory))

        then:
        thrown(IllegalArgumentException)
    }

    void 'a TransactionManagerAware bean is injected with the well-known transactionManager bean'() {
        given:
        PlatformTransactionManager transactionManager = Mock(PlatformTransactionManager)
        ConfigurableListableBeanFactory beanFactory = Mock(ConfigurableListableBeanFactory) {
            containsBean(GrailsApplication.TRANSACTION_MANAGER_BEAN) >> true
            getBean(GrailsApplication.TRANSACTION_MANAGER_BEAN, PlatformTransactionManager) >> transactionManager
        }
        TransactionManagerPostProcessor processor = new TransactionManagerPostProcessor()
        processor.setBeanFactory(beanFactory)

        TransactionManagerAware bean = Mock(TransactionManagerAware)

        when:
        boolean result = processor.postProcessAfterInstantiation(bean, 'myBean')

        then:
        result
        1 * bean.setTransactionManager(transactionManager)
    }

    void 'falls back to a bean of type PlatformTransactionManager when no well-known bean exists'() {
        given:
        PlatformTransactionManager transactionManager = Mock(PlatformTransactionManager)
        ConfigurableListableBeanFactory beanFactory = Mock(ConfigurableListableBeanFactory) {
            containsBean(GrailsApplication.TRANSACTION_MANAGER_BEAN) >> false
            getBeanNamesForType(PlatformTransactionManager, false, false) >> (['someTxManager'] as String[])
            getBean('someTxManager') >> transactionManager
        }
        TransactionManagerPostProcessor processor = new TransactionManagerPostProcessor()
        processor.setBeanFactory(beanFactory)

        TransactionManagerAware bean = Mock(TransactionManagerAware)

        when:
        processor.postProcessAfterInstantiation(bean, 'myBean')

        then:
        1 * bean.setTransactionManager(transactionManager)
    }

    void 'a non-aware bean is left untouched and always returns true'() {
        given:
        TransactionManagerPostProcessor processor = new TransactionManagerPostProcessor()
        processor.setBeanFactory(Mock(ConfigurableListableBeanFactory))

        expect:
        processor.postProcessAfterInstantiation(new Object(), 'plainBean')
    }

    void 'the transaction manager lookup only happens once even across several aware beans'() {
        given:
        PlatformTransactionManager transactionManager = Mock(PlatformTransactionManager)
        ConfigurableListableBeanFactory beanFactory = Mock(ConfigurableListableBeanFactory) {
            containsBean(GrailsApplication.TRANSACTION_MANAGER_BEAN) >> true
            getBean(GrailsApplication.TRANSACTION_MANAGER_BEAN, PlatformTransactionManager) >> transactionManager
        }
        TransactionManagerPostProcessor processor = new TransactionManagerPostProcessor()
        processor.setBeanFactory(beanFactory)

        when:
        processor.postProcessAfterInstantiation(Mock(TransactionManagerAware), 'bean1')
        processor.postProcessAfterInstantiation(Mock(TransactionManagerAware), 'bean2')

        then:
        1 * beanFactory.containsBean(GrailsApplication.TRANSACTION_MANAGER_BEAN) >> true
    }

    void 'order defaults to lowest precedence'() {
        expect:
        new TransactionManagerPostProcessor().order == org.springframework.core.Ordered.LOWEST_PRECEDENCE
    }
}
