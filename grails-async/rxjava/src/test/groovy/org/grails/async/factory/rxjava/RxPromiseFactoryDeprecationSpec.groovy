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
package org.grails.async.factory.rxjava

import java.lang.reflect.Field
import java.util.concurrent.atomic.AtomicBoolean

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import org.slf4j.LoggerFactory
import spock.lang.Specification

import grails.async.PromiseFactory
import org.grails.async.factory.PromiseFactoryBuilder

class RxPromiseFactoryDeprecationSpec extends Specification {

    Logger factoryLogger = LoggerFactory.getLogger(RxPromiseFactory) as Logger
    ListAppender<ILoggingEvent> appender = new ListAppender<>()

    void setup() {
        resetDeprecationWarning()
        appender.start()
        factoryLogger.addAppender(appender)
    }

    void cleanup() {
        factoryLogger.detachAppender(appender)
        appender.stop()
    }

    void 'the RxJava 1 promise classes are deprecated for removal since Grails 9.0'() {
        expect:
            [RxPromiseFactory, RxPromise].every { Class type ->
                Deprecated deprecated = type.getAnnotation(Deprecated)
                deprecated.forRemoval() && deprecated.since() == '9.0'
            }
    }

    void 'creating the RxJava 1 promise factory warns that it is deprecated'() {
        when: 'the factory is created'
            PromiseFactory factory = new RxPromiseFactory()

        then: 'the factory still works'
            factory.createPromise { 1 + 1 }.get() == 2

        and: 'a single warning names the module and its replacement'
            appender.list.size() == 1
            appender.list[0].level == Level.WARN
            appender.list[0].formattedMessage.contains('(grails-async-rxjava)')
            appender.list[0].formattedMessage.contains('deprecated')
            appender.list[0].formattedMessage.contains('grails-async-rxjava3')
    }

    void 'the RxJava 1 promise factory warns once per JVM however many are created'() {
        when: 'several factories are created'
            3.times { new RxPromiseFactory() }

        then: 'only the first one logs the deprecation warning'
            appender.list.size() == 1
    }

    void 'the service-loaded promise factory is the deprecated RxJava 1 factory and warns once'() {
        when: 'a promise factory is built through the registered service'
            PromiseFactory factory = PromiseFactoryBuilder.build()

        then: 'the RxJava 1 factory is still used'
            factory instanceof RxPromiseFactory
            appender.list.size() == 1
            appender.list[0].level == Level.WARN
    }

    /**
     * The deprecation warning is logged once per JVM, so reset the latch before each feature.
     * Other specs in this module construct the same class in the same fork.
     */
    private static void resetDeprecationWarning() {
        Field field = RxPromiseFactory.getDeclaredField('DEPRECATION_LOGGED')
        field.accessible = true
        ((AtomicBoolean) field.get(null)).set(false)
    }
}
