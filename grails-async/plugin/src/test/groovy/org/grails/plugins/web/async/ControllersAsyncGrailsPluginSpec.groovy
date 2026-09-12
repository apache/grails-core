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
package org.grails.plugins.web.async

import org.springframework.beans.factory.support.BeanRegistryAdapter
import org.springframework.beans.factory.support.DefaultListableBeanFactory
import org.springframework.core.env.StandardEnvironment
import org.springframework.core.task.AsyncTaskExecutor
import org.springframework.core.task.TaskDecorator
import org.springframework.core.task.SyncTaskExecutor
import org.springframework.core.task.support.TaskExecutorAdapter

import grails.async.PromiseFactory
import org.grails.plugins.web.async.mvc.AsyncActionResultTransformer

import spock.lang.Specification

class ControllersAsyncGrailsPluginSpec extends Specification {

    void "beanRegistrar registers the async promise beans"() {
        given:
        def beanFactory = new DefaultListableBeanFactory()
        def registrar = new ControllersAsyncGrailsPlugin().beanRegistrar()

        when:
        new BeanRegistryAdapter(beanFactory, new StandardEnvironment(), registrar.getClass()).register(registrar)

        then:
        beanFactory.getBeanDefinition('asyncPromiseResponseActionResultTransformer').beanClassName == AsyncActionResultTransformer.name
        beanFactory.getBeanDefinition('grailsPromiseFactory').beanClassName == PromiseFactory.name
        beanFactory.getBeanDefinition('grailsWebRequestTaskDecorator').beanClassName == TaskDecorator.name
        beanFactory.getBeanDefinition('grailsPromiseExecutor').fallback
    }

    void 'promise factory uses the application task executor'() {
        given:
        def beanFactory = new DefaultListableBeanFactory()
        AsyncTaskExecutor executor = new TaskExecutorAdapter(new SyncTaskExecutor())
        beanFactory.registerSingleton('applicationTaskExecutor', executor)
        def registrar = new ControllersAsyncGrailsPlugin().beanRegistrar()
        new BeanRegistryAdapter(beanFactory, new StandardEnvironment(), registrar.getClass()).register(registrar)

        when:
        PromiseFactory promiseFactory = beanFactory.getBean('grailsPromiseFactory', PromiseFactory)

        then:
        promiseFactory.createPromise { Thread.currentThread() }.get().is(Thread.currentThread())
    }

    void 'promise factory uses a managed fallback executor when Boot does not provide one'() {
        given:
        def beanFactory = new DefaultListableBeanFactory()
        def registrar = new ControllersAsyncGrailsPlugin().beanRegistrar()
        new BeanRegistryAdapter(beanFactory, new StandardEnvironment(), registrar.getClass()).register(registrar)

        when:
        PromiseFactory promiseFactory = beanFactory.getBean('grailsPromiseFactory', PromiseFactory)
        Thread worker = promiseFactory.createPromise { Thread.currentThread() }.get()

        then:
        worker.name.startsWith('grails-promise-')

        cleanup:
        beanFactory.destroySingletons()
    }
}
