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

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import org.slf4j.LoggerFactory
import spock.lang.Specification

import grails.async.PromiseFactory
import grails.async.Promises

class RxPromiseFactoryDeprecationSpec extends Specification {

    Logger factoryLogger = LoggerFactory.getLogger(RxPromiseFactory) as Logger
    ListAppender<ILoggingEvent> appender = new ListAppender<>()

    void setup() {
        appender.start()
        factoryLogger.addAppender(appender)
    }

    void cleanup() {
        factoryLogger.detachAppender(appender)
        appender.stop()
    }

    void 'the RxJava 1 promise factory is deprecated for removal since Grails 9.0'() {
        expect:
            RxPromiseFactory.getAnnotation(Deprecated).forRemoval()
            RxPromiseFactory.getAnnotation(Deprecated).since() == '9.0'
    }

    void 'creating the RxJava 1 promise factory warns that it is deprecated'() {
        when: 'the factory is created'
            PromiseFactory factory = new RxPromiseFactory()

        then: 'the factory still works'
            factory.createPromise { 1 + 1 }.get() == 2

        and: 'a single warning names the module and its replacement'
            appender.list.size() == 1
            appender.list[0].level == Level.WARN
            appender.list[0].formattedMessage.contains('grails-async-rxjava')
            appender.list[0].formattedMessage.contains('deprecated')
            appender.list[0].formattedMessage.contains('grails-async-rxjava3')
    }

    void 'the service-loaded promise factory is the deprecated RxJava 1 factory'() {
        expect: 'the factory registered through META-INF/services is still used'
            Promises.promiseFactory instanceof RxPromiseFactory
    }
}
