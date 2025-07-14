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

package org.grails.events.bus.spring

import grails.events.bus.EventBus
import grails.events.bus.EventBusBuilder
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.grails.events.bus.ExecutorEventBus
import org.springframework.beans.factory.FactoryBean
import org.springframework.beans.factory.InitializingBean
import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationContextAware

import java.util.concurrent.ExecutorService

/**
 * Factory bean for usage within Spring
 *
 * @author Graeme Rocher
 * @since 3.3
 */
@Slf4j
@CompileStatic
class EventBusFactoryBean extends EventBusBuilder implements FactoryBean<EventBus>, InitializingBean, ApplicationContextAware {

    ApplicationContext applicationContext
    EventBus eventBus

    @Override
    EventBus getObject() throws Exception {
        return eventBus
    }

    @Override
    Class<?> getObjectType() {
        return EventBus
    }

    @Override
    boolean isSingleton() {
        return true
    }

    @Override
    void afterPropertiesSet() throws Exception {
        this.eventBus = super.build()
    }

    @Override
    protected EventBus createDefaultEventBus() {
        if (applicationContext.containsBean('grailsPromiseFactory')) {
            Object promiseFactory = applicationContext.getBean('grailsPromiseFactory')
            if (promiseFactory instanceof ExecutorService) {
                log.debug('Creating event bus from PromiseFactory {}', promiseFactory)
                return new ExecutorEventBus((ExecutorService) promiseFactory)
            }
        }
        return super.createDefaultEventBus()
    }
}
