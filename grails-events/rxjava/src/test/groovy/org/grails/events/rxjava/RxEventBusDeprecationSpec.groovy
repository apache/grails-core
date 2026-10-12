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
package org.grails.events.rxjava

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import rx.schedulers.Schedulers
import org.slf4j.LoggerFactory
import spock.lang.Specification

import grails.events.bus.EventBus
import grails.events.bus.EventBusBuilder

class RxEventBusDeprecationSpec extends Specification {

    Logger busLogger = LoggerFactory.getLogger(RxEventBus) as Logger
    ListAppender<ILoggingEvent> appender = new ListAppender<>()

    void setup() {
        appender.start()
        busLogger.addAppender(appender)
    }

    void cleanup() {
        busLogger.detachAppender(appender)
        appender.stop()
    }

    void 'the RxJava 1 event bus is deprecated for removal since Grails 9.0'() {
        expect:
            RxEventBus.getAnnotation(Deprecated).forRemoval()
            RxEventBus.getAnnotation(Deprecated).since() == '9.0'
            EventWithReply.getAnnotation(Deprecated).forRemoval()
            EventWithReply.getAnnotation(Deprecated).since() == '9.0'
    }

    void 'creating the RxJava 1 event bus warns that it is deprecated'() {
        when: 'the event bus is created'
            EventBus eventBus = new RxEventBus(Schedulers.immediate())

        then: 'the event bus still works'
            def result = null
            eventBus.on('test') { result = it }
            eventBus.notify('test', 'bar')
            result == 'bar'

        and: 'a single warning names the module and its replacement'
            appender.list.size() == 1
            appender.list[0].level == Level.WARN
            appender.list[0].formattedMessage.contains('grails-events-rxjava')
            appender.list[0].formattedMessage.contains('deprecated')
            appender.list[0].formattedMessage.contains('grails-events-rxjava3')
    }

    void 'the service-loaded event bus is the deprecated RxJava 1 bus and warns once'() {
        when: 'an event bus is built through the registered service'
            EventBus eventBus = new EventBusBuilder().build()

        then: 'the RxJava 1 bus is still used'
            eventBus instanceof RxEventBus
            appender.list.size() == 1
            appender.list[0].level == Level.WARN
    }
}
