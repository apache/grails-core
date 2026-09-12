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

import groovy.transform.CompileStatic

import org.springframework.beans.factory.BeanRegistrar
import org.springframework.beans.factory.BeanRegistry
import org.springframework.core.env.Environment
import org.springframework.core.task.AsyncTaskExecutor
import org.springframework.core.task.TaskDecorator
import org.springframework.core.task.support.CompositeTaskDecorator
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor

import grails.async.PromiseFactory
import grails.async.Promises
import grails.plugins.Plugin
import grails.async.web.WebPromises
import org.grails.async.factory.PromiseFactoryBuilder
import org.grails.plugins.web.async.mvc.AsyncActionResultTransformer

/**
 * Async support for the Grails 2.0. Doesn't do much right now, most logic handled
 * by the compile time transform.
 *
 * @author Graeme Rocher
 * @since 2.0
 */
@CompileStatic
class ControllersAsyncGrailsPlugin extends Plugin {

    def grailsVersion = '8.0.0-SNAPSHOT > *'
    def loadAfter = ['controllers']

    @Override
    BeanRegistrar beanRegistrar() {
        return { BeanRegistry registry, Environment environment ->
            registry.registerBean('asyncPromiseResponseActionResultTransformer', AsyncActionResultTransformer)
            registry.registerBean('grailsWebRequestTaskDecorator', TaskDecorator) {
                it.supplier { new GrailsWebRequestTaskDecorator() }
            }
            registry.registerBean('grailsPromiseExecutor', AsyncTaskExecutor) {
                it.fallback()
                it.supplier { context ->
                    List<TaskDecorator> decorators = context.beanProvider(TaskDecorator).orderedStream().toList()
                    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor()
                    executor.threadNamePrefix = 'grails-promise-'
                    if (decorators) {
                        executor.taskDecorator = new CompositeTaskDecorator(decorators)
                    }
                    return executor
                }
            }
            registry.registerBean('grailsPromiseFactory', PromiseFactory) {
                it.supplier { context ->
                    AsyncTaskExecutor executor = context.bean(AsyncTaskExecutor)
                    PromiseFactory promiseFactory = PromiseFactoryBuilder.build(executor)
                    Promises.setPromiseFactory(promiseFactory)
                    WebPromises.setPromiseFactory(promiseFactory)
                    return promiseFactory
                }
            }
        }
    }
}
