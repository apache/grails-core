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
import groovy.util.logging.Slf4j

import java.time.Duration
import java.util.concurrent.Executor
import java.util.function.UnaryOperator

import org.springframework.beans.factory.BeanRegistrar
import org.springframework.beans.factory.BeanRegistry
import org.springframework.beans.factory.NoSuchBeanDefinitionException
import org.springframework.boot.convert.DurationStyle
import org.springframework.core.env.Environment
import org.springframework.core.task.AsyncTaskExecutor
import org.springframework.core.task.TaskDecorator
import org.springframework.core.task.support.CompositeTaskDecorator
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import org.springframework.util.ClassUtils

import grails.async.PromiseFactory
import grails.async.Promises
import grails.async.web.StreamItemRenderer
import grails.async.web.WebPromises
import grails.plugins.Plugin
import org.grails.async.factory.PromiseFactoryBuilder
import org.grails.async.factory.future.VirtualThreadPromiseFactory
import org.grails.plugins.web.async.mvc.AsyncActionResultTransformer
import org.grails.plugins.web.async.mvc.DefaultStreamItemRenderer
import org.grails.plugins.web.async.mvc.JacksonStreamItemRenderer
import org.grails.plugins.web.async.mvc.PublisherActionResultTransformer

/**
 * Async support for the Grails 2.0. Doesn't do much right now, most logic handled
 * by the compile time transform.
 *
 * @author Graeme Rocher
 * @since 2.0
 */
@CompileStatic
@Slf4j
class ControllersAsyncGrailsPlugin extends Plugin {

    public static final String STREAMING_ENABLED = 'grails.async.streaming.enabled'
    public static final String STREAMING_TIMEOUT = 'grails.async.streaming.timeout'
    public static final String DEFAULT_RENDERER = 'defaultStreamItemRenderer'
    public static final String JACKSON_RENDERER = 'jacksonStreamItemRenderer'
    private static final String JSON_MAPPER_CLASS = 'tools.jackson.databind.json.JsonMapper'

    def grailsVersion = '8.0.0-SNAPSHOT > *'
    def loadAfter = ['controllers']

    @Override
    BeanRegistrar beanRegistrar() {
        return { BeanRegistry registry, Environment environment ->
            registry.registerBean('asyncPromiseResponseActionResultTransformer', AsyncActionResultTransformer)
            registerStreamingBeans(registry, environment)
            registry.registerBean('grailsWebRequestTaskDecorator', TaskDecorator) {
                it.supplier { new GrailsWebRequestTaskDecorator() }
            }
            registry.registerBean('grailsPromiseExecutor', AsyncTaskExecutor) {
                it.fallback()
                it.supplier { context ->
                    List<TaskDecorator> decorators = context.beanProvider(TaskDecorator).orderedStream().toList()
                    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor()
                    executor.threadNamePrefix = 'grails-promise-'
                    executor.corePoolSize = 8
                    if (decorators) {
                        executor.taskDecorator = new CompositeTaskDecorator(decorators)
                    }
                    return executor
                }
            }
            registry.registerBean('grailsPromiseFactory', PromiseFactory) {
                it.supplier { context ->
                    AsyncTaskExecutor executor
                    String executorName = 'applicationTaskExecutor'
                    try {
                        executor = context.bean('applicationTaskExecutor', AsyncTaskExecutor)
                    }
                    catch (NoSuchBeanDefinitionException missing) {
                        if (missing.beanName != 'applicationTaskExecutor') {
                            throw missing
                        }
                        executor = context.bean('grailsPromiseExecutor', AsyncTaskExecutor)
                        executorName = 'grailsPromiseExecutor'
                    }
                    CompositeTaskDecorator decorator = new CompositeTaskDecorator(context.beanProvider(TaskDecorator).orderedStream().toList())
                    UnaryOperator<Executor> decorateExecutor = (Executor owned) -> {
                        Executor decorated = (Runnable task) -> owned.execute(decorator.decorate(task))
                        return decorated
                    }
                    PromiseFactory promiseFactory = PromiseFactoryBuilder.build(executor, decorateExecutor)
                    String execution = promiseFactory instanceof VirtualThreadPromiseFactory ? 'owned virtual-thread executor' : executorName
                    log.debug('Created promise factory {} with {}', promiseFactory.class.name, execution)
                    Promises.setPromiseFactory(promiseFactory)
                    WebPromises.setPromiseFactory(promiseFactory)
                    return promiseFactory
                }
            }
        }
    }

    /**
     * Registers the beans that stream a {@code Publisher} returned from an action as server-sent events,
     * unless {@code grails.async.streaming.enabled} is {@code false}. The renderers a plugin or the
     * application registers are consulted first, in {@code Ordered} order, and the two built-in ones last,
     * so a renderer wins over them for its types whether or not it is {@code Ordered}.
     */
    private static void registerStreamingBeans(BeanRegistry registry, Environment environment) {
        if (!environment.getProperty(STREAMING_ENABLED, Boolean, Boolean.TRUE)) {
            return
        }
        registry.registerBean(DEFAULT_RENDERER, DefaultStreamItemRenderer)
        boolean jackson = ClassUtils.isPresent(JSON_MAPPER_CLASS, ControllersAsyncGrailsPlugin.classLoader)
        if (jackson) {
            registry.registerBean(JACKSON_RENDERER, JacksonStreamItemRenderer) {
                it.supplier { context -> JacksonStreamItemRenderer.create(context) }
            }
        }
        String configuredTimeout = environment.getProperty(STREAMING_TIMEOUT)
        Duration timeout = configuredTimeout ? DurationStyle.detectAndParse(configuredTimeout) : null
        registry.registerBean('publisherActionResultTransformer', PublisherActionResultTransformer) {
            it.supplier { context ->
                List<StreamItemRenderer> builtIn = [context.bean(DEFAULT_RENDERER, StreamItemRenderer)]
                if (jackson) {
                    builtIn << context.bean(JACKSON_RENDERER, StreamItemRenderer)
                }
                // a copy, because the built-in renderers are added to it: Groovy 6 compiles toList() to the
                // JDK's Stream.toList(), whose list is unmodifiable, where Groovy 5 used its own mutable one
                List<StreamItemRenderer> renderers = new ArrayList<>(context.beanProvider(StreamItemRenderer).orderedStream()
                        .filter { StreamItemRenderer renderer -> builtIn.every { StreamItemRenderer own -> renderer !== own } }
                        .toList())
                renderers.addAll(builtIn)
                new PublisherActionResultTransformer(renderers, timeout)
            }
        }
    }

}
