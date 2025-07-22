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
package org.grails.test.support

import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationContextAware
import org.springframework.beans.factory.config.AutowireCapableBeanFactory
import org.springframework.beans.factory.annotation.AutowiredAnnotationBeanPostProcessor

/**
 * Convenience class to autowire test classes
 */
class GrailsTestAutowirer {

    ApplicationContext applicationContext

    GrailsTestAutowirer(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext
    }

    /**
     * Autowires the bean by name, processes any autowiring annotations,
     * and set's the applicationContext if it implements ApplicationContextAware.
     */
    void autowire(bean) {
        def beanFactory = applicationContext.autowireCapableBeanFactory
        beanFactory.autowireBeanProperties(bean, AutowireCapableBeanFactory.AUTOWIRE_BY_NAME, false)
        beanFactory.initializeBean(bean, bean.class.name)

        def annotationProcessor = beanFactory.createBean(AutowiredAnnotationBeanPostProcessor, AutowireCapableBeanFactory.AUTOWIRE_BY_NAME, false)
        annotationProcessor.processInjection(bean)

        if (bean instanceof ApplicationContextAware && applicationContext != null) {
            bean.setApplicationContext(applicationContext)
        }
    }
}
